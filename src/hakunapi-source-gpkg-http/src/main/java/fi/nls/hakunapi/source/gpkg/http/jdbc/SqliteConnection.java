package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import fi.nls.hakunapi.source.gpkg.http.vfs.SqliteVfs;

/**
 * Connection to a SQLite database opened through {@link SqliteVfs}.
 *
 * Read-only, and the owner of its sqlite3 handle: {@link #close()} closes the
 * handle. Statements must therefore be closed before the connection - a
 * {@code sqlite3_stmt} is allocated from the handle's {@code Arena}, so closing
 * the connection first leaves one pointing at released memory. Callers here
 * close rs -> ps -> c throughout, which is the order this relies on. Nothing is lost in that - the handle carries no page cache worth
 * keeping, because the cache is the block cache below the VFS. See
 * {@code SqliteVfsDataSource}. Transaction control is a no-op because the file
 * is immutable.
 */
public class SqliteConnection extends UnsupportedConnection {

    private final SqliteHandle handle;
    private final String dbName;

    private boolean closed;

    public SqliteConnection(String dbName, SqliteHandle handle) {
        this.dbName = dbName;
        this.handle = handle;
    }

    public SqliteHandle handle() {
        return handle;
    }

    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return new SqlitePreparedStatement(this, handle, sql);
    }

    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return prepareStatement(sql);
    }

    @Override
    public Statement createStatement() throws SQLException {
        throw new SQLException("Only prepared statements are supported");
    }

    @Override
    public void setAutoCommit(boolean autoCommit) {
        // Read-only; nothing to commit
    }

    @Override
    public boolean getAutoCommit() {
        return true;
    }

    @Override
    public void commit() {
        // Read-only
    }

    @Override
    public void rollback() {
        // Read-only
    }

    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        if (!readOnly) {
            throw new SQLException("Connection is read-only");
        }
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isValid(int timeout) {
        return !closed;
    }

    @Override
    public DatabaseMetaData getMetaData() {
        return new SqliteDatabaseMetaData(this);
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

    /**
     * Closes the handle. Idempotent, because a JDBC close is allowed to be
     * called twice and {@code Arena.close()} inside the handle is not.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        handle.close();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String toString() {
        return "SqliteConnection[" + dbName + "]";
    }

}
