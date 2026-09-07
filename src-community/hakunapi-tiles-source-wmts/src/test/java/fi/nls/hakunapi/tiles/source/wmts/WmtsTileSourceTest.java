package fi.nls.hakunapi.tiles.source.wmts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.DefaultTileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.MapTileLayer;

/**
 * Integration test for {@link WmtsTileSource} against a stubbed upstream WMTS
 * server (JDK {@link HttpServer}). Verifies the GetTile request is built
 * correctly for both KVP and RESTful encodings and that the upstream bytes are
 * passed through unchanged.
 */
public class WmtsTileSourceTest {

    private static final byte[] PNG_BYTES = "FAKE_PNG".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String baseUrl;
    /** The path+query of the last request the stub received. */
    private final AtomicReference<String> lastRequest = new AtomicReference<>();

    @Before
    public void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/wmts", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/wmts";
    }

    @After
    public void stopStub() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String uri = ex.getRequestURI().toString();
        lastRequest.set(uri);
        // RESTful 'missing' tile -> 404 to exercise the empty path.
        if (uri.contains("/99/")) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        ex.getResponseHeaders().add("Content-Type", "image/png");
        ex.sendResponseHeaders(200, PNG_BYTES.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(PNG_BYTES);
        }
    }

    private Properties baseProps() {
        Properties p = new Properties();
        p.setProperty("tiles.layers", "wmts_layer");
        p.setProperty("tiles.layers.wmts_layer.type", "wmts");
        p.setProperty("tiles.layers.wmts_layer.tileMatrixSets", "WebMercatorQuad");
        p.setProperty("tiles.layers.wmts_layer.wmts.baseUrl", baseUrl);
        p.setProperty("tiles.layers.wmts_layer.wmts.layer", "upstream:lyr");
        p.setProperty("tiles.layers.wmts_layer.wmts.format", "image/png");
        p.setProperty("tiles.layers.wmts_layer.wmts.tms.WebMercatorQuad", "GoogleMapsCompatible");
        return p;
    }

    @Test
    public void kvpGetTilePassesThroughBytesAndBuildsRequest() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            HakunaConfigParser cfg = new HakunaConfigParser(baseProps());
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            Optional<Tile> tile = source.getTile(layer, "WebMercatorQuad", "5", 12, 7, "image/png", Map.of());

            assertTrue(tile.isPresent());
            assertArrayEquals(PNG_BYTES, tile.get().getBytes());
            assertEquals("image/png", tile.get().getMediaType());

            String req = lastRequest.get();
            assertTrue(req, req.contains("SERVICE=WMTS"));
            assertTrue(req, req.contains("REQUEST=GetTile"));
            assertTrue(req, req.contains("TILEMATRIXSET=GoogleMapsCompatible"));
            assertTrue(req, req.contains("TILEMATRIX=5"));
            assertTrue(req, req.contains("TILEROW=12"));
            assertTrue(req, req.contains("TILECOL=7"));
        }
    }

    @Test
    public void restfulGetTileBuildsTemplatedUrl() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.setProperty("tiles.layers.wmts_layer.wmts.kvp", "false");
            p.setProperty("tiles.layers.wmts_layer.wmts.urlTemplate",
                    baseUrl + "/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            Optional<Tile> tile = source.getTile(layer, "WebMercatorQuad", "5", 12, 7, "image/png", Map.of());

            assertTrue(tile.isPresent());
            assertArrayEquals(PNG_BYTES, tile.get().getBytes());
            assertEquals("/wmts/GoogleMapsCompatible/5/12/7.png", lastRequest.get());
        }
    }

    @Test
    public void extraParamsProxiedVerbatimKvp() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.setProperty("tiles.layers.wmts_layer.wmts.param.api-key", "s3cr3t");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            source.getTile(layer, "WebMercatorQuad", "5", 12, 7, "image/png", Map.of());

            String req = lastRequest.get();
            assertTrue(req, req.contains("api-key=s3cr3t"));
        }
    }

    @Test
    public void extraParamsProxiedVerbatimRestful() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.setProperty("tiles.layers.wmts_layer.wmts.kvp", "false");
            p.setProperty("tiles.layers.wmts_layer.wmts.urlTemplate",
                    baseUrl + "/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png");
            p.setProperty("tiles.layers.wmts_layer.wmts.param.api-key", "s3cr3t");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            source.getTile(layer, "WebMercatorQuad", "5", 12, 7, "image/png", Map.of());

            String req = lastRequest.get();
            assertTrue(req, req.contains("api-key=s3cr3t"));
            assertTrue(req, req.startsWith("/wmts/GoogleMapsCompatible/5/12/7.png?"));
        }
    }

    @Test
    public void passthroughForwardsWhitelistedInboundParam() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.setProperty("tiles.layers.wmts_layer.wmts.passthrough", "api-key");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            assertEquals(Set.of("api-key"), source.getPassthroughParams(layer));

            source.getTile(layer, "WebMercatorQuad", "5", 12, 7, "image/png",
                    Map.of("api-key", "fromClient"));

            assertTrue(lastRequest.get(), lastRequest.get().contains("api-key=fromClient"));
        }
    }

    @Test
    public void inboundParamOverridesConfigDefault() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.setProperty("tiles.layers.wmts_layer.wmts.param.api-key", "default");
            p.setProperty("tiles.layers.wmts_layer.wmts.passthrough", "api-key");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            source.getTile(layer, "WebMercatorQuad", "5", 12, 7, "image/png",
                    Map.of("api-key", "override"));

            String req = lastRequest.get();
            assertTrue(req, req.contains("api-key=override"));
            assertFalse(req, req.contains("api-key=default"));
        }
    }

    @Test
    public void nonWhitelistedInboundParamDropped() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.setProperty("tiles.layers.wmts_layer.wmts.passthrough", "api-key");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            source.getTile(layer, "WebMercatorQuad", "5", 12, 7, "image/png",
                    Map.of("evil", "x"));

            assertFalse(lastRequest.get(), lastRequest.get().contains("evil"));
        }
    }

    @Test
    public void missingTileReturnsEmpty() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.setProperty("tiles.layers.wmts_layer.wmts.kvp", "false");
            p.setProperty("tiles.layers.wmts_layer.wmts.urlTemplate",
                    baseUrl + "/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            MapTileLayer layer = source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");

            Optional<Tile> tile = source.getTile(layer, "WebMercatorQuad", "99", 0, 0, "image/png", Map.of());
            assertFalse(tile.isPresent());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingTmsMappingFailsParse() throws Exception {
        try (WmtsTileSource source = new WmtsTileSource()) {
            Properties p = baseProps();
            p.remove("tiles.layers.wmts_layer.wmts.tms.WebMercatorQuad");
            HakunaConfigParser cfg = new HakunaConfigParser(p);
            source.parse(cfg, Path.of("."), new DefaultTileMatrixSetRegistry(), "wmts_layer");
        }
    }

}
