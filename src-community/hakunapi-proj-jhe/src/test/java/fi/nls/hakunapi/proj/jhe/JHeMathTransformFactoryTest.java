package fi.nls.hakunapi.proj.jhe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import fi.nls.hakunapi.core.projection.ProjectionTransformer;

public class JHeMathTransformFactoryTest {

    // EUREF-FIN lon 24 lat 60, and the plane coordinates of that same point in
    // every supported projected CRS. Reference values from PROJ 8.2.0:
    //   echo "60 24" | cs2cs -f "%.4f" EPSG:4258 EPSG:<srid>
    // cs2cs emits them in EPSG axis order, so the northing-first ones (3046-3048,
    // 3873-3885) are swapped here -- EUREFFIN is easting-first throughout.
    private static final double LON = 24.0;
    private static final double LAT = 60.0;
    private static final double TM34_E = 667294.8211;
    private static final double TM34_N = 6655205.4835;
    private static final double TM35_E = 332705.1789;
    private static final double TM35_N = 6655205.4835;
    private static final double TM36_E = -961.4045;
    private static final double TM36_N = 6685590.8934;
    private static final double GK19_E = 19778822.7589;
    private static final double GK19_N = 6664618.8880;
    private static final double GK24_E = 24500000.0000;
    private static final double GK24_N = 6654072.8194;
    private static final double GK31_E = 31109887.2536;
    private static final double GK31_N = 6674749.3938;
    private static final double WEBMERC_X = 2671667.7790;
    private static final double WEBMERC_Y = 8399737.8898;

    private static final double DELTA_M = 1e-3;
    private static final double DELTA_DEG = 1e-8;

    @Test
    public void testGeographicToProjected() throws Exception {
        assertTransform(4258, 3046, LON, LAT, TM34_E, TM34_N, DELTA_M);
        assertTransform(4258, 3047, LON, LAT, TM35_E, TM35_N, DELTA_M);
        assertTransform(4258, 3048, LON, LAT, TM36_E, TM36_N, DELTA_M);
        assertTransform(4258, 3067, LON, LAT, TM35_E, TM35_N, DELTA_M);
        assertTransform(4258, 3857, LON, LAT, WEBMERC_X, WEBMERC_Y, DELTA_M);

        assertTransform(4258, 3873, LON, LAT, GK19_E, GK19_N, DELTA_M);
        assertTransform(4258, 3878, LON, LAT, GK24_E, GK24_N, DELTA_M);
        assertTransform(4258, 3885, LON, LAT, GK31_E, GK31_N, DELTA_M);

        // 4326 and 84 are treated as EUREF-FIN, the ellipsoid difference is ignored
        assertTransform(4326, 3067, LON, LAT, TM35_E, TM35_N, DELTA_M);
        assertTransform(84, 3067, LON, LAT, TM35_E, TM35_N, DELTA_M);
    }

    @Test
    public void testProjectedToGeographic() throws Exception {
        assertTransform(3046, 4258, TM34_E, TM34_N, LON, LAT, DELTA_DEG);
        assertTransform(3047, 4258, TM35_E, TM35_N, LON, LAT, DELTA_DEG);
        assertTransform(3048, 4258, TM36_E, TM36_N, LON, LAT, DELTA_DEG);
        assertTransform(3067, 4258, TM35_E, TM35_N, LON, LAT, DELTA_DEG);
        assertTransform(3857, 4258, WEBMERC_X, WEBMERC_Y, LON, LAT, DELTA_DEG);

        assertTransform(3873, 4258, GK19_E, GK19_N, LON, LAT, DELTA_DEG);
        assertTransform(3878, 4258, GK24_E, GK24_N, LON, LAT, DELTA_DEG);
        assertTransform(3885, 4258, GK31_E, GK31_N, LON, LAT, DELTA_DEG);

        assertTransform(3067, 4326, TM35_E, TM35_N, LON, LAT, DELTA_DEG);
    }

    @Test
    public void testProjectedToProjected() throws Exception {
        assertTransform(3067, 3046, TM35_E, TM35_N, TM34_E, TM34_N, DELTA_M);
        assertTransform(3067, 3048, TM35_E, TM35_N, TM36_E, TM36_N, DELTA_M);
        assertTransform(3067, 3857, TM35_E, TM35_N, WEBMERC_X, WEBMERC_Y, DELTA_M);
        assertTransform(3067, 3878, TM35_E, TM35_N, GK24_E, GK24_N, DELTA_M);
        assertTransform(3857, 3067, WEBMERC_X, WEBMERC_Y, TM35_E, TM35_N, DELTA_M);
    }

    // Every GKnn source pair used to decode the zone meridian from the TARGET SRID,
    // yielding NaN for 3046-3048/3067 targets and a silently wrong result for 3857
    // Every GKnn source pair used to decode the zone meridian from the TARGET SRID,
    // yielding NaN for the 3046-3048/3067 targets and a silently wrong result for 3857
    @Test
    public void testGKnnSourceZoneFromSourceSRID() throws Exception {
        assertTransform(3878, 3067, GK24_E, GK24_N, TM35_E, TM35_N, DELTA_M);
        assertTransform(3878, 3047, GK24_E, GK24_N, TM35_E, TM35_N, DELTA_M);
        assertTransform(3878, 3046, GK24_E, GK24_N, TM34_E, TM34_N, DELTA_M);
        assertTransform(3878, 3048, GK24_E, GK24_N, TM36_E, TM36_N, DELTA_M);
        assertTransform(3878, 3857, GK24_E, GK24_N, WEBMERC_X, WEBMERC_Y, DELTA_M);
        assertTransform(3873, 3885, GK19_E, GK19_N, GK31_E, GK31_N, DELTA_M);
    }

    // Every other case of the to == 4258 branch returns degrees; this one returned radians
    @Test
    public void testWebMercatorToGeographicReturnsDegrees() throws Exception {
        double[] xy = transform(3857, 4258, WEBMERC_X, WEBMERC_Y);
        assertTrue("Expected degrees, got " + xy[0] + " " + xy[1],
                Math.abs(xy[0]) > 1.0 && Math.abs(xy[1]) > 1.0);
        assertEquals(LON, xy[0], DELTA_DEG);
        assertEquals(LAT, xy[1], DELTA_DEG);
    }

    @Test
    public void testRoundTripAllSupportedPairs() throws Exception {
        int[] srids = { 4258, 4326, 3046, 3047, 3048, 3067, 3857,
                3873, 3874, 3875, 3876, 3877, 3878, 3879, 3880, 3881, 3882, 3883, 3884, 3885 };

        for (int from : srids) {
            double[] start = transform(4258, from, LON, LAT);
            for (int to : srids) {
                double[] there = transform(from, to, start[0], start[1]);
                double[] back = transform(to, from, there[0], there[1]);
                double delta = isGeographic(from) ? 1e-7 : 1e-3;
                assertEquals(from + " -> " + to + " -> " + from + " x", start[0], back[0], delta);
                assertEquals(from + " -> " + to + " -> " + from + " y", start[1], back[1], delta);
            }
        }
    }

    @Test
    public void testEquivalentSRIDsAreNOP() {
        assertNull(JHeMathTransformFactory.findMathTransform(3067, 3067));
        assertNull(JHeMathTransformFactory.findMathTransform(4258, 4326));
        assertNull(JHeMathTransformFactory.findMathTransform(4326, 84));
        assertNotNull(JHeMathTransformFactory.findMathTransform(3067, 4258));
    }

    @Test
    public void testUnsupportedSRID() {
        assertThrows(IllegalArgumentException.class,
                () -> JHeMathTransformFactory.findMathTransform(2393, 3067));
        assertThrows(IllegalArgumentException.class,
                () -> JHeMathTransformFactory.findMathTransform(3067, 2393));
    }

    @Test
    public void testTransformsManyPointsAtOffset() throws Exception {
        JHeStage t = JHeMathTransformFactory.findMathTransform(4258, 3067);
        double[] xy = { 0, 0, LON, LAT, LON, LAT, 0, 0 };
        t.apply(xy, 2, 2);

        assertEquals(0.0, xy[0], 0.0);
        assertEquals(0.0, xy[1], 0.0);
        assertEquals(TM35_E, xy[2], DELTA_M);
        assertEquals(TM35_N, xy[3], DELTA_M);
        assertEquals(TM35_E, xy[4], DELTA_M);
        assertEquals(TM35_N, xy[5], DELTA_M);
        assertEquals(0.0, xy[6], 0.0);
        assertEquals(0.0, xy[7], 0.0);
    }

    // transformInPlace over a whole ring: only the first point used to be transformed,
    // because the per-point loop never advanced dstOff
    @Test
    public void testTransformsEveryPointOfARing() throws Exception {
        JHeProjectionTransformerFactory f = new JHeProjectionTransformerFactory();
        ProjectionTransformer t = f.getTransformer(4258, 3067);

        double[] ring = { LON, LAT, LON, LAT, LON, LAT, LON, LAT };
        t.transformInPlace(ring, 0, 4);

        for (int i = 0; i < ring.length; i += 2) {
            assertEquals("point " + (i / 2) + " x", TM35_E, ring[i], DELTA_M);
            assertEquals("point " + (i / 2) + " y", TM35_N, ring[i + 1], DELTA_M);
        }
    }

    @Test
    public void testTransformerIsCachedAndNOPForEquivalentSRIDs() throws Exception {
        JHeProjectionTransformerFactory f = new JHeProjectionTransformerFactory();

        assertTrue(f.getTransformer(3067, 3067).isNOP());
        assertTrue(f.getTransformer(4258, 4326).isNOP());
        assertSame(f.getTransformer(3067, 4258), f.getTransformer(3067, 4258));
        assertSame(f.getTransformer(3067, 4258), new JHeProjectionTransformerFactory().getTransformer(3067, 4258));
    }

    private static void assertTransform(int from, int to,
            double x, double y, double expectedX, double expectedY, double delta) {
        double[] xy = transform(from, to, x, y);
        assertEquals(from + " -> " + to + " x", expectedX, xy[0], delta);
        assertEquals(from + " -> " + to + " y", expectedY, xy[1], delta);
    }

    private static boolean isGeographic(int srid) {
        return srid == 4258 || srid == 4326 || srid == 84;
    }

    private static double[] transform(int from, int to, double x, double y) {
        JHeStage t = JHeMathTransformFactory.findMathTransform(from, to);
        double[] xy = { x, y };
        if (t != null) {
            t.apply(xy, 0, 1);
        }
        return xy;
    }

}
