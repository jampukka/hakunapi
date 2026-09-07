package fi.nls.hakunapi.tiles.servlet.jakarta;

import fi.nls.hakunapi.core.extension.OpenAPISchemaContributor;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Describes the OGC API - Tiles response models in the OpenAPI document.
 *
 * <p>TODO: the models are currently described as untyped objects. Spelling them
 * out properly is what the OAS30 conformance claim needs.
 */
public class TilesOpenAPISchemaContributor implements OpenAPISchemaContributor {

    private static final String SCHEMAS_PACKAGE = "fi.nls.hakunapi.tiles.schemas.";

    @Override
    public Schema<?> getSchema(Class<?> clazz) {
        if (clazz.getName().startsWith(SCHEMAS_PACKAGE)) {
            return new ObjectSchema();
        }
        return null;
    }

}
