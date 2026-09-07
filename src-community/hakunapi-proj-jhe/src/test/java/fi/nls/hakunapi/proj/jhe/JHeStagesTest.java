package fi.nls.hakunapi.proj.jhe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class JHeStagesTest {

    private static final double LON = 24.0;
    private static final double LAT = 60.0;
    private static final double TM35_E = 332705.1789;
    private static final double TM35_N = 6655205.4835;

    private static final double DELTA_M = 1e-3;
    private static final double DELTA_DEG = 1e-8;
    private static final double DELTA_RAD = 1e-10;

    @Test
    public void testPipelineIsSourceToRadiansThenRadiansToTarget() {
        JHeStage[] stages = JHeMathTransformFactory.findStages(3067, 3878);
        assertEquals(2, stages.length);

        // Applying the stages by hand must match the resolved transform
        double[] byHand = { TM35_E, TM35_N };
        for (JHeStage stage : stages) {
            stage.apply(byHand, 0, 1);
        }

        double[] byTransform = { TM35_E, TM35_N };
        JHeMathTransformFactory.findMathTransform(3067, 3878).apply(byTransform, 0, 1);

        assertEquals(byTransform[0], byHand[0], 0.0);
        assertEquals(byTransform[1], byHand[1], 0.0);
    }

    // A geographic end folds its degree conversion into the neighbouring projection
    // stage, so those pairs are a single pass rather than two
    @Test
    public void testGeographicEndIsASinglePass() {
        assertEquals(1, JHeMathTransformFactory.findStages(4326, 3067).length);
        assertEquals(1, JHeMathTransformFactory.findStages(3067, 4326).length);
        assertEquals(1, JHeMathTransformFactory.findStages(3857, 4258).length);
        assertEquals(1, JHeMathTransformFactory.findStages(4258, 3878).length);

        // Neither end geographic: source projection then target projection
        assertEquals(2, JHeMathTransformFactory.findStages(3067, 3878).length);
        assertEquals(2, JHeMathTransformFactory.findStages(3067, 3857).length);
        assertEquals(2, JHeMathTransformFactory.findStages(3878, 3067).length);
    }

    @Test
    public void testDegreeAndRadianStagesAgree() {
        double lonRad = Math.toRadians(LON);
        double latRad = Math.toRadians(LAT);

        double[] deg = { LON, LAT };
        JHeStages.fromGeodetic(3067, true).apply(deg, 0, 1);

        double[] rad = { lonRad, latRad };
        JHeStages.fromGeodetic(3067, false).apply(rad, 0, 1);

        assertEquals(deg[0], rad[0], DELTA_M);
        assertEquals(deg[1], rad[1], DELTA_M);
        assertEquals(TM35_E, deg[0], DELTA_M);
        assertEquals(TM35_N, deg[1], DELTA_M);

        double[] back = { TM35_E, TM35_N };
        JHeStages.toGeodetic(3067, true).apply(back, 0, 1);
        assertEquals(LON, back[0], DELTA_DEG);
        assertEquals(LAT, back[1], DELTA_DEG);

        back = new double[] { TM35_E, TM35_N };
        JHeStages.toGeodetic(3067, false).apply(back, 0, 1);
        assertEquals(lonRad, back[0], DELTA_RAD);
        assertEquals(latRad, back[1], DELTA_RAD);
    }

    @Test
    public void testStageTransformsWholeBlockAtOffset() {
        JHeStage stage = JHeStages.fromGeodetic(3067, false);

        double lonRad = Math.toRadians(LON);
        double latRad = Math.toRadians(LAT);
        double[] xy = { -1, -1, lonRad, latRad, lonRad, latRad, lonRad, latRad, -1, -1 };
        stage.apply(xy, 2, 3);

        assertEquals(-1.0, xy[0], 0.0);
        assertEquals(-1.0, xy[1], 0.0);
        for (int i = 2; i < 8; i += 2) {
            assertEquals("point " + (i / 2) + " E", TM35_E, xy[i], DELTA_M);
            assertEquals("point " + (i / 2) + " N", TM35_N, xy[i + 1], DELTA_M);
        }
        assertEquals(-1.0, xy[8], 0.0);
        assertEquals(-1.0, xy[9], 0.0);
    }

    // A single-stage pair is returned as that stage, with no wrapper around it
    @Test
    public void testSingleStagePipelineIsNotWrapped() {
        assertEquals(1, JHeMathTransformFactory.findStages(3857, 4258).length);
        assertSame(JHeMathTransformFactory.findStages(3857, 4258)[0],
                JHeMathTransformFactory.findMathTransform(3857, 4258));
    }

    // Every stage is built once at class init, so resolving a pair allocates nothing
    @Test
    public void testStagesAreSharedInstances() {
        assertSame(JHeStages.toGeodetic(3067, false), JHeStages.toGeodetic(3067, false));
        assertSame(JHeStages.fromGeodetic(3878, true), JHeStages.fromGeodetic(3878, true));
        assertSame(JHeStages.toGeodetic(3857, true), JHeStages.toGeodetic(3857, true));

        assertSame(JHeMathTransformFactory.findStages(3067, 3878)[0],
                JHeMathTransformFactory.findStages(3067, 3885)[0]);

        // 3047 and 3067 are the same projection but must stay distinct SRIDs
        assertSame(JHeStages.toGeodetic(3047, false), JHeStages.toGeodetic(3047, false));
    }

    @Test
    public void testUnsupportedSRIDPerDirection() {
        assertThrows(IllegalArgumentException.class, () -> JHeStages.toGeodetic(2393, false));
        assertThrows(IllegalArgumentException.class, () -> JHeStages.fromGeodetic(2393, false));
        assertThrows(IllegalArgumentException.class, () -> JHeStages.toGeodetic(4258, false));
        assertThrows(IllegalArgumentException.class, () -> JHeStages.toGeodetic(3886, false));
        assertThrows(IllegalArgumentException.class, () -> JHeStages.toGeodetic(3045, false));
    }

    @Test
    public void testUnsupportedSRIDNamesTheEndThatFailed() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> JHeMathTransformFactory.findStages(2393, 3067));
        assertEquals("Could not find transform from 2393", e.getMessage());

        e = assertThrows(IllegalArgumentException.class,
                () -> JHeMathTransformFactory.findStages(3067, 2393));
        assertEquals("Could not find transform to 2393", e.getMessage());
    }

}
