# hakunapi-source-duckdb

A `SimpleSource` that serves features from [GeoParquet](https://geoparquet.org/) files through
[DuckDB](https://duckdb.org/) and its `spatial` extension.

The source is JDBC-based and reuses the same query/streaming machinery as `hakunapi-source-postgis`.
Each collection is backed by a parquet file (or glob); queries run against
`read_parquet('<path>')`. The `spatial` extension is installed and loaded automatically at startup.

## Enabling the source

Add the source class to `db.classes` in your service config:

```properties
db.classes=fi.nls.hakunapi.simple.duckdb.DuckDBSimpleSource
```

(If it is the only source, `default.collections.type` is not needed; otherwise set
`collections.<id>.type=duckdb`.)

## Collection configuration

```properties
collections=roads

# Path or glob to the GeoParquet file(s). Becomes read_parquet('<table>').
collections.roads.table=/data/roads.parquet

# Relation alias used in generated SQL. Defaults to the collection id.
#collections.roads.relation=roads

collections.roads.id.mapping=id

# Geometry metadata is config-driven: DuckDB has no geometry_columns catalog.
collections.roads.geometry.mapping=geom
collections.roads.geometry.type=LINESTRING
collections.roads.srid.storage=4326
#collections.roads.geometry.dim=2
#collections.roads.geometry.nullable=true

# Properties; "*" discovers all non-reserved columns from the file schema.
collections.roads.properties=name,class
```

## bbox covering and performance

GeoParquet read performance comes from row-group / page pruning, not from the WKB decode. Two cases:

- **GeoParquet 1.1 covering bbox column** — a `struct<xmin,ymin,xmax,ymax>` column (conventionally
  named `bbox`). When present it is auto-detected and the BBOX filter emits a predicate against the
  struct fields so DuckDB can prune row groups, with an exact `ST_Intersects` refinement. Override
  detection with `collections.<id>.geometry.bbox=<column>`.
- **GeoParquet 2.0 native `GEOMETRY` type** — DuckDB prunes on the geometry column's own statistics;
  the BBOX filter falls back to `ST_Intersects` only.

## Notes / current limitations

- Geometry is read as plain WKB via `ST_AsWKB` and decoded by `HakunaGeometryEWKB` (SRID comes from
  config, not the blob).
- Single-file collections only; cross-file joins are not yet supported.
- Reprojection: if DuckDB-side projection (`sourceproj=true`) is not used, geometries are reprojected
  in the JVM by the core machinery.
