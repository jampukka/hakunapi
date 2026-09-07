package fi.nls.hakunapi.source.gpkg.http.jdbc;

import static org.junit.Assert.assertEquals;

import java.sql.Types;

import org.junit.Test;

/**
 * The declared types GeoPackage requires (OGC 12-128r19 Table 1), plus SQLite's
 * affinity fallbacks for anything a writer put there instead.
 */
public class SqliteTypeMappingTest {

    @Test
    public void mapsGeoPackageColumnTypes() {
        assertEquals(Types.BOOLEAN, SqliteResultSetMetaData.toJdbcType("BOOLEAN"));
        assertEquals(Types.TINYINT, SqliteResultSetMetaData.toJdbcType("TINYINT"));
        assertEquals(Types.SMALLINT, SqliteResultSetMetaData.toJdbcType("SMALLINT"));
        assertEquals(Types.INTEGER, SqliteResultSetMetaData.toJdbcType("MEDIUMINT"));
        assertEquals(Types.INTEGER, SqliteResultSetMetaData.toJdbcType("INTEGER"));
        assertEquals(Types.BIGINT, SqliteResultSetMetaData.toJdbcType("BIGINT"));
        assertEquals(Types.REAL, SqliteResultSetMetaData.toJdbcType("FLOAT"));
        assertEquals(Types.DOUBLE, SqliteResultSetMetaData.toJdbcType("DOUBLE"));
        assertEquals(Types.DOUBLE, SqliteResultSetMetaData.toJdbcType("REAL"));
        assertEquals(Types.VARCHAR, SqliteResultSetMetaData.toJdbcType("TEXT"));
        assertEquals(Types.BLOB, SqliteResultSetMetaData.toJdbcType("BLOB"));
        assertEquals(Types.DATE, SqliteResultSetMetaData.toJdbcType("DATE"));
        assertEquals(Types.TIMESTAMP, SqliteResultSetMetaData.toJdbcType("DATETIME"));
    }

    /** GeoPackage allows TEXT(n) and BLOB(n). */
    @Test
    public void ignoresDeclaredLength() {
        assertEquals(Types.VARCHAR, SqliteResultSetMetaData.toJdbcType("TEXT(50)"));
        assertEquals(Types.BLOB, SqliteResultSetMetaData.toJdbcType("BLOB(16)"));
        assertEquals(Types.VARCHAR, SqliteResultSetMetaData.toJdbcType("VARCHAR(255)"));
    }

    @Test
    public void isCaseInsensitive() {
        assertEquals(Types.INTEGER, SqliteResultSetMetaData.toJdbcType("mediumint"));
        assertEquals(Types.VARCHAR, SqliteResultSetMetaData.toJdbcType("Text"));
    }

    /**
     * Geometry columns are declared with the geometry type name, e.g. POLYGON,
     * and carry a BLOB in GeoPackage's own binary envelope.
     */
    @Test
    public void treatsGeometryTypesAsBlobs() {
        assertEquals(Types.BLOB, SqliteResultSetMetaData.toJdbcType("POINT"));
        assertEquals(Types.BLOB, SqliteResultSetMetaData.toJdbcType("POLYGON"));
        assertEquals(Types.BLOB, SqliteResultSetMetaData.toJdbcType("MULTILINESTRING"));
        assertEquals(Types.BLOB, SqliteResultSetMetaData.toJdbcType("GEOMETRY"));
    }

    /** An expression column has no declared type at all. */
    @Test
    public void fallsBackToBlobForExpressions() {
        assertEquals(Types.BLOB, SqliteResultSetMetaData.toJdbcType(""));
    }

    @Test
    public void appliesSqliteAffinityRules() {
        assertEquals(Types.INTEGER, SqliteResultSetMetaData.toJdbcType("UNSIGNED BIG INT"));
        assertEquals(Types.VARCHAR, SqliteResultSetMetaData.toJdbcType("NATIVE CHARACTER"));
        assertEquals(Types.DOUBLE, SqliteResultSetMetaData.toJdbcType("DOUBLE PRECISION"));
        assertEquals(Types.NUMERIC, SqliteResultSetMetaData.toJdbcType("DECIMAL"));
    }

}
