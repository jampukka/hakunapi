package fi.nls.hakunapi.source.gpkg.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.BeforeClass;
import org.junit.Test;

import fi.nls.hakunapi.source.gpkg.http.jdbc.SqliteConnection;
import fi.nls.hakunapi.source.gpkg.http.jdbc.SqliteHandle;
import fi.nls.hakunapi.source.gpkg.http.vfs.FileRangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.RangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.SqliteVfs;

/**
 * Runs SQL through the whole stack: SQLite's engine on top of a VFS whose reads
 * are served from Java.
 */
public class SqliteVfsTest {

    private static Path gpkg;

    @BeforeClass
    public static void setup() throws Exception {
        SqliteVfs.register();
        URL url = SqliteVfsTest.class.getResource("/sample_feature_table.gpkg");
        assertNotNull("test GeoPackage on classpath", url);
        gpkg = Paths.get(url.toURI());
    }

    /**
     * A connection straight onto the VFS, bypassing SqliteVfsDataSource: these
     * tests are about SQL over a Java-served VFS, so they open the handle
     * themselves. SqliteConnection.close() closes it, and SqliteHandle.close()
     * is idempotent, so the try-with-resources on both is harmless.
     */
    private SqliteHandle openHandle() throws Exception {
        RangeReader reader = new FileRangeReader(gpkg);
        String name = SqliteVfs.attach(reader);
        return SqliteHandle.open(name, SqliteVfs.VFS_NAME);
    }

    private SqliteConnection open(SqliteHandle handle) {
        return new SqliteConnection("test", handle);
    }

    @Test
    public void readsGeoPackageMetadata() throws Exception {
        try (SqliteHandle h = openHandle();
                SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("SELECT table_name, data_type FROM gpkg_contents");
                ResultSet rs = ps.executeQuery()) {
            assertTrue("gpkg_contents has a row", rs.next());
            assertNotNull(rs.getString(1));
            assertEquals("features", rs.getString(2));
        }
    }

    @Test
    public void countsFeatures() throws Exception {
        try (SqliteHandle h = openHandle();
                SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sample_feature_table");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertTrue("table has features", rs.getInt(1) > 0);
        }
    }

    @Test
    public void bindsParameters() throws Exception {
        try (SqliteHandle h = openHandle();
                SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM gpkg_contents WHERE data_type = ?")) {
            ps.setString(1, "features");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0);
            }
        }
    }

    @Test
    public void reportsNullAsNull() throws Exception {
        try (SqliteHandle h = openHandle();
                SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("SELECT NULL, 1");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
            assertTrue("wasNull after reading NULL", rs.wasNull());
            assertEquals(1, rs.getInt(2));
            assertTrue("not null after reading 1", !rs.wasNull());
        }
    }

    @Test
    public void readsBlobs() throws Exception {
        try (SqliteHandle h = openHandle();
                SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("SELECT x'0102030405'");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            byte[] blob = rs.getBytes(1);
            assertEquals(5, blob.length);
            assertEquals(1, blob[0]);
            assertEquals(5, blob[4]);
        }
    }

    @Test
    public void exposesColumnNames() throws Exception {
        try (SqliteHandle h = openHandle();
                SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("SELECT 1 AS one, 2 AS two");
                ResultSet rs = ps.executeQuery()) {
            assertEquals(2, rs.getMetaData().getColumnCount());
            assertEquals("one", rs.getMetaData().getColumnName(1));
            assertEquals("two", rs.getMetaData().getColumnName(2));
        }
    }

    /** SqliteHandle.close() must be safe to call twice: Arena.close() throws on the second. */
    @Test
    public void handleClosesTwiceWithoutThrowing() throws Exception {
        SqliteHandle h = openHandle();
        h.close();
        h.close();
    }

    /**
     * Closing a connection closes its handle. Reuse across the 31 collection
     * queries of a tile is the block cache's job, not the handle's, so nothing
     * is meant to stay open here - and a handle left behind per query would
     * leak an Arena.
     */
    @Test
    public void connectionCloseClosesHandle() throws Exception {
        SqliteHandle h = openHandle();
        try (SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sample_feature_table");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
        }
        try (SqliteConnection reopened = open(h)) {
            reopened.prepareStatement("SELECT COUNT(*) FROM sample_feature_table");
            throw new AssertionError("expected the handle to be closed");
        } catch (SQLException e) {
            // Closed, as it should be
        }
    }

    /**
     * The reason this module exists: the planner has to reach the rtree module,
     * which a hand-written reader would have to reimplement.
     */
    @Test
    public void usesRtreeIndexForBoundingBoxQueries() throws Exception {
        try (SqliteHandle h = openHandle();
                SqliteConnection c = open(h);
                PreparedStatement ps = c.prepareStatement("EXPLAIN QUERY PLAN"
                        + " SELECT id FROM sample_feature_table WHERE id IN ("
                        + " SELECT id FROM rtree_sample_feature_table_geometry"
                        + " WHERE maxx >= ? AND minx <= ? AND maxy >= ? AND miny <= ?)")) {
            ps.setDouble(1, -180.0);
            ps.setDouble(2, 180.0);
            ps.setDouble(3, -90.0);
            ps.setDouble(4, 90.0);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder plan = new StringBuilder();
                while (rs.next()) {
                    plan.append(rs.getString(4)).append('\n');
                }
                assertTrue("plan uses the rtree virtual table: " + plan,
                        plan.toString().contains("VIRTUAL TABLE INDEX"));
            }
        }
    }

}
