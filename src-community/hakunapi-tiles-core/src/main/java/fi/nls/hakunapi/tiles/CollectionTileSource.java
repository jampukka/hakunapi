package fi.nls.hakunapi.tiles;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.config.HakunaConfigParser;

/**
 * Pluggable source of <em>vector</em> tiles rendered from feature collections -
 * the data-plane counterpart of {@link TileSource}. Where a {@link TileSource}
 * serves precomputed map (raster) tiles from a store (WMTS/PMTiles/GeoPackage)
 * and needs no feature access, a {@code CollectionTileSource} renders one or more
 * {@link FeatureType feature collections} into a vector tile on the fly. It backs
 * the OGC API - Tiles geodata/vector tilesets ({@code /collections/{id}/tiles}
 * and the dataset-level {@code /tiles}).
 *
 * <p>The two are deliberately separate interfaces with no shared base: the
 * fetch contracts differ (a collection tile needs the resolved
 * {@link FeatureType}s to render, a map tile does not), and an endpoint binds to
 * one or the other by type.
 *
 * <p>Like {@link TileSource}, this source owns all tile-domain logic - parsing
 * the layer config and resolving a tile address (matrix set / zoom / row /
 * column) into a ground bounding box. The calling endpoint only resolves the
 * layer's configured collection ids into {@link FeatureType}s (it holds the
 * feature service config) and passes them in; it does no tile math.
 */
public interface CollectionTileSource extends AutoCloseable {

    /**
     * @return the configuration type id of this source (matched against
     *         {@code tiles.layers.<id>.type})
     */
    String getType();

    /**
     * Build a {@link VectorTileLayer} from configuration.
     *
     * @param cfg     the configuration parser
     * @param path    the configuration file path (for resolving relative resources)
     * @param tms     the service's tile matrix set registry
     * @param layerId the tile layer id being configured
     * @return the parsed tile layer
     */
    VectorTileLayer parse(HakunaConfigParser cfg, Path path, TileMatrixSetRegistry tms, String layerId) throws Exception;

    /**
     * Inbound request query-param names this source forwards to its renderer; see
     * {@link TileSource#getPassthroughParams}.
     */
    default Set<String> getPassthroughParams(VectorTileLayer layer) {
        return Set.of();
    }

    /**
     * Render a single vector tile for the given address from the supplied feature
     * collections (one MVT layer each).
     *
     * @param layer           the tile layer (produced by {@link #parse})
     * @param tileMatrixSetId the tile matrix set id
     * @param tileMatrix      the tile matrix (zoom level) id
     * @param tileRow         the tile row
     * @param tileCol         the tile column
     * @param collections     the resolved feature collections to render, one MVT
     *                        layer each; never null (may be empty)
     * @param mediaType       the requested tile media type
     * @param requestParams   whitelisted inbound query params (see
     *                        {@link #getPassthroughParams}); never null
     * @return the tile, or empty if no tile exists at that address
     */
    Optional<Tile> getTile(VectorTileLayer layer, String tileMatrixSetId, String tileMatrix,
            long tileRow, long tileCol, List<FeatureType> collections,
            String mediaType, Map<String, String> requestParams) throws Exception;

    @Override
    default void close() throws Exception {
        // NOP
    }

}
