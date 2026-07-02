# Mosaic feature source (design)

Status: design-only, MVP. Branch `feature/source-mosaic`.

## Goal

A `SimpleSource` of `type = mosaic` publishing one logical collection backed by a
**dynamic** set of underlying vector files ("mosaic tiles"). Tiles may be a mix of
backends (parquet-via-DuckDB, …). The set is not known at config time: a
**PostGIS index** is spatial-queried on every request to decide which tiles to
read, then their rows are returned as a plain **`UNION ALL`** (concat, no dedup).

Two parts:

1. **Index** — a PostGIS table with, per tile, at least a spatial `extent` plus the
   tile's `path` and `backend`. Queried each request with a bbox predicate.
2. **Tiles** — the backing files. Each matched `(path, backend)` is read on the fly
   as a feature source, cached by path.

Tiles are assumed non-overlapping and to **obey a single shared schema declared by
the mosaic** (id / geometry / properties). Tiles are *trusted* — no per-tile schema
probe. This is the key difference from `source-union` (which dedups by id and always
queries both fixed children).

## Decisions (locked)

- Combine: **UNION ALL concat**, no dedup. `numberMatched = -1` (omitted).
- Index: **PostGIS table**, `WHERE extent && ST_MakeEnvelope(?,?,?,?)`.
- Tiles: **per-tile, mixed backends**, built from the index row + shared schema,
  cached by path. No inline tile configs.
- Schema: **mosaic-declared, shared, trusted** — tiles are not probed.

## Config shape

```
collections.mymosaic.type = mosaic

# --- shared schema, declared once on the mosaic ---
collections.mymosaic.id             = id
collections.mymosaic.geometry       = geom
collections.mymosaic.geometry.type  = polygon
collections.mymosaic.srid.storage   = 3067
collections.mymosaic.properties     = name, class, updated
collections.mymosaic.name.type      = string
...                                              # ordinary hakunapi property config

# --- index (PostGIS) ---
collections.mymosaic.mosaic.index.db.jdbcUrl  = jdbc:postgresql://host/catalog
collections.mymosaic.mosaic.index.db.username = readonly
collections.mymosaic.mosaic.index.table       = mosaic_index
collections.mymosaic.mosaic.index.extentColumn = extent      # geometry col, default 'extent'
collections.mymosaic.mosaic.index.pathColumn    = path
collections.mymosaic.mosaic.index.backendColumn = backend
collections.mymosaic.mosaic.index.srid          = 3067       # extent column srid

# --- per-backend read settings (no path; path comes from index rows) ---
collections.mymosaic.mosaic.backend.duckdb.type = duckdb
collections.mymosaic.mosaic.backend.duckdb. ...              # e.g. bbox column name, dim
```

## How a tile is built (per matched row)

All backends already bind the file path via a mutable field on their own
`SimpleFeatureType` (DuckDB: `SQLFeatureType.setParquetPath`), and their producer
reads the columns straight from `col.getProperties()` — which is the mosaic schema.
So a tile does **not** need the backend's schema-probing `parse()`:

1. At startup, per configured backend, build a **prototype** feature type carrying
   that backend's engine/datasource + the mosaic's shared properties/id/geom, with
   an empty path. (A small `MosaicBackend` adapter per backend does this; MVP ships
   the DuckDB adapter.)
2. Per matched row: `proto.copyWithPath(row.path)` → a cheap FeatureType clone with
   the path set. Cache by `path`.
3. `col.withFt(tileFt)` rebinds the incoming request (filters, orderBy, the shared
   properties) to the tile; the backend producer runs the query against that file.

Trust model: if a tile file's columns don't match the declared schema, that tile's
query fails at read time — mosaic does not validate up front.

## Components

```
MosaicSource            implements SimpleSource   type=mosaic
    parse(): declare shared schema (reuse base property parsing), build MosaicIndex
             + Map<backend, MosaicBackend> prototypes → MosaicFeatureType

MosaicIndex             holds a PostGIS DataSource + table/column config
    select(double[] bbox): List<TileRef>            # SELECT path,backend WHERE extent && env
    TileRef: String path, String backend

MosaicBackend           per-backend adapter (interface); DuckDB impl for MVP
    prototype(sharedSchema, cfg): FeatureType with datasource + shared props, no path
    tileFeatureType(proto, path): FeatureType clone bound to path

MosaicFeatureType       extends SimpleFeatureType
    owns the shared schema (id/geom/properties declared on the mosaic)
    getFeatureProducer() → MosaicFeatureProducer(index, backends)

MosaicFeatureProducer   implements FeatureProducer
    getFeatures(req, col):
        bbox     = extractBbox(col.getFilters())     # null ⇒ no bbox ⇒ select all
        refs     = index.select(bbox)                # PostGIS spatial query
        streams  = refs.map(r ->
                       backends.get(r.backend)
                               .tileFeatureType(proto, r.path)     # path-cached
                               .getFeatureProducer()
                               .getFeatures(req, col.withFt(tileFt)))
        return new ConcatFeatureStream(streams)       # lazy, one tile open at a time
    getNumberMatched(): -1

ConcatFeatureStream     implements FeatureStream
    lazily opens each tile stream in order, one at a time, closing before the next;
    close() closes current + all not-yet-opened. O(1) concurrent open.

TileFeatureTypeCache    path → tile FeatureType (bounded LRU; avoids re-cloning)
```

### Bbox extraction

Walk `col.getFilters()`; for spatial ops (`INTERSECTS`, `INTERSECTS_INDEX`,
`WITHIN`, `CONTAINS`, `OVERLAPS`) `Filter.getValue()` is a JTS `Geometry` →
`getEnvelopeInternal()`. AND-combine to one bbox. No spatial filter ⇒ `null` ⇒
select all tiles (correct).

## Ordering / paging caveat

`UNION ALL` concat has no global id order, so single-cursor pagination does not span
tiles cleanly. Accepted for MVP (matches the "UNION ALL of the results" ask).
`numberMatched` omitted. Ordered cross-tile paging (N-way id-merge) is deferred; the
index + selection layer would be unchanged.

## Module layout

```
src/hakunapi-source-mosaic/
  pom.xml                         # core + postgis (index) + duckdb (tile backend)
  src/main/java/fi/nls/hakunapi/simple/mosaic/
    MosaicSource.java
    MosaicFeatureType.java
    MosaicFeatureProducer.java
    MosaicIndex.java              # PostGIS spatial query → List<TileRef>
    MosaicBackend.java            # adapter interface
    DuckDBMosaicBackend.java      # MVP backend
    ConcatFeatureStream.java
  src/test/java/fi/nls/hakunapi/simple/mosaic/
    MosaicIndexTest.java          # bbox → SQL predicate + row mapping (mock/H2)
    ConcatFeatureStreamTest.java  # concat order + lazy open + close semantics
    MosaicFeatureProducerTest.java# bbox extract → refs → concat, all-tiles fallback
```

Register in root `pom.xml` `<modules>`; enable via `MosaicSource` in `db.classes`.

## Deferred (post-MVP)

- More tile backends behind `MosaicBackend` (fgb, gpkg, native pg).
- R-tree/local cache of the index to cut per-request round-trips.
- Temporal / attribute pruning columns in the index.
- Ordered cross-tile paging via N-way id-merge.
- Index in GeoParquet/DuckDB instead of PostGIS (swap `MosaicIndex` impl).
- **Async tile fetch**: current `ConcatFeatureStream` opens tiles sequentially
  (one at a time). Could instead fire matched tile queries concurrently and stream
  each as it's ready — cuts latency when many tiles match. Trades the O(1)
  concurrent-open guarantee for parallelism + a bounded worker/prefetch pool.
