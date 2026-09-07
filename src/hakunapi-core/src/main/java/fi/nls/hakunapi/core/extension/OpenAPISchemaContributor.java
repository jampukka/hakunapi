package fi.nls.hakunapi.core.extension;

import io.swagger.v3.oas.models.media.Schema;

/**
 * Supplies the OpenAPI schema for a response type the generator does not know,
 * so that a module contributing its own API paths can describe its own response
 * models without the generator naming them.
 *
 * <p>Discovered via {@code ServiceLoader}: a contributor needs no configuration,
 * only to be on the classpath. Contributors are consulted in load order and the
 * first non-null answer wins; the generator throws if none owns the type.
 */
public interface OpenAPISchemaContributor {

    /**
     * The schema describing {@code clazz}, or {@code null} if this contributor
     * does not own it.
     */
    public Schema<?> getSchema(Class<?> clazz);

}
