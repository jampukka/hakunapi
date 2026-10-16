package fi.nls.hakunapi.tiles.source.wmts;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/** tmsNames maps hakunapi tile matrix set id to upstream name; urlTemplate is null when kvp. */
public record WmtsConfig(
        String baseUrl,
        String layer,
        String style,
        String format,
        boolean kvp,
        String urlTemplate,
        Map<String, String> tmsNames,
        Map<String, String> queryParams,
        Set<String> passthrough,
        Duration readTimeout) {
}
