package fi.nls.hakunapi.simple.postgis.branch;

import java.util.Map;

import org.postgresql.ds.PGSimpleDataSource;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import fi.nls.hakunapi.simple.postgis.SQLFeatureType;
import fi.nls.hakunapi.simple.postgis.SimplePostGIS;

/**
 * Producer for a {@link BranchFeatureType}, giving git-worktree semantics: a {@code ?branch=<name>}
 * request reads that branch's own database verbatim. There is no "master" and no merge — a branch is
 * an isolated checkout of the collection's schema/table in another database, so this producer simply
 * resolves the branch connection and reads it as a plain PostGIS source.
 *
 * <p>The {@code ?branch=} parameter is required: without a (validated) branch value there is no
 * datasource to read, and {@link #getFeatures} throws {@link IllegalArgumentException} (surfaced as a
 * 400). {@link BranchParam} has already validated and stashed the value on the request.
 *
 * <p>The incoming collection is bound to the {@link BranchFeatureType} wrapper; this producer rebinds
 * it to the wrapped {@link SQLFeatureType} ({@code col.withFt(inner)}) so the PostGIS query code sees
 * a real {@code SQLFeatureType}.
 *
 * <p>The branch connection uses a non-pooled {@link PGSimpleDataSource}; the JDBC connection is opened
 * and closed per stream by {@link SimplePostGIS}, so there is no pool to manage. Recognised db props:
 * {@code jdbcUrl} (required), {@code username}, {@code password}.
 */
public class BranchPostGISProducer implements FeatureProducer {

    private final SQLFeatureType inner;
    private final BranchConfig branchConfig;

    public BranchPostGISProducer(SQLFeatureType inner, BranchConfig branchConfig) {
        this.inner = inner;
        this.branchConfig = branchConfig;
    }

    @Override
    public FeatureStream getFeatures(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        FeatureProducer branch = resolveBranchProducer(request);
        return branch.getFeatures(request, col.withFt(inner));
    }

    @Override
    public int getNumberMatched(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        FeatureProducer branch = resolveBranchProducer(request);
        return branch.getNumberMatched(request, col.withFt(inner));
    }

    /**
     * Resolve the producer for the requested branch. The {@code ?branch=} value is required and has
     * already been validated by {@link BranchParam}.
     *
     * @throws IllegalArgumentException if no branch value is present on the request
     */
    private FeatureProducer resolveBranchProducer(GetFeatureRequest request) {
        String branchValue = request.getQueryParam(BranchParam.PARAM_NAME);
        if (branchValue == null || branchValue.isEmpty()) {
            throw new IllegalArgumentException("Query parameter '" + BranchParam.PARAM_NAME + "' is required");
        }
        return buildBranchProducer(branchValue);
    }

    private FeatureProducer buildBranchProducer(String validatedBranchValue) {
        Map<String, String> props = branchConfig.resolveDbProps(validatedBranchValue);
        String jdbcUrl = props.get("jdbcUrl");
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            throw new IllegalArgumentException("branch db props missing required 'jdbcUrl'");
        }
        PGSimpleDataSource branchDs = new PGSimpleDataSource();
        branchDs.setUrl(jdbcUrl);
        String username = props.get("username");
        if (username != null) {
            branchDs.setUser(username);
        }
        String password = props.get("password");
        if (password != null) {
            branchDs.setPassword(password);
        }
        return new SimplePostGIS(branchDs);
    }

}
