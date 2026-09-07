package fi.nls.hakunapi.tiles;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import fi.nls.hakunapi.core.config.HakunaConfigParser;

/**
 * Pluggable source of tiles, the OGC API - Tiles analog of
 * {@code fi.nls.hakunapi.core.SimpleSource}. Implementations are discovered on the
 * classpath via {@link java.util.ServiceLoader} (a
 * {@code META-INF/services/fi.nls.hakunapi.tiles.TileSource} entry) and keyed by
 * {@link #getType()}.
 *
 * <p>Concrete implementations (e.g. MBTiles/PMTiles readers, PostGIS
 * {@code ST_AsMVT}) live in their own modules; this interface only defines the
 * contract.
 */
public interface TileSource extends AutoCloseable {

    /**
     * @return the configuration type id of this source (matched against
     *         {@code tiles.layers.<id>.type})
     */
    String getType();

    /**
     * Build a {@link MapTileLayer} from configuration.
     *
     * @param cfg     the configuration parser
     * @param path    the configuration file path (for resolving relative resources)
     * @param tms     the service's tile matrix set registry (the tiling schemes
     *                the layer's {@code tileMatrixSets} ids resolve against)
     * @param layerId the tile layer id being configured
     * @return the parsed tile layer
     */
    MapTileLayer parse(HakunaConfigParser cfg, Path path, TileMatrixSetRegistry tms, String layerId) throws Exception;

    /**
     * Inbound request query-param names this source is willing to forward to its
     * upstream. The tile resource passes only these (intersected with what the
     * client actually sent) into {@link #getTile}; everything else is dropped so a
     * client cannot inject arbitrary params into the upstream request.
     *
     * @param layer the tile layer
     * @return the whitelisted passthrough param names (empty by default)
     */
    default Set<String> getPassthroughParams(MapTileLayer layer) {
        return Set.of();
    }

    /**
     * Fetch a single tile.
     *
     * @param layer           the tile layer (produced by {@link #parse})
     * @param tileMatrixSetId the tile matrix set id
     * @param tileMatrix      the tile matrix (zoom level) id
     * @param tileRow         the tile row
     * @param tileCol         the tile column
     * @param mediaType       the requested tile media type
     * @param requestParams   whitelisted inbound query params (see
     *                        {@link #getPassthroughParams}); never null
     * @return the tile, or empty if no tile exists at that address
     */
    Optional<Tile> getTile(MapTileLayer layer, String tileMatrixSetId, String tileMatrix,
            long tileRow, long tileCol, String mediaType, Map<String, String> requestParams) throws Exception;

    @Override
    default void close() throws Exception {
        // NOP
    }

}
