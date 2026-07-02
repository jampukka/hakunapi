package fi.nls.hakunapi.simple.postgis.branch;

import fi.nls.hakunapi.core.FeatureServiceConfig;
import fi.nls.hakunapi.core.param.GetFeatureParam;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.Parameter.StyleEnum;
import io.swagger.v3.oas.models.parameters.QueryParameter;

/**
 * Collection-specific query parameter that selects the branch (git-worktree) for a collection's
 * {@code /items} response: {@code GET /collections/{id}/items?branch=<value>}.
 *
 * <p>Registered via {@code FeatureType.getParameters()} on the branch collection, so it is a known
 * parameter (passes {@code checkUnknownParameters}) and its {@link #modify} runs before the producer.
 * Its job is the security/validation seam: validate the raw value against the configured allowlist
 * regexp and stash the validated value on the request via
 * {@link GetFeatureRequest#addQueryParam}. Resolving the branch connection and reading it happens
 * later in {@link BranchPostGISProducer}, which reads the stashed value with
 * {@link GetFeatureRequest#getQueryParam}.
 *
 * <p>The validated value is never used to derive a filesystem path here; it is only handed to the
 * backend resolver after passing the regexp guard.
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
                .description("Select the branch (worktree) to read this collection's items from."
                        + " Each branch is an isolated checkout in its own database.")
                .schema(new StringSchema());
    }

    @Override
    public void modify(FeatureServiceConfig service, GetFeatureRequest request, String value)
            throws IllegalArgumentException {
        if (value == null || value.isEmpty()) {
            return;
        }
        branchConfig.validate(value);
        request.addQueryParam(getParamName(), value);
    }

    @Override
    public int priority() {
        // Run last: after sortby, filter and pagination have shaped the collection, so the
        // overlay producer sees the final filter/order state.
        return 100;
    }

}
