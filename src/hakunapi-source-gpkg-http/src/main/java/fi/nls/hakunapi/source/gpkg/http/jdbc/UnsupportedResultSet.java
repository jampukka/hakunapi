package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Every ResultSet method hakunapi does not use, throwing.
 *
 * Generated shape, hand-checked: the subclass overrides only what the GeoPackage
 * read path actually calls, and anything else is a programming error rather than
 * a silent wrong answer.
 */
abstract class UnsupportedResultSet implements java.sql.ResultSet {

    @Override
    public boolean absolute(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("absolute");
    }

    @Override
    public void afterLast() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("afterLast");
    }

    @Override
    public void beforeFirst() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("beforeFirst");
    }

    @Override
    public void cancelRowUpdates() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("cancelRowUpdates");
    }

    @Override
    public void clearWarnings() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("clearWarnings");
    }

    @Override
    public void deleteRow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("deleteRow");
    }

    @Override
    public boolean first() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("first");
    }

    @Override
    public java.sql.Array getArray(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getArray");
    }

    @Override
    public java.sql.Array getArray(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getArray");
    }

    @Override
    public java.io.InputStream getAsciiStream(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getAsciiStream");
    }

    @Override
    public java.io.InputStream getAsciiStream(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getAsciiStream");
    }

    @Override
    public java.math.BigDecimal getBigDecimal(java.lang.String a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal");
    }

    @Override
    public java.math.BigDecimal getBigDecimal(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal");
    }

    @Override
    public java.math.BigDecimal getBigDecimal(int a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal");
    }

    @Override
    public java.math.BigDecimal getBigDecimal(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBigDecimal");
    }

    @Override
    public java.io.InputStream getBinaryStream(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBinaryStream");
    }

    @Override
    public java.io.InputStream getBinaryStream(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBinaryStream");
    }

    @Override
    public java.sql.Blob getBlob(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBlob");
    }

    @Override
    public java.sql.Blob getBlob(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBlob");
    }

    @Override
    public java.io.Reader getCharacterStream(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCharacterStream");
    }

    @Override
    public java.io.Reader getCharacterStream(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCharacterStream");
    }

    @Override
    public java.sql.Clob getClob(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getClob");
    }

    @Override
    public java.sql.Clob getClob(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getClob");
    }

    @Override
    public int getConcurrency() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getConcurrency");
    }

    @Override
    public java.lang.String getCursorName() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCursorName");
    }

    @Override
    public java.sql.Date getDate(java.lang.String a0, java.util.Calendar a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getDate");
    }

    @Override
    public java.sql.Date getDate(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getDate");
    }

    @Override
    public java.sql.Date getDate(int a0, java.util.Calendar a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getDate");
    }

    @Override
    public java.sql.Date getDate(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getDate");
    }

    @Override
    public int getFetchDirection() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getFetchDirection");
    }

    @Override
    public int getFetchSize() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getFetchSize");
    }

    @Override
    public int getHoldability() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getHoldability");
    }

    @Override
    public java.io.Reader getNCharacterStream(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNCharacterStream");
    }

    @Override
    public java.io.Reader getNCharacterStream(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNCharacterStream");
    }

    @Override
    public java.sql.NClob getNClob(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNClob");
    }

    @Override
    public java.sql.NClob getNClob(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNClob");
    }

    @Override
    public java.lang.String getNString(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNString");
    }

    @Override
    public java.lang.String getNString(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNString");
    }

    @Override
    public java.lang.Object getObject(java.lang.String a0, java.util.Map<java.lang.String, java.lang.Class<?>> a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getObject");
    }

    @Override
    public java.lang.Object getObject(int a0, java.util.Map<java.lang.String, java.lang.Class<?>> a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getObject");
    }

    @Override
    public java.sql.Ref getRef(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getRef");
    }

    @Override
    public java.sql.Ref getRef(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getRef");
    }

    @Override
    public int getRow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getRow");
    }

    @Override
    public java.sql.RowId getRowId(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getRowId");
    }

    @Override
    public java.sql.RowId getRowId(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getRowId");
    }

    @Override
    public java.sql.SQLXML getSQLXML(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSQLXML");
    }

    @Override
    public java.sql.SQLXML getSQLXML(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSQLXML");
    }

    @Override
    public java.sql.Statement getStatement() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getStatement");
    }

    @Override
    public java.sql.Time getTime(java.lang.String a0, java.util.Calendar a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTime");
    }

    @Override
    public java.sql.Time getTime(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTime");
    }

    @Override
    public java.sql.Time getTime(int a0, java.util.Calendar a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTime");
    }

    @Override
    public java.sql.Time getTime(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTime");
    }

    @Override
    public java.sql.Timestamp getTimestamp(java.lang.String a0, java.util.Calendar a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTimestamp");
    }

    @Override
    public java.sql.Timestamp getTimestamp(int a0, java.util.Calendar a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTimestamp");
    }

    @Override
    public int getType() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getType");
    }

    @Override
    public java.net.URL getURL(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getURL");
    }

    @Override
    public java.net.URL getURL(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getURL");
    }

    @Override
    public java.io.InputStream getUnicodeStream(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getUnicodeStream");
    }

    @Override
    public java.io.InputStream getUnicodeStream(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getUnicodeStream");
    }

    @Override
    public java.sql.SQLWarning getWarnings() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getWarnings");
    }

    @Override
    public void insertRow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("insertRow");
    }

    @Override
    public boolean isAfterLast() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isAfterLast");
    }

    @Override
    public boolean isBeforeFirst() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isBeforeFirst");
    }

    @Override
    public boolean isFirst() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isFirst");
    }

    @Override
    public boolean isLast() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isLast");
    }

    @Override
    public boolean isWrapperFor(java.lang.Class<?> a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isWrapperFor");
    }

    @Override
    public boolean last() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("last");
    }

    @Override
    public void moveToCurrentRow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("moveToCurrentRow");
    }

    @Override
    public void moveToInsertRow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("moveToInsertRow");
    }

    @Override
    public boolean previous() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("previous");
    }

    @Override
    public void refreshRow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("refreshRow");
    }

    @Override
    public boolean relative(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("relative");
    }

    @Override
    public boolean rowDeleted() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("rowDeleted");
    }

    @Override
    public boolean rowInserted() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("rowInserted");
    }

    @Override
    public boolean rowUpdated() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("rowUpdated");
    }

    @Override
    public void setFetchDirection(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setFetchDirection");
    }

    @Override
    public void setFetchSize(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("setFetchSize");
    }

    @Override
    public <T> T unwrap(java.lang.Class<T> a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("unwrap");
    }

    @Override
    public void updateArray(java.lang.String a0, java.sql.Array a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateArray");
    }

    @Override
    public void updateArray(int a0, java.sql.Array a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateArray");
    }

    @Override
    public void updateAsciiStream(java.lang.String a0, java.io.InputStream a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream");
    }

    @Override
    public void updateAsciiStream(java.lang.String a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream");
    }

    @Override
    public void updateAsciiStream(java.lang.String a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream");
    }

    @Override
    public void updateAsciiStream(int a0, java.io.InputStream a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream");
    }

    @Override
    public void updateAsciiStream(int a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream");
    }

    @Override
    public void updateAsciiStream(int a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateAsciiStream");
    }

    @Override
    public void updateBigDecimal(java.lang.String a0, java.math.BigDecimal a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBigDecimal");
    }

    @Override
    public void updateBigDecimal(int a0, java.math.BigDecimal a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBigDecimal");
    }

    @Override
    public void updateBinaryStream(java.lang.String a0, java.io.InputStream a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream");
    }

    @Override
    public void updateBinaryStream(java.lang.String a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream");
    }

    @Override
    public void updateBinaryStream(java.lang.String a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream");
    }

    @Override
    public void updateBinaryStream(int a0, java.io.InputStream a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream");
    }

    @Override
    public void updateBinaryStream(int a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream");
    }

    @Override
    public void updateBinaryStream(int a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBinaryStream");
    }

    @Override
    public void updateBlob(java.lang.String a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob");
    }

    @Override
    public void updateBlob(java.lang.String a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob");
    }

    @Override
    public void updateBlob(java.lang.String a0, java.sql.Blob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob");
    }

    @Override
    public void updateBlob(int a0, java.io.InputStream a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob");
    }

    @Override
    public void updateBlob(int a0, java.io.InputStream a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob");
    }

    @Override
    public void updateBlob(int a0, java.sql.Blob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBlob");
    }

    @Override
    public void updateBoolean(java.lang.String a0, boolean a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBoolean");
    }

    @Override
    public void updateBoolean(int a0, boolean a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBoolean");
    }

    @Override
    public void updateByte(java.lang.String a0, byte a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateByte");
    }

    @Override
    public void updateByte(int a0, byte a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateByte");
    }

    @Override
    public void updateBytes(java.lang.String a0, byte[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBytes");
    }

    @Override
    public void updateBytes(int a0, byte[] a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateBytes");
    }

    @Override
    public void updateCharacterStream(java.lang.String a0, java.io.Reader a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream");
    }

    @Override
    public void updateCharacterStream(java.lang.String a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream");
    }

    @Override
    public void updateCharacterStream(java.lang.String a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream");
    }

    @Override
    public void updateCharacterStream(int a0, java.io.Reader a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream");
    }

    @Override
    public void updateCharacterStream(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream");
    }

    @Override
    public void updateCharacterStream(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateCharacterStream");
    }

    @Override
    public void updateClob(java.lang.String a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateClob");
    }

    @Override
    public void updateClob(java.lang.String a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateClob");
    }

    @Override
    public void updateClob(java.lang.String a0, java.sql.Clob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateClob");
    }

    @Override
    public void updateClob(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateClob");
    }

    @Override
    public void updateClob(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateClob");
    }

    @Override
    public void updateClob(int a0, java.sql.Clob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateClob");
    }

    @Override
    public void updateDate(java.lang.String a0, java.sql.Date a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateDate");
    }

    @Override
    public void updateDate(int a0, java.sql.Date a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateDate");
    }

    @Override
    public void updateDouble(java.lang.String a0, double a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateDouble");
    }

    @Override
    public void updateDouble(int a0, double a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateDouble");
    }

    @Override
    public void updateFloat(java.lang.String a0, float a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateFloat");
    }

    @Override
    public void updateFloat(int a0, float a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateFloat");
    }

    @Override
    public void updateInt(java.lang.String a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateInt");
    }

    @Override
    public void updateInt(int a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateInt");
    }

    @Override
    public void updateLong(java.lang.String a0, long a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateLong");
    }

    @Override
    public void updateLong(int a0, long a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateLong");
    }

    @Override
    public void updateNCharacterStream(java.lang.String a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream");
    }

    @Override
    public void updateNCharacterStream(java.lang.String a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream");
    }

    @Override
    public void updateNCharacterStream(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream");
    }

    @Override
    public void updateNCharacterStream(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNCharacterStream");
    }

    @Override
    public void updateNClob(java.lang.String a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob");
    }

    @Override
    public void updateNClob(java.lang.String a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob");
    }

    @Override
    public void updateNClob(java.lang.String a0, java.sql.NClob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob");
    }

    @Override
    public void updateNClob(int a0, java.io.Reader a1, long a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob");
    }

    @Override
    public void updateNClob(int a0, java.io.Reader a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob");
    }

    @Override
    public void updateNClob(int a0, java.sql.NClob a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNClob");
    }

    @Override
    public void updateNString(java.lang.String a0, java.lang.String a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNString");
    }

    @Override
    public void updateNString(int a0, java.lang.String a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNString");
    }

    @Override
    public void updateNull(java.lang.String a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNull");
    }

    @Override
    public void updateNull(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateNull");
    }

    @Override
    public void updateObject(java.lang.String a0, java.lang.Object a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateObject");
    }

    @Override
    public void updateObject(java.lang.String a0, java.lang.Object a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateObject");
    }

    @Override
    public void updateObject(int a0, java.lang.Object a1, int a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateObject");
    }

    @Override
    public void updateObject(int a0, java.lang.Object a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateObject");
    }

    @Override
    public void updateRef(java.lang.String a0, java.sql.Ref a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateRef");
    }

    @Override
    public void updateRef(int a0, java.sql.Ref a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateRef");
    }

    @Override
    public void updateRow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateRow");
    }

    @Override
    public void updateRowId(java.lang.String a0, java.sql.RowId a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateRowId");
    }

    @Override
    public void updateRowId(int a0, java.sql.RowId a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateRowId");
    }

    @Override
    public void updateSQLXML(java.lang.String a0, java.sql.SQLXML a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateSQLXML");
    }

    @Override
    public void updateSQLXML(int a0, java.sql.SQLXML a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateSQLXML");
    }

    @Override
    public void updateShort(java.lang.String a0, short a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateShort");
    }

    @Override
    public void updateShort(int a0, short a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateShort");
    }

    @Override
    public void updateString(java.lang.String a0, java.lang.String a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateString");
    }

    @Override
    public void updateString(int a0, java.lang.String a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateString");
    }

    @Override
    public void updateTime(java.lang.String a0, java.sql.Time a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateTime");
    }

    @Override
    public void updateTime(int a0, java.sql.Time a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateTime");
    }

    @Override
    public void updateTimestamp(java.lang.String a0, java.sql.Timestamp a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateTimestamp");
    }

    @Override
    public void updateTimestamp(int a0, java.sql.Timestamp a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updateTimestamp");
    }

}
