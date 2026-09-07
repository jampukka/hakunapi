# hakunapi-tiles-servlet-jakarta

Community module serving [OGC API - Tiles](https://ogcapi.ogc.org/tiles/) over
HTTP: the JAX-RS resources, and the `ApiExtension` that plugs them into the
Hakunapi servlet layer.

`hakunapi-simple-servlet-jakarta` knows nothing about tiles. This module
contributes everything through the `ApiExtension` seam in `hakunapi-core`:

- the tile resources and their OpenAPI operations
- the conformance classes claimed in `/conformance`
- the `/tileMatrixSets` link on the landing page
- a tilesets link on every feature collection that has a vector tile layer
- each map tile layer as a collection of its own — in OGC API a collection is
  any geospatial data resource, so a map tile layer is a collection with no
  `items`

## Resources

| path | what |
| --- | --- |
| `/tileMatrixSets` | tile matrix sets supported by the service |
| `/tileMatrixSets/{tileMatrixSetId}` | one tile matrix set definition |
| `/collections/{collectionId}/tiles` | vector tilesets of a collection |
| `/collections/{collectionId}/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}` | a vector tile |
| `/collections/{collectionId}/map/tiles` | map tilesets of a collection |
| `/collections/{collectionId}/map/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}` | a map tile |

Resources are registered only for the kinds of layer actually configured.

## Usage

Deploy `TilesContextListener` in place of `HakunaContextListener`. It parses the
tile configuration, discovers the tile sources on the classpath, and hands the
servlet layer a `TilesApiExtension`:

```xml
<listener>
    <listener-class>
        fi.nls.hakunapi.tiles.servlet.jakarta.TilesContextListener
    </listener-class>
</listener>
```

Add this module plus the tile sources the deployment needs:

```xml
<dependency>
    <groupId>fi.nls.hakunapi</groupId>
    <artifactId>hakunapi-tiles-servlet-jakarta</artifactId>
</dependency>
<dependency>
    <groupId>fi.nls.hakunapi</groupId>
    <artifactId>hakunapi-tiles-source-vectortile</artifactId>
</dependency>
```

When no tile layers are configured, no tile resources are registered and no
tiles conformance is claimed — the service is a plain OGC API - Features
service.

See [hakunapi-tiles-core](../hakunapi-tiles-core/README.md) for the
configuration.

## Status and support

This is a community module under `src-community/`. It is built and
(automatically) tested together with the Hakunapi core modules, but it is not
covered by the support the National Land Survey of Finland provides for core
modules.

See [SUBMITTERS](SUBMITTERS) for authors and
[CONTRIBUTING.md](../../CONTRIBUTING.md) for how to contribute.
