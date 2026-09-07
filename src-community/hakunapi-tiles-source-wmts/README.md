# hakunapi-tiles-source-wmts

Community module proxying an upstream
[WMTS](https://www.ogc.org/standard/wmts/) server as OGC API - Tiles map tiles,
served under `/collections/{collectionId}/map/tiles`.

Tiles are fetched from the upstream on request and relayed verbatim — no
decoding, re-encoding or caching. Both KVP and RESTful GetTile are supported.

## Configuration

The source registers as type `wmts`. Every hakunapi tile matrix set the layer is
served in must be mapped to its upstream WMTS tile matrix set name.

```
tiles.layers=basemap
tiles.layers.basemap.type=wmts
tiles.layers.basemap.title=Proxied WMTS layer
tiles.layers.basemap.tileMatrixSets=WebMercatorQuad

tiles.layers.basemap.wmts.baseUrl=https://example.org/wmts
tiles.layers.basemap.wmts.layer=upstream:layer
tiles.layers.basemap.wmts.style=default
tiles.layers.basemap.wmts.format=image/png
tiles.layers.basemap.wmts.kvp=true

# Required: hakunapi tile matrix set id -> upstream WMTS name
tiles.layers.basemap.wmts.tms.WebMercatorQuad=GoogleMapsCompatible
```

RESTful instead of KVP:

```
tiles.layers.basemap.wmts.kvp=false
tiles.layers.basemap.wmts.urlTemplate=https://example.org/wmts/upstream:layer/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png
```

### Upstream query parameters

Fixed parameters appended to every upstream request — an API key, say:

```
tiles.layers.basemap.wmts.param.api-key=secret
```

Inbound request parameters may be forwarded to the upstream by naming them
explicitly. An inbound value overrides a fixed default of the same name:

```
tiles.layers.basemap.wmts.passthrough=time,elevation
```

Only the named parameters are forwarded. Anything else in the inbound request is
dropped rather than proxied.

## Usage

```xml
<dependency>
    <groupId>fi.nls.hakunapi</groupId>
    <artifactId>hakunapi-tiles-source-wmts</artifactId>
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
