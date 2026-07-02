package fi.nls.hakunapi.simple.postgis.branch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import fi.nls.hakunapi.core.config.HakunaConfigParser;

/**
 * Configuration for the query-param-driven "branch" (git-worktree) selector of a collection.
 *
 * A collection that carries a {@code BranchConfig} accepts a {@code ?branch=<value>} query
 * parameter that selects which database its {@code /items} response is read from: each branch is an
 * isolated checkout of the same schema/table in another database.
 *
 * <p>This holds only the backend-agnostic pieces decided at config time:
 * <ul>
 *   <li>an optional {@link #getPattern() regexp} the raw {@code ?branch=} value must fully match
 *       before it is ever substituted into a connection target. This is the path-traversal /
 *       injection guard: when unset, no validation is applied (caller opted out).</li>
 *   <li>a {@link #getDbPropsTemplate() db-props template} whose values may contain the
 *       {@value #BRANCH_PLACEHOLDER} placeholder; the validated value is substituted in to build
 *       the per-request branch connection target. The interpretation of these props is left to the
 *       backend that resolves the branch (e.g. PostGIS reads {@code jdbcUrl}/{@code username}).</li>
 * </ul>
 *
 * <p>Each branch reuses the collection's schema/id/table verbatim; only the connection target
 * differs. Compatibility is therefore guaranteed by construction and no schema-diff check is
 * performed.
 */
public class BranchConfig {

    public static final String BRANCH_PLACEHOLDER = "{branch}";

    private final Pattern pattern;
    private final Map<String, String> dbPropsTemplate;

    public BranchConfig(Pattern pattern, Map<String, String> dbPropsTemplate) {
        this.pattern = pattern;
        this.dbPropsTemplate = dbPropsTemplate == null ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(dbPropsTemplate));
    }

    /**
     * Parse the branch (git-worktree selector) configuration for a collection.
     *
     * <pre>
     * &lt;prefix&gt;branch.pattern  = ^[a-z0-9_]+$   (optional regexp guard; unset = no validation)
     * &lt;prefix&gt;branch.db.&lt;k&gt;  = ...            (db-props template; {branch} substituted at request time)
     * </pre>
     *
     * The branch reuses the collection's schema/id/table; only the connection target (the
     * {@code branch.db.*} props) varies per {@code ?branch=} value. Returns {@code null} when no
     * {@code branch.db.*} props are configured (branch selector disabled).
     *
     * @param prefix the collection prefix, e.g. {@code "collections.mycol."}
     */
    public static BranchConfig parse(HakunaConfigParser cfg, String prefix) {
        Map<String, String> dbPropsTemplate = cfg.getAllStartingWith(prefix + "branch.db.");
        if (dbPropsTemplate.isEmpty()) {
            return null;
        }
        String patternStr = cfg.get(prefix + "branch.pattern");
        Pattern pattern = null;
        if (patternStr != null) {
            try {
                pattern = Pattern.compile(patternStr);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "Invalid regexp for " + prefix + "branch.pattern: " + e.getMessage());
            }
        }
        return new BranchConfig(pattern, dbPropsTemplate);
    }

    /**
     * @return the regexp the raw {@code ?branch=} value must fully match, or {@code null} when no
     *         validation is configured.
     */
    public Pattern getPattern() {
        return pattern;
    }

    /**
     * @return the db-property template; values may contain {@value #BRANCH_PLACEHOLDER}.
     */
    public Map<String, String> getDbPropsTemplate() {
        return dbPropsTemplate;
    }

    /**
     * Validate a raw {@code ?branch=} value against the configured pattern.
     *
     * @throws IllegalArgumentException if a pattern is configured and the value does not fully match
     */
    public void validate(String value) throws IllegalArgumentException {
        if (value == null) {
            throw new IllegalArgumentException("branch value must not be null");
        }
        if (pattern != null && !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid branch value");
        }
    }

    /**
     * Substitute a validated branch value into the db-props template, replacing every
     * {@value #BRANCH_PLACEHOLDER} occurrence.
     *
     * <p>Callers must {@link #validate(String)} the value first; this method does not re-validate.
     *
     * @return resolved db props for the branch connection target
     */
    public Map<String, String> resolveDbProps(String value) {
        Map<String, String> resolved = new LinkedHashMap<>(dbPropsTemplate.size());
        for (Map.Entry<String, String> e : dbPropsTemplate.entrySet()) {
            resolved.put(e.getKey(), e.getValue().replace(BRANCH_PLACEHOLDER, value));
        }
        return resolved;
    }

}
