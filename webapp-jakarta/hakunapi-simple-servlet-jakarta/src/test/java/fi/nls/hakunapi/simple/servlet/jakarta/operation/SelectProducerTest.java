package fi.nls.hakunapi.simple.servlet.jakarta.operation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import fi.nls.hakunapi.core.BranchConfig;
import fi.nls.hakunapi.core.BranchableFeatureType;
import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.UnionFeatureProducer;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;

public class SelectProducerTest {

    private static final FeatureProducer MASTER = new FeatureProducer() {
        @Override public FeatureStream getFeatures(GetFeatureRequest r, GetFeatureCollection c) { return null; }
        @Override public int getNumberMatched(GetFeatureRequest r, GetFeatureCollection c) { return 0; }
    };

    private static final FeatureProducer BRANCH = new FeatureProducer() {
        @Override public FeatureStream getFeatures(GetFeatureRequest r, GetFeatureCollection c) { return null; }
        @Override public int getNumberMatched(GetFeatureRequest r, GetFeatureCollection c) { return 0; }
    };

    /** A branchable feature type recording whether the branch producer was requested. */
    private static class BranchableFt extends SimpleFeatureType implements BranchableFeatureType {
        Map<String, String> seenProps;

        @Override public FeatureProducer getFeatureProducer() { return MASTER; }

        @Override public FeatureProducer getBranchFeatureProducer(Map<String, String> resolvedDbProps) {
            this.seenProps = resolvedDbProps;
            return BRANCH;
        }
    }

    /** A plain feature type that cannot branch. */
    private static class PlainFt extends SimpleFeatureType {
        @Override public FeatureProducer getFeatureProducer() { return MASTER; }
    }

    private static BranchConfig branchConfig() {
        return new BranchConfig(Pattern.compile("^[a-z0-9_]+$"),
                Collections.singletonMap("jdbcUrl", "jdbc:postgresql://h/db_{branch}"));
    }

    @Test
    public void noBranchValueReturnsPlainProducer() {
        BranchableFt ft = new BranchableFt();
        ft.setBranchConfig(branchConfig());
        GetFeatureRequest request = new GetFeatureRequest(); // no branch value
        assertSame(MASTER, GetCollectionItemsOperation.selectProducer(request, ft));
    }

    @Test
    public void branchValueWithConfigReturnsUnionProducerAndResolvesProps() {
        BranchableFt ft = new BranchableFt();
        ft.setBranchConfig(branchConfig());
        GetFeatureRequest request = new GetFeatureRequest();
        request.setBranchValue("wip");

        FeatureProducer p = GetCollectionItemsOperation.selectProducer(request, ft);
        assertNotNull(p);
        assertTrue("expected a union producer when branch is active", p instanceof UnionFeatureProducer);
        // The resolved {branch} substitution reached the backend factory.
        assertEquals("jdbc:postgresql://h/db_wip", ft.seenProps.get("jdbcUrl"));
    }

    @Test
    public void branchValueButNoBranchConfigFallsBackToPlain() {
        BranchableFt ft = new BranchableFt(); // branchable, but no BranchConfig set
        GetFeatureRequest request = new GetFeatureRequest();
        request.setBranchValue("wip");
        assertSame(MASTER, GetCollectionItemsOperation.selectProducer(request, ft));
    }

    @Test
    public void branchValueButNotBranchableFallsBackToPlain() {
        PlainFt ft = new PlainFt();
        ft.setBranchConfig(branchConfig());
        GetFeatureRequest request = new GetFeatureRequest();
        request.setBranchValue("wip");
        assertSame(MASTER, GetCollectionItemsOperation.selectProducer(request, ft));
    }
}
