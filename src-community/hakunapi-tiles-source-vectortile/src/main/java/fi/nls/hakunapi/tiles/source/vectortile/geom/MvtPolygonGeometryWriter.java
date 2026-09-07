package fi.nls.hakunapi.tiles.source.vectortile.geom;

/**
 * MVT geometry writer specialised for (multi)polygons. Each leaf ring arriving
 * via {@link #startRing()}/{@link #endRing()} is transformed to tile-pixel space
 * by the base, then on {@code endRing} pushed through the polygon tile pipeline
 * and emitted as MVT geometry commands by {@link MvtGeometryEncoder}:
 *
 * <ol>
 *   <li>{@link RingClip Sutherland&ndash;Hodgman clip} against the tile rect;</li>
 *   <li>{@link SnapSimplify#snapDedupRing snap to the integer MVT grid + dedup}
 *       (one fused pass) then
 *       {@link SnapSimplify#dropCollinearRing collinear drop};</li>
 *   <li>degenerate drop (a ring with &lt; 3 vertices or zero area);</li>
 *   <li>winding verification (see below) and command emission.</li>
 * </ol>
 *
 * <h2>Ring nesting</h2>
 * The {@code GeometryWriter} contract nests rings: a bare polygon is a
 * {@code startRing} wrapper around per-ring {@code startRing}s (outer then
 * holes); a multipolygon adds another wrapper level per part. Leaf rings - the
 * only ones that carry coordinates - are therefore the deepest. The first leaf
 * ring of each part is its exterior; the rest are holes.
 *
 * <h2>Winding</h2>
 * We trust the source ring order and roles (first leaf = exterior) rather than
 * imposing an absolute orientation, but we <em>verify</em> that grid snapping did
 * not corrupt the winding. After snap/simplify we take each ring's signed area:
 * a ring that collapsed to zero area is dropped; a hole whose orientation now
 * matches its exterior (snapping flipped it) is emitted reversed so it stays
 * opposite the exterior. The exterior's own source orientation is preserved.
 */
public final class MvtPolygonGeometryWriter extends AbstractMvtGeometryWriter {

    private final MvtGeometryEncoder enc = new MvtGeometryEncoder();
    private final SnapSimplify.RingResult ringResult = new SnapSimplify.RingResult();

    private int depth;          // startRing/endRing nesting depth
    private boolean lastWasStart; // previous structural event was startRing (=> wrapper)
    private boolean expectExterior; // next leaf ring is a part's exterior
    private double exteriorArea; // signed area of the current part's exterior

    @Override
    public void startRing() throws Exception {
        if (lastWasStart) {
            // The previous startRing opened a wrapper; entering it begins a part
            // whose first leaf ring is the exterior.
            expectExterior = true;
        }
        depth++;
        lastWasStart = true;
        resetRing();
    }

    @Override
    public void endRing() throws Exception {
        depth--;
        if (n > 0) {
            processRing();
            resetRing();
        }
        lastWasStart = false;
    }

    @Override
    public void end() throws Exception {
        // Feature complete; commands are ready in the encoder.
    }

    /**
     * Pull-path encode of a whole (multi)polygon via its navigable view. Each
     * part's ring 0 is the exterior, the rest holes; coordinates are bulk-loaded
     * and transformed by {@link #loadRing} then run through the same per-ring
     * pipeline as the push path. Call {@link #resetFeature()} first and read the
     * result from {@link #encoder()}.
     */
    public void writeNavigable(fi.nls.hakunapi.core.geom.NavigableHakunaGeometry geom) {
        int parts = geom.getNumGeometries();
        for (int p = 0; p < parts; p++) {
            fi.nls.hakunapi.core.geom.NavigableHakunaGeometry part = geom.getGeometryN(p);
            int rings = part.getNumRings();
            for (int r = 0; r < rings; r++) {
                loadRing(part, r);
                expectExterior = (r == 0);
                processRing();
                resetRing();
            }
        }
    }

    private void processRing() {
        float[] clipped;
        int m;
        if (ringBboxValid && (ringMaxX < clipMinX || ringMinX > clipMaxX
                || ringMaxY < clipMinY || ringMinY > clipMaxY)) {
            return; // bbox fully outside the clip rect: nothing crosses in
        }
        ensureB(n + 4); // Sutherland-Hodgman can add up to one vertex per edge
        if (ringBboxValid && ringMinX >= clipMinX && ringMaxX <= clipMaxX
                && ringMinY >= clipMinY && ringMaxY <= clipMaxY) {
            // Fully inside: clip is a no-op, snap/simplify straight on scratch.
            clipped = scratch;
            m = n;
        } else {
            clipped = scratchB;
            m = RingClip.clip(scratch, n, clipMinX, clipMinY, clipMaxX, clipMaxY, clipped, scratchC);
            if (m == 0) {
                return;
            }
        }
        // Snap+dedup and locate the leftmost-lowest corner in one pass; the corner
        // anchors the single-pass collinear drop below.
        long snapped = SnapSimplify.snapDedupRingTracked(clipped, m);
        m = SnapSimplify.trackedCount(snapped);
        int start = SnapSimplify.trackedStart(snapped);
        // Collinear drop writes survivors into a fresh buffer (single-pass ring
        // simplify needs read/write separation); pick whichever scratch the snap
        // result is not already occupying.
        float[] out = (clipped == scratchB) ? scratchC : scratchB;
        SnapSimplify.dropCollinearRing(clipped, m, start, out, ringResult);
        m = ringResult.n;
        clipped = out;
        if (m < 3) {
            return; // no area
        }
        double area = ringResult.area;
        if (area == 0.0) {
            return; // collapsed to a sliver after snapping
        }

        boolean isExterior = expectExterior;
        expectExterior = false;
        boolean reverse;
        if (isExterior) {
            // Preserve the source exterior orientation; remember it for holes.
            exteriorArea = area;
            reverse = false;
        } else {
            // Hole must wind opposite the exterior; reverse if snapping made it match.
            reverse = sameSign(area, exteriorArea);
        }
        enc.ring(clipped, 0, m, reverse);
    }

    private static boolean sameSign(double a, double b) {
        return (a > 0) == (b > 0);
    }

    /** Reset encoder + nesting state before encoding a new feature. */
    public void resetFeature() {
        enc.reset();
        depth = 0;
        lastWasStart = false;
        expectExterior = false;
        exteriorArea = 0.0;
        resetRing();
    }

    /** The encoder holding this feature's MVT geometry command stream. */
    public MvtGeometryEncoder encoder() {
        return enc;
    }
}
