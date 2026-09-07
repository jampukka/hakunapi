package fi.nls.hakunapi.tiles.source.pmtiles;

/**
 * Conversion of an XYZ tile address (zoom, x, y; top-left origin) to the
 * PMTiles tile id, the 1-D index along a Hilbert space-filling curve used to
 * order tiles within an archive.
 *
 * <p>The id space starts at 0 for z0 and is offset by the total number of tiles
 * in all lower zoom levels ({@code (4^z - 1) / 3}), then advanced by the tile's
 * position on the per-level Hilbert curve. Port of the reference algorithm in
 * the PMTiles v3 specification.
 */
public final class TileId {

    private TileId() {
    }

    public static long fromZXY(int z, long x, long y) {
        if (z > 31) {
            throw new IllegalArgumentException("PMTiles zoom out of range: " + z);
        }
        long dim = 1L << z;
        if (x < 0 || y < 0 || x >= dim || y >= dim) {
            throw new IllegalArgumentException("Tile out of range for zoom " + z + ": x=" + x + " y=" + y);
        }
        // Number of tiles in all zoom levels below z: (4^z - 1) / 3.
        long acc = (dim * dim - 1) / 3;
        long tx = x;
        long ty = y;
        long d = 0;
        for (long s = dim / 2; s > 0; s /= 2) {
            long rx = (tx & s) > 0 ? 1 : 0;
            long ry = (ty & s) > 0 ? 1 : 0;
            d += s * s * ((3 * rx) ^ ry);
            // Rotate the quadrant.
            if (ry == 0) {
                if (rx == 1) {
                    tx = s - 1 - tx;
                    ty = s - 1 - ty;
                }
                long t = tx;
                tx = ty;
                ty = t;
            }
        }
        return acc + d;
    }

}
