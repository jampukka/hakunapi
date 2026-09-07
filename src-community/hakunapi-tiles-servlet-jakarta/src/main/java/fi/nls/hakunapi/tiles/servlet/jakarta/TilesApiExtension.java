package fi.nls.hakunapi.tiles.servlet.jakarta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.ConformanceClass;
import fi.nls.hakunapi.core.extension.ApiExtension;
import fi.nls.hakunapi.core.operation.OperationImpl;
import fi.nls.hakunapi.core.schemas.CollectionInfo;
import fi.nls.hakunapi.core.schemas.Crs;
import fi.nls.hakunapi.core.schemas.Extent;
import fi.nls.hakunapi.core.schemas.Link;
import fi.nls.hakunapi.core.schemas.SpatialExtent;
import fi.nls.hakunapi.core.util.U;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TilesLinkRel;
import fi.nls.hakunapi.tiles.TilesServiceConfig;
import fi.nls.hakunapi.tiles.VectorTileLayer;

/**
 * Contributes the OGC API - Tiles resources to the landing page and the
 * collections resources, so that hakunapi-simple-servlet-jakarta does not have
 * to know about tiles.
 *
 * <p>Four contributions: the shared {@code /tileMatrixSets} link and the dataset
 * {@code /tiles} link on the landing page, a tilesets link on every feature
 * collection that has a tileset of its own, and each map (raster) tile layer as a
 * collection of its own - in OGC API - Tiles a collection is any geospatial data
 * resource, so a map tile layer is a collection with no {@code items}.
 *
 * <p>Only a layer that is a {@linkplain VectorTileLayer#isCollectionTileset()
 * tileset of one collection} is published under {@code /collections}; a layer
 * combining several collections is a dataset tileset and lives at {@code /tiles}.
 */
public class TilesApiExtension implements ApiExtension {

    /** Resource name for {@link #getCollectionIds}: vector tiles of a collection. */
    public static final String VECTOR_TILES = "tiles";

    /** Resource name for {@link #getCollectionIds}: map (raster) tiles of a collection. */
    public static final String MAP_TILES = "map/tiles";

    private static final String JSON = "application/json";

    /** {@code f} value selecting a Mapbox Vector Tile. */
    private static final String MVT = "mvt";

    private final TilesServiceConfig tiles;

    public TilesApiExtension(TilesServiceConfig tiles) {
        if (!tiles.hasVectorLayers() && !tiles.hasMapLayers()) {
            throw new IllegalArgumentException("No tile layers, do not publish OGC API - Tiles!");
        }
        this.tiles = tiles;
    }

    @Override
    public String getName() {
        return "OGC API - Tiles";
    }

    @Override
    public List<ConformanceClass> getConformanceClasses() {
        List<ConformanceClass> classes = new ArrayList<>();
        classes.add(ConformanceClass.TILES_CORE);
        classes.add(ConformanceClass.TILES_TILESET);
        classes.add(ConformanceClass.TILES_TILESETS_LIST);
        classes.add(ConformanceClass.TILES_GEODATA_TILESETS);
        if (tiles.hasVectorLayers()) {
            classes.add(ConformanceClass.TILES_DATASET_TILESETS);
        }
        classes.add(ConformanceClass.TILES_OAS30);
        classes.add(ConformanceClass.TILEMATRIXSET);
        // Claimed from the media types the layers actually offer, not from the
        // presence of vector layers - a generator may be configured to produce
        // something else.
        if (servesMvt()) {
            classes.add(ConformanceClass.TILES_MVT);
        }
        return classes;
    }

    @Override
    public List<OperationImpl> getOperations() {
        List<OperationImpl> operations = new ArrayList<>();
        operations.add(new OperationImpl(new GetTileMatrixSetsOperation(), TileMatrixSetsImpl.class));
        if (tiles.hasVectorLayers()) {
            operations.add(new OperationImpl(new GetTilesetsListOperation(), GetCollectionTilesetsImpl.class));
            operations.add(new OperationImpl(new GetTileOperation(), GetCollectionTileOperation.class));
            operations.add(new OperationImpl(new GetDatasetTilesetsListOperation(), GetDatasetTilesetsImpl.class));
            operations.add(new OperationImpl(new GetDatasetTileOperation(), GetDatasetTileImpl.class));
        }
        if (tiles.hasMapLayers()) {
            operations.add(new OperationImpl(new GetMapTilesetsListOperation(), GetCollectionMapTilesetsImpl.class));
            operations.add(new OperationImpl(new GetMapTileOperation(), GetCollectionMapTileOperation.class));
        }
        return operations;
    }

    @Override
    public Map<Class<?>, Object> getInjectables() {
        return Map.of(TilesServiceConfig.class, tiles);
    }

    @Override
    public List<Link> getLandingPageLinks(String baseUrl, Map<String, String> queryParams) {
        String query = U.toQuery(queryParams);
        List<Link> links = new ArrayList<>();
        links.add(new Link(baseUrl + "/tileMatrixSets" + query, TilesLinkRel.TILING_SCHEMES, JSON,
                "The tile matrix sets supported by this service"));
        if (tiles.hasVectorLayers()) {
            links.add(new Link(baseUrl + "/tiles" + query, TilesLinkRel.TILESETS_VECTOR, JSON,
                    "Vector tilesets of the dataset"));
        }
        return links;
    }

    @Override
    public List<Link> getCollectionLinks(FeatureType ft, String baseUrl, Map<String, String> queryParams) {
        if (!hasVectorLayer(ft.getName())) {
            return List.of();
        }
        String href = baseUrl + "/collections/" + ft.getName() + "/tiles" + U.toQuery(queryParams);
        return List.of(new Link(href, TilesLinkRel.TILESETS_VECTOR, JSON,
                "Vector tilesets for " + ft.getName()));
    }

    @Override
    public List<CollectionInfo> getCollections(String baseUrl, Map<String, String> queryParams) {
        List<CollectionInfo> collections = new ArrayList<>();
        for (MapTileLayer layer : tiles.getMapLayers()) {
            collections.add(toCollectionInfo(layer, baseUrl));
        }
        return collections;
    }

    @Override
    public List<String> getCollectionIds(String resource) {
        List<String> ids;
        switch (resource) {
        case VECTOR_TILES:
            ids = new ArrayList<>();
            for (VectorTileLayer layer : tiles.getVectorLayers()) {
                if (layer.isCollectionTileset()) {
                    ids.add(layer.getId());
                }
            }
            return ids;
        case MAP_TILES:
            ids = new ArrayList<>();
            for (MapTileLayer layer : tiles.getMapLayers()) {
                ids.add(layer.getId());
            }
            return ids;
        default:
            return List.of();
        }
    }

    /**
     * {@code f=mvt} asks for the vector tile media type, so a tile can be
     * fetched with the same query parameter the rest of the API uses instead of
     * only through an Accept header. Only offered when a layer actually serves
     * MVT, for the same reason the conformance class is.
     */
    @Override
    public Map<String, List<String>> getFormatMediaTypes() {
        if (!servesMvt()) {
            return Map.of();
        }
        return Map.of(MVT, List.of(GetCollectionTileOperation.VECTOR_TILE));
    }

    /** Whether any vector tile layer offers the MVT media type. */
    private boolean servesMvt() {
        for (VectorTileLayer layer : tiles.getVectorLayers()) {
            if (layer.getMediaTypes().contains(GetCollectionTileOperation.VECTOR_TILE)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVectorLayer(String collectionId) {
        VectorTileLayer layer = tiles.getVectorLayer(collectionId).orElse(null);
        return layer != null && layer.isCollectionTileset();
    }

    /**
     * Collection metadata for a map tile layer, which has no feature collection:
     * no {@code items} link, only a map tilesets link, and {@code itemType} of
     * {@code "map"} per OGC API - Maps. The WGS84 spatial extent comes from the
     * layer's bbox when configured.
     */
    private CollectionInfo toCollectionInfo(MapTileLayer layer, String baseUrl) {
        String id = layer.getId();

        List<Link> links = new ArrayList<>();
        links.add(new Link(baseUrl + "/collections/" + id, "self", JSON, "This document"));
        links.add(new Link(baseUrl + "/collections/" + id + "/map/tiles", TilesLinkRel.TILESETS_MAP,
                JSON, "Map tilesets for " + id));

        double[] bbox = layer.getBbox();
        SpatialExtent spatialExtent = bbox == null ? null : new SpatialExtent(bbox, Crs.CRS84);
        Extent extent = new Extent(spatialExtent, null);

        CollectionInfo ci = new CollectionInfo(id, layer.getTitle(), layer.getDescription(),
                links, extent, null, null);
        ci.setItemType("map");
        return ci;
    }

}
