package fi.nls.hakunapi.tiles.schemas;

import java.util.List;

/**
 * Response for the {@code /tileMatrixSets} resource (list of available tile
 * matrix sets).
 */
public class TileMatrixSets {

    private final List<TileMatrixSetItem> tileMatrixSets;

    public TileMatrixSets(List<TileMatrixSetItem> tileMatrixSets) {
        this.tileMatrixSets = tileMatrixSets;
    }

    public List<TileMatrixSetItem> getTileMatrixSets() {
        return tileMatrixSets;
    }

}
