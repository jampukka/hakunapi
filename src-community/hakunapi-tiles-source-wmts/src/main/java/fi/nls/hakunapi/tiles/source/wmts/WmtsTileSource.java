package fi.nls.hakunapi.tiles.source.wmts;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.BytesTile;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.TileSource;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

/**
 * {@link TileSource} that proxies tiles from an upstream WMTS server. Tiles are
 * fetched verbatim (passthrough) - hakunapi does no re-encoding, so the upstream
 * tile format is advertised as the layer's media type.
 *
 * <p>The upstream GetTile request is built either as a KVP query
 * ({@code SERVICE=WMTS&REQUEST=GetTile&...}) or from a RESTful URL template,
 * selected by the {@code wmts.kvp} flag. The configured tiling schemes are the
 * hakunapi {@link fi.nls.hakunapi.tiles.TileMatrixSet}s referenced by id under
 * {@code tiles.layers.<id>.tileMatrixSets}; each must be mapped to its upstream
 * tile matrix set name with a required {@code wmts.tms.<tmsId>} knob. Tile matrix
 * (zoom level) ids are passed through unchanged.
 *
 * <p>Per-layer configuration ({@code tiles.layers.<id>.wmts.*}):
 * <ul>
 *   <li>{@code baseUrl} - WMTS service endpoint (KVP) or RESTful base</li>
 *   <li>{@code layer} - upstream WMTS layer identifier</li>
 *   <li>{@code style} - upstream style (default {@code default})</li>
 *   <li>{@code format} - tile media type, e.g. {@code image/png} (required)</li>
 *   <li>{@code kvp} - {@code true} for KVP GetTile, {@code false} for RESTful (default true)</li>
 *   <li>{@code urlTemplate} - RESTful GetTile template (required when {@code kvp=false}),
 *       with placeholders {@code {TileMatrixSet} {TileMatrix} {TileRow} {TileCol}}</li>
 *   <li>{@code tms.<tmsId>} - upstream tile matrix set name backing hakunapi TMS {@code <tmsId>} (required)</li>
 *   <li>{@code param.<name>} - extra query param appended verbatim to every upstream
 *       GetTile request, e.g. {@code param.api-key=...}; proxied as-is</li>
 *   <li>{@code passthrough} - comma-separated list of inbound request query-param
 *       names allowed to be forwarded to the upstream, e.g. {@code passthrough=api-key}.
 *       Only listed names are forwarded; an inbound value overrides a {@code param.<name>}
 *       default of the same name. Unlisted client params are dropped.</li>
 * </ul>
 */
public class WmtsTileSource implements TileSource {

    private static final Logger LOG = LoggerFactory.getLogger(WmtsTileSource.class);

    public static final String TYPE = "wmts";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public MapTileLayer parse(HakunaConfigParser cfg, Path path, TileMatrixSetRegistry tms, String layerId)
            throws Exception {
        TilesConfigParser tiles = new TilesConfigParser(cfg);
        String p = "tiles.layers." + layerId + ".";
        String w = p + "wmts.";

        String title = cfg.get(p + "title", layerId);
        String description = cfg.get(p + "description", title);
        List<String> tmsIds = tiles.readTileMatrixSetIds(layerId);

        String baseUrl = cfg.getRequired(w + "baseUrl");
        String upstreamLayer = cfg.getRequired(w + "layer");
        String style = cfg.get(w + "style", "default");
        String format = cfg.getRequired(w + "format");
        boolean kvp = Boolean.parseBoolean(cfg.get(w + "kvp", "true"));
        String urlTemplate = cfg.get(w + "urlTemplate");
        if (!kvp && urlTemplate == null) {
            throw new IllegalArgumentException(
                    "tile layer " + layerId + ": wmts.urlTemplate is required when wmts.kvp=false");
        }

        // Required mapping: hakunapi TMS id -> upstream WMTS tile matrix set name.
        Map<String, String> tmsNames = new HashMap<>();
        for (String tmsId : tmsIds) {
            String upstream = cfg.get(w + "tms." + tmsId);
            if (upstream == null) {
                throw new IllegalArgumentException("tile layer " + layerId
                        + ": missing upstream tile matrix set mapping wmts.tms." + tmsId);
            }
            tmsNames.put(tmsId, upstream);
        }

        // Extra query params (e.g. api-key) proxied verbatim to upstream GetTile.
        Map<String, String> queryParams = cfg.getAllStartingWith(w + "param.");

        // Inbound query-param names the client is allowed to forward to upstream.
        Set<String> passthrough = new LinkedHashSet<>();
        for (String name : Arrays.asList(cfg.get(w + "passthrough", "").split(","))) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                passthrough.add(trimmed);
            }
        }

        WmtsConfig config = new WmtsConfig(baseUrl, upstreamLayer, style, format, kvp, urlTemplate,
                tmsNames, queryParams, passthrough);
        LOG.info("wmts tile layer {} -> {} layer={}", layerId, baseUrl, upstreamLayer);
        return new WmtsTileLayer(layerId, title, description, this, tmsIds, List.of(format), null, config);
    }

    @Override
    public Set<String> getPassthroughParams(MapTileLayer layer) {
        return ((WmtsTileLayer) layer).getWmtsConfig().passthrough();
    }

    @Override
    public Optional<Tile> getTile(MapTileLayer layer, String tileMatrixSetId, String tileMatrix,
            long tileRow, long tileCol, String mediaType, Map<String, String> requestParams) throws Exception {
        WmtsConfig cfg = ((WmtsTileLayer) layer).getWmtsConfig();
        String upstreamTms = cfg.tmsNames().get(tileMatrixSetId);
        if (upstreamTms == null) {
            return Optional.empty();
        }

        // Config defaults first, then whitelisted inbound params override by name.
        Map<String, String> params = new LinkedHashMap<>(cfg.queryParams());
        for (Map.Entry<String, String> e : requestParams.entrySet()) {
            if (cfg.passthrough().contains(e.getKey())) {
                params.put(e.getKey(), e.getValue());
            }
        }

        URI uri = cfg.kvp()
                ? buildKvpUri(cfg, upstreamTms, tileMatrix, tileRow, tileCol, params)
                : buildRestUri(cfg, upstreamTms, tileMatrix, tileRow, tileCol, params);

        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        int status = resp.statusCode();
        if (status == 404 || status == 204) {
            return Optional.empty();
        }
        if (status != 200) {
            throw new IllegalStateException("Upstream WMTS GetTile failed: HTTP " + status + " for " + uri);
        }
        return Optional.of(new BytesTile(tileMatrixSetId, tileMatrix, tileRow, tileCol, cfg.format(), resp.body()));
    }

    private static URI buildKvpUri(WmtsConfig cfg, String upstreamTms, String tileMatrix,
            long tileRow, long tileCol, Map<String, String> params) {
        StringBuilder sb = new StringBuilder(cfg.baseUrl());
        sb.append(cfg.baseUrl().indexOf('?') < 0 ? '?' : '&');
        sb.append("SERVICE=WMTS");
        sb.append("&REQUEST=GetTile");
        sb.append("&VERSION=1.0.0");
        sb.append("&LAYER=").append(enc(cfg.layer()));
        sb.append("&STYLE=").append(enc(cfg.style()));
        sb.append("&FORMAT=").append(enc(cfg.format()));
        sb.append("&TILEMATRIXSET=").append(enc(upstreamTms));
        sb.append("&TILEMATRIX=").append(enc(tileMatrix));
        sb.append("&TILEROW=").append(tileRow);
        sb.append("&TILECOL=").append(tileCol);
        appendParams(sb, params);
        return URI.create(sb.toString());
    }

    private static URI buildRestUri(WmtsConfig cfg, String upstreamTms, String tileMatrix,
            long tileRow, long tileCol, Map<String, String> params) {
        String url = cfg.urlTemplate()
                .replace("{TileMatrixSet}", upstreamTms)
                .replace("{TileMatrix}", tileMatrix)
                .replace("{TileRow}", Long.toString(tileRow))
                .replace("{TileCol}", Long.toString(tileCol))
                .replace("{Style}", cfg.style())
                .replace("{Layer}", cfg.layer());
        StringBuilder sb = new StringBuilder(url);
        appendParams(sb, params);
        return URI.create(sb.toString());
    }

    private static void appendParams(StringBuilder sb, Map<String, String> params) {
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(sb.indexOf("?") < 0 ? '?' : '&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

}
