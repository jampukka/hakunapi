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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.BytesTile;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.MapTileSource;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

public class WmtsTileSource implements MapTileSource {

    private static final Logger LOG = LoggerFactory.getLogger(WmtsTileSource.class);

    public static final String TYPE = "wmts";

    private static final Pattern TILE_MATRIX = Pattern.compile("[A-Za-z0-9_.:-]+");
    private static final Pattern REST_PLACEHOLDER =
            Pattern.compile("\\{(TileMatrixSet|TileMatrix|TileRow|TileCol|Style|Layer)\\}");
    private static final Set<String> FIXED_KVP_KEYS = Set.of("SERVICE", "REQUEST", "VERSION", "LAYER", "STYLE",
            "FORMAT", "TILEMATRIXSET", "TILEMATRIX", "TILEROW", "TILECOL");

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

        Map<String, String> tmsNames = new HashMap<>();
        for (String tmsId : tmsIds) {
            String upstream = cfg.get(w + "tms." + tmsId);
            if (upstream == null) {
                throw new IllegalArgumentException("tile layer " + layerId
                        + ": missing upstream tile matrix set mapping wmts.tms." + tmsId);
            }
            tmsNames.put(tmsId, upstream);
        }

        Map<String, String> queryParams = cfg.getAllStartingWith(w + "param.");

        Set<String> passthrough = new LinkedHashSet<>();
        for (String name : Arrays.asList(cfg.get(w + "passthrough", "").split(","))) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (FIXED_KVP_KEYS.contains(trimmed.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("tile layer " + layerId + ": wmts.passthrough must not name "
                        + trimmed + ", it is a fixed KVP parameter");
            }
            passthrough.add(trimmed);
        }

        Duration readTimeout = Duration.ofSeconds(Long.parseLong(cfg.get(w + "readTimeout", "30")));

        WmtsConfig config = new WmtsConfig(baseUrl, upstreamLayer, style, format, kvp, urlTemplate,
                tmsNames, queryParams, passthrough, readTimeout);
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
        if (!TILE_MATRIX.matcher(tileMatrix).matches()) {
            return Optional.empty();
        }
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
                .timeout(cfg.readTimeout())
                .GET()
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        int status = resp.statusCode();
        if (status == 400 || status == 404 || status == 204) {
            return Optional.empty();
        }
        if (status != 200) {
            LOG.warn("Upstream WMTS GetTile failed: HTTP {} for {}", status, withoutQuery(uri));
            throw new IllegalStateException("Upstream WMTS GetTile failed: HTTP " + status);
        }
        String contentType = resp.headers().firstValue("Content-Type").orElse(null);
        if (contentType != null && !contentType.split(";", 2)[0].trim().equalsIgnoreCase(cfg.format())) {
            LOG.warn("Upstream WMTS GetTile returned Content-Type {} instead of {} for {}",
                    contentType, cfg.format(), withoutQuery(uri));
            return Optional.empty();
        }
        return Optional.of(new BytesTile(tileMatrixSetId, tileMatrix, tileRow, tileCol, cfg.format(), resp.body()));
    }

    @Override
    public void close() throws Exception {
        http.close();
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
        String url = REST_PLACEHOLDER.matcher(cfg.urlTemplate()).replaceAll(m -> Matcher.quoteReplacement(
                switch (m.group(1)) {
                    case "TileMatrixSet" -> upstreamTms;
                    case "TileMatrix" -> tileMatrix;
                    case "TileRow" -> Long.toString(tileRow);
                    case "TileCol" -> Long.toString(tileCol);
                    case "Style" -> cfg.style();
                    default -> cfg.layer();
                }));
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

    private static String withoutQuery(URI uri) {
        String s = uri.toString();
        int q = s.indexOf('?');
        return q < 0 ? s : s.substring(0, q);
    }

}
