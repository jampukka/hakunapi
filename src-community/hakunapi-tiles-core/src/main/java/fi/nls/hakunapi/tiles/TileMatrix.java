package fi.nls.hakunapi.tiles;

/**
 * A single zoom level within a {@link TileMatrixSet}, as defined by the OGC
 * Two Dimensional Tile Matrix Set standard (OGC 17-083r4).
 */
public class TileMatrix {

    private final String id;
    private final double scaleDenominator;
    private final double cellSize;
    private final double pointOfOriginX;
    private final double pointOfOriginY;
    private final int tileWidth;
    private final int tileHeight;
    private final int matrixWidth;
    private final int matrixHeight;

    public TileMatrix(String id, double scaleDenominator, double cellSize,
            double pointOfOriginX, double pointOfOriginY,
            int tileWidth, int tileHeight, int matrixWidth, int matrixHeight) {
        this.id = id;
        this.scaleDenominator = scaleDenominator;
        this.cellSize = cellSize;
        this.pointOfOriginX = pointOfOriginX;
        this.pointOfOriginY = pointOfOriginY;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.matrixWidth = matrixWidth;
        this.matrixHeight = matrixHeight;
    }

    public String getId() {
        return id;
    }

    public double getScaleDenominator() {
        return scaleDenominator;
    }

    public double getCellSize() {
        return cellSize;
    }

    public double getPointOfOriginX() {
        return pointOfOriginX;
    }

    public double getPointOfOriginY() {
        return pointOfOriginY;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public int getMatrixWidth() {
        return matrixWidth;
    }

    public int getMatrixHeight() {
        return matrixHeight;
    }

}
