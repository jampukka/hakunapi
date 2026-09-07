package fi.nls.hakunapi.tiles.source.vectortile.geom;

/**
 * MVT geometry writer specialised for (multi)linestrings. Each leaf line arriving
 * via {@link #startRing()}/{@link #endRing()} is transformed to tile-pixel space
 * by the base, then on {@code endRing} pushed through the line tile pipeline:
 *
 * <ol>
 *   <li>{@link LineClip Liang&ndash;Barsky clip} against the tile rect, which may
 *       split one line into several disjoint parts;</li>
 *   <li>per part: {@link SnapSimplify#snapDedupLine snap to the grid + dedup}
 *       (one fused pass), then
 *       {@link SnapSimplify#dropCollinearLine collinear drop};</li>
 *   <li>degenerate drop (a part with &lt; 2 distinct points draws nothing);</li>
 *   <li>emit {@code MoveTo} first vertex + {@code LineTo} the rest (no ClosePath).</li>
 * </ol>
 *
 * <h2>Nesting</h2>
 * A {@code LINESTRING} is a single {@code startRing}/{@code endRing} of vertices;
 * a {@code MULTILINESTRING} wraps those leaf lines in one more level. Leaf lines -
 * the only ones carrying coordinates - are the deepest, detected like the polygon
 * writer by coordinates having accumulated at {@code endRing}.
 *
 * <p>The cursor in {@link MvtGeometryEncoder} carries across every part and every
 * leaf line of the feature; it resets only per feature.
 */
public final class MvtLineGeometryWriter extends AbstractMvtGeometryWriter {

    private final MvtGeometryEncoder enc = new MvtGeometryEncoder();
    private final LineClip.Parts parts = new LineClip.Parts();

    @Override
    public void startRing() throws Exception {
        resetRing();
    }

    @Override
    public void endRing() throws Exception {
        if (n > 0) {
            processLine();
            resetRing();
        }
    }

    @Override
    public void end() throws Exception {
        // Feature complete; commands are ready in the encoder.
    }

    /**
     * Pull-path encode of a (multi)linestring via its navigable view. Each part is
     * one line (ring 0); coordinates are bulk-loaded and transformed by
     * {@link #loadRing} then run through the same clip/snap/emit as the push path.
     */
    public void writeNavigable(fi.nls.hakunapi.core.geom.NavigableHakunaGeometry geom) {
        int n = geom.getNumGeometries();
        for (int p = 0; p < n; p++) {
            loadRing(geom.getGeometryN(p), 0);
            if (this.n > 0) {
                processLine();
                resetRing();
            }
        }
    }

    private void processLine() {
        LineClip.clip(scratch, n, clipMinX, clipMinY, clipMaxX, clipMaxY, parts);
        for (int part = 0; part < parts.partCount; part++) {
            int start = LineClip.partStart(parts, part);
            int end = parts.partEnd[part];
            int m = end - start;
            if (m < 2) {
                continue;
            }
            // Snap/simplify operate on a 0-based view; shift the part to the front
            // of a scratch buffer so the in-place helpers can run on it.
            ensureB(m);
            System.arraycopy(parts.xy, start * 2, scratchB, 0, m * 2);
            m = SnapSimplify.snapDedupLine(scratchB, m);
            m = SnapSimplify.dropCollinearLine(scratchB, m);
            if (m < 2) {
                continue;
            }
            enc.moveTo((int) scratchB[0], (int) scratchB[1]);
            enc.lineTo(scratchB, 1, m - 1, +1);
        }
    }

    /** Reset encoder state before encoding a new feature. */
    public void resetFeature() {
        enc.reset();
        resetRing();
    }

    /** The encoder holding this feature's MVT geometry command stream. */
    public MvtGeometryEncoder encoder() {
        return enc;
    }
}
