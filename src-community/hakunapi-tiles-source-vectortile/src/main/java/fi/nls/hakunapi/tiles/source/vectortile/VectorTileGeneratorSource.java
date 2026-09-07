package fi.nls.hakunapi.tiles.source.vectortile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.BodyTile;
import fi.nls.hakunapi.tiles.CollectionTileSource;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.TileBody;
import fi.nls.hakunapi.tiles.TileMatrix;
import fi.nls.hakunapi.tiles.TileMatrixSet;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.VectorTileLayer;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

/**
 * {@link CollectionTileSource} that renders vector tiles on the fly via a
 * pluggable {@link VectorTileGenerator}, instead of reading precomputed bytes
 * from a store. This source is pure glue: it parses the layer config, resolves
 * the requested tile address against the service's {@link TileMatrixSetRegistry}
 * into a ground bounding box, and delegates the encoding of the supplied feature
 * collections to the generator bound to the layer.
 *
 * <p>Per-layer configuration ({@code tiles.layers.<id>.vectortile.*}):
 * <ul>
 *   <li>{@code generator} - {@link VectorTileGenerator} type id bound to this
 *       layer (resolved from the classpath by {@link VectorTileGeneratorProvider});
 *       falls back to the service-wide
 *       {@code default.tiles.layers.vectortile.generator}</li>
 *   <li>{@code collections} - feature collection ids rendered into the tile, one
 *       MVT layer each (default: a single collection sharing the layer id)</li>
 *   <li>{@code format} - advertised tile media type
 *       (default {@code application/vnd.mapbox-vector-tile})</li>
 *   <li>{@code passthroughParams} - inbound query-param names forwarded to the
 *       generator (default none)</li>
 * </ul>
 */
public class VectorTileGeneratorSource implements CollectionTileSource {

    private static final Logger LOG = LoggerFactory.getLogger(VectorTileGeneratorSource.class);

    public static final String TYPE = "vectortile-generator";

    public static final String DEFAULT_MEDIA_TYPE = "application/vnd.mapbox-vector-tile";

    private TileMatrixSetRegistry tms;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public VectorTileLayer parse(HakunaConfigParser cfg, Path path, TileMatrixSetRegistry tms, String layerId)
            throws Exception {
        // Keep the registry: getTile resolves tile addresses against it.
        this.tms = tms;

        TilesConfigParser tiles = new TilesConfigParser(cfg);
        String p = "tiles.layers." + layerId + ".";
        String gp = p + "vectortile.";

        String title = cfg.get(p + "title", layerId);
        String description = cfg.get(p + "description", title);
        List<String> tmsIds = tiles.readTileMatrixSetIds(layerId);
        String mediaType = cfg.get(gp + "format", DEFAULT_MEDIA_TYPE);
        Set<String> passthrough = Set.of(cfg.getMultiple(gp + "passthroughParams"));
        // Feature collections rendered into the tile (one MVT layer each);
        // defaults to a single collection sharing the tile layer id.
        List<String> collectionIds = List.of(cfg.getMultiple(gp + "collections", new String[] { layerId }));

        VectorTileGenerator generator = bindGenerator(cfg, gp, layerId);

        LOG.info("generator tile layer {} -> generator={} type={} collections={}",
                layerId, generator.getType(), mediaType, collectionIds);
        return new VectorTileGeneratorLayer(layerId, title, description, this, tmsIds,
                List.of(mediaType), null, generator, collectionIds, passthrough);
    }

    /**
     * Bind the single {@link VectorTileGenerator} for this layer: the per-layer
     * {@code vectortile.generator} type id, falling back to the service-wide
     * {@code default.tiles.layers.vectortile.generator}. The generator is
     * resolved from the classpath via {@link VectorTileGeneratorProvider}.
     */
    private VectorTileGenerator bindGenerator(HakunaConfigParser cfg, String gp, String layerId) {
        String type = cfg.get(gp + "generator", cfg.get("default.tiles.layers.vectortile.generator"));
        if (type == null) {
            throw new IllegalArgumentException("tile layer " + layerId + ": " + gp
                    + "generator is required (or set default.tiles.layers.vectortile.generator)");
        }
        VectorTileGenerator generator = VectorTileGeneratorProvider.get(type);
        if (generator == null) {
            throw new IllegalArgumentException("tile layer " + layerId
                    + ": unknown vector tile generator type: " + type);
        }
        return generator;
    }

    @Override
    public Set<String> getPassthroughParams(VectorTileLayer layer) {
        return ((VectorTileGeneratorLayer) layer).getPassthroughParams();
    }

    @Override
    public Optional<Tile> getTile(VectorTileLayer layer, String tileMatrixSetId, String tileMatrix,
            long tileRow, long tileCol, List<FeatureType> collections,
            String mediaType, Map<String, String> requestParams) throws Exception {
        VectorTileGeneratorLayer l = (VectorTileGeneratorLayer) layer;
        if (!l.supportsTileMatrixSet(tileMatrixSetId)) {
            return Optional.empty();
        }

        Optional<TileMatrixSet> tmsOpt = tms.get(tileMatrixSetId);
        if (tmsOpt.isEmpty()) {
            return Optional.empty();
        }
        TileMatrixSet tileMatrixSet = tmsOpt.get();
        Optional<TileMatrix> tmOpt = tileMatrixSet.getTileMatrix(tileMatrix);
        if (tmOpt.isEmpty()) {
            return Optional.empty();
        }
        TileMatrix tm = tmOpt.get();

        // Address out of range for this zoom level: no tile.
        if (tileCol < 0 || tileRow < 0 || tileCol >= tm.getMatrixWidth() || tileRow >= tm.getMatrixHeight()) {
            return Optional.empty();
        }

        TileContext ctx = toTileContext(tileMatrixSet, tm, tileRow, tileCol);
        Optional<TileBody> body = l.getGenerator().generate(ctx, collections, mediaType, requestParams);
        return body.map(b -> new BodyTile(tileMatrixSetId, tileMatrix, tileRow, tileCol,
                l.getMediaTypes().get(0), b));
    }

    /**
     * Turn a tile address into its ground bounding box. The point of origin is
     * the top-left of the matrix ({@code originX, originY}); a tile spans
     * {@code tileWidth * cellSize} ground units in X (columns increase to the
     * right) and {@code tileHeight * cellSize} in Y (rows increase downward).
     */
    private static TileContext toTileContext(TileMatrixSet tms, TileMatrix tm, long tileRow, long tileCol) {
        double spanX = tm.getTileWidth() * tm.getCellSize();
        double spanY = tm.getTileHeight() * tm.getCellSize();
        double minX = tm.getPointOfOriginX() + tileCol * spanX;
        double maxX = minX + spanX;
        double maxY = tm.getPointOfOriginY() - tileRow * spanY;
        double minY = maxY - spanY;
        return new TileContext(tms.getSrid(), minX, minY, maxX, maxY,
                tm.getTileWidth(), tm.getTileHeight(), tm.getCellSize());
    }

    // No close(): generators are stateless pure functions and the source holds
    // no resources of its own, so TileSource's NOP close default suffices.

}
