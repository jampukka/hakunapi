package fi.nls.hakunapi.tiles.source.pmtiles;

import java.util.List;

import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileSource;

/**
 * A {@link MapTileLayer} backed by a PMTiles archive, carrying the parsed
 * {@link PMTilesConfig} (including the opened archive) used to serve tiles.
 */
public class PMTilesTileLayer extends MapTileLayer {

    private final PMTilesConfig pmtilesConfig;

    public PMTilesTileLayer(String id, String title, String description, TileSource source,
            List<String> tileMatrixSetIds, List<String> mediaTypes, double[] bbox, PMTilesConfig pmtilesConfig) {
        super(id, title, description, source, tileMatrixSetIds, mediaTypes, bbox);
        this.pmtilesConfig = pmtilesConfig;
    }

    public PMTilesConfig getPMTilesConfig() {
        return pmtilesConfig;
    }

}
