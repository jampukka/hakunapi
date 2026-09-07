package fi.nls.hakunapi.tiles.source.vectortile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.Test;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.DefaultTileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.VectorTileLayer;

/**
 * Tests {@link VectorTileGeneratorSource} - the glue that resolves a tile
 * address into a ground bounding box and delegates encoding to a
 * {@link VectorTileGenerator} bound by type id via
 * {@link VectorTileGeneratorProvider}. The {@code empty} stub
 * ({@link EmptyVectorTileGenerator}) stands in for a real encoder; the
 * {@code recording} test generator ({@link RecordingGenerator}) is used to
 * assert the {@link TileContext} the glue computes. Both are registered through
 * the test {@code META-INF/services} entry.
 */
public class VectorTileGeneratorSourceTest {

    private static final String TMS = "WebMercatorQuad";
    private static final String MVT = "application/vnd.mapbox-vector-tile";

    /** Web Mercator extent half-width: origin is (-X, +X), full world at z0. */
    private static final double WM = 20037508.342789244;
    private static final double EPS = 1e-6;

    /** baseProps binds the {@code empty} stub generator by type id. */
    private Properties baseProps(String generatorType) {
        Properties p = new Properties();
        p.setProperty("tiles.layers", "vt");
        p.setProperty("tiles.layers.vt.type", "vectortile-generator");
        p.setProperty("tiles.layers.vt.tileMatrixSets", TMS);
        p.setProperty("tiles.layers.vt.vectortile.generator", generatorType);
        return p;
    }

    private VectorTileLayer parse(VectorTileGeneratorSource source, Properties p) throws Exception {
        HakunaConfigParser cfg = new HakunaConfigParser(p);
        return source.parse(cfg, null, new DefaultTileMatrixSetRegistry(), "vt");
    }

    @Test
    public void servesEmptyMvtFromStubGenerator() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            VectorTileLayer layer = parse(source, baseProps(EmptyVectorTileGenerator.TYPE));
            assertEquals(MVT, layer.getMediaTypes().get(0));

            Optional<Tile> tile = source.getTile(layer, TMS, "0", 0, 0, java.util.List.of(), MVT, Map.of());
            assertTrue(tile.isPresent());
            // Empty MVT: a protobuf Tile with no layers is zero bytes.
            assertEquals(0, tile.get().getBytes().length);
            assertEquals(MVT, tile.get().getMediaType());
        }
    }

    @Test
    public void resolvesTileAddressToGroundBbox() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            VectorTileLayer layer = parse(source, baseProps("recording"));

            // z0/0/0 in WebMercatorQuad covers the whole Web Mercator extent.
            source.getTile(layer, TMS, "0", 0, 0, java.util.List.of(), MVT, Map.of());
            TileContext ctx = RecordingGenerator.last;
            assertNotNull(ctx);
            assertEquals(3857, ctx.srid());
            assertEquals(-WM, ctx.minX(), EPS);
            assertEquals(-WM, ctx.minY(), EPS);
            assertEquals(WM, ctx.maxX(), EPS);
            assertEquals(WM, ctx.maxY(), EPS);
            assertEquals(256, ctx.tileWidth());

            // z1/row0/col1 is the top-right quadrant: x in [0, WM], y in [0, WM].
            source.getTile(layer, TMS, "1", 0, 1, java.util.List.of(), MVT, Map.of());
            ctx = RecordingGenerator.last;
            assertEquals(0.0, ctx.minX(), EPS);
            assertEquals(WM, ctx.maxX(), EPS);
            assertEquals(0.0, ctx.minY(), EPS);
            assertEquals(WM, ctx.maxY(), EPS);
        }
    }

    @Test
    public void outOfRangeAddressReturnsEmpty() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            VectorTileLayer layer = parse(source, baseProps(EmptyVectorTileGenerator.TYPE));
            // z0 has a single tile (0/0); col 1 is out of range.
            assertFalse(source.getTile(layer, TMS, "0", 0, 1, java.util.List.of(), MVT, Map.of()).isPresent());
            assertFalse(source.getTile(layer, TMS, "0", -1, 0, java.util.List.of(), MVT, Map.of()).isPresent());
        }
    }

    @Test
    public void unsupportedTileMatrixSetReturnsEmpty() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            VectorTileLayer layer = parse(source, baseProps(EmptyVectorTileGenerator.TYPE));
            assertFalse(source.getTile(layer, "SomeOtherTMS", "0", 0, 0, java.util.List.of(), MVT, Map.of()).isPresent());
        }
    }

    @Test
    public void passthroughParamsConfigured() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            Properties p = baseProps(EmptyVectorTileGenerator.TYPE);
            p.setProperty("tiles.layers.vt.vectortile.passthroughParams", "filter,limit");
            VectorTileLayer layer = parse(source, p);
            assertEquals(java.util.Set.of("filter", "limit"), source.getPassthroughParams(layer));
        }
    }

    @Test
    public void formatOverrideWins() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            Properties p = baseProps(EmptyVectorTileGenerator.TYPE);
            p.setProperty("tiles.layers.vt.vectortile.format", "application/x-protobuf");
            VectorTileLayer layer = parse(source, p);
            assertEquals("application/x-protobuf", layer.getMediaTypes().get(0));
        }
    }

    @Test
    public void defaultsCollectionToLayerId() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            VectorTileLayer layer = parse(source, baseProps(EmptyVectorTileGenerator.TYPE));
            assertEquals(java.util.List.of("vt"),
                    ((VectorTileGeneratorLayer) layer).getCollectionIds());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingGeneratorFailsParse() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            Properties p = baseProps(EmptyVectorTileGenerator.TYPE);
            p.remove("tiles.layers.vt.vectortile.generator");
            parse(source, p);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownGeneratorTypeFailsParse() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            parse(source, baseProps("no-such-generator"));
        }
    }

    @Test
    public void serviceWideDefaultGeneratorWins() throws Exception {
        try (VectorTileGeneratorSource source = new VectorTileGeneratorSource()) {
            Properties p = baseProps(EmptyVectorTileGenerator.TYPE);
            // No per-layer generator; fall back to the service-wide default.
            p.remove("tiles.layers.vt.vectortile.generator");
            p.setProperty("default.tiles.layers.vectortile.generator", "recording");
            VectorTileLayer layer = parse(source, p);
            assertEquals("recording", ((VectorTileGeneratorLayer) layer).getGenerator().getType());
        }
    }

}
