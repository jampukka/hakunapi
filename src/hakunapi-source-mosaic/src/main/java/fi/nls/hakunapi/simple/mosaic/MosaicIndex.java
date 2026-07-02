package fi.nls.hakunapi.simple.mosaic;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import fi.nls.hakunapi.core.config.HakunaConfigParser;

/**
 * The mosaic catalog: a PostGIS table with one row per tile, carrying at least the tile's spatial
 * {@code extent} (a geometry), its backing file {@code path} and the {@code backend} type that reads
 * it. {@link #select(double[])} returns the tiles whose extent intersects a query bbox, so only the
 * relevant files are read.
 *
 * <p>Configured under {@code collections.<id>.mosaic.index.*}:
 * <pre>
 * mosaic.index.db.jdbcUrl   = jdbc:postgresql://host/catalog   # HikariCP props under db.*
 * mosaic.index.db.username  = readonly
 * mosaic.index.table        = mosaic_index
 * mosaic.index.extentColumn = extent          # geometry column (default 'extent')
 * mosaic.index.pathColumn   = path            # default 'path'
 * mosaic.index.backendColumn = backend        # default 'backend'
 * mosaic.index.srid         = 3067            # srid of the extent column (default 4326)
 * </pre>
 *
 * <p>A {@code null} query bbox (no spatial filter on the request) selects every tile.
 */
public class MosaicIndex implements AutoCloseable {

    /** One catalog row: which file to read and with which backend. */
    public static final class TileRef {
        public final String path;
        public final String backend;

        public TileRef(String path, String backend) {
            this.path = path;
            this.backend = backend;
        }
    }

    private final HikariDataSource ds;
    private final DataSource injectedDs;
    private final String table;
    private final String extentColumn;
    private final String pathColumn;
    private final String backendColumn;
    private final int srid;

    private final String selectAllSql;
    private final String selectBboxSql;

    public MosaicIndex(HakunaConfigParser cfg, String prefix) {
        // prefix = "collections.<id>.mosaic.index."
        Properties props = new Properties();
        cfg.getAllStartingWith(prefix + "db.").forEach(props::setProperty);
        if (props.isEmpty()) {
            throw new IllegalArgumentException("Missing mosaic index datasource config under " + prefix + "db.*");
        }
        this.ds = new HikariDataSource(new HikariConfig(props));
        this.injectedDs = null;

        this.table = required(cfg, prefix + "table");
        this.extentColumn = cfg.get(prefix + "extentColumn", "extent");
        this.pathColumn = cfg.get(prefix + "pathColumn", "path");
        this.backendColumn = cfg.get(prefix + "backendColumn", "backend");
        this.srid = Integer.parseInt(cfg.get(prefix + "srid", "4326"));

        this.selectAllSql = "SELECT " + pathColumn + ", " + backendColumn + " FROM " + table;
        this.selectBboxSql = selectAllSql
                + " WHERE " + extentColumn + " && ST_MakeEnvelope(?, ?, ?, ?, " + srid + ")";
    }

    /** Package-visible test constructor: inject a DataSource, skip Hikari. */
    MosaicIndex(DataSource dataSource, String table, String extentColumn, String pathColumn,
            String backendColumn, int srid) {
        this.ds = null;
        this.table = table;
        this.extentColumn = extentColumn;
        this.pathColumn = pathColumn;
        this.backendColumn = backendColumn;
        this.srid = srid;
        this.selectAllSql = "SELECT " + pathColumn + ", " + backendColumn + " FROM " + table;
        this.selectBboxSql = selectAllSql
                + " WHERE " + extentColumn + " && ST_MakeEnvelope(?, ?, ?, ?, " + srid + ")";
        this.injectedDs = dataSource;
    }

    private DataSource dataSource() {
        return injectedDs != null ? injectedDs : ds;
    }

    /**
     * Tiles whose extent intersects {@code bbox} ({minx, miny, maxx, maxy}). A {@code null} bbox
     * means the request carried no spatial filter, so every tile is returned.
     */
    public List<TileRef> select(double[] bbox) throws Exception {
        try (Connection c = dataSource().getConnection();
                PreparedStatement ps = prepare(c, bbox)) {
            try (ResultSet rs = ps.executeQuery()) {
                List<TileRef> refs = new ArrayList<>();
                while (rs.next()) {
                    refs.add(new TileRef(rs.getString(1), rs.getString(2)));
                }
                return refs;
            }
        }
    }

    private PreparedStatement prepare(Connection c, double[] bbox) throws Exception {
        if (bbox == null) {
            return c.prepareStatement(selectAllSql);
        }
        PreparedStatement ps = c.prepareStatement(selectBboxSql);
        ps.setDouble(1, bbox[0]);
        ps.setDouble(2, bbox[1]);
        ps.setDouble(3, bbox[2]);
        ps.setDouble(4, bbox[3]);
        return ps;
    }

    private static String required(HakunaConfigParser cfg, String key) {
        String v = cfg.get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("Missing required property " + key);
        }
        return v;
    }

    @Override
    public void close() {
        if (ds != null) {
            ds.close();
        }
    }

}
