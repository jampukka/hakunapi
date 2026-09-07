package fi.nls.hakunapi.tiles.source.vectortile.geom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SnapSimplifyTest {

    @Test
    public void snapRoundsToGrid() {
        float[] a = {0.4f, 0.6f, 1.5f, 2.49f, -0.4f, -0.6f};
        SnapSimplify.snap(a, 3);
        // rint: 0.4->0, 0.6->1, 1.5->2 (half-even), 2.49->2, -0.4->0(-0.0), -0.6->-1
        assertEquals(0.0, a[0], 0);
        assertEquals(1.0, a[1], 0);
        assertEquals(2.0, a[2], 0);
        assertEquals(2.0, a[3], 0);
        assertEquals(-1.0, a[5], 0);
    }

    @Test
    public void dedupLineDropsConsecutiveDuplicates() {
        float[] a = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2};
        int n = SnapSimplify.dedupLine(a, 5);
        assertEquals(3, n);
        assertArrayEquals(new float[] {0, 0, 1, 1, 2, 2}, slice(a, n), 0);
    }

    @Test
    public void dedupRingDropsExplicitClosure() {
        float[] a = {0, 0, 4, 0, 4, 4, 0, 4, 0, 0};
        int n = SnapSimplify.dedupRing(a, 5);
        assertEquals(4, n);
    }

    @Test
    public void snapDedupLineFusesRoundAndDedup() {
        // sub-pixel jitter that collapses to the same grid point after rounding
        float[] a = {0.1f, 0.1f, 0.4f, -0.3f, 1.6f, 2.4f, 2.5f, 2.5f};
        int n = SnapSimplify.snapDedupLine(a, 4);
        // rounds to (0,0),(0,0),(2,2),(2,2) -> dedup -> (0,0),(2,2)
        assertEquals(2, n);
        assertArrayEquals(new float[] {0, 0, 2, 2}, slice(a, n), 0);
    }

    @Test
    public void snapDedupRingDropsClosureAfterRounding() {
        // square whose explicit closing point only matches the first once rounded
        float[] a = {0.2f, -0.1f, 4, 0, 4, 4, 0, 4, -0.3f, 0.4f};
        int n = SnapSimplify.snapDedupRing(a, 5);
        assertEquals(4, n);
        assertArrayEquals(new float[] {0, 0, 4, 0, 4, 4, 0, 4}, slice(a, n), 0);
    }

    @Test
    public void snapDedupRingTrackedFindsCorner() {
        // ring whose leftmost-lowest survivor is the (1,2) vertex at index 2
        float[] a = {3.1f, 5f, 4f, 4f, 1.2f, 2.4f, 2f, 3f};
        long p = SnapSimplify.snapDedupRingTracked(a, 4);
        assertEquals(4, SnapSimplify.trackedCount(p));
        assertEquals(2, SnapSimplify.trackedStart(p)); // rounds to (1,2): min x then y
        assertArrayEquals(new float[] {3, 5, 4, 4, 1, 2, 2, 3}, slice(a, 4), 0);
    }

    @Test
    public void dropCollinearRingTracksAreaInPass() {
        // CCW 4x4 square with a collinear midpoint; area = 16, shoelace = 32 (2*area)
        float[] a = {0, 0, 2, 0, 4, 0, 4, 4, 0, 4};
        float[] out = new float[12];
        SnapSimplify.RingResult res = new SnapSimplify.RingResult();
        SnapSimplify.dropCollinearRing(a, 5, 0, out, res);
        assertEquals(4, res.n);
        assertEquals(32.0, res.area, 0); // 2 * signed area, CCW positive
        assertArrayEquals(new float[] {0, 0, 4, 0, 4, 4, 0, 4}, slice(out, res.n), 0);
    }

    @Test
    public void dropCollinearRingAreaSignFollowsWinding() {
        // same square clockwise -> negative area
        float[] a = {0, 0, 0, 4, 4, 4, 4, 0, 2, 0};
        float[] out = new float[12];
        SnapSimplify.RingResult res = new SnapSimplify.RingResult();
        SnapSimplify.dropCollinearRing(a, 5, 0, out, res);
        assertEquals(4, res.n);
        assertEquals(-32.0, res.area, 0);
    }

    @Test
    public void dropCollinearLineRemovesMidpoints() {
        // straight line with redundant midpoints
        float[] a = {0, 0, 1, 0, 2, 0, 2, 5};
        int n = SnapSimplify.dropCollinearLine(a, 4);
        assertEquals(3, n);
        assertArrayEquals(new float[] {0, 0, 2, 0, 2, 5}, slice(a, n), 0);
    }

    @Test
    public void dropCollinearRingKeepsSquareCorners() {
        // square with an extra collinear vertex on the bottom edge
        float[] a = {0, 0, 2, 0, 4, 0, 4, 4, 0, 4};
        float[] out = new float[12];
        int n = SnapSimplify.dropCollinearRing(a, 5, 0, out); // (0,0) is the corner
        assertEquals(4, n);
        // output rotated to the leftmost-lowest corner (0,0), winding preserved
        assertArrayEquals(new float[] {0, 0, 4, 0, 4, 4, 0, 4}, slice(out, n), 0);
    }

    @Test
    public void dropCollinearRingHandlesWrapJoint() {
        // collinear vertex straddling the first/last wrap: (0,0)-(2,0)-(4,0)
        float[] a = {2, 0, 4, 0, 4, 4, 0, 4, 0, 0};
        float[] out = new float[12];
        int n = SnapSimplify.dropCollinearRing(a, 5, 4, out); // (0,0) at index 4
        // (2,0) is interior to the bottom edge -> dropped, even across the wrap
        assertEquals(4, n);
        assertArrayEquals(new float[] {0, 0, 4, 0, 4, 4, 0, 4}, slice(out, n), 0);
    }

    @Test
    public void dropCollinearRingCascadesAlongStraightRun() {
        // three collinear vertices on the bottom edge: all interior ones drop in
        // a single pass (the old multi-pass case)
        float[] a = {0, 0, 1, 0, 2, 0, 3, 0, 4, 0, 4, 4, 0, 4};
        float[] out = new float[a.length];
        int n = SnapSimplify.dropCollinearRing(a, 7, 0, out); // (0,0) at index 0
        assertEquals(4, n);
        assertArrayEquals(new float[] {0, 0, 4, 0, 4, 4, 0, 4}, slice(out, n), 0);
    }

    private static float[] slice(float[] a, int pairs) {
        float[] b = new float[pairs * 2];
        System.arraycopy(a, 0, b, 0, pairs * 2);
        return b;
    }
}
