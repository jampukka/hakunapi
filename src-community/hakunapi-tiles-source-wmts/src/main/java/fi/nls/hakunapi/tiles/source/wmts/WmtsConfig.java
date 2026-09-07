package fi.nls.hakunapi.tiles.source.wmts;

import java.util.Map;
import java.util.Set;

/**
 * Parsed upstream WMTS connection settings for one tile layer.
 *
 * @param baseUrl     WMTS service endpoint (KVP) or RESTful base URL
 * @param layer       upstream WMTS layer identifier
 * @param style       upstream style identifier
 * @param format      tile media type (advertised verbatim as the layer's media type)
 * @param kvp         {@code true} for KVP GetTile, {@code false} for a RESTful template
 * @param urlTemplate RESTful GetTile template (null when {@code kvp})
 * @param tmsNames    hakunapi tile matrix set id -> upstream WMTS tile matrix set name
 * @param queryParams extra query params appended verbatim to every upstream GetTile
 *                    request (e.g. {@code api-key}); proxied as-is
 * @param passthrough inbound request query-param names allowed to be forwarded to the
 *                    upstream; an inbound value overrides a {@code queryParams} default
 *                    with the same name
 */
public record WmtsConfig(
        String baseUrl,
        String layer,
        String style,
        String format,
        boolean kvp,
        String urlTemplate,
        Map<String, String> tmsNames,
        Map<String, String> queryParams,
        Set<String> passthrough) {
}
