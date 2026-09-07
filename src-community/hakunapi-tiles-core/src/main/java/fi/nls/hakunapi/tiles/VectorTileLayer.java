package fi.nls.hakunapi.tiles;

import java.util.List;

/**
 * A vector tile layer: the OGC API - Tiles analog of a feature collection,
 * whose tiles are rendered on the fly from feature collections by a
 * {@link CollectionTileSource}. Exposed under the geodata/vector tile resources
 * {@code /tiles} (dataset) and {@code /collections/{collectionId}/tiles}
 * (collection).
 *
 * <p>The counterpart for precomputed raster tiles is {@link MapTileLayer},
 * served from a store via {@link TileSource}. The two layer types are
 * deliberately separate with no shared base (see {@link MapTileLayer}). Concrete
 * vector sources subclass this to carry their generator and the ids of the
 * feature collections rendered into the tile.
 */
public class VectorTileLayer {

    private final String id;
    private final String title;
    private final String description;
    private final CollectionTileSource source;
    /** Ids of the feature collections rendered into the tile (one MVT layer each). */
    private final List<String> collectionIds;
    /** Identifiers of the {@link TileMatrixSet}s this layer is available in. */
    private final List<String> tileMatrixSetIds;
    /** Tile media types this layer can produce (e.g. application/vnd.mapbox-vector-tile). */
    private final List<String> mediaTypes;
    /** Optional WGS84 bounding box [minLon, minLat, maxLon, maxLat]; may be null. */
    private final double[] bbox;

    public VectorTileLayer(String id, String title, String description, CollectionTileSource source,
            List<String> collectionIds, List<String> tileMatrixSetIds, List<String> mediaTypes, double[] bbox) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.source = source;
        this.collectionIds = List.copyOf(collectionIds);
        this.tileMatrixSetIds = List.copyOf(tileMatrixSetIds);
        this.mediaTypes = List.copyOf(mediaTypes);
        this.bbox = bbox;
    }

    public String getId() {
        return id;
    }

    /** The {@link CollectionTileSource} that renders this layer's tiles. */
    public CollectionTileSource getSource() {
        return source;
    }

    /** Ids of the feature collections this tile layer renders (one MVT layer each). */
    public List<String> getCollectionIds() {
        return collectionIds;
    }

    /**
     * Whether this layer is a tileset <em>of one collection</em>: it renders
     * exactly the collection it is named after. Only such a layer belongs under
     * {@code /collections/{collectionId}/tiles}, where the path segment is a
     * collection id. A layer combining several collections - or named something
     * that is not a collection at all - is a dataset tileset, served from
     * {@code /tiles}, which advertises the collections it carries.
     */
    public boolean isCollectionTileset() {
        return collectionIds.size() == 1 && id.equals(collectionIds.get(0));
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getTileMatrixSetIds() {
        return tileMatrixSetIds;
    }

    public boolean supportsTileMatrixSet(String tileMatrixSetId) {
        return tileMatrixSetIds.contains(tileMatrixSetId);
    }

    public List<String> getMediaTypes() {
        return mediaTypes;
    }

    public double[] getBbox() {
        return bbox;
    }

}
