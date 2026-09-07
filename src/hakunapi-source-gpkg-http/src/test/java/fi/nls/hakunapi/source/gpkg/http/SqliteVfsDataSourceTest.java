package fi.nls.hakunapi.source.gpkg.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.BeforeClass;
import org.junit.Test;

import fi.nls.hakunapi.source.gpkg.http.jdbc.SqliteConnection;
import fi.nls.hakunapi.source.gpkg.http.jdbc.SqliteHandle;
import fi.nls.hakunapi.source.gpkg.http.vfs.FileRangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.SqliteVfs;

/**
 * A handle per connection, which is only cheap because the file is virtual and
 * the cache is below the handle rather than on it.
 *
 * What used to be tested here was handle reuse: while SQLite's page cache was
 * the only cache, a handle per query started cold and one MTK vector tile
 * fetched twice issued 9170 and then 9171 Range requests. That is now the block
 * cache's job, so what matters here is that opening and closing handles freely
 * works and stays concurrent.
 */
public class SqliteVfsDataSourceTest {

    private static Path gpkg;

    @BeforeClass
    public static void setup() throws Exception {
        SqliteVfs.register();
        URL url = SqliteVfsDataSourceTest.class.getResource("/sample_feature_table.gpkg");
        assertNotNull("test GeoPackage on classpath", url);
        gpkg = Paths.get(url.toURI());
    }

    private SqliteVfsDataSource open() throws Exception {
        return new SqliteVfsDataSource(new FileRangeReader(gpkg));
    }

    private static SqliteHandle handleOf(Connection c) {
        return ((SqliteConnection) c).handle();
    }

    private static int count(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM sample_feature_table");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    /**
     * Every connection owns its own handle. Serialized mode mutexes a handle, so
     * sharing one would serialise the queries: a map view is a dozen concurrent
     * tile requests, and that is the concurrency being paid for.
     */
    @Test
    public void givesEveryConnectionItsOwnHandle() throws Exception {
        try (SqliteVfsDataSource ds = open()) {
            try (Connection a = ds.getConnection();
                    Connection b = ds.getConnection();
                    Connection c = ds.getConnection()) {
                assertNotSame("two open connections cannot share a handle", handleOf(a), handleOf(b));
                assertNotSame("two open connections cannot share a handle", handleOf(b), handleOf(c));
                assertNotSame("two open connections cannot share a handle", handleOf(a), handleOf(c));
            }
        }
    }

    /** A closed connection closes its handle, and the next one opens a fresh one. */
    @Test
    public void queryWorksAfterAnEarlierConnectionWasClosed() throws Exception {
        try (SqliteVfsDataSource ds = open()) {
            ds.getConnection().close();
            try (Connection c = ds.getConnection()) {
                assertTrue("table has features", count(c) > 0);
            }
        }
    }

    /** Closing twice must not close the handle twice: Arena.close() throws. */
    @Test
    public void toleratesClosingAConnectionTwice() throws Exception {
        try (SqliteVfsDataSource ds = open()) {
            Connection c = ds.getConnection();
            c.close();
            c.close();
            try (Connection other = ds.getConnection()) {
                assertTrue(count(other) > 0);
            }
        }
    }

    /** Opening and closing a handle per query, from several threads at once. */
    @Test
    public void servesConcurrentQueries() throws Exception {
        try (SqliteVfsDataSource ds = open()) {
            int threads = 8;
            Thread[] workers = new Thread[threads];
            AtomicReference<Exception> failure = new AtomicReference<>();
            for (int i = 0; i < threads; i++) {
                workers[i] = new Thread(() -> {
                    for (int n = 0; n < 20; n++) {
                        try (Connection c = ds.getConnection()) {
                            if (count(c) <= 0) {
                                failure.compareAndSet(null, new IllegalStateException("no features"));
                            }
                        } catch (Exception e) {
                            failure.compareAndSet(null, e);
                        }
                    }
                });
            }
            for (int i = 0; i < threads; i++) {
                workers[i].start();
            }
            for (int i = 0; i < threads; i++) {
                workers[i].join();
            }
            assertEquals(null, failure.get());
        }
    }

    /** A closed data source hands out no more connections. */
    @Test
    public void refusesConnectionsOnceClosed() throws Exception {
        SqliteVfsDataSource ds = open();
        ds.close();
        try {
            ds.getConnection();
            throw new AssertionError("expected a SQLException");
        } catch (java.sql.SQLException e) {
            assertTrue(e.getMessage().contains("closed"));
        }
    }

}
