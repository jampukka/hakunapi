# hakunapi-tiles-source-pmtiles

Community module serving tiles from a
[PMTiles v3](https://github.com/protomaps/PMTiles) archive as OGC API - Tiles
map tiles, under `/collections/{collectionId}/map/tiles`.

PMTiles is a single-file tile archive addressed by byte range, so an archive is
read in place — from a local file, or from a remote HTTPS URL using HTTP range
requests, with no download or extraction step. The directory is parsed once at
startup; a tile read is then a range read plus a relay of the stored bytes.

## Configuration

The source registers as type `pmtiles`. Give either a local `path` or a remote
`url`.

```
tiles.layers=basemap
tiles.layers.basemap.type=pmtiles
tiles.layers.basemap.title=Basemap
tiles.layers.basemap.tileMatrixSets=WebMercatorQuad

tiles.layers.basemap.pmtiles.path=/data/basemap.pmtiles
```

Remote archive:

```
tiles.layers.basemap.pmtiles.url=https://example.org/basemap.pmtiles
```

| key | meaning |
| --- | --- |
| `pmtiles.path` | local archive file |
| `pmtiles.url` | remote archive over HTTPS, read with range requests |
| `pmtiles.format` | media type advertised; taken from the archive header when unset |

PMTiles addresses tiles by XYZ zoom level, so every tile matrix set the layer is
served in must use numeric tile matrix ids. `WebMercatorQuad` does.

### Compression

Tile bytes are relayed exactly as stored. When the archive's `tile_compression`
is gzip, the tiles are served with `Content-Encoding: gzip` rather than being
decompressed and re-compressed; other compressions are served without the
header.

The archive's own directory and metadata must be stored uncompressed or gzipped
— brotli and zstd `internal_compression` are rejected at startup, since
decompressing them would mean a dependency.

## Usage

```xml
<dependency>
    <groupId>fi.nls.hakunapi</groupId>
    <artifactId>hakunapi-tiles-source-pmtiles</artifactId>
</dependency>
```

The source is discovered at runtime via `ServiceLoader`, so it is enough for the
module to be on the classpath. Serving the tiles over HTTP additionally needs
`hakunapi-tiles-servlet-jakarta`.

## Status and support

This is a community module under `src-community/`. It is built and
(automatically) tested together with the Hakunapi core modules, but it is not
covered by the support the National Land Survey of Finland provides for core
modules.

See [SUBMITTERS](SUBMITTERS) for authors and
[CONTRIBUTING.md](../../CONTRIBUTING.md) for how to contribute.
