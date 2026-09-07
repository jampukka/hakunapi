package fi.nls.hakunapi.tiles.source.pmtiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.junit.Test;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.DefaultTileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.MapTileLayer;

/**
 * Tests {@link PMTilesTileSource} against a real PMTiles v3 archive
 * ({@code test_fixture_1.pmtiles}, from the protomaps/PMTiles reference
 * fixtures). The fixture holds a single gzip-compressed MVT tile at z0/0/0.
 *
 * <p>The HTTP range reader is intentionally not exercised here: it differs from
 * the file reader only in how a byte range is fetched, which the
 * {@link RangeReader} abstraction isolates.
 */
public class PMTilesTileSourceTest {

    private static final String TMS = "WebMercatorQuad";

    private Path fixture() throws Exception {
        return Paths.get(getClass().getResource("/test_fixture_1.pmtiles").toURI());
    }

    private Properties baseProps(Path archive) {
        Properties p = new Properties();
        p.setProperty("tiles.layers", "pm_layer");
        p.setProperty("tiles.layers.pm_layer.type", "pmtiles");
        p.setProperty("tiles.layers.pm_layer.tileMatrixSets", TMS);
        // path resolves against the config path's sibling; pass the file name and
        // a config path in the same directory.
        p.setProperty("tiles.layers.pm_layer.pmtiles.path", archive.getFileName().toString());
        return p;
    }

    private MapTileLayer parse(PMTilesTileSource source, Properties p, Path archive) throws Exception {
        HakunaConfigParser cfg = new HakunaConfigParser(p);
        Path configPath = archive.resolveSibling("service.properties");
        return source.parse(cfg, configPath, new DefaultTileMatrixSetRegistry(), "pm_layer");
    }

    @Test
    public void servesTileBytesVerbatimWithGzipEncoding() throws Exception {
        Path archive = fixture();
        try (PMTilesTileSource source = new PMTilesTileSource()) {
            MapTileLayer layer = parse(source, baseProps(archive), archive);

            // Media type is derived from the archive tile type (MVT).
            assertEquals("application/vnd.mapbox-vector-tile", layer.getMediaTypes().get(0));

            Optional<Tile> tile = source.getTile(layer, TMS, "0", 0, 0,
                    "application/vnd.mapbox-vector-tile", Map.of());

            assertTrue(tile.isPresent());
            byte[] bytes = tile.get().getBytes();
            // Single tile in the fixture: 69 raw (still gzip'd) bytes, gzip magic.
            assertEquals(69, bytes.length);
            assertEquals((byte) 0x1f, bytes[0]);
            assertEquals((byte) 0x8b, bytes[1]);
            assertEquals("application/vnd.mapbox-vector-tile", tile.get().getMediaType());
            assertEquals("gzip", tile.get().getContentEncoding());
        }
    }

    @Test
    public void missingTileReturnsEmpty() throws Exception {
        Path archive = fixture();
        try (PMTilesTileSource source = new PMTilesTileSource()) {
            MapTileLayer layer = parse(source, baseProps(archive), archive);
            // Fixture only holds z0; z1/0/0 is absent.
            Optional<Tile> tile = source.getTile(layer, TMS, "1", 0, 0,
                    "application/vnd.mapbox-vector-tile", Map.of());
            assertFalse(tile.isPresent());
        }
    }

    @Test
    public void unsupportedTileMatrixSetReturnsEmpty() throws Exception {
        Path archive = fixture();
        try (PMTilesTileSource source = new PMTilesTileSource()) {
            MapTileLayer layer = parse(source, baseProps(archive), archive);
            Optional<Tile> tile = source.getTile(layer, "SomeOtherTMS", "0", 0, 0,
                    "application/vnd.mapbox-vector-tile", Map.of());
            assertFalse(tile.isPresent());
        }
    }

    @Test
    public void nonNumericTileMatrixReturnsEmpty() throws Exception {
        Path archive = fixture();
        try (PMTilesTileSource source = new PMTilesTileSource()) {
            MapTileLayer layer = parse(source, baseProps(archive), archive);
            Optional<Tile> tile = source.getTile(layer, TMS, "EPSG:3857:0", 0, 0,
                    "application/vnd.mapbox-vector-tile", Map.of());
            assertFalse(tile.isPresent());
        }
    }

    @Test
    public void formatOverrideWins() throws Exception {
        Path archive = fixture();
        try (PMTilesTileSource source = new PMTilesTileSource()) {
            Properties p = baseProps(archive);
            p.setProperty("tiles.layers.pm_layer.pmtiles.format", "application/x-protobuf");
            MapTileLayer layer = parse(source, p, archive);
            assertEquals("application/x-protobuf", layer.getMediaTypes().get(0));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void bothPathAndUrlFailsParse() throws Exception {
        Path archive = fixture();
        try (PMTilesTileSource source = new PMTilesTileSource()) {
            Properties p = baseProps(archive);
            p.setProperty("tiles.layers.pm_layer.pmtiles.url", "https://example.org/x.pmtiles");
            parse(source, p, archive);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void neitherPathNorUrlFailsParse() throws Exception {
        Path archive = fixture();
        try (PMTilesTileSource source = new PMTilesTileSource()) {
            Properties p = baseProps(archive);
            p.remove("tiles.layers.pm_layer.pmtiles.path");
            parse(source, p, archive);
        }
    }

}
