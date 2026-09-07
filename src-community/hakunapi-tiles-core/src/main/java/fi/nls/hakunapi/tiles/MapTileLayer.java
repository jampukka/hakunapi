package fi.nls.hakunapi.tiles;

import java.util.List;

/**
 * A map (raster) tile layer: precomputed tiles served from a store
 * ({@link TileSource}) such as WMTS, PMTiles or GeoPackage. Exposed under the
 * OGC API - Tiles map tile resources {@code /map/tiles} (dataset) and
 * {@code /collections/{collectionId}/map/tiles} (collection).
 *
 * <p>The data-plane counterpart for vector tiles is {@link VectorTileLayer},
 * rendered on the fly by a {@link CollectionTileSource}. The two layer types are
 * deliberately separate with no shared base: a map layer always has a backing
 * {@link TileSource} and needs no feature access, while a vector layer carries a
 * generator and the ids of the feature collections it renders. Concrete map
 * sources subclass this to carry their store-specific config.
 */
public class MapTileLayer {

    private final String id;
    private final String title;
    private final String description;
    private final TileSource source;
    /** Identifiers of the {@link TileMatrixSet}s this layer is available in. */
    private final List<String> tileMatrixSetIds;
    /** Tile media types this layer can produce (e.g. image/png, image/jpeg). */
    private final List<String> mediaTypes;
    /** Optional WGS84 bounding box [minLon, minLat, maxLon, maxLat]; may be null. */
    private final double[] bbox;

    public MapTileLayer(String id, String title, String description, TileSource source,
            List<String> tileMatrixSetIds, List<String> mediaTypes, double[] bbox) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.source = source;
        this.tileMatrixSetIds = List.copyOf(tileMatrixSetIds);
        this.mediaTypes = List.copyOf(mediaTypes);
        this.bbox = bbox;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TileSource getSource() {
        return source;
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
