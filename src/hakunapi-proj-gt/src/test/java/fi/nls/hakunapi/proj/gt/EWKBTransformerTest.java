package fi.nls.hakunapi.proj.gt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;

import fi.nls.hakunapi.core.geom.HakunaGeometryEWKB;
import fi.nls.hakunapi.core.projection.EWKBTransformer;
import fi.nls.hakunapi.core.projection.JTSTransformer;
import fi.nls.hakunapi.core.projection.ProjectionTransformer;
import fi.nls.hakunapi.core.projection.ProjectionTransformerFactory;

/**
 * Reprojecting the WKB bytes in place must agree with reprojecting the JTS tree,
 * under a real GeoTools transform. The structural cases live in hakunapi-core's
 * own EWKBTransformerTest against an affine stub; what a real transform adds here
 * is a non-linear one, where an error in coordinate pairing or ordering would
 * show up as a wrong result rather than a plausible one.
 */
public class EWKBTransformerTest {

    /**
     * Both routes call the identical GeoTools MathTransform on the same ordinates,
     * so they should agree to the last bit; the tolerance only allows for the
     * order of operations differing.
     */
    private static final double TOLERANCE_METRES = 1e-9;

    @Test
    public void testGeoToolsEWKB() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        ProjectionTransformerFactory fact = new GeoToolsProjectionTransformerFactory();
        ProjectionTransformer t = fact.getTransformer(3067, 3879);
        EWKBTransformer ewkbt = new EWKBTransformer(t);

        Geometry g3067 = gf.createPoint(new Coordinate(387352, 6673122)).buffer(10, 8);

        WKBWriter w = new WKBWriter();
        byte[] bytest3067 = w.write(g3067);

        WKBReader r = new WKBReader();

        Geometry gWkbRead = r.read(bytest3067);

        assertTrue(g3067.equals(gWkbRead));

        ewkbt.transformEWKB(bytest3067);

        // The reference: the same geometry reprojected the JTS way.
        Geometry expected = g3067.copy();
        expected.apply((CoordinateSequenceFilter) new JTSTransformer(t));

        assertCoordinatesEqual(expected, r.read(bytest3067));
    }

    /**
     * The path the feature mapper actually takes for a GeoPackage geometry:
     * transform(WKBBacked), walking the body from getDataStart().
     */
    @Test
    public void testWkbBackedMatchesJtsPath() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        ProjectionTransformerFactory fact = new GeoToolsProjectionTransformerFactory();
        ProjectionTransformer t = fact.getTransformer(3067, 3857);

        // A polygon with a hole, so ring order and hole offsets are exercised.
        Geometry outer = gf.createPoint(new Coordinate(387352, 6673122)).buffer(100, 16);
        Geometry inner = gf.createPoint(new Coordinate(387352, 6673122)).buffer(30, 16);
        Geometry g3067 = outer.difference(inner);

        byte[] wkb = new WKBWriter().write(g3067);
        HakunaGeometryEWKB geom = new HakunaGeometryEWKB(wkb);
        new EWKBTransformer(t).transform(geom);

        Geometry expected = g3067.copy();
        expected.apply((CoordinateSequenceFilter) new JTSTransformer(t));

        assertCoordinatesEqual(expected, new WKBReader().read(wkb));
    }

    private static void assertCoordinatesEqual(Geometry expected, Geometry actual) {
        Coordinate[] e = expected.getCoordinates();
        Coordinate[] a = actual.getCoordinates();
        assertEquals("coordinate count", e.length, a.length);
        for (int i = 0; i < e.length; i++) {
            assertEquals("x[" + i + "]", e[i].x, a[i].x, TOLERANCE_METRES);
            assertEquals("y[" + i + "]", e[i].y, a[i].y, TOLERANCE_METRES);
        }
    }

}
