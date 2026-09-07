package fi.nls.hakunapi.proj.jhe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class EUREFFINTest {

    // Reference values from PROJ 8.2.0, easting-first:
    //   echo "60 24" | cs2cs -f "%.4f" EPSG:4258 EPSG:3067
    private static final double LON = 24.0;
    private static final double LAT = 60.0;
    private static final double TM35_E = 332705.1789;
    private static final double TM35_N = 6655205.4835;

    // The series expansions are exact to well under a tenth of a millimetre, which
    // is also the precision the cs2cs reference values are quoted to
    private static final double DELTA_M = 1e-4;

    @Test
    public void testAgainstProjReferenceValues() {
        double[] xy = new double[2];

        EUREFFIN.geoToTM35fin(LON, LAT, xy, 0);
        assertEquals(TM35_E, xy[0], DELTA_M);
        assertEquals(TM35_N, xy[1], DELTA_M);

        EUREFFIN.geoToPlane(LON, LAT, EUREFFIN.k0_TM, EUREFFIN.l0_TM34, EUREFFIN.E0_TM, xy, 0);
        assertEquals(667294.8211, xy[0], DELTA_M);
        assertEquals(6655205.4835, xy[1], DELTA_M);

        EUREFFIN.geoToPlane(LON, LAT, EUREFFIN.k0_TM, EUREFFIN.l0_TM36, EUREFFIN.E0_TM, xy, 0);
        assertEquals(-961.4045, xy[0], DELTA_M);
        assertEquals(6685590.8934, xy[1], DELTA_M);

        EUREFFIN.geoToGKnn(19, LON, LAT, xy, 0);
        assertEquals(19778822.7589, xy[0], DELTA_M);
        assertEquals(6664618.8880, xy[1], DELTA_M);

        EUREFFIN.geoToGKnn(24, LON, LAT, xy, 0);
        assertEquals(24500000.0000, xy[0], DELTA_M);
        assertEquals(6654072.8194, xy[1], DELTA_M);

        EUREFFIN.geoToGKnn(31, LON, LAT, xy, 0);
        assertEquals(31109887.2536, xy[0], DELTA_M);
        assertEquals(6674749.3938, xy[1], DELTA_M);

        EUREFFIN.geoToWebMerc(LON, LAT, xy, 0);
        assertEquals(2671667.7790, xy[0], DELTA_M);
        assertEquals(8399737.8898, xy[1], DELTA_M);
    }

    @Test
    public void testInverseFromProjReferenceValues() {
        double[] xy = new double[2];

        EUREFFIN.tm35finToGeo(TM35_E, TM35_N, xy, 0);
        assertEquals(LON, xy[0], 1e-8);
        assertEquals(LAT, xy[1], 1e-8);

        EUREFFIN.gkNNtoGeo(24, 24500000.0000, 6654072.8194, xy, 0);
        assertEquals(LON, xy[0], 1e-8);
        assertEquals(LAT, xy[1], 1e-8);
    }

    // The forward and inverse latitude series must invert each other well inside a
    // millimetre over an extent generously wider than the CRSs are defined for
    @Test
    public void testRoundTripAccuracyAcrossExtent() {
        double[] xy = new double[2];
        double worstMillimetres = 0.0;

        for (double lon = 15.0; lon <= 40.0; lon += 0.5) {
            for (double lat = 45.0; lat <= 84.0; lat += 0.5) {
                EUREFFIN.geoToTM35fin(lon, lat, xy, 0);
                EUREFFIN.tm35finToGeo(xy[0], xy[1], xy, 0);

                // Degrees to metres, generously: a degree of latitude is ~111 km
                double dx = (xy[0] - lon) * 111320.0 * Math.cos(Math.toRadians(lat));
                double dy = (xy[1] - lat) * 111320.0;
                worstMillimetres = Math.max(worstMillimetres, Math.hypot(dx, dy) * 1000.0);
            }
        }

        assertTrue("Round trip off by " + worstMillimetres + " mm", worstMillimetres < 1.0);
    }

}
