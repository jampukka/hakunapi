package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Just enough DatabaseMetaData for GpkgSimpleSource, which uses it only to find
 * a feature table's primary key.
 */
public class SqliteDatabaseMetaData extends UnsupportedDatabaseMetaData {

    private final SqliteConnection connection;

    SqliteDatabaseMetaData(SqliteConnection connection) {
        this.connection = connection;
    }

    /**
     * Primary key columns of a table, in key order.
     *
     * JDBC specifies columns TABLE_CAT, TABLE_SCHEM, TABLE_NAME, COLUMN_NAME,
     * KEY_SEQ, PK_NAME; the caller reads COLUMN_NAME (4) and nothing else, so
     * the query projects them in that order and lets pragma_table_info do the
     * work.
     */
    @Override
    public ResultSet getPrimaryKeys(String catalog, String schema, String table) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(
                "SELECT NULL AS TABLE_CAT, NULL AS TABLE_SCHEM, ? AS TABLE_NAME,"
                        + " name AS COLUMN_NAME, pk AS KEY_SEQ, NULL AS PK_NAME"
                        + " FROM pragma_table_info(?) WHERE pk > 0 ORDER BY pk");
        ps.setString(1, table);
        ps.setString(2, table);
        return ps.executeQuery();
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public String getDatabaseProductName() {
        return "SQLite";
    }

    @Override
    public String getDatabaseProductVersion() {
        return fi.nls.hakunapi.source.gpkg.http.vfs.Sqlite.libversionString();
    }

    @Override
    public String getDriverName() {
        return "hakunapi-source-gpkg-http";
    }

    @Override
    public String getDriverVersion() {
        return getDatabaseProductVersion();
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
