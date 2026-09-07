package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.lang.foreign.MemorySegment;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import fi.nls.hakunapi.source.gpkg.http.vfs.Sqlite;

/**
 * Column count, names and declared types for a prepared statement.
 *
 * SQLite values are dynamically typed, but a column has a declared type from
 * CREATE TABLE, and that is what GpkgSimpleSource maps to a HakunaPropertyType.
 * The declared names are the ones GeoPackage requires (OGC 12-128r19 §1.1.1.1
 * Table 1), so the mapping is exact rather than a guess; SQLite's own type
 * affinity rules cover anything else.
 */
public class SqliteResultSetMetaData implements ResultSetMetaData {

    private final MemorySegment stmt;
    private final int columnCount;

    SqliteResultSetMetaData(MemorySegment stmt, int columnCount) {
        this.stmt = stmt;
        this.columnCount = columnCount;
    }

    @Override
    public int getColumnCount() {
        return columnCount;
    }

    @Override
    public String getColumnName(int column) throws SQLException {
        return SqliteResultSet.columnName(stmt, column);
    }

    @Override
    public String getColumnLabel(int column) throws SQLException {
        return getColumnName(column);
    }

    @Override
    public int getColumnType(int column) throws SQLException {
        String declared = getColumnTypeName(column);
        return toJdbcType(declared);
    }

    @Override
    public String getColumnTypeName(int column) throws SQLException {
        try {
            MemorySegment p = (MemorySegment) Sqlite.column_decltype.invokeExact(stmt, column - 1);
            String name = Sqlite.cstring(p);
            // An expression has no declared type; SQLite reports NULL
            return name == null ? "" : name;
        } catch (Throwable e) {
            throw new SQLException("Failed to read declared column type", e);
        }
    }

    /**
     * Maps a declared GeoPackage column type to a java.sql.Types constant.
     *
     * The GeoPackage types are listed first; beyond them SQLite's affinity rules
     * apply, which is why the fallbacks look at substrings the way the engine
     * itself does.
     */
    static int toJdbcType(String declared) {
        String type = declared.toUpperCase(java.util.Locale.ROOT);
        int paren = type.indexOf('(');
        if (paren > 0) {
            type = type.substring(0, paren);
        }
        type = type.trim();
        switch (type) {
        case "BOOLEAN":
            return Types.BOOLEAN;
        case "TINYINT":
            return Types.TINYINT;
        case "SMALLINT":
            return Types.SMALLINT;
        case "MEDIUMINT":
        case "INT":
        case "INTEGER":
            return Types.INTEGER;
        case "BIGINT":
            return Types.BIGINT;
        case "FLOAT":
            return Types.REAL;
        case "DOUBLE":
        case "REAL":
            return Types.DOUBLE;
        case "TEXT":
        case "CLOB":
        case "VARCHAR":
        case "CHARACTER":
            return Types.VARCHAR;
        case "BLOB":
            return Types.BLOB;
        case "DATE":
            return Types.DATE;
        case "DATETIME":
        case "TIMESTAMP":
            return Types.TIMESTAMP;
        // Geometry columns are declared with the geometry type name and hold a
        // GeoPackage binary blob. These have to be matched before the affinity
        // rules below, or POINT and MULTIPOINT would match "INT".
        case "GEOMETRY":
        case "POINT":
        case "LINESTRING":
        case "POLYGON":
        case "MULTIPOINT":
        case "MULTILINESTRING":
        case "MULTIPOLYGON":
        case "GEOMETRYCOLLECTION":
        case "CIRCULARSTRING":
        case "COMPOUNDCURVE":
        case "CURVEPOLYGON":
        case "MULTICURVE":
        case "MULTISURFACE":
        case "CURVE":
        case "SURFACE":
            return Types.BLOB;
        default:
            break;
        }
        // SQLite type affinity, in the order the engine applies it
        if (type.contains("INT")) {
            return Types.INTEGER;
        }
        if (type.contains("CHAR") || type.contains("CLOB") || type.contains("TEXT")) {
            return Types.VARCHAR;
        }
        if (type.contains("BLOB") || type.isEmpty()) {
            return Types.BLOB;
        }
        if (type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB")) {
            return Types.DOUBLE;
        }
        return Types.NUMERIC;
    }

    @Override
    public int isNullable(int column) {
        return columnNullableUnknown;
    }

    @Override
    public boolean isReadOnly(int column) {
        return true;
    }

    @Override
    public boolean isWritable(int column) {
        return false;
    }

    @Override
    public boolean isDefinitelyWritable(int column) {
        return false;
    }

    @Override
    public boolean isAutoIncrement(int column) {
        return false;
    }

    @Override
    public boolean isCaseSensitive(int column) {
        return true;
    }

    @Override
    public boolean isSearchable(int column) {
        return true;
    }

    @Override
    public boolean isCurrency(int column) {
        return false;
    }

    @Override
    public boolean isSigned(int column) {
        return true;
    }

    @Override
    public int getColumnDisplaySize(int column) {
        return 0;
    }

    @Override
    public String getSchemaName(int column) {
        return "";
    }

    @Override
    public int getPrecision(int column) {
        return 0;
    }

    @Override
    public int getScale(int column) {
        return 0;
    }

    @Override
    public String getTableName(int column) {
        return "";
    }

    @Override
    public String getCatalogName(int column) {
        return "";
    }

    @Override
    public String getColumnClassName(int column) {
        return Object.class.getName();
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
