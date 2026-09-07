package fi.nls.hakunapi.source.gpkg.http;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.source.gpkg.GpkgFeatureType;
import fi.nls.hakunapi.source.gpkg.GpkgSimpleSource;
import fi.nls.hakunapi.source.gpkg.http.vfs.BlockCacheRangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.HttpRangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.RangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.Sqlite;
import fi.nls.hakunapi.source.gpkg.http.vfs.SqliteVfs;

/**
 * A GeoPackage source that reads a remote file over HTTP Range requests.
 *
 * Everything above the bytes is real SQLite: the query planner picks the rtree
 * index for bounding-box queries, so a spatial query over a multi-gigabyte file
 * fetches kilobytes rather than the whole thing. Metadata parsing, query
 * building and the filter SQL are inherited from {@link GpkgSimpleSource}
 * unchanged.
 *
 * Configuration, per db name:
 *
 * <pre>
 * db.mydata.url = https://storage.example.com/bucket/mydata.gpkg
 * db.mydata.blockSizeKb = 4
 * db.mydata.blockCacheKb = 131072
 * </pre>
 */
public class GpkgHttpSimpleSource extends GpkgSimpleSource {

    private static final Logger LOG = LoggerFactory.getLogger(GpkgHttpSimpleSource.class);

    /**
     * The block cache is the only cache in this stack - SQLite's own page cache
     * is turned off, see {@code SqliteVfsDataSource} - so its size is required
     * rather than defaulted:
     *
     * <ul>
     * <li>{@code db.<name>.blockSizeKb} - bytes fetched per Range request, so
     * one request serves the pages next to the one asked for. Over HTTP a round
     * trip costs about the same whatever it asks for: measured against object
     * storage ~200 ms for 4 KiB and for 512 KiB alike, against ~70 us to nginx
     * on the same host.
     * <li>{@code db.<name>.blockCacheKb} - how much it may hold. This is the
     * memory ceiling of the source, and it is a ceiling rather than a per-request
     * cost: guessing it wrong is what ran a 256 MiB heap out of memory when the
     * cache was per handle.
     * </ul>
     *
     * Neither has a default. What a good size is depends on the machine and on
     * the file - a 31-collection vector tile touches hundreds of blocks, a single
     * bbox query touches a handful - and there is no value that is right for
     * both, so it is asked for rather than guessed on the operator's behalf.
     * Which way to trade them off is measured in {@code BlockCacheRangeReader}:
     * larger blocks fetch fewer times, more blocks evict less, and on MTK it was
     * the many-small-blocks end that won.
     */
    private final Map<String, SqliteVfsDataSource> dataSources = new HashMap<>();

    @Override
    public String getType() {
        return "gpkg-http";
    }

    @Override
    protected DataSource getDataSource(HakunaConfigParser cfg, Path path, String name) throws SQLException {
        SqliteVfsDataSource ds = dataSources.get(name);
        if (ds != null) {
            return ds;
        }

        String url = cfg.get("db." + name + ".url");
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("Missing required property db." + name + ".url");
        }

        SqliteVfs.register();

        int blockSizeKb = requirePositive(cfg, name, "blockSizeKb");
        int blockCacheKb = requirePositive(cfg, name, "blockCacheKb");

        RangeReader reader;
        try {
            reader = new BlockCacheRangeReader(new HttpRangeReader(URI.create(url)),
                    blockSizeKb * 1024, (long) blockCacheKb * 1024);
        } catch (IOException e) {
            throw new SQLException("Failed to open " + url, e);
        }

        ds = new SqliteVfsDataSource(reader);
        try {
            requireRtreeModule(ds);
        } catch (SQLException e) {
            closeSilent(ds);
            throw e;
        }
        dataSources.put(name, ds);
        LOG.info("Opened db {} from {}", name, url);
        return ds;
    }

    /**
     * A cache size the operator chose. There is no sensible default - see the
     * constants above - and a missing one is a configuration mistake rather than
     * something to fill in.
     */
    private static int requirePositive(HakunaConfigParser cfg, String name, String property) {
        String key = "db." + name + "." + property;
        String value = cfg.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required property " + key);
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " is not a number: " + value);
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(key + " must be greater than zero, was " + parsed);
        }
        return parsed;
    }

    /**
     * Checks that libsqlite3 itself was built with the rtree module. Whether a
     * given table has an index is checked per collection in parse().
     */
    private void requireRtreeModule(DataSource ds) throws SQLException {
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM pragma_compile_options WHERE compile_options LIKE '%ENABLE_RTREE%'");
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next() || rs.getInt(1) == 0) {
                throw new SQLException("libsqlite3 " + Sqlite.libversionString()
                        + " was built without SQLITE_ENABLE_RTREE, so GeoPackage spatial indexes cannot be used");
            }
        }
    }

    /**
     * A spatial index is not optional here. Without one GpkgIntersects falls
     * back to ST_MinX/ST_MaxX scalar functions, which are registered through
     * sqlite-jdbc's org.sqlite.Function and so unavailable to this source; and
     * even if they were, the query would be a full table scan, which over HTTP
     * means fetching the whole file. Better to fail at startup than to serve
     * that quietly.
     */
    @Override
    public SimpleFeatureType parse(HakunaConfigParser cfg, Path path, String collectionId, int[] srids)
            throws Exception {
        SimpleFeatureType ft = super.parse(cfg, path, collectionId, srids);
        if (ft instanceof GpkgFeatureType gpkgFt && gpkgFt.getGeom() != null && !gpkgFt.isSpatialIndex()) {
            throw new IllegalArgumentException("Table " + gpkgFt.getTable() + " of collection " + collectionId
                    + " has no gpkg_rtree_index spatial index, which is required when reading over HTTP");
        }
        return ft;
    }

    private static void closeSilent(SqliteVfsDataSource ds) {
        try {
            ds.close();
        } catch (IOException ignore) {
        }
    }

    @Override
    public void close() throws IOException {
        super.close();
        List<IOException> failures = new ArrayList<>();
        for (SqliteVfsDataSource ds : dataSources.values()) {
            try {
                ds.close();
            } catch (IOException e) {
                failures.add(e);
            }
        }
        dataSources.clear();
        if (!failures.isEmpty()) {
            throw failures.get(0);
        }
    }

}
