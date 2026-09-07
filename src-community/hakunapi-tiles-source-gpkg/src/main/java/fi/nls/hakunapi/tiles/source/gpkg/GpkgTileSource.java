package fi.nls.hakunapi.tiles.source.gpkg;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.BytesTile;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileMatrix;
import fi.nls.hakunapi.tiles.TileMatrixSet;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.TileSource;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

/**
 * {@link TileSource} backed by a GeoPackage tile pyramid (OGC GeoPackage, clause
 * 2.2 "Tiles"). The archive is a single SQLite file whose pyramid user table
 * holds pre-encoded image tiles ({@code (zoom_level, tile_column, tile_row,
 * tile_data)}); this source resolves the requested tile via that table and
 * passes the stored image bytes through verbatim (no re-encoding).
 *
 * <p>Like the WMTS source, the tiling schemes are the service-wide hakunapi
 * {@link TileMatrixSet}s referenced by {@code tiles.layers.<id>.tileMatrixSets}.
 * A GeoPackage carries its own {@code zoom_level} integers (not necessarily
 * 0-based or contiguous), so at parse time each hakunapi tile matrix is mapped
 * to the GeoPackage {@code zoom_level} whose ground resolution
 * ({@code gpkg_tile_matrix.pixel_x_size}) matches the matrix
 * {@link TileMatrix#getCellSize() cell size}. The referenced tile matrix sets
 * must therefore share the GeoPackage's CRS/units for the resolutions to be
 * comparable.
 *
 * <p>Tile addresses follow the GeoPackage convention, which matches OGC tile
 * matrix sets: {@code tile_row} has a top-left origin, so the inbound
 * {@code tileRow}/{@code tileCol} map directly to {@code tile_row}/
 * {@code tile_column}.
 *
 * <p>Per-layer configuration ({@code tiles.layers.<id>.gpkg.*}):
 * <ul>
 *   <li>{@code path} - GeoPackage file path (relative paths resolve against the config file)</li>
 *   <li>{@code table} - tile pyramid user table (default: the layer id)</li>
 *   <li>{@code format} - override the advertised tile media type (default: sniffed from the tiles)</li>
 *   <li>{@code resolutionTolerance} - relative tolerance for resolution matching (default {@code 0.01})</li>
 * </ul>
 */
public class GpkgTileSource implements TileSource {

    private static final Logger LOG = LoggerFactory.getLogger(GpkgTileSource.class);

    public static final String TYPE = "gpkg";

    private static final double DEFAULT_RESOLUTION_TOLERANCE = 0.01;

    private final List<Connection> connections = new ArrayList<>();

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public MapTileLayer parse(HakunaConfigParser cfg, Path path, TileMatrixSetRegistry tms, String layerId)
            throws Exception {
        TilesConfigParser tiles = new TilesConfigParser(cfg);
        String p = "tiles.layers." + layerId + ".";
        String g = p + "gpkg.";

        String title = cfg.get(p + "title", layerId);
        String description = cfg.get(p + "description", title);
        List<String> tmsIds = tiles.readTileMatrixSetIds(layerId);

        String filePath = cfg.getRequired(g + "path");
        Path gpkgPath = path != null ? path.resolveSibling(filePath) : Path.of(filePath);
        String table = cfg.get(g + "table", layerId);
        double tolerance = Double.parseDouble(cfg.get(g + "resolutionTolerance",
                Double.toString(DEFAULT_RESOLUTION_TOLERANCE)));

        Connection connection = openReadOnly(gpkgPath);
        connections.add(connection);

        try {
            validateTilesTable(connection, table);

            // GeoPackage zoom_level -> ground resolution for this pyramid table.
            Map<Integer, Double> zoomResolutions = readZoomResolutions(connection, table);

            // For each configured TMS, map matrix id -> GeoPackage zoom_level by resolution.
            Map<String, Map<String, Integer>> zoomByTms = new LinkedHashMap<>();
            for (String tmsId : tmsIds) {
                TileMatrixSet matrixSet = tms.get(tmsId).orElseThrow(() -> new IllegalArgumentException(
                        "tile layer " + layerId + ": unknown tileMatrixSet " + tmsId));
                Map<String, Integer> byMatrix = new LinkedHashMap<>();
                for (TileMatrix matrix : matrixSet.getTileMatrices()) {
                    Integer zoom = matchZoom(zoomResolutions, matrix.getCellSize(), tolerance);
                    if (zoom != null) {
                        byMatrix.put(matrix.getId(), zoom);
                    }
                }
                if (byMatrix.isEmpty()) {
                    throw new IllegalArgumentException("tile layer " + layerId + ": no zoom level in "
                            + gpkgPath + " table " + table + " matches the resolutions of tileMatrixSet " + tmsId);
                }
                zoomByTms.put(tmsId, byMatrix);
            }

            String mediaType = cfg.get(g + "format", sniffTableMediaType(connection, table));

            GpkgTileConfig config = new GpkgTileConfig(connection, table, mediaType, zoomByTms);
            LOG.info("gpkg tile layer {} -> {} table={} type={}", layerId, gpkgPath, table, mediaType);
            return new GpkgTileLayer(layerId, title, description, this, tmsIds, List.of(mediaType), null, config);
        } catch (Exception e) {
            connections.remove(connection);
            connection.close();
            throw e;
        }
    }

    private static Connection openReadOnly(Path gpkgPath) throws Exception {
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.setReadOnly(true);
        return DriverManager.getConnection("jdbc:sqlite:" + gpkgPath.toAbsolutePath(), sqlite.toProperties());
    }

    private static void validateTilesTable(Connection c, String table) throws Exception {
        String sql = "SELECT 1 FROM gpkg_contents WHERE table_name = ? AND data_type = 'tiles'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "GeoPackage has no tiles table named '" + table + "' in gpkg_contents");
                }
            }
        }
    }

    private static Map<Integer, Double> readZoomResolutions(Connection c, String table) throws Exception {
        String sql = "SELECT zoom_level, pixel_x_size FROM gpkg_tile_matrix WHERE table_name = ?";
        Map<Integer, Double> out = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getInt("zoom_level"), rs.getDouble("pixel_x_size"));
                }
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException("GeoPackage table '" + table + "' has no gpkg_tile_matrix rows");
        }
        return out;
    }

    /** @return the zoom_level whose resolution is within {@code tolerance} of {@code cellSize}, or null */
    private static Integer matchZoom(Map<Integer, Double> zoomResolutions, double cellSize, double tolerance) {
        Integer best = null;
        double bestDiff = Double.MAX_VALUE;
        for (Map.Entry<Integer, Double> e : zoomResolutions.entrySet()) {
            double diff = Math.abs(e.getValue() - cellSize);
            if (diff <= tolerance * cellSize && diff < bestDiff) {
                bestDiff = diff;
                best = e.getKey();
            }
        }
        return best;
    }

    private static String sniffTableMediaType(Connection c, String table) throws Exception {
        String sql = "SELECT tile_data FROM \"" + table.replace("\"", "\"\"") + "\" LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String sniffed = GpkgFormat.sniff(rs.getBytes(1));
                if (sniffed != null) {
                    return sniffed;
                }
            }
        }
        // Empty table or unrecognised blob: PNG is the GeoPackage default tile encoding.
        return GpkgFormat.PNG;
    }

    @Override
    public Optional<Tile> getTile(MapTileLayer layer, String tileMatrixSetId, String tileMatrix,
            long tileRow, long tileCol, String mediaType, Map<String, String> requestParams) throws Exception {
        GpkgTileConfig cfg = ((GpkgTileLayer) layer).getGpkgConfig();
        Map<String, Integer> byMatrix = cfg.zoomByTms().get(tileMatrixSetId);
        if (byMatrix == null) {
            return Optional.empty();
        }
        Integer zoom = byMatrix.get(tileMatrix);
        if (zoom == null) {
            return Optional.empty();
        }

        String sql = "SELECT tile_data FROM \"" + cfg.tableName().replace("\"", "\"\"") + "\""
                + " WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?";
        byte[] bytes;
        try (PreparedStatement ps = cfg.connection().prepareStatement(sql)) {
            ps.setInt(1, zoom);
            ps.setLong(2, tileCol);
            ps.setLong(3, tileRow);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                bytes = rs.getBytes(1);
            }
        }
        if (bytes == null) {
            return Optional.empty();
        }

        // Advertise the configured media type; fall back to sniffing the actual
        // blob so a mixed-format pyramid still reports each tile correctly.
        String tileMediaType = cfg.mediaType();
        String sniffed = GpkgFormat.sniff(bytes);
        if (sniffed != null) {
            tileMediaType = sniffed;
        }
        return Optional.of(new BytesTile(tileMatrixSetId, tileMatrix, tileRow, tileCol, tileMediaType, bytes));
    }

    @Override
    public void close() throws Exception {
        Exception first = null;
        for (Connection c : connections) {
            try {
                c.close();
            } catch (Exception e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        connections.clear();
        if (first != null) {
            throw first;
        }
    }

}
