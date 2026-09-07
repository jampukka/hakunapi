package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import fi.nls.hakunapi.source.gpkg.http.vfs.Sqlite;

/**
 * ResultSet over a stepped sqlite3_stmt.
 *
 * Forward-only, read-only, and only the accessors hakunapi's
 * ResultSetValueProvider actually uses are implemented; the rest throw. Column
 * indices are 1-based as JDBC requires, SQLite's own are 0-based.
 */
public class SqliteResultSet extends UnsupportedResultSet {

    private final SqliteHandle handle;
    private final MemorySegment stmt;
    private final int columnCount;
    private final boolean ownsStatement;

    private boolean wasNull;
    private boolean closed;

    SqliteResultSet(SqliteHandle handle, MemorySegment stmt, boolean ownsStatement) throws SQLException {
        this.handle = handle;
        this.stmt = stmt;
        this.ownsStatement = ownsStatement;
        try {
            this.columnCount = (int) Sqlite.column_count.invokeExact(stmt);
        } catch (Throwable e) {
            throw new SQLException("Failed to read column count", e);
        }
    }

    @Override
    public boolean next() throws SQLException {
        try {
            int rc = (int) Sqlite.step.invokeExact(stmt);
            if (rc == Sqlite.SQLITE_ROW) {
                return true;
            }
            if (rc == Sqlite.SQLITE_DONE) {
                return false;
            }
            throw new SQLException("Failed to step: " + handle.errmsg(), null, rc);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to step", e);
        }
    }

    private int type(int columnIndex) throws SQLException {
        try {
            return (int) Sqlite.column_type.invokeExact(stmt, columnIndex - 1);
        } catch (Throwable e) {
            throw new SQLException("Failed to read column type", e);
        }
    }

    private boolean isNull(int columnIndex) throws SQLException {
        boolean isNull = type(columnIndex) == Sqlite.SQLITE_NULL;
        wasNull = isNull;
        return isNull;
    }

    @Override
    public boolean wasNull() {
        return wasNull;
    }

    @Override
    public int getInt(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) {
            return 0;
        }
        try {
            return (int) Sqlite.column_int.invokeExact(stmt, columnIndex - 1);
        } catch (Throwable e) {
            throw new SQLException("Failed to read int", e);
        }
    }

    @Override
    public long getLong(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) {
            return 0L;
        }
        try {
            return (long) Sqlite.column_int64.invokeExact(stmt, columnIndex - 1);
        } catch (Throwable e) {
            throw new SQLException("Failed to read long", e);
        }
    }

    @Override
    public double getDouble(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) {
            return 0.0d;
        }
        try {
            return (double) Sqlite.column_double.invokeExact(stmt, columnIndex - 1);
        } catch (Throwable e) {
            throw new SQLException("Failed to read double", e);
        }
    }

    @Override
    public float getFloat(int columnIndex) throws SQLException {
        return (float) getDouble(columnIndex);
    }

    @Override
    public boolean getBoolean(int columnIndex) throws SQLException {
        return getInt(columnIndex) != 0;
    }

    @Override
    public short getShort(int columnIndex) throws SQLException {
        return (short) getInt(columnIndex);
    }

    @Override
    public byte getByte(int columnIndex) throws SQLException {
        return (byte) getInt(columnIndex);
    }

    @Override
    public String getString(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) {
            return null;
        }
        try {
            MemorySegment p = (MemorySegment) Sqlite.column_text.invokeExact(stmt, columnIndex - 1);
            return Sqlite.cstring(p);
        } catch (Throwable e) {
            throw new SQLException("Failed to read string", e);
        }
    }

    @Override
    public byte[] getBytes(int columnIndex) throws SQLException {
        if (isNull(columnIndex)) {
            return null;
        }
        try {
            int len = (int) Sqlite.column_bytes.invokeExact(stmt, columnIndex - 1);
            MemorySegment p = (MemorySegment) Sqlite.column_blob.invokeExact(stmt, columnIndex - 1);
            if (len == 0 || MemorySegment.NULL.equals(p)) {
                return new byte[0];
            }
            return p.reinterpret(len).toArray(Sqlite.C_CHAR);
        } catch (Throwable e) {
            throw new SQLException("Failed to read blob", e);
        }
    }

    @Override
    public Object getObject(int columnIndex) throws SQLException {
        int type = type(columnIndex);
        wasNull = type == Sqlite.SQLITE_NULL;
        switch (type) {
        case Sqlite.SQLITE_INTEGER:
            return getLong(columnIndex);
        case Sqlite.SQLITE_FLOAT:
            return getDouble(columnIndex);
        case Sqlite.SQLITE_TEXT:
            return getString(columnIndex);
        case Sqlite.SQLITE_BLOB:
            return getBytes(columnIndex);
        case Sqlite.SQLITE_NULL:
            return null;
        default:
            throw new SQLException("Unknown SQLite column type " + type);
        }
    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
        if (type == LocalDateTime.class) {
            String s = getString(columnIndex);
            return s == null ? null : type.cast(LocalDateTime.parse(s.replace(' ', 'T')));
        }
        if (type == LocalDate.class) {
            String s = getString(columnIndex);
            return s == null ? null : type.cast(LocalDate.parse(s));
        }
        if (type == String.class) {
            return type.cast(getString(columnIndex));
        }
        throw new SQLFeatureNotSupportedException("getObject for " + type.getName());
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        String s = getString(columnIndex);
        if (s == null) {
            return null;
        }
        return Timestamp.valueOf(s.replace('T', ' '));
    }

    @Override
    public ResultSetMetaData getMetaData() {
        return new SqliteResultSetMetaData(stmt, columnCount);
    }

    /**
     * Resolves a column label to its 1-based index, case-insensitively as JDBC
     * requires. Linear over the column count, which is small and only used by
     * metadata queries - the per-feature path indexes directly.
     */
    @Override
    public int findColumn(String columnLabel) throws SQLException {
        for (int i = 1; i <= columnCount; i++) {
            if (columnName(stmt, i).equalsIgnoreCase(columnLabel)) {
                return i;
            }
        }
        throw new SQLException("No such column: " + columnLabel);
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        return getString(findColumn(columnLabel));
    }

    @Override
    public int getInt(String columnLabel) throws SQLException {
        return getInt(findColumn(columnLabel));
    }

    @Override
    public long getLong(String columnLabel) throws SQLException {
        return getLong(findColumn(columnLabel));
    }

    @Override
    public double getDouble(String columnLabel) throws SQLException {
        return getDouble(findColumn(columnLabel));
    }

    @Override
    public float getFloat(String columnLabel) throws SQLException {
        return getFloat(findColumn(columnLabel));
    }

    @Override
    public boolean getBoolean(String columnLabel) throws SQLException {
        return getBoolean(findColumn(columnLabel));
    }

    @Override
    public short getShort(String columnLabel) throws SQLException {
        return getShort(findColumn(columnLabel));
    }

    @Override
    public byte getByte(String columnLabel) throws SQLException {
        return getByte(findColumn(columnLabel));
    }

    @Override
    public byte[] getBytes(String columnLabel) throws SQLException {
        return getBytes(findColumn(columnLabel));
    }

    @Override
    public Object getObject(String columnLabel) throws SQLException {
        return getObject(findColumn(columnLabel));
    }

    @Override
    public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
        return getObject(findColumn(columnLabel), type);
    }

    @Override
    public java.sql.Timestamp getTimestamp(String columnLabel) throws SQLException {
        return getTimestamp(findColumn(columnLabel));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (ownsStatement) {
            SqliteHandle.finalizeStatement(stmt);
        } else {
            try {
                int ignore = (int) Sqlite.reset.invokeExact(stmt);
            } catch (Throwable e) {
                // Ignore
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    static String columnName(MemorySegment stmt, int columnIndex) throws SQLException {
        try {
            MemorySegment p = (MemorySegment) Sqlite.column_name.invokeExact(stmt, columnIndex - 1);
            String name = Sqlite.cstring(p);
            return name == null ? "" : name;
        } catch (Throwable e) {
            throw new SQLException("Failed to read column name", e);
        }
    }

    static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

}
