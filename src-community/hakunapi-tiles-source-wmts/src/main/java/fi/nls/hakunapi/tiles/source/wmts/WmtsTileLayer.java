package fi.nls.hakunapi.tiles.source.wmts;

import java.util.List;

import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileSource;

/**
 * A {@link MapTileLayer} backed by an upstream WMTS server, carrying the parsed
 * {@link WmtsConfig} needed to build GetTile requests.
 */
public class WmtsTileLayer extends MapTileLayer {

    private final WmtsConfig wmtsConfig;

    public WmtsTileLayer(String id, String title, String description, TileSource source,
            List<String> tileMatrixSetIds, List<String> mediaTypes, double[] bbox, WmtsConfig wmtsConfig) {
        super(id, title, description, source, tileMatrixSetIds, mediaTypes, bbox);
        this.wmtsConfig = wmtsConfig;
    }

    public WmtsConfig getWmtsConfig() {
        return wmtsConfig;
    }

}
