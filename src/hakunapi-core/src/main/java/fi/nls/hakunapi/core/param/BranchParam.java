package fi.nls.hakunapi.core.param;

import fi.nls.hakunapi.core.BranchConfig;
import fi.nls.hakunapi.core.FeatureServiceConfig;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.Parameter.StyleEnum;
import io.swagger.v3.oas.models.parameters.QueryParameter;

/**
 * Collection-specific query parameter that activates the UNION overlay ("branch") for a
 * collection's {@code /items} response: {@code GET /collections/{id}/items?branch=<value>}.
 *
 * <p>This param is registered only on collections that declare a {@link BranchConfig}, via
 * {@code FeatureType.getParameters()}. Its sole job here is the security/validation seam: validate
 * the raw value against the configured allowlist regexp and stash the validated value on the
 * request. The actual overlay (resolving the branch connection target, swapping the producer, and
 * the id-sorted merge) happens later in the producer phase, which reads
 * {@link GetFeatureRequest#getBranchValue()}.
 *
 * <p>The validated value is never used to derive a filesystem path here; it is only handed to the
 * backend resolver after passing the regexp guard. See {@code docs/design/union-overlay.md} §6.
 */
public class BranchParam implements GetFeatureParam {

    public static final String PARAM_NAME = "branch";

    private final BranchConfig branchConfig;

    public BranchParam(BranchConfig branchConfig) {
        this.branchConfig = branchConfig;
    }

    @Override
    public String getParamName() {
        return PARAM_NAME;
    }

    @Override
    public boolean isCommon() {
        // Collection-specific: only present on collections that declare a branch overlay.
        return false;
    }

    @Override
    public Parameter toParameter(FeatureServiceConfig service) {
        return new QueryParameter()
                .name(getParamName())
                .style(StyleEnum.FORM)
                .explode(false)
                .description("Overlay an extra datasource onto this collection's items"
                        + " (UNION-distinct by feature id; the branch wins on id collision).")
                .schema(new StringSchema());
    }

    @Override
    public void modify(FeatureServiceConfig service, GetFeatureRequest request, String value)
            throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            return;
        }
        branchConfig.validate(value);
        request.setBranchValue(value);
        request.addQueryParam(getParamName(), value);
    }

    @Override
    public int priority() {
        // Run last: after sortby, filter and pagination have shaped the collection, so the
        // overlay producer sees the final filter/order state.
        return 100;
    }

}
