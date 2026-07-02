package fi.nls.hakunapi.simple.postgis.branch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.nls.hakunapi.core.CacheSettings;
import fi.nls.hakunapi.core.DatetimeProperty;
import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.OrderBy;
import fi.nls.hakunapi.core.PaginationStrategy;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.param.GetFeatureParam;
import fi.nls.hakunapi.core.projection.ProjectionTransformerFactory;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;
import fi.nls.hakunapi.core.schemas.Link;
import fi.nls.hakunapi.simple.postgis.SQLFeatureType;
import io.swagger.v3.oas.models.media.Schema;

/**
 * A published feature type that adds a required per-request {@code ?branch=} selector (git-worktree
 * semantics) to a plain PostGIS collection: each branch is read from its own database. It wraps the
 * {@link SQLFeatureType} produced by the base PostGIS parser (delegating all schema/metadata to it,
 * so its bound properties and filters keep working) and only changes two things:
 *
 * <ul>
 *   <li>{@link #getParameters()} additionally registers a {@link BranchParam}, making {@code branch}
 *       a known query parameter that validates and stashes its value.</li>
 *   <li>{@link #getFeatureProducer()} returns a {@link BranchPostGISProducer} that reads the branch's
 *       own database for the validated branch value.</li>
 * </ul>
 *
 * <p>A wrapper (not a re-parse) is required because the base parser hard-codes {@code SQLFeatureType}
 * and binds every property to that instance, and a property's feature type cannot be rebound. This
 * type therefore extends {@link SimpleFeatureType} only to satisfy the {@code SimpleSource.parse}
 * return type; all of its own state is unused and every getter delegates to {@link #inner}. The
 * producer feeds {@code inner} back into the query collection (via
 * {@code GetFeatureCollection.withFt}), so the PostGIS query code still sees a {@code SQLFeatureType}.
 */
public class BranchFeatureType extends SimpleFeatureType {

    private final SQLFeatureType inner;
    private final BranchConfig branchConfig;

    public BranchFeatureType(SQLFeatureType inner, BranchConfig branchConfig) {
        this.inner = inner;
        this.branchConfig = branchConfig;
    }

    /** The wrapped PostGIS feature type; the PostGIS query code must see this, not the wrapper. */
    public SQLFeatureType getInner() {
        return inner;
    }

    @Override
    public List<GetFeatureParam> getParameters() {
        List<GetFeatureParam> params = new ArrayList<>(inner.getParameters());
        params.add(new BranchParam(branchConfig));
        return params;
    }

    @Override
    public FeatureProducer getFeatureProducer() {
        return new BranchPostGISProducer(inner, branchConfig);
    }

    // ---- everything else delegates verbatim to the wrapped PostGIS feature type ----

    @Override public String getName() { return inner.getName(); }
    @Override public String getNS() { return inner.getNS(); }
    @Override public String getSchemaLocation() { return inner.getSchemaLocation(); }
    @Override public String getTitle() { return inner.getTitle(); }
    @Override public String getDescription() { return inner.getDescription(); }
    @Override public Map<String, Object> getMetadata() { return inner.getMetadata(); }
    @Override public HakunaProperty getId() { return inner.getId(); }
    @Override public HakunaPropertyGeometry getGeom() { return inner.getGeom(); }
    @Override public List<HakunaProperty> getProperties() { return inner.getProperties(); }
    @Override public List<HakunaProperty> getSchemaProperties() { return inner.getSchemaProperties(); }
    @Override public List<HakunaProperty> getQueryableProperties() { return inner.getQueryableProperties(); }
    @Override public List<DatetimeProperty> getDatetimeProperties() { return inner.getDatetimeProperties(); }
    @Override public double[] getSpatialExtent() { return inner.getSpatialExtent(); }
    @Override public Instant[] getTemporalExtent() { return inner.getTemporalExtent(); }
    @Override public List<GetFeatureParam> getConformanceParams(List<GetFeatureParam> p) { return inner.getConformanceParams(p); }
    @Override public List<Filter> getStaticFilters() { return inner.getStaticFilters(); }
    @Override public ProjectionTransformerFactory getProjectionTransformerFactory() { return inner.getProjectionTransformerFactory(); }
    @Override public boolean isSourceWillProject() { return inner.isSourceWillProject(); }
    @Override public PaginationStrategy getPaginationStrategy() { return inner.getPaginationStrategy(); }
    @Override public List<OrderBy> getDefaultOrderBy() { return inner.getDefaultOrderBy(); }
    @Override public CacheSettings getCacheSettings() { return inner.getCacheSettings(); }
    @Override public List<Link> getAdditionalLinks() { return inner.getAdditionalLinks(); }
    @Override public Map<String, Schema<?>> getLangToSchema() { return inner.getLangToSchema(); }

}
