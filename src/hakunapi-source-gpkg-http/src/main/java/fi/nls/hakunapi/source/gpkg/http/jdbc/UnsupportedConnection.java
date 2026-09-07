package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Every Connection method hakunapi does not use, throwing.
 *
 * Generated shape, hand-checked: the subclass overrides only what the GeoPackage
 * read path actually calls, and anything else is a programming error rather than
 * a silent wrong answer.
 */
abstract class UnsupportedConnection implements java.sql.Connection {

    @Override
    public void abort(java.util.concurrent.Executor a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("abort");
    }

    @Override
    public void clearWarnings() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("clearWarnings");
    }

    @Override
    public java.sql.Array createArrayOf(java.lang.String a0, java.lang.Object[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createArrayOf");
    }

    @Override
    public java.sql.Blob createBlob() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createBlob");
    }

    @Override
    public java.sql.Clob createClob() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createClob");
    }

    @Override
    public java.sql.NClob createNClob() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createNClob");
    }

    @Override
    public java.sql.SQLXML createSQLXML() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createSQLXML");
    }

    @Override
    public java.sql.Statement createStatement(int a0, int a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createStatement");
    }

    @Override
    public java.sql.Statement createStatement(int a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createStatement");
    }

    @Override
    public java.sql.Struct createStruct(java.lang.String a0, java.lang.Object[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("createStruct");
    }

    @Override
    public java.lang.String getCatalog() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCatalog");
    }

    @Override
    public java.util.Properties getClientInfo() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfo");
    }

    @Override
    public java.lang.String getClientInfo(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfo");
    }

    @Override
    public int getHoldability() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getHoldability");
    }

    @Override
    public int getNetworkTimeout() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNetworkTimeout");
    }

    @Override
    public java.lang.String getSchema() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSchema");
    }

    @Override
    public int getTransactionIsolation() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTransactionIsolation");
    }

    @Override
    public java.util.Map<java.lang.String, java.lang.Class<?>> getTypeMap() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTypeMap");
    }

    @Override
    public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getWarnings");
    }

    @Override
    public java.lang.String nativeSQL(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("nativeSQL");
    }

    @Override
    public java.sql.CallableStatement prepareCall(java.lang.String a0, int a1, int a2, int a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("prepareCall");
    }

    @Override
    public java.sql.CallableStatement prepareCall(java.lang.String a0, int a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("prepareCall");
    }

    @Override
    public java.sql.CallableStatement prepareCall(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("prepareCall");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String a0, int[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String a0, java.lang.String[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String a0, int a1, int a2, int a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement");
    }

    @Override
    public java.sql.PreparedStatement prepareStatement(java.lang.String a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("prepareStatement");
    }

    @Override
    public void releaseSavepoint(java.sql.Savepoint a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("releaseSavepoint");
    }

    @Override
    public void rollback(java.sql.Savepoint a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("rollback");
    }

    @Override
    public void setCatalog(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setCatalog");
    }

    @Override
    public void setClientInfo(java.lang.String a0, java.lang.String a1) throws java.sql.SQLClientInfoException {
        throw new UnsupportedOperationException("setClientInfo");
    }

    @Override
    public void setClientInfo(java.util.Properties a0) throws java.sql.SQLClientInfoException {
        throw new UnsupportedOperationException("setClientInfo");
    }

    @Override
    public void setHoldability(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setHoldability");
    }

    @Override
    public void setNetworkTimeout(java.util.concurrent.Executor a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNetworkTimeout");
    }

    @Override
    public java.sql.Savepoint setSavepoint() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setSavepoint");
    }

    @Override
    public java.sql.Savepoint setSavepoint(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setSavepoint");
    }

    @Override
    public void setSchema(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setSchema");
    }

    @Override
    public void setTransactionIsolation(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setTransactionIsolation");
    }

    @Override
    public void setTypeMap(java.util.Map<java.lang.String, java.lang.Class<?>> a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setTypeMap");
    }

}
