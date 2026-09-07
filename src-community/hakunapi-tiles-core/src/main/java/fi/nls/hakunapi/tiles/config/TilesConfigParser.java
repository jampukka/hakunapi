package fi.nls.hakunapi.tiles.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.core.util.CrsUtil;
import fi.nls.hakunapi.tiles.CollectionTileSource;
import fi.nls.hakunapi.tiles.DefaultTileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileMatrix;
import fi.nls.hakunapi.tiles.TileMatrixSet;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.TileSource;
import fi.nls.hakunapi.tiles.VectorTileLayer;

/**
 * Parses OGC API - Tiles configuration from the shared hakunapi {@code .properties}
 * file. Kept in the tiles module (wrapping {@link HakunaConfigParser}) so that
 * hakunapi-core stays free of any dependency on the tiles model.
 *
 * <p>Recognised properties:
 * <ul>
 *   <li>{@code tiles.vector=true} - auto-publish vector tiles for every geometry collection</li>
 *   <li>{@code tiles.layers=<id>,<id>,...} - explicitly configured tile layer ids</li>
 *   <li>{@code tiles.layers.<id>.type=<sourceType>} - which tile source backs a layer
 *       (source impls are discovered on the classpath via {@code ServiceLoader})</li>
 *   <li>{@code tileMatrixSets.<id>.*} - additional tile matrix set definitions (reserved)</li>
 * </ul>
 */
public class TilesConfigParser {

    private static final Logger LOG = LoggerFactory.getLogger(TilesConfigParser.class);

    public static final String PROP_LAYERS = "tiles.layers";
    public static final String PROP_TILE_MATRIX_SETS = "tileMatrixSets";

    /**
     * When {@code true}, every feature collection with a geometry is published as a
     * vector tile layer (sharing the collection id) using the service-default
     * generator, with no per-collection tile configuration required. Explicit
     * {@code tiles.layers.<id>} definitions take precedence over the toggle.
     */
    public static final String PROP_VECTOR = "tiles.vector";

    /**
     * OGC standardized rendering pixel size in metres (0.28 mm), used to relate a
     * tile matrix cell size (ground resolution) to its scale denominator.
     * See OGC 17-083r4 §6.2.
     */
    public static final double STANDARDIZED_PIXEL_SIZE = 0.00028;

    /**
     * Relative tolerance for the "extent is a whole number of tiles" check; the
     * tile count per axis must be within this fraction of an integer. Sized to
     * absorb double rounding in extent/resolution constants (typically a few
     * ULPs) while still rejecting genuinely misaligned extents.
     */
    public static final double WHOLE_TILE_EPSILON = 1e-6;

    private final HakunaConfigParser cfg;

    public TilesConfigParser(HakunaConfigParser cfg) {
        this.cfg = cfg;
    }

    /** @return the configured tile layer ids, or an empty array if tiles are not configured */
    public String[] readTileLayerIds() {
        return cfg.getMultiple(PROP_LAYERS);
    }

    public boolean hasTileLayers() {
        return readTileLayerIds().length > 0;
    }

    /** @return whether the {@code tiles.vector} auto-publish toggle is enabled */
    public boolean isVectorAutoPublishEnabled() {
        return Boolean.parseBoolean(cfg.get(PROP_VECTOR, "false"));
    }


    /**
     * Build the {@link TileMatrixSetRegistry}: the built-in WebMercatorQuad plus
     * any sets defined via {@code tileMatrixSets.*} config (none parsed yet -
     * reserved for future generic definitions).
     */
    public TileMatrixSetRegistry readTileMatrixSets() {
        Collection<TileMatrixSet> additional = new ArrayList<>();
        for (String id : cfg.getMultiple(PROP_TILE_MATRIX_SETS)) {
            additional.add(readTileMatrixSet(id));
        }
        return new DefaultTileMatrixSetRegistry(additional);
    }

    /**
     * Parse a single config-defined tile matrix set. The tiling scheme is given
     * compactly: a CRS ({@code srid}) + extent {@code bbox} and a {@code resolutions} array
     * (ground units per pixel, one entry per zoom level). The point of origin is
     * the top-left corner of the extent ({@code minX, maxY}). Per level the cell
     * size is the resolution, the scale denominator is {@code resolution /}
     * {@link #STANDARDIZED_PIXEL_SIZE}, and the matrix width/height are derived
     * from the extent: {@code extentWidth / (resolution * tileWidth)}.
     *
     * <p>Tile matrix ids default to the level index ({@code 0..n-1}); supply
     * {@code matrixIds} to override (OpenLayers style, e.g. when the upstream
     * scheme labels levels differently).
     *
     * <p>The extent must be an exact whole number of tiles at every level: for
     * each resolution {@code extentWidth / (resolution * tileWidth)} (and the
     * height analog) must be within {@link #WHOLE_TILE_EPSILON} of an integer,
     * otherwise the definition is rejected. This catches a {@code bbox} that does
     * not line up with the declared {@code resolutions} and {@code tileWidth}.
     */
    public TileMatrixSet readTileMatrixSet(String id) {
        String p = PROP_TILE_MATRIX_SETS + "." + id + ".";
        String title = cfg.get(p + "title", id);
        int srid = Integer.parseInt(cfg.getRequired(p + "srid"));
        String crs = CrsUtil.toUri(srid);
        int tileWidth = Integer.parseInt(cfg.get(p + "tileWidth", "256"));
        int tileHeight = Integer.parseInt(cfg.get(p + "tileHeight", "256"));

        double[] bbox = parseDoubles(cfg.getRequired(p + "bbox"));
        if (bbox.length != 4) {
            throw new IllegalArgumentException("tileMatrixSet " + id + ": bbox must be minX,minY,maxX,maxY");
        }
        double minX = bbox[0], minY = bbox[1], maxX = bbox[2], maxY = bbox[3];

        // Point of origin is the top-left corner of the extent.
        double originX = minX;
        double originY = maxY;

        double[] resolutions = parseDoubles(cfg.getRequired(p + "resolutions"));
        if (resolutions.length == 0) {
            throw new IllegalArgumentException("tileMatrixSet " + id + ": resolutions must not be empty");
        }
        String[] matrixIds = cfg.getMultiple(p + "matrixIds");
        if (matrixIds.length != 0 && matrixIds.length != resolutions.length) {
            throw new IllegalArgumentException("tileMatrixSet " + id
                    + ": matrixIds length must match resolutions length");
        }

        double extentWidth = maxX - minX;
        double extentHeight = maxY - minY;
        List<TileMatrix> matrices = new ArrayList<>(resolutions.length);
        for (int z = 0; z < resolutions.length; z++) {
            double res = resolutions[z];
            String matrixId = matrixIds.length != 0 ? matrixIds[z] : Integer.toString(z);
            double scaleDenominator = res / STANDARDIZED_PIXEL_SIZE;
            int matrixWidth = wholeTiles(id, matrixId, "width", extentWidth, res, tileWidth);
            int matrixHeight = wholeTiles(id, matrixId, "height", extentHeight, res, tileHeight);
            matrices.add(new TileMatrix(matrixId, scaleDenominator, res, originX, originY,
                    tileWidth, tileHeight, matrixWidth, matrixHeight));
        }
        return new TileMatrixSet(id, title, crs, srid, matrices);
    }

    /**
     * The number of tiles spanning {@code extent} at the given resolution,
     * required to be a whole number (within {@link #WHOLE_TILE_EPSILON}).
     *
     * @throws IllegalArgumentException if the extent is not an integer number of
     *                                  tiles for this resolution
     */
    private static int wholeTiles(String tmsId, String matrixId, String axis,
            double extent, double res, int tilePixels) {
        double tiles = extent / (res * tilePixels);
        long rounded = Math.round(tiles);
        if (rounded <= 0 || Math.abs(tiles - rounded) > WHOLE_TILE_EPSILON * rounded) {
            throw new IllegalArgumentException("tileMatrixSet " + tmsId + " matrix " + matrixId
                    + ": extent " + axis + " is not a whole number of tiles at resolution " + res
                    + " (got " + tiles + " tiles)");
        }
        return (int) rounded;
    }

    private static double[] parseDoubles(String csv) {
        String[] parts = csv.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }

    /**
     * The configured source {@code type} of a tile layer, or {@code null} if not
     * set (and no service-wide {@code default.tiles.layers.type} default).
     */
    public String readTileLayerType(String layerId) {
        String p = PROP_LAYERS + "." + layerId + ".";
        return cfg.get(p + "type", cfg.get("default.tiles.layers.type"));
    }

    /**
     * Parse a single map (raster) tile layer using the appropriate
     * {@link TileSource}. The caller has already determined that {@code layerId}'s
     * configured {@code type} is one of {@code sourcesByType}.
     *
     * @param path           the config file path
     * @param sourcesByType  map tile sources keyed by {@link TileSource#getType()}
     * @param tms            the service tile matrix set registry
     * @param layerId        the tile layer id
     */
    public MapTileLayer readMapTileLayer(Path path, Map<String, TileSource> sourcesByType,
            TileMatrixSetRegistry tms, String layerId) throws Exception {
        LOG.info("map tile layer {}", layerId);
        TileSource source = resolveSource(sourcesByType, layerId);
        return source.parse(cfg, path, tms, layerId);
    }

    /**
     * Parse a single vector tile layer using the appropriate
     * {@link CollectionTileSource}. The caller has already determined that
     * {@code layerId}'s configured {@code type} is one of {@code sourcesByType}.
     *
     * @param path           the config file path
     * @param sourcesByType  vector tile sources keyed by
     *                       {@link CollectionTileSource#getType()}
     * @param tms            the service tile matrix set registry
     * @param layerId        the tile layer id
     */
    public VectorTileLayer readVectorTileLayer(Path path, Map<String, CollectionTileSource> sourcesByType,
            TileMatrixSetRegistry tms, String layerId) throws Exception {
        LOG.info("vector tile layer {}", layerId);
        CollectionTileSource source = resolveSource(sourcesByType, layerId);
        return source.parse(cfg, path, tms, layerId);
    }

    /**
     * Pick the source backing {@code layerId} from a typed registry: the sole
     * source when there is exactly one, otherwise the one matching the layer's
     * configured {@code type}.
     */
    private <S> S resolveSource(Map<String, S> sourcesByType, String layerId) {
        if (sourcesByType.size() == 1) {
            return sourcesByType.values().iterator().next();
        }
        String type = readTileLayerType(layerId);
        if (type == null) {
            throw new IllegalArgumentException("Missing tile source type for layer: " + layerId);
        }
        S source = sourcesByType.get(type);
        if (source == null) {
            throw new IllegalArgumentException("Unknown tile source type: " + type + ", layer: " + layerId);
        }
        return source;
    }

    /**
     * Supported tile matrix set ids for a layer. Resolution order: the
     * per-layer {@code tiles.layers.<id>.tileMatrixSets}, else the service-wide
     * {@code default.tiles.layers.tileMatrixSets} (which also applies to
     * auto-published vector layers), else the built-in WebMercatorQuad.
     */
    public List<String> readTileMatrixSetIds(String layerId) {
        String p = PROP_LAYERS + "." + layerId + ".";
        String[] ids = cfg.getMultiple(p + "tileMatrixSets", null);
        if (ids == null) {
            ids = cfg.getMultiple("default." + PROP_LAYERS + ".tileMatrixSets",
                    new String[] { DefaultTileMatrixSetRegistry.WEB_MERCATOR_QUAD });
        }
        return Arrays.asList(ids);
    }

}
