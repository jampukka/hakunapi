package fi.nls.hakunapi.tiles.source.vectortile.geom;

/**
 * Encodes a single feature's geometry into the Mapbox Vector Tile
 * {@code geometry} command stream: a {@code repeated uint32} of
 * {@code CommandInteger} / {@code ParameterInteger} values.
 *
 * <p>Encoding (per the MVT 2.1 spec):
 * <ul>
 *   <li>A <b>command integer</b> is {@code (id & 0x7) | (count << 3)}, where
 *       {@code id} is {@link #CMD_MOVETO MoveTo (1)},
 *       {@link #CMD_LINETO LineTo (2)} or {@link #CMD_CLOSEPATH ClosePath (7)}
 *       and {@code count} is how many times the command repeats.</li>
 *   <li>A <b>parameter integer</b> is a zig-zag encoded delta
 *       {@code (n << 1) ^ (n >> 31)}. MoveTo and LineTo each take two
 *       parameters (dx, dy) per repeat; ClosePath takes none.</li>
 * </ul>
 *
 * <p>The <b>cursor</b> ({@link #curX},{@link #curY}) holds the last absolute
 * position. It is reset to {@code (0,0)} once per feature ({@link #reset()}) and
 * carries across every part and ring of that feature - delta encoding never
 * resets at a ring or part boundary, only at the feature/geometry-field
 * boundary. MoveTo and LineTo advance the cursor; ClosePath does not (the cursor
 * stays at the last LineTo vertex, not the ring's first point).
 *
 * <p>Output accumulates in a reused {@code int[]} that grows once and is shared
 * across features (cleared by {@link #reset()}), so encoding small geometries
 * allocates nothing on the hot path.
 */
public final class MvtGeometryEncoder {

    public static final int CMD_MOVETO = 1;
    public static final int CMD_LINETO = 2;
    public static final int CMD_CLOSEPATH = 7;

    private int[] cmds = new int[64];
    private int len;

    private int curX;
    private int curY;

    /** Clear the command buffer and reset the cursor for a new feature. */
    public void reset() {
        len = 0;
        curX = 0;
        curY = 0;
    }

    public int length() {
        return len;
    }

    /** Backing command array; only {@code [0, length())} is valid. */
    public int[] commands() {
        return cmds;
    }

    private void ensure(int extra) {
        int need = len + extra;
        if (need > cmds.length) {
            int cap = cmds.length;
            while (cap < need) {
                cap <<= 1;
            }
            int[] g = new int[cap];
            System.arraycopy(cmds, 0, g, 0, len);
            cmds = g;
        }
    }

    private static int command(int id, int count) {
        return (id & 0x7) | (count << 3);
    }

    private static int zigzag(int n) {
        return (n << 1) ^ (n >> 31);
    }

    /** Emit a single MoveTo to absolute integer tile coords, advancing the cursor. */
    public void moveTo(int x, int y) {
        ensure(3);
        cmds[len++] = command(CMD_MOVETO, 1);
        cmds[len++] = zigzag(x - curX);
        cmds[len++] = zigzag(y - curY);
        curX = x;
        curY = y;
    }

    /**
     * Emit a {@code LineTo} run of {@code count} vertices read from the flat
     * {@code xy} buffer starting at pair index {@code start}, stepping by
     * {@code step} pairs (use {@code +1} forward, {@code -1} reversed). Advances
     * the cursor to the last vertex.
     */
    public void lineTo(float[] xy, int start, int count, int step) {
        if (count <= 0) {
            return;
        }
        ensure(1 + count * 2);
        cmds[len++] = command(CMD_LINETO, count);
        int idx = start;
        for (int i = 0; i < count; i++) {
            int x = (int) xy[idx * 2];
            int y = (int) xy[idx * 2 + 1];
            cmds[len++] = zigzag(x - curX);
            cmds[len++] = zigzag(y - curY);
            curX = x;
            curY = y;
            idx += step;
        }
    }

    /**
     * Emit a single {@code MoveTo} run of {@code count} points read from the flat
     * snapped {@code xy} buffer (pair indices {@code [0, count)}), advancing the
     * cursor across them. Used for (multi)point geometry, where the whole feature
     * is one MoveTo command.
     */
    public void moveToRun(float[] xy, int count) {
        if (count <= 0) {
            return;
        }
        ensure(1 + count * 2);
        cmds[len++] = command(CMD_MOVETO, count);
        for (int i = 0; i < count; i++) {
            int x = (int) xy[i * 2];
            int y = (int) xy[i * 2 + 1];
            cmds[len++] = zigzag(x - curX);
            cmds[len++] = zigzag(y - curY);
            curX = x;
            curY = y;
        }
    }

    /** Emit a single ClosePath; does not move the cursor. */
    public void closePath() {
        ensure(1);
        cmds[len++] = command(CMD_CLOSEPATH, 1);
    }

    /**
     * Emit one polygon ring (MoveTo first vertex, LineTo the rest, ClosePath) from
     * the flat snapped {@code xy} buffer. The ring spans pair indices
     * {@code [start, start+m)} and is <em>not</em> explicitly closed (no repeated
     * first vertex). When {@code reverse} is true the ring is emitted back-to-front,
     * flipping its winding without touching the source buffer.
     *
     * @param xy      flat x,y buffer in snapped integer tile coords
     * @param start   first vertex pair index of the ring
     * @param m       ring vertex count (&ge; 3)
     * @param reverse emit in reverse order to flip winding
     */
    public void ring(float[] xy, int start, int m, boolean reverse) {
        if (m < 3) {
            return;
        }
        if (!reverse) {
            int first = start;
            moveTo((int) xy[first * 2], (int) xy[first * 2 + 1]);
            lineTo(xy, start + 1, m - 1, +1);
        } else {
            int first = start + m - 1;
            moveTo((int) xy[first * 2], (int) xy[first * 2 + 1]);
            lineTo(xy, first - 1, m - 1, -1);
        }
        closePath();
    }
}
