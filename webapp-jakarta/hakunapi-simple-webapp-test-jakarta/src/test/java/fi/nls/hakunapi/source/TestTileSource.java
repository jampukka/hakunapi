package fi.nls.hakunapi.source;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.BytesTile;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.TileSource;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

/**
 * In-memory map (raster) {@link TileSource} for tests: returns a fixed byte
 * payload for every requested tile. Serves the OGC API - Tiles map tile
 * resources ({@code /map/tiles}, {@code /collections/{id}/map/tiles}).
 */
public class TestTileSource implements TileSource {

    public static final byte[] TILE_BYTES = "TEST_TILE".getBytes(StandardCharsets.UTF_8);
    public static final String MEDIA_TYPE = "image/png";

    @Override
    public String getType() {
        return "test-map-tiles";
    }

    @Override
    public MapTileLayer parse(HakunaConfigParser cfg, Path path, TileMatrixSetRegistry tms, String layerId)
            throws Exception {
        TilesConfigParser tiles = new TilesConfigParser(cfg);
        String p = "tiles.layers." + layerId + ".";
        String title = cfg.get(p + "title", layerId);
        String description = cfg.get(p + "description", title);
        List<String> tmsIds = tiles.readTileMatrixSetIds(layerId);
        return new MapTileLayer(layerId, title, description, this, tmsIds, List.of(MEDIA_TYPE), null);
    }

    @Override
    public Optional<Tile> getTile(MapTileLayer layer, String tileMatrixSetId, String tileMatrix,
            long tileRow, long tileCol, String mediaType, Map<String, String> requestParams) throws Exception {
        return Optional.of(new BytesTile(tileMatrixSetId, tileMatrix, tileRow, tileCol, MEDIA_TYPE, TILE_BYTES));
    }

}
