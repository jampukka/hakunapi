package fi.nls.hakunapi.tiles.source.vectortile.geom;

/**
 * Sutherland&ndash;Hodgman clip of a single polygon ring against an axis-aligned
 * rectangle, working directly on flat {@code x0,y0,x1,y1,...} buffers with no
 * intermediate objects. Clipping a convex window keeps a ring a single ring, so
 * the result is again a flat coordinate run.
 *
 * <p>The algorithm clips against one rectangle edge at a time, ping-ponging
 * between two buffers; after four passes the surviving polygon is whatever lies
 * inside all four half-planes. The input ring need not be explicitly closed
 * (last point == first); the clip treats it as closed regardless.
 */
final class RingClip {

    private RingClip() {}

    // Edge codes for which half-plane we keep.
    private static final int LEFT = 0;   // keep x >= minX
    private static final int RIGHT = 1;  // keep x <= maxX
    private static final int BOTTOM = 2; // keep y >= minY
    private static final int TOP = 3;    // keep y <= maxY

    /**
     * Clip ring {@code src[0..nIn*2)} against the rectangle into {@code dst},
     * using {@code tmp} as the ping-pong scratch. Both {@code dst} and
     * {@code tmp} must hold at least {@code nIn * 2 + 8} doubles per pass; the
     * caller sizes them. Returns the number of output coordinate pairs (0 if the
     * ring is fully outside).
     */
    static int clip(float[] src, int nIn,
            double minX, double minY, double maxX, double maxY,
            float[] dst, float[] tmp) {
        if (nIn == 0) {
            return 0;
        }
        // Pass 1: src -> dst, then alternate tmp<->dst.
        int n = clipEdge(src, nIn, LEFT, minX, minY, maxX, maxY, dst);
        if (n == 0) return 0;
        n = clipEdge(dst, n, RIGHT, minX, minY, maxX, maxY, tmp);
        if (n == 0) return 0;
        n = clipEdge(tmp, n, BOTTOM, minX, minY, maxX, maxY, dst);
        if (n == 0) return 0;
        n = clipEdge(dst, n, TOP, minX, minY, maxX, maxY, tmp);
        if (n == 0) return 0;
        System.arraycopy(tmp, 0, dst, 0, n * 2);
        return n;
    }

    private static boolean inside(int edge, double x, double y,
            double minX, double minY, double maxX, double maxY) {
        switch (edge) {
        case LEFT:   return x >= minX;
        case RIGHT:  return x <= maxX;
        case BOTTOM: return y >= minY;
        default:     return y <= maxY; // TOP
        }
    }

    /** Intersection of segment (ax,ay)-(bx,by) with the given edge line. */
    private static double intersectX(int edge, double ax, double ay, double bx, double by,
            double minX, double maxX) {
        switch (edge) {
        case LEFT:  return interpX(ax, ay, bx, by, minX);
        case RIGHT: return interpX(ax, ay, bx, by, maxX);
        default:    return 0; // unused for horizontal edges
        }
    }

    private static double interpX(double ax, double ay, double bx, double by, double edgeX) {
        double t = (edgeX - ax) / (bx - ax);
        return ay + t * (by - ay);
    }

    private static double interpY(double ax, double ay, double bx, double by, double edgeY) {
        double t = (edgeY - ay) / (by - ay);
        return ax + t * (bx - ax);
    }

    /**
     * Clip one edge: read {@code nIn} pairs from {@code in}, write survivors to
     * {@code out}, return output pair count. Emits the entering intersection on a
     * outside-&gt;inside crossing and the leaving intersection on inside-&gt;outside.
     */
    private static int clipEdge(float[] in, int nIn, int edge,
            double minX, double minY, double maxX, double maxY, float[] out) {
        int o = 0;
        double px = in[(nIn - 1) * 2];
        double py = in[(nIn - 1) * 2 + 1];
        boolean pIn = inside(edge, px, py, minX, minY, maxX, maxY);
        for (int i = 0; i < nIn; i++) {
            double cx = in[i * 2];
            double cy = in[i * 2 + 1];
            boolean cIn = inside(edge, cx, cy, minX, minY, maxX, maxY);
            if (cIn) {
                if (!pIn) {
                    o = emitCross(out, o, edge, px, py, cx, cy, minX, minY, maxX, maxY);
                }
                out[o * 2] = (float) cx;
                out[o * 2 + 1] = (float) cy;
                o++;
            } else if (pIn) {
                o = emitCross(out, o, edge, px, py, cx, cy, minX, minY, maxX, maxY);
            }
            px = cx;
            py = cy;
            pIn = cIn;
        }
        return o;
    }

    private static int emitCross(float[] out, int o, int edge,
            double px, double py, double cx, double cy,
            double minX, double minY, double maxX, double maxY) {
        double ix, iy;
        switch (edge) {
        case LEFT:
            ix = minX; iy = intersectX(edge, px, py, cx, cy, minX, maxX); break;
        case RIGHT:
            ix = maxX; iy = intersectX(edge, px, py, cx, cy, minX, maxX); break;
        case BOTTOM:
            iy = minY; ix = interpY(px, py, cx, cy, minY); break;
        default: // TOP
            iy = maxY; ix = interpY(px, py, cx, cy, maxY); break;
        }
        out[o * 2] = (float) ix;
        out[o * 2 + 1] = (float) iy;
        return o + 1;
    }

}
