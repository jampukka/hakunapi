package fi.nls.hakunapi.simple.mosaic;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.filter.FilterOp;

public class MosaicFeatureProducerTest {

    private static final GeometryFactory GF = new GeometryFactory();
    private static final double EPS = 1e-9;

    @Test
    public void noSpatialFilterSelectsAllTiles() {
        assertNull(MosaicFeatureProducer.extractBbox(Collections.emptyList()));
        List<Filter> attrOnly = Arrays.asList(new Filter(FilterOp.EQUAL_TO, null, "x"));
        assertNull(MosaicFeatureProducer.extractBbox(attrOnly));
    }

    @Test
    public void intersectsGivesGeometryEnvelope() {
        Geometry g = box(10, 20, 30, 40);
        double[] bbox = MosaicFeatureProducer.extractBbox(
                Arrays.asList(new Filter(FilterOp.INTERSECTS, null, g)));
        assertArrayEquals(new double[] { 10, 20, 30, 40 }, bbox, EPS);
    }

    @Test
    public void multipleSpatialFiltersIntersect() {
        Filter a = new Filter(FilterOp.INTERSECTS, null, box(0, 0, 30, 30));
        Filter b = new Filter(FilterOp.WITHIN, null, box(10, 10, 40, 40));
        double[] bbox = MosaicFeatureProducer.extractBbox(Arrays.asList(a, b));
        assertArrayEquals(new double[] { 10, 10, 30, 30 }, bbox, EPS);
    }

    @Test
    public void spatialFilterNestedInAndIsFound() {
        Filter spatial = new Filter(FilterOp.INTERSECTS, null, box(5, 5, 15, 15));
        Filter attr = new Filter(FilterOp.EQUAL_TO, null, "x");
        Filter and = new Filter(FilterOp.AND, null, Arrays.asList(attr, spatial));
        double[] bbox = MosaicFeatureProducer.extractBbox(Collections.singletonList(and));
        assertArrayEquals(new double[] { 5, 5, 15, 15 }, bbox, EPS);
    }

    private static Geometry box(double minx, double miny, double maxx, double maxy) {
        return GF.toGeometry(new Envelope(minx, maxx, miny, maxy));
    }

}
