package fi.nls.hakunapi.tiles.source.vectortile.geom;

/**
 * MVT geometry writer specialised for (multi)points. Points are the simplest
 * tile geometry: each coordinate is transformed to tile-pixel space by the base,
 * rejected if it falls outside the clip rect (tile extent plus buffer), snapped
 * to the integer grid, and emitted - there is no edge clipping, no simplify and
 * no winding.
 *
 * <p>The whole (multi)point becomes a single {@code MoveTo} command whose repeat
 * count is the number of surviving points; the cursor carries delta-to-delta
 * across them, per the MVT spec.
 *
 * <h2>Nesting</h2>
 * The {@code GeometryWriter} contract delivers a single {@code POINT} as bare
 * {@code writeCoordinate} calls (no ring), and a {@code MULTIPOINT} as a single
 * {@code startRing}/{@code endRing} wrapping N coordinates. This writer handles
 * both: it simply collects every coordinate that arrives between {@link #reset()
 * resetFeature} and {@link #end()} and flushes them as one MoveTo run on
 * {@code end()}.
 *
 * <p>Consecutive duplicate points (after snapping) are dropped; MVT permits
 * repeated points but they carry no extra information and cost bytes.
 */
public final class MvtPointGeometryWriter extends AbstractMvtGeometryWriter {

    private final MvtGeometryEncoder enc = new MvtGeometryEncoder();

    // Accumulated surviving points in snapped integer tile coords (x,y pairs).
    private float[] pts = new float[64];
    private int nPts;

    @Override
    public void writeCoordinate(double x, double y) throws Exception {
        accept(toTileX(x), toTileY(y));
    }

    @Override
    public void writeCoordinate(double x, double y, double z) throws Exception {
        accept(toTileX(x), toTileY(y));
    }

    @Override
    public void writeCoordinate(double x, double y, double z, double m) throws Exception {
        accept(toTileX(x), toTileY(y));
    }

    private void accept(double tx, double ty) {
        if (tx < clipMinX || tx > clipMaxX || ty < clipMinY || ty > clipMaxY) {
            return; // outside the tile (+buffer)
        }
        float sx = (float) Math.rint(tx);
        float sy = (float) Math.rint(ty);
        if (nPts > 0 && pts[(nPts - 1) * 2] == sx && pts[(nPts - 1) * 2 + 1] == sy) {
            return; // consecutive duplicate after snap
        }
        int need = (nPts + 1) * 2;
        if (need > pts.length) {
            pts = grow(pts, need);
        }
        pts[nPts * 2] = sx;
        pts[nPts * 2 + 1] = sy;
        nPts++;
    }

    /**
     * Pull-path encode of a (multi)point via its navigable view. Each part is one
     * point (ring 0, one coordinate); coordinates are bulk-loaded and transformed
     * by {@link #loadRing} into {@link #scratch}, then fed through the same
     * accept (clip/snap/dedup) as the push path.
     */
    public void writeNavigable(fi.nls.hakunapi.core.geom.NavigableHakunaGeometry geom) {
        int parts = geom.getNumGeometries();
        for (int p = 0; p < parts; p++) {
            fi.nls.hakunapi.core.geom.NavigableHakunaGeometry part = geom.getGeometryN(p);
            loadRing(part, 0);
            for (int i = 0; i < n; i++) {
                accept(scratch[i * 2], scratch[i * 2 + 1]);
            }
            resetRing();
        }
    }

    @Override
    public void startRing() throws Exception {
        // MULTIPOINT wrapper: no per-ring state, points accumulate directly.
    }

    @Override
    public void endRing() throws Exception {
        // no-op; see startRing
    }

    @Override
    public void end() throws Exception {
        if (nPts == 0) {
            return;
        }
        // One MoveTo run of nPts points; encoder carries the cursor across them.
        enc.moveToRun(pts, nPts);
    }

    /** Reset encoder + accumulated points before encoding a new feature. */
    public void resetFeature() {
        enc.reset();
        nPts = 0;
    }

    /** The encoder holding this feature's MVT geometry command stream. */
    public MvtGeometryEncoder encoder() {
        return enc;
    }
}
