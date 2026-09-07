package fi.nls.hakunapi.tiles.source.gpkg;

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
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

/**
 * Tests {@link GpkgTileSource} against a real GeoPackage tile pyramid
 * ({@code 3857.gpkg}, from the ngageoint/geopackage-js reference fixtures). The
 * fixture holds a single pyramid table {@code imagery} in EPSG:3857 with one
 * zoom level ({@code zoom_level=4}, 450x450 px tiles, ground resolution
 * ~5565.97 m/px).
 *
 * <p>The fixture's tiling scheme is not WebMercatorQuad (450 px tiles), so the
 * test defines a matching config tile matrix set ({@code Imagery3857}). Its lone
 * matrix has id {@code "0"} while the GeoPackage stores the tiles at
 * {@code zoom_level=4}; the source must bridge that gap by matching the matrix
 * cell size to {@code gpkg_tile_matrix.pixel_x_size}.
 */
public class GpkgTileSourceTest {

    private static final String TMS = "Imagery3857";
    private static final double RESOLUTION = 5565.974539663679;

    private Path fixture() throws Exception {
        return Paths.get(getClass().getResource("/3857.gpkg").toURI());
    }

    private Properties baseProps(Path gpkg) {
        Properties p = new Properties();
        p.setProperty("tiles.layers", "img");
        p.setProperty("tiles.layers.img.type", "gpkg");
        p.setProperty("tiles.layers.img.tileMatrixSets", TMS);
        // path resolves against the config path's sibling.
        p.setProperty("tiles.layers.img.gpkg.path", gpkg.getFileName().toString());
        p.setProperty("tiles.layers.img.gpkg.table", "imagery");

        // Config tile matrix set matching the fixture's pyramid (single level).
        p.setProperty("tileMatrixSets", TMS);
        p.setProperty("tileMatrixSets." + TMS + ".srid", "3857");
        p.setProperty("tileMatrixSets." + TMS + ".tileWidth", "450");
        p.setProperty("tileMatrixSets." + TMS + ".tileHeight", "450");
        p.setProperty("tileMatrixSets." + TMS + ".bbox",
                "-20037508.342789244,5009377.085697312,-15028131.257091932,20037508.342789244");
        p.setProperty("tileMatrixSets." + TMS + ".resolutions", Double.toString(RESOLUTION));
        return p;
    }

    private MapTileLayer parse(GpkgTileSource source, Properties p, Path gpkg) throws Exception {
        HakunaConfigParser cfg = new HakunaConfigParser(p);
        TileMatrixSetRegistry tms = new TilesConfigParser(cfg).readTileMatrixSets();
        Path configPath = gpkg.resolveSibling("service.properties");
        return source.parse(cfg, configPath, tms, "img");
    }

    @Test
    public void servesPngTileMappedByResolution() throws Exception {
        Path gpkg = fixture();
        try (GpkgTileSource source = new GpkgTileSource()) {
            MapTileLayer layer = parse(source, baseProps(gpkg), gpkg);

            // Media type sniffed from the pyramid's tiles (PNG).
            assertEquals("image/png", layer.getMediaTypes().get(0));

            // Matrix id "0" -> GeoPackage zoom_level 4 (by resolution match).
            Optional<Tile> tile = source.getTile(layer, TMS, "0", 0, 0, "image/png", Map.of());
            assertTrue(tile.isPresent());

            byte[] bytes = tile.get().getBytes();
            // PNG magic.
            assertEquals((byte) 0x89, bytes[0]);
            assertEquals((byte) 'P', bytes[1]);
            assertEquals((byte) 'N', bytes[2]);
            assertEquals((byte) 'G', bytes[3]);
            assertEquals("image/png", tile.get().getMediaType());
        }
    }

    @Test
    public void missingTileReturnsEmpty() throws Exception {
        Path gpkg = fixture();
        try (GpkgTileSource source = new GpkgTileSource()) {
            MapTileLayer layer = parse(source, baseProps(gpkg), gpkg);
            // Matrix is 2x6 at this level; column 99 is out of range.
            Optional<Tile> tile = source.getTile(layer, TMS, "0", 0, 99, "image/png", Map.of());
            assertFalse(tile.isPresent());
        }
    }

    @Test
    public void unsupportedTileMatrixSetReturnsEmpty() throws Exception {
        Path gpkg = fixture();
        try (GpkgTileSource source = new GpkgTileSource()) {
            MapTileLayer layer = parse(source, baseProps(gpkg), gpkg);
            Optional<Tile> tile = source.getTile(layer, "SomeOtherTMS", "0", 0, 0, "image/png", Map.of());
            assertFalse(tile.isPresent());
        }
    }

    @Test
    public void unmappedTileMatrixReturnsEmpty() throws Exception {
        Path gpkg = fixture();
        try (GpkgTileSource source = new GpkgTileSource()) {
            MapTileLayer layer = parse(source, baseProps(gpkg), gpkg);
            // Only matrix "0" maps to a zoom level; "1" has no mapping.
            Optional<Tile> tile = source.getTile(layer, TMS, "1", 0, 0, "image/png", Map.of());
            assertFalse(tile.isPresent());
        }
    }

    @Test
    public void formatOverrideWins() throws Exception {
        Path gpkg = fixture();
        try (GpkgTileSource source = new GpkgTileSource()) {
            Properties p = baseProps(gpkg);
            p.setProperty("tiles.layers.img.gpkg.format", "image/x-custom");
            MapTileLayer layer = parse(source, p, gpkg);
            // Advertised media type is the override; the served tile is still
            // sniffed (PNG) so clients get the true content type.
            assertEquals("image/x-custom", layer.getMediaTypes().get(0));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownTableFailsParse() throws Exception {
        Path gpkg = fixture();
        try (GpkgTileSource source = new GpkgTileSource()) {
            Properties p = baseProps(gpkg);
            p.setProperty("tiles.layers.img.gpkg.table", "no_such_table");
            parse(source, p, gpkg);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void noResolutionMatchFailsParse() throws Exception {
        Path gpkg = fixture();
        try (GpkgTileSource source = new GpkgTileSource()) {
            Properties p = baseProps(gpkg);
            // A resolution nowhere near the pyramid's ~5565.97 m/px.
            p.setProperty("tileMatrixSets." + TMS + ".resolutions", "1.0");
            parse(source, p, gpkg);
        }
    }

}
