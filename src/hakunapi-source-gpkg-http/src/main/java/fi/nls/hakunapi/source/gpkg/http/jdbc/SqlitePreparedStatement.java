package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import fi.nls.hakunapi.source.gpkg.http.vfs.Sqlite;

/**
 * PreparedStatement over a sqlite3_stmt.
 *
 * Bound values are copied by SQLite (SQLITE_TRANSIENT), so the confined arena
 * used to marshal them can be released immediately.
 */
public class SqlitePreparedStatement extends UnsupportedPreparedStatement {

    private final SqliteConnection connection;
    private final SqliteHandle handle;
    private final String sql;
    private final MemorySegment stmt;

    private boolean closed;

    SqlitePreparedStatement(SqliteConnection connection, SqliteHandle handle, String sql) throws SQLException {
        this.connection = connection;
        this.handle = handle;
        this.sql = sql;
        this.stmt = handle.prepare(sql);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        // The statement owns the sqlite3_stmt, so closing the result set resets
        // rather than finalizes it
        return new SqliteResultSet(handle, stmt, false);
    }

    private void reset() {
        try {
            int ignore = (int) Sqlite.reset.invokeExact(stmt);
        } catch (Throwable e) {
            // Ignore
        }
    }

    @Override
    public boolean execute() throws SQLException {
        executeQuery().next();
        return true;
    }

    private void checkBind(int rc, int parameterIndex) throws SQLException {
        if (rc != Sqlite.SQLITE_OK) {
            throw new SQLException("Failed to bind parameter " + parameterIndex + ": " + handle.errmsg(), null, rc);
        }
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        try {
            checkBind((int) Sqlite.bind_null.invokeExact(stmt, parameterIndex), parameterIndex);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to bind null", e);
        }
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        try {
            checkBind((int) Sqlite.bind_int.invokeExact(stmt, parameterIndex, x), parameterIndex);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to bind int", e);
        }
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        try {
            checkBind((int) Sqlite.bind_int64.invokeExact(stmt, parameterIndex, x), parameterIndex);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to bind long", e);
        }
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        try {
            checkBind((int) Sqlite.bind_double.invokeExact(stmt, parameterIndex, x), parameterIndex);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to bind double", e);
        }
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        setDouble(parameterIndex, x);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        setInt(parameterIndex, x ? 1 : 0);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        setInt(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, java.sql.Types.VARCHAR);
            return;
        }
        byte[] bytes = x.getBytes(StandardCharsets.UTF_8);
        try (Arena local = Arena.ofConfined()) {
            MemorySegment value = local.allocate(bytes.length);
            MemorySegment.copy(bytes, 0, value, Sqlite.C_CHAR, 0, bytes.length);
            MemorySegment transient_ = MemorySegment.ofAddress(Sqlite.SQLITE_TRANSIENT);
            checkBind((int) Sqlite.bind_text.invokeExact(stmt, parameterIndex, value, bytes.length, transient_),
                    parameterIndex);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to bind string", e);
        }
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, java.sql.Types.BLOB);
            return;
        }
        try (Arena local = Arena.ofConfined()) {
            MemorySegment value = local.allocate(Math.max(x.length, 1));
            MemorySegment.copy(x, 0, value, Sqlite.C_CHAR, 0, x.length);
            MemorySegment transient_ = MemorySegment.ofAddress(Sqlite.SQLITE_TRANSIENT);
            checkBind((int) Sqlite.bind_blob.invokeExact(stmt, parameterIndex, value, x.length, transient_),
                    parameterIndex);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to bind blob", e);
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        if (x == null) {
            setNull(parameterIndex, java.sql.Types.NULL);
        } else if (x instanceof Integer v) {
            setInt(parameterIndex, v);
        } else if (x instanceof Long v) {
            setLong(parameterIndex, v);
        } else if (x instanceof Double v) {
            setDouble(parameterIndex, v);
        } else if (x instanceof Float v) {
            setDouble(parameterIndex, v);
        } else if (x instanceof Short v) {
            setInt(parameterIndex, v);
        } else if (x instanceof Boolean v) {
            setInt(parameterIndex, v ? 1 : 0);
        } else if (x instanceof byte[] v) {
            setBytes(parameterIndex, v);
        } else if (x instanceof String v) {
            setString(parameterIndex, v);
        } else {
            setString(parameterIndex, x.toString());
        }
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        setObject(parameterIndex, x);
    }

    @Override
    public void clearParameters() throws SQLException {
        reset();
    }

    @Override
    public void setFetchSize(int rows) {
        // SQLite streams a row at a time; nothing to tune
    }

    @Override
    public int getFetchSize() {
        return 0;
    }

    @Override
    public void setQueryTimeout(int seconds) {
        // No busy timeout on an immutable read-only file
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        try {
            int columnCount = (int) Sqlite.column_count.invokeExact(stmt);
            return new SqliteResultSetMetaData(stmt, columnCount);
        } catch (Throwable e) {
            throw new SQLException("Failed to read column count", e);
        }
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            SqliteHandle.finalizeStatement(stmt);
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public String toString() {
        return sql;
    }

}
