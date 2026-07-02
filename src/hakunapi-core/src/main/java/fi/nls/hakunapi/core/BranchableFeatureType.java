package fi.nls.hakunapi.core;

import java.util.Map;

/**
 * A {@link FeatureType} that can produce a per-request "branch" {@link FeatureProducer} reading its
 * own schema/table from a different connection target, for the dynamic {@code ?branch=} UNION
 * overlay. See {@code docs/design/union-overlay.md}.
 *
 * <p>The branch reuses this feature type's schema, id and table verbatim; only the connection target
 * differs. The resolved connection properties are produced by
 * {@link BranchConfig#resolveDbProps(String)} from the validated {@code ?branch=} value, so the raw
 * request value never reaches this method.
 */
public interface BranchableFeatureType {

    /**
     * Build a producer that reads this feature type's schema/table from the connection described by
     * {@code resolvedDbProps} (the backend interprets the keys, e.g. {@code jdbcUrl}/{@code username}).
     *
     * @param resolvedDbProps connection properties with the {@code {branch}} placeholder already
     *        substituted; never derived directly from raw request input
     * @return a producer over the branch connection, sharing this feature type's schema
     */
    FeatureProducer getBranchFeatureProducer(Map<String, String> resolvedDbProps);

}
