package fi.nls.hakunapi.source.gpkg.http;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import fi.nls.hakunapi.source.gpkg.http.jdbc.SqliteConnection;
import fi.nls.hakunapi.source.gpkg.http.jdbc.SqliteHandle;
import fi.nls.hakunapi.source.gpkg.http.vfs.BlockCacheRangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.RangeReader;
import fi.nls.hakunapi.source.gpkg.http.vfs.SqliteVfs;

/**
 * Hands out connections to one database read through {@link SqliteVfs}.
 *
 * A handle per connection, opened on getConnection and closed with it. There is
 * nothing to pool: the file is virtual, so opening a database performs no
 * open(2) and reads nothing of its own - {@code SqliteVfs.attach} registered a
 * name, and every byte SQLite goes on to read reaches the {@link RangeReader} as
 * an xRead whether the handle is new or reused.
 *
 * That is only true because the cache lives below the handle. SQLite's page
 * cache belongs to a handle and dies with it, and while it was the only cache
 * here a handle per query did start cold every time - one MTK vector tile
 * queries 31 collections, and the same tile fetched twice issued 9170 and then
 * 9171 Range requests, the second fetching every page the first had read.
 * Pooling handles brought that to 395. A shared {@link BlockCacheRangeReader}
 * gets that reuse without the handle being what holds it, and holds it better:
 *
 * <ul>
 * <li>One cache rather than one per handle, so its memory is a ceiling the
 * operator sets rather than a multiple of how many requests are in flight. That
 * multiple is what ran a 256 MiB heap out of memory.
 * <li>It outlives a connection, so the second of two tile requests reads what
 * the first fetched. A page cache was thrown away at the end of the query that
 * filled it unless a pool kept its handle alive.
 * <li>It is outside the handle mutex. Serialized mode mutexes a handle, so a
 * page cache hit is serialised against every other query on that handle while a
 * block cache hit is not - which is why sharing one handle needed a pool to get
 * concurrency back, and why a handle per connection needs nothing.
 * </ul>
 *
 * So {@code PRAGMA cache_size = 0} is set on every handle: a page cache above
 * the block cache would hold the same bytes a second time, at a cost paid per
 * handle, and evict them on a schedule of its own.
 */
public class SqliteVfsDataSource implements DataSource, Closeable {

    private final RangeReader reader;
    private final String dbName;

    private volatile boolean closed;

    public SqliteVfsDataSource(RangeReader reader) {
        this.reader = reader;
        this.dbName = SqliteVfs.attach(reader);
    }

    /**
     * A new handle with its page cache off, so the block cache below is the only
     * one. PRAGMA cache_size reads nothing from the database, so neither this nor
     * the open touches the network.
     */
    @Override
    public Connection getConnection() throws SQLException {
        if (closed) {
            throw new SQLException("DataSource is closed");
        }
        SqliteHandle handle = SqliteHandle.open(dbName, SqliteVfs.VFS_NAME);
        try {
            handle.exec("PRAGMA cache_size = 0");
            return new SqliteConnection(dbName, handle);
        } catch (SQLException | RuntimeException | Error e) {
            handle.close();
            throw e;
        }
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    /**
     * Detaches the database and closes the reader.
     *
     * This does not wait for connections still open: it closes the reader while
     * they may still be reading through it, so it is only safe once the requests
     * holding them are done. That is the servlet container's ordering to get
     * right - it stops serving before it destroys the context - and blocking
     * here on a count of open handles would trade a shutdown-ordering bug we
     * have not seen for a shutdown that can hang, which is worse.
     */
    @Override
    public void close() throws IOException {
        closed = true;
        SqliteVfs.detach(dbName);
        reader.close();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
        // No logging of our own
    }

    @Override
    public void setLoginTimeout(int seconds) {
        // Timeouts belong to the reader
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

}
