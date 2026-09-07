package fi.nls.hakunapi.tiles.source.vectortile.geom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RingClipTest {

    private static final double MIN = 0, MAX = 10;

    @Test
    public void ringFullyInsideUnchanged() {
        float[] src = {2, 2, 8, 2, 8, 8, 2, 8};
        float[] dst = new float[64];
        float[] tmp = new float[64];
        int n = RingClip.clip(src, 4, MIN, MIN, MAX, MAX, dst, tmp);
        assertEquals(4, n);
    }

    @Test
    public void ringFullyOutsideEmpty() {
        float[] src = {20, 20, 30, 20, 30, 30, 20, 30};
        float[] dst = new float[64];
        float[] tmp = new float[64];
        int n = RingClip.clip(src, 4, MIN, MIN, MAX, MAX, dst, tmp);
        assertEquals(0, n);
    }

    @Test
    public void ringCrossingEdgesClampedToRect() {
        // big square covering whole rect -> clipped to the rect itself
        float[] src = {-5, -5, 15, -5, 15, 15, -5, 15};
        float[] dst = new float[64];
        float[] tmp = new float[64];
        int n = RingClip.clip(src, 4, MIN, MIN, MAX, MAX, dst, tmp);
        assertEquals(4, n);
        // all output coords lie on the rect boundary
        for (int i = 0; i < n; i++) {
            double x = dst[i * 2], y = dst[i * 2 + 1];
            assertTrue(x >= MIN - 1e-9 && x <= MAX + 1e-9);
            assertTrue(y >= MIN - 1e-9 && y <= MAX + 1e-9);
        }
    }

    @Test
    public void triangleHalfOutsideAddsIntersections() {
        // triangle poking out the right edge gains vertices on x=MAX
        float[] src = {5, 5, 15, 5, 5, 9};
        float[] dst = new float[64];
        float[] tmp = new float[64];
        int n = RingClip.clip(src, 3, MIN, MIN, MAX, MAX, dst, tmp);
        assertTrue("expected clipped poly to gain a vertex", n >= 4);
        for (int i = 0; i < n; i++) {
            assertTrue(dst[i * 2] <= MAX + 1e-9);
        }
    }
}
