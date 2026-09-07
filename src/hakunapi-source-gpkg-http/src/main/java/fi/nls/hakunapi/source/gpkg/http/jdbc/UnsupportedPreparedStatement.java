package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Every PreparedStatement method hakunapi does not use, throwing.
 *
 * Generated shape, hand-checked: the subclass overrides only what the GeoPackage
 * read path actually calls, and anything else is a programming error rather than
 * a silent wrong answer.
 */
abstract class UnsupportedPreparedStatement implements java.sql.PreparedStatement {

    @Override
    public void addBatch() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("addBatch");
    }

    @Override
    public void addBatch(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("addBatch");
    }

    @Override
    public void cancel() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("cancel");
    }

    @Override
    public void clearBatch() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("clearBatch");
    }

    @Override
    public void clearWarnings() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("clearWarnings");
    }

    @Override
    public void closeOnCompletion() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("closeOnCompletion");
    }

    @Override
    public boolean execute(java.lang.String a0, int[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("execute");
    }

    @Override
    public boolean execute(java.lang.String a0, java.lang.String[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("execute");
    }

    @Override
    public boolean execute(java.lang.String a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("execute");
    }

    @Override
    public boolean execute(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("execute");
    }

    @Override
    public int[] executeBatch() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("executeBatch");
    }

    @Override
    public java.sql.ResultSet executeQuery(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("executeQuery");
    }

    @Override
    public int executeUpdate() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate");
    }

    @Override
    public int executeUpdate(java.lang.String a0, int[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate");
    }

    @Override
    public int executeUpdate(java.lang.String a0, java.lang.String[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate");
    }

    @Override
    public int executeUpdate(java.lang.String a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate");
    }

    @Override
    public int executeUpdate(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("executeUpdate");
    }

    @Override
    public int getFetchDirection() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getFetchDirection");
    }

    @Override
    public java.sql.ResultSet getGeneratedKeys() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getGeneratedKeys");
    }

    @Override
    public int getMaxFieldSize() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxFieldSize");
    }

    @Override
    public int getMaxRows() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxRows");
    }

    @Override
    public boolean getMoreResults() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMoreResults");
    }

    @Override
    public boolean getMoreResults(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMoreResults");
    }

    @Override
    public java.sql.ParameterMetaData getParameterMetaData() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getParameterMetaData");
    }

    @Override
    public int getQueryTimeout() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getQueryTimeout");
    }

    @Override
    public java.sql.ResultSet getResultSet() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getResultSet");
    }

    @Override
    public int getResultSetConcurrency() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetConcurrency");
    }

    @Override
    public int getResultSetHoldability() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetHoldability");
    }

    @Override
    public int getResultSetType() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetType");
    }

    @Override
    public int getUpdateCount() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getUpdateCount");
    }

    @Override
    public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getWarnings");
    }

    @Override
    public boolean isCloseOnCompletion() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isCloseOnCompletion");
    }

    @Override
    public boolean isPoolable() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isPoolable");
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isWrapperFor");
    }

    @Override
    public void setArray(int a0, java.sql.Array a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setArray");
    }

    @Override
    public void setAsciiStream(int a0, java.io.InputStream a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setAsciiStream");
    }

    @Override
    public void setAsciiStream(int a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setAsciiStream");
    }

    @Override
    public void setAsciiStream(int a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setAsciiStream");
    }

    @Override
    public void setBigDecimal(int a0, java.math.BigDecimal a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setBigDecimal");
    }

    @Override
    public void setBinaryStream(int a0, java.io.InputStream a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setBinaryStream");
    }

    @Override
    public void setBinaryStream(int a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setBinaryStream");
    }

    @Override
    public void setBinaryStream(int a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setBinaryStream");
    }

    @Override
    public void setBlob(int a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setBlob");
    }

    @Override
    public void setBlob(int a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setBlob");
    }

    @Override
    public void setBlob(int a0, java.sql.Blob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setBlob");
    }

    @Override
    public void setByte(int a0, byte a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setByte");
    }

    @Override
    public void setCharacterStream(int a0, java.io.Reader a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setCharacterStream");
    }

    @Override
    public void setCharacterStream(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setCharacterStream");
    }

    @Override
    public void setCharacterStream(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setCharacterStream");
    }

    @Override
    public void setClob(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setClob");
    }

    @Override
    public void setClob(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setClob");
    }

    @Override
    public void setClob(int a0, java.sql.Clob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setClob");
    }

    @Override
    public void setCursorName(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setCursorName");
    }

    @Override
    public void setDate(int a0, java.sql.Date a1, java.util.Calendar a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setDate");
    }

    @Override
    public void setDate(int a0, java.sql.Date a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setDate");
    }

    @Override
    public void setEscapeProcessing(boolean a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setEscapeProcessing");
    }

    @Override
    public void setFetchDirection(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setFetchDirection");
    }

    @Override
    public void setMaxFieldSize(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setMaxFieldSize");
    }

    @Override
    public void setMaxRows(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setMaxRows");
    }

    @Override
    public void setNCharacterStream(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNCharacterStream");
    }

    @Override
    public void setNCharacterStream(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNCharacterStream");
    }

    @Override
    public void setNClob(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNClob");
    }

    @Override
    public void setNClob(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNClob");
    }

    @Override
    public void setNClob(int a0, java.sql.NClob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNClob");
    }

    @Override
    public void setNString(int a0, java.lang.String a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNString");
    }

    @Override
    public void setNull(int a0, int a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setNull");
    }

    @Override
    public void setObject(int a0, java.lang.Object a1, int a2, int a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setObject");
    }

    @Override
    public void setPoolable(boolean a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setPoolable");
    }

    @Override
    public void setRef(int a0, java.sql.Ref a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setRef");
    }

    @Override
    public void setRowId(int a0, java.sql.RowId a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setRowId");
    }

    @Override
    public void setSQLXML(int a0, java.sql.SQLXML a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setSQLXML");
    }

    @Override
    public void setTime(int a0, java.sql.Time a1, java.util.Calendar a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setTime");
    }

    @Override
    public void setTime(int a0, java.sql.Time a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setTime");
    }

    @Override
    public void setTimestamp(int a0, java.sql.Timestamp a1, java.util.Calendar a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setTimestamp");
    }

    @Override
    public void setTimestamp(int a0, java.sql.Timestamp a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setTimestamp");
    }

    @Override
    public void setURL(int a0, java.net.URL a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setURL");
    }

    @Override
    public void setUnicodeStream(int a0, java.io.InputStream a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setUnicodeStream");
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("unwrap");
    }

}
