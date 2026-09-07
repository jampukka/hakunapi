package fi.nls.hakunapi.tiles.source.vectortile.geom;

/**
 * Liang&ndash;Barsky clip of a polyline against an axis-aligned rectangle,
 * working directly on flat {@code x0,y0,x1,y1,...} buffers with no per-segment
 * allocation. Unlike a polygon ring, a clipped line is not a single run: a
 * polyline that leaves and re-enters the rectangle splits into several disjoint
 * parts, so the result is a flat coordinate buffer plus an array of part-end
 * offsets.
 *
 * <p>Each input segment is clipped independently to its visible {@code [t0,t1]}
 * sub-segment. Consecutive segments that stay inside and share their joint extend
 * the current part; a fully-clipped or exiting segment ends the current part, and
 * the next entering segment starts a new one.
 */
final class LineClip {

    private LineClip() {}

    /** Result holder (caller-owned, reused): clipped coords + part boundaries. */
    static final class Parts {
        float[] xy = new float[64];     // flat clipped coords across all parts
        int n;                          // valid coordinate pairs in xy
        int[] partEnd = new int[8];     // exclusive end pair index of each part
        int partCount;

        // Scratch for the most recent clipped segment (avoids per-call alloc).
        // Kept as double: the Liang-Barsky intersection math runs in double, the
        // result is narrowed to float only when pushed into xy.
        double sx0, sy0, sx1, sy1;

        void reset() {
            n = 0;
            partCount = 0;
        }

        private void ensureXy(int pairs) {
            if (pairs * 2 > xy.length) {
                int cap = xy.length;
                while (cap < pairs * 2) cap <<= 1;
                float[] g = new float[cap];
                System.arraycopy(xy, 0, g, 0, n * 2);
                xy = g;
            }
        }

        private void push(double x, double y) {
            ensureXy(n + 1);
            xy[n * 2] = (float) x;
            xy[n * 2 + 1] = (float) y;
            n++;
        }

        private void endPart() {
            if (partCount == partEnd.length) {
                int[] g = new int[partEnd.length << 1];
                System.arraycopy(partEnd, 0, g, 0, partCount);
                partEnd = g;
            }
            partEnd[partCount++] = n;
        }
    }

    /**
     * Clip the polyline {@code src[0..nIn*2)} into {@code out}. After the call
     * part {@code p} spans pair indices {@code [partStart(out,p), out.partEnd[p])}.
     */
    static void clip(float[] src, int nIn,
            double minX, double minY, double maxX, double maxY, Parts out) {
        out.reset();
        if (nIn < 2) {
            return;
        }
        boolean open = false;
        double lastX = 0, lastY = 0;

        for (int i = 0; i < nIn - 1; i++) {
            double ax = src[i * 2], ay = src[i * 2 + 1];
            double bx = src[(i + 1) * 2], by = src[(i + 1) * 2 + 1];

            if (!clipSegment(ax, ay, bx, by, minX, minY, maxX, maxY, out)) {
                if (open) {
                    out.endPart();
                    open = false;
                }
                continue;
            }
            double cx0 = out.sx0, cy0 = out.sy0, cx1 = out.sx1, cy1 = out.sy1;

            if (!open) {
                out.push(cx0, cy0);
                out.push(cx1, cy1);
                open = true;
            } else if (cx0 == lastX && cy0 == lastY) {
                out.push(cx1, cy1);
            } else {
                out.endPart();
                out.push(cx0, cy0);
                out.push(cx1, cy1);
            }
            lastX = cx1;
            lastY = cy1;

            // Exited the rect within this segment -> the part ends here.
            if (cx1 != bx || cy1 != by) {
                out.endPart();
                open = false;
            }
        }
        if (open) {
            out.endPart();
        }
    }

    /**
     * Liang&ndash;Barsky clip of one segment. On a visible result writes the
     * endpoints into {@code out.sx0..sy1} and returns true; returns false if the
     * segment is fully outside.
     */
    private static boolean clipSegment(double ax, double ay, double bx, double by,
            double minX, double minY, double maxX, double maxY, Parts out) {
        double dx = bx - ax;
        double dy = by - ay;
        // Liang-Barsky parameter window [t0,t1] along the segment, narrowed by
        // each of the four edges. p = direction component, q = distance to edge.
        double t0 = 0.0, t1 = 1.0;
        double p, q, r;

        // left: keep x >= minX
        p = -dx; q = ax - minX;
        if (p == 0.0) { if (q < 0.0) return false; }
        else { r = q / p; if (p < 0.0) { if (r > t1) return false; if (r > t0) t0 = r; }
                          else         { if (r < t0) return false; if (r < t1) t1 = r; } }
        // right: keep x <= maxX
        p = dx; q = maxX - ax;
        if (p == 0.0) { if (q < 0.0) return false; }
        else { r = q / p; if (p < 0.0) { if (r > t1) return false; if (r > t0) t0 = r; }
                          else         { if (r < t0) return false; if (r < t1) t1 = r; } }
        // bottom: keep y >= minY
        p = -dy; q = ay - minY;
        if (p == 0.0) { if (q < 0.0) return false; }
        else { r = q / p; if (p < 0.0) { if (r > t1) return false; if (r > t0) t0 = r; }
                          else         { if (r < t0) return false; if (r < t1) t1 = r; } }
        // top: keep y <= maxY
        p = dy; q = maxY - ay;
        if (p == 0.0) { if (q < 0.0) return false; }
        else { r = q / p; if (p < 0.0) { if (r > t1) return false; if (r > t0) t0 = r; }
                          else         { if (r < t0) return false; if (r < t1) t1 = r; } }

        out.sx0 = ax + t0 * dx;
        out.sy0 = ay + t0 * dy;
        out.sx1 = ax + t1 * dx;
        out.sy1 = ay + t1 * dy;
        return true;
    }

    static int partStart(Parts out, int part) {
        return part == 0 ? 0 : out.partEnd[part - 1];
    }
}
