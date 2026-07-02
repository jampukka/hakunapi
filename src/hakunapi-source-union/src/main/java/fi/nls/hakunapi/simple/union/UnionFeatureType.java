package fi.nls.hakunapi.simple.union;

import java.util.List;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;

/**
 * A {@link FeatureType} whose {@code /items} response is the UNION-distinct-by-id overlay of two
 * child feature types: a {@code branch} laid on top of a {@code master}, the branch winning on an id
 * collision.
 *
 * <p>The two children share this type's schema (identical id/geometry/properties, same order), so the
 * union has no schema of its own: it publishes the master's (whose {@link HakunaProperty} instances
 * are already bound to the master feature type — delegating instead of re-owning via
 * {@code setId}/{@code setProperties}, which would re-bind and fail) and delegates row production to a
 * {@link UnionFeatureProducer} over the children's producers. Mixed backends are allowed as long as
 * both children emit rows ordered ascending by id and honor the same pushdown.
 */
public class UnionFeatureType extends SimpleFeatureType {

    private final FeatureType branch;
    private final FeatureType master;

    public UnionFeatureType(FeatureType branch, FeatureType master) {
        this.branch = branch;
        this.master = master;
    }

    public FeatureType getBranch() {
        return branch;
    }

    public FeatureType getMaster() {
        return master;
    }

    @Override
    public HakunaProperty getId() {
        return master.getId();
    }

    @Override
    public HakunaPropertyGeometry getGeom() {
        return master.getGeom();
    }

    @Override
    public List<HakunaProperty> getProperties() {
        return master.getProperties();
    }

    @Override
    public FeatureProducer getFeatureProducer() {
        return new UnionFeatureProducer(
                branch, branch.getFeatureProducer(),
                master, master.getFeatureProducer());
    }

}
