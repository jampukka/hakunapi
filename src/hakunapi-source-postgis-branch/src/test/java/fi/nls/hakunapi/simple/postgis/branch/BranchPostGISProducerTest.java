package fi.nls.hakunapi.simple.postgis.branch;

import static org.junit.Assert.assertThrows;

import java.util.Collections;
import java.util.regex.Pattern;

import org.junit.Test;

import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import fi.nls.hakunapi.simple.postgis.SQLFeatureType;

public class BranchPostGISProducerTest {

    private static SQLFeatureType innerFt() {
        SQLFeatureType ft = new SQLFeatureType();
        ft.setName("mycol");
        ft.setStaticFilters(Collections.emptyList());
        ft.setProperties(Collections.emptyList());
        return ft;
    }

    private static BranchConfig branchConfig() {
        return new BranchConfig(Pattern.compile("^[a-z0-9_]+$"),
                Collections.singletonMap("jdbcUrl", "jdbc:postgresql://h/db_{branch}"));
    }

    private static GetFeatureCollection collectionFor(SQLFeatureType ft) {
        // The operation builds the collection bound to the published (wrapper) feature type; emulate a
        // wrapper by binding the collection to a BranchFeatureType over the same inner.
        BranchFeatureType wrapper = new BranchFeatureType(ft, branchConfig());
        return new GetFeatureCollection(wrapper);
    }

    /** The branch param is required (git-worktree: no branch, no datasource to read). */
    @Test
    public void getFeaturesRequiresBranchValue() {
        SQLFeatureType inner = innerFt();
        BranchPostGISProducer p = new BranchPostGISProducer(inner, branchConfig());

        assertThrows(IllegalArgumentException.class,
                () -> p.getFeatures(new GetFeatureRequest(), collectionFor(inner)));
    }

    @Test
    public void getNumberMatchedRequiresBranchValue() {
        SQLFeatureType inner = innerFt();
        BranchPostGISProducer p = new BranchPostGISProducer(inner, branchConfig());

        assertThrows(IllegalArgumentException.class,
                () -> p.getNumberMatched(new GetFeatureRequest(), collectionFor(inner)));
    }

    /** A branch value whose resolved db props carry no jdbcUrl is rejected before any connection. */
    @Test
    public void missingJdbcUrlRejected() {
        SQLFeatureType inner = innerFt();
        BranchConfig noUrl = new BranchConfig(Pattern.compile("^[a-z0-9_]+$"),
                Collections.singletonMap("username", "ro"));
        BranchPostGISProducer p = new BranchPostGISProducer(inner, noUrl);

        GetFeatureRequest request = new GetFeatureRequest();
        request.addQueryParam(BranchParam.PARAM_NAME, "wip");
        assertThrows(IllegalArgumentException.class,
                () -> p.getFeatures(request, collectionFor(inner)));
    }
}
