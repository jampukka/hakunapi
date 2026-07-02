package fi.nls.hakunapi.simple.union;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;

import org.junit.Test;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyWriters;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyLong;

public class UnionFeatureTypeTest {

    private static SimpleFeatureType ftWithId() {
        SimpleFeatureType ft = new SimpleFeatureType() {
            @Override public FeatureProducer getFeatureProducer() {
                return new FeatureProducer() {
                    @Override public fi.nls.hakunapi.core.FeatureStream getFeatures(
                            fi.nls.hakunapi.core.request.GetFeatureRequest r,
                            fi.nls.hakunapi.core.request.GetFeatureCollection c) { return null; }
                    @Override public int getNumberMatched(
                            fi.nls.hakunapi.core.request.GetFeatureRequest r,
                            fi.nls.hakunapi.core.request.GetFeatureCollection c) { return 0; }
                };
            }
        };
        ft.setId(new HakunaPropertyLong("id", "t", "id", false, true, HakunaPropertyWriters.HIDDEN));
        ft.setProperties(new ArrayList<>(Collections.singletonList(
                new HakunaPropertyLong("val", "t", "val", true, false, HakunaPropertyWriters.HIDDEN))));
        ft.setStaticFilters(new ArrayList<>());
        return ft;
    }

    @Test
    public void schemaDelegatesToMaster() {
        SimpleFeatureType master = ftWithId();
        SimpleFeatureType branch = ftWithId();
        UnionFeatureType union = new UnionFeatureType(branch, master);

        assertSame(master.getId(), union.getId());
        assertSame(master.getProperties(), union.getProperties());
        assertSame(master, union.getMaster());
        assertSame(branch, union.getBranch());
    }

    @Test
    public void producesUnionFeatureProducer() {
        UnionFeatureType union = new UnionFeatureType(ftWithId(), ftWithId());
        assertTrue(union.getFeatureProducer() instanceof UnionFeatureProducer);
    }
}
