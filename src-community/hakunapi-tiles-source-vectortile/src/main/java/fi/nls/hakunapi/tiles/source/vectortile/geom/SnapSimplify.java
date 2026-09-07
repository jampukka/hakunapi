package fi.nls.hakunapi.tiles.source.vectortile.geom;

/**
 * Grid snapping and topology-preserving simplification of a coordinate run in
 * tile-pixel space, in place on a flat {@code x0,y0,...} buffer with no
 * allocation. Two concerns, applied in order:
 *
 * <ol>
 *   <li><b>Snap</b>: round every coordinate to the integer tile grid (MVT
 *       coordinates are integers), which also collapses sub-pixel detail.</li>
 *   <li><b>Simplify</b>: drop consecutive duplicate points produced by snapping,
 *       then drop collinear interior points (a vertex lying exactly on the line
 *       between its neighbours carries no shape and only costs bytes).</li>
 * </ol>
 *
 * Callers decide afterwards whether the surviving run is degenerate: a line needs
 * &ge;&nbsp;2 distinct points, a ring &ge;&nbsp;3.
 */
final class SnapSimplify {

    private SnapSimplify() {}

    /** Round all {@code n} coordinate pairs in {@code a} to the integer grid, in place. */
    static void snap(float[] a, int n) {
        int len = n * 2;
        for (int i = 0; i < len; i++) {
            a[i] = (float) Math.rint(a[i]);
        }
    }

    /**
     * Snap and dedup an (open) line run in one pass: round each coordinate to the
     * integer grid and drop consecutive duplicates the rounding produces. Fuses
     * {@link #snap} into {@link #dedupLine} so the buffer is rewritten once instead
     * of twice. In place; returns the surviving pair count.
     */
    static int snapDedupLine(float[] a, int n) {
        if (n == 0) {
            return 0;
        }
        float px = (float) Math.rint(a[0]);
        float py = (float) Math.rint(a[1]);
        a[0] = px;
        a[1] = py;
        int w = 1; // first point always kept
        for (int i = 1; i < n; i++) {
            float x = (float) Math.rint(a[i * 2]);
            float y = (float) Math.rint(a[i * 2 + 1]);
            if (x != px || y != py) {
                a[w * 2] = x;
                a[w * 2 + 1] = y;
                w++;
                px = x; py = y;
            }
        }
        return w;
    }

    /**
     * Snap and dedup a closed ring in one pass (round + drop consecutive dups,
     * including the last-vs-first explicit closure). Ring analogue of
     * {@link #snapDedupLine}; output is not explicitly closed. Returns survivors.
     */
    static int snapDedupRing(float[] a, int n) {
        int w = snapDedupLine(a, n);
        while (w > 1 && a[(w - 1) * 2] == a[0] && a[(w - 1) * 2 + 1] == a[1]) {
            w--;
        }
        return w;
    }

    /**
     * Snap + dedup a closed ring (as {@link #snapDedupRing}) while also locating
     * the leftmost-lowest survivor in the same pass, so the collinear-drop that
     * follows can start at that guaranteed corner without a second scan. Returns
     * the result packed as {@code (startIndex << 32) | survivorCount}; unpack with
     * {@link #trackedCount} / {@link #trackedStart}.
     */
    static long snapDedupRingTracked(float[] a, int n) {
        if (n == 0) {
            return 0L;
        }
        float px = (float) Math.rint(a[0]);
        float py = (float) Math.rint(a[1]);
        a[0] = px;
        a[1] = py;
        int w = 1;
        int best = 0;        // index of the leftmost-lowest survivor
        float bx = px, by = py;
        for (int i = 1; i < n; i++) {
            float x = (float) Math.rint(a[i * 2]);
            float y = (float) Math.rint(a[i * 2 + 1]);
            if (x != px || y != py) {
                a[w * 2] = x;
                a[w * 2 + 1] = y;
                if (x < bx || (x == bx && y < by)) {
                    best = w; bx = x; by = y;
                }
                w++;
                px = x; py = y;
            }
        }
        // Drop trailing points equal to the first (explicit closure).
        while (w > 1 && a[(w - 1) * 2] == a[0] && a[(w - 1) * 2 + 1] == a[1]) {
            w--;
        }
        // A trimmed tail equals vertex 0, itself leftmost-lowest, so 0 is a valid
        // corner start if the tracked min fell into the trimmed region.
        if (best >= w) {
            best = 0;
        }
        return ((long) best << 32) | (w & 0xffffffffL);
    }

    static int trackedCount(long packed) {
        return (int) packed;
    }

    static int trackedStart(long packed) {
        return (int) (packed >>> 32);
    }

    /**
     * Remove consecutive duplicate points from an (open) line run, in place.
     * Returns the surviving pair count.
     */
    static int dedupLine(float[] a, int n) {
        if (n <= 1) {
            return n;
        }
        int w = 1; // first point always kept
        float px = a[0], py = a[1];
        for (int i = 1; i < n; i++) {
            float x = a[i * 2], y = a[i * 2 + 1];
            if (x != px || y != py) {
                a[w * 2] = x;
                a[w * 2 + 1] = y;
                w++;
                px = x; py = y;
            }
        }
        return w;
    }

    /**
     * Remove consecutive duplicates treating the run as a closed ring (the last
     * point's equality is checked against the first too), in place. Returns the
     * surviving pair count. The output is not explicitly closed.
     */
    static int dedupRing(float[] a, int n) {
        int w = dedupLine(a, n);
        // Drop a trailing point equal to the first (explicit closure).
        while (w > 1 && a[(w - 1) * 2] == a[0] && a[(w - 1) * 2 + 1] == a[1]) {
            w--;
        }
        return w;
    }

    /**
     * Drop collinear interior points from an open line: keep endpoints, drop any
     * middle vertex collinear with its neighbours. In place; returns survivor count.
     */
    static int dropCollinearLine(float[] a, int n) {
        if (n <= 2) {
            return n;
        }
        int w = 1; // keep first
        for (int i = 1; i < n - 1; i++) {
            float bx = a[i * 2], by = a[i * 2 + 1];
            float ax = a[(w - 1) * 2], ay = a[(w - 1) * 2 + 1];
            float cx = a[(i + 1) * 2], cy = a[(i + 1) * 2 + 1];
            if (!collinear(ax, ay, bx, by, cx, cy)) {
                a[w * 2] = bx;
                a[w * 2 + 1] = by;
                w++;
            }
        }
        // keep last
        a[w * 2] = a[(n - 1) * 2];
        a[w * 2 + 1] = a[(n - 1) * 2 + 1];
        w++;
        return w;
    }

    /**
     * Drop collinear vertices from a closed ring (not explicitly closed on
     * input). Every vertex, including the wrap-around joints, is tested against
     * its ring neighbours. In place; returns survivor count.
     */
    /**
     * Result of {@link #dropCollinearRing}: surviving vertex count and the ring's
     * shoelace signed area (positive/negative by winding), both produced in the
     * single simplify pass. Caller-owned and reused across rings.
     */
    static final class RingResult {
        int n;
        double area; // twice the signed area (shoelace sum); sign + zero are all the caller needs
    }

    /**
     * As the array-returning form, but also accumulates the ring's shoelace area
     * over the surviving vertices in the same pass (no separate area scan). The
     * caller reads {@code res.n} and {@code res.area}. {@code area} is twice the
     * true signed area; only its sign and zero-ness are used downstream.
     */
    static void dropCollinearRing(float[] a, int n, int start, float[] out, RingResult res) {
        if (n <= 3) {
            System.arraycopy(a, 0, out, 0, n * 2);
            res.n = n;
            res.area = shoelace(out, n);
            return;
        }
        // Seed: the start corner is always kept and anchors the back-neighbour.
        float sx0 = a[start * 2], sy0 = a[start * 2 + 1];
        out[0] = sx0;
        out[1] = sy0;
        int w = 1;
        double ax = sx0, ay = sy0;   // last survivor, also shoelace running vertex
        double area2 = 0.0;

        for (int k = 1; k < n; k++) {
            int i = idx(start + k, n);
            float bx = a[i * 2], by = a[i * 2 + 1];
            int j = idx(start + k + 1, n);
            float cx = a[j * 2], cy = a[j * 2 + 1];
            if (collinear(ax, ay, bx, by, cx, cy)) {
                continue; // collinear with last survivor + next original: drop
            }
            out[w * 2] = bx;
            out[w * 2 + 1] = by;
            w++;
            area2 += ax * by - (double) bx * ay; // edge (last survivor)->b
            ax = bx; ay = by;
        }
        area2 += ax * sy0 - (double) sx0 * ay; // wrap edge last->first
        res.n = w;
        res.area = area2;
    }

    /** Shoelace of the first {@code n} pairs of {@code xy} (twice the signed area). */
    private static double shoelace(float[] xy, int n) {
        double a = 0.0;
        int prev = n - 1;
        for (int i = 0; i < n; i++) {
            double x1 = xy[prev * 2], y1 = xy[prev * 2 + 1];
            double x2 = xy[i * 2], y2 = xy[i * 2 + 1];
            a += x1 * y2 - x2 * y1;
            prev = i;
        }
        return a;
    }

    static int dropCollinearRing(float[] a, int n, int start, float[] out) {
        if (n <= 3) {
            System.arraycopy(a, 0, out, 0, n * 2);
            return n;
        }
        // Single pass. The cascade that forces a ring's collinear drop to iterate
        // lives only at the wrap joint: vertex 0's verdict depends on the ring's
        // final last vertex, unknown mid-pass. We sidestep it by starting the walk
        // at a vertex that can never be dropped - {@code start} is the ring's
        // leftmost-lowest vertex, an extreme point of the convex hull and so a
        // strict corner (located by snapDedupRingTracked in its own pass). Walking
        // the ring once from there, the "previous survivor / next original" carry
        // that already removes interior runs in one pass now also covers the joint,
        // because both ends of the walk sit at that fixed corner.
        //
        // Reads run rotated (start..start-1 mod n) while survivors compact into
        // 'out' from index 0; a separate output buffer avoids the read-after-write
        // collision an in-place rotated compaction would hit.

        // Seed: the start corner is always kept and anchors the back-neighbour.
        float ax = a[start * 2], ay = a[start * 2 + 1];
        out[0] = ax;
        out[1] = ay;
        int w = 1;

        // Visit the other n-1 vertices in ring order; decide each against its last
        // kept predecessor (ax,ay) and its next original neighbour (cx,cy).
        for (int k = 1; k < n; k++) {
            int i = idx(start + k, n);
            float bx = a[i * 2], by = a[i * 2 + 1];
            int j = idx(start + k + 1, n); // next original (wraps back to start at k=n-1)
            float cx = a[j * 2], cy = a[j * 2 + 1];
            if (collinear(ax, ay, bx, by, cx, cy)) {
                continue; // b lies on the edge (last survivor)->(next): drop it
            }
            out[w * 2] = bx;
            out[w * 2 + 1] = by;
            w++;
            ax = bx; ay = by; // b becomes the new last survivor
        }
        return w;
    }

    /** Reduce {@code v} modulo {@code n} for a single forward step past the end. */
    private static int idx(int v, int n) {
        return v >= n ? v - n : v;
    }

    /** True if b lies exactly on segment a-c (zero cross product). Integer-safe. */
    private static boolean collinear(double ax, double ay, double bx, double by,
            double cx, double cy) {
        // cross of (b-a) x (c-a)
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax) == 0.0;
    }

}
