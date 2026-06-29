package fi.nls.hakunapi.simple.duckdb;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.duckdb.DuckDBConnection;

/**
 * Minimal {@link DataSource} over a single in-memory DuckDB instance.
 *
 * <p>One root {@link DuckDBConnection} owns the in-memory database; every {@link #getConnection()}
 * returns a lightweight duplicate sharing the same instance (and the loaded {@code spatial}
 * extension), so concurrent feature streams each get their own connection.
 */
public class DuckDBDataSource implements DataSource, AutoCloseable {

    private final DuckDBConnection root;

    public DuckDBDataSource() throws SQLException {
        this.root = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:");
        try (Statement st = root.createStatement()) {
            st.execute("INSTALL spatial");
            st.execute("LOAD spatial");
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return root.duplicate();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return getConnection();
    }

    @Override
    public void close() throws SQLException {
        root.close();
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {
    }

    @Override
    public void setLoginTimeout(int seconds) {
    }

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

}
