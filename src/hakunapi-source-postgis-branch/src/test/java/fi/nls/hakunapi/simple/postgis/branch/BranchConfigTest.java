package fi.nls.hakunapi.simple.postgis.branch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

public class BranchConfigTest {

    private static Map<String, String> template() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("jdbcUrl", "jdbc:postgresql://host/branch_{branch}");
        t.put("username", "ro");
        return t;
    }

    @Test
    public void validateAcceptsMatchingValue() {
        BranchConfig cfg = new BranchConfig(Pattern.compile("^[a-z0-9_]+$"), template());
        cfg.validate("wip_2026"); // no throw
    }

    @Test
    public void validateRejectsNonMatchingValue() {
        BranchConfig cfg = new BranchConfig(Pattern.compile("^[a-z0-9_]+$"), template());
        for (String bad : new String[] { "../etc/passwd", "a' OR '1'='1", "branch-1", "x/y", "" }) {
            try {
                cfg.validate(bad);
                fail("Expected rejection of branch value: " + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void validateNullAlwaysRejected() {
        try {
            new BranchConfig(null, template()).validate(null);
            fail("Expected rejection of null branch value");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void validateNoPatternAcceptsAnything() {
        BranchConfig cfg = new BranchConfig(null, template());
        cfg.validate("../still-no-check"); // opted out of validation: no throw
    }

    @Test
    public void resolveDbPropsSubstitutesPlaceholder() {
        BranchConfig cfg = new BranchConfig(Pattern.compile("^[a-z0-9_]+$"), template());
        Map<String, String> resolved = cfg.resolveDbProps("wip");
        assertEquals("jdbc:postgresql://host/branch_wip", resolved.get("jdbcUrl"));
        assertEquals("ro", resolved.get("username"));
    }

    @Test
    public void nullTemplateYieldsEmptyProps() {
        BranchConfig cfg = new BranchConfig(null, null);
        assertEquals(0, cfg.getDbPropsTemplate().size());
        assertNull(cfg.getPattern());
    }

}
