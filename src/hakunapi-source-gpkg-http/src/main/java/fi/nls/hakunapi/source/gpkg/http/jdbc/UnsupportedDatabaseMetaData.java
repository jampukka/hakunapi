package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

/**
 * Every DatabaseMetaData method hakunapi does not use, throwing.
 *
 * Generated shape, hand-checked: the subclass overrides only what the GeoPackage
 * read path actually calls, and anything else is a programming error rather than
 * a silent wrong answer.
 */
abstract class UnsupportedDatabaseMetaData implements java.sql.DatabaseMetaData {

    @Override
    public boolean allProceduresAreCallable() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("allProceduresAreCallable");
    }

    @Override
    public boolean allTablesAreSelectable() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("allTablesAreSelectable");
    }

    @Override
    public boolean autoCommitFailureClosesAllResultSets() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("autoCommitFailureClosesAllResultSets");
    }

    @Override
    public boolean dataDefinitionCausesTransactionCommit() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("dataDefinitionCausesTransactionCommit");
    }

    @Override
    public boolean dataDefinitionIgnoredInTransactions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("dataDefinitionIgnoredInTransactions");
    }

    @Override
    public boolean deletesAreDetected(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("deletesAreDetected");
    }

    @Override
    public boolean doesMaxRowSizeIncludeBlobs() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("doesMaxRowSizeIncludeBlobs");
    }

    @Override
    public boolean generatedKeyAlwaysReturned() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("generatedKeyAlwaysReturned");
    }

    @Override
    public java.sql.ResultSet getAttributes(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getAttributes");
    }

    @Override
    public java.sql.ResultSet getBestRowIdentifier(java.lang.String a0, java.lang.String a1, java.lang.String a2, int a3, boolean a4) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getBestRowIdentifier");
    }

    @Override
    public java.lang.String getCatalogSeparator() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogSeparator");
    }

    @Override
    public java.lang.String getCatalogTerm() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogTerm");
    }

    @Override
    public java.sql.ResultSet getCatalogs() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCatalogs");
    }

    @Override
    public java.sql.ResultSet getClientInfoProperties() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getClientInfoProperties");
    }

    @Override
    public java.sql.ResultSet getColumnPrivileges(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getColumnPrivileges");
    }

    @Override
    public java.sql.ResultSet getColumns(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getColumns");
    }

    @Override
    public java.sql.ResultSet getCrossReference(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3, java.lang.String a4, java.lang.String a5) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getCrossReference");
    }

    @Override
    public int getDatabaseMajorVersion() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseMajorVersion");
    }

    @Override
    public int getDatabaseMinorVersion() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getDatabaseMinorVersion");
    }

    @Override
    public int getDefaultTransactionIsolation() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getDefaultTransactionIsolation");
    }

    @Override
    public int getDriverMajorVersion() {
        throw new UnsupportedOperationException("getDriverMajorVersion");
    }

    @Override
    public int getDriverMinorVersion() {
        throw new UnsupportedOperationException("getDriverMinorVersion");
    }

    @Override
    public java.sql.ResultSet getExportedKeys(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getExportedKeys");
    }

    @Override
    public java.lang.String getExtraNameCharacters() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getExtraNameCharacters");
    }

    @Override
    public java.sql.ResultSet getFunctionColumns(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getFunctionColumns");
    }

    @Override
    public java.sql.ResultSet getFunctions(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getFunctions");
    }

    @Override
    public java.lang.String getIdentifierQuoteString() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getIdentifierQuoteString");
    }

    @Override
    public java.sql.ResultSet getImportedKeys(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getImportedKeys");
    }

    @Override
    public java.sql.ResultSet getIndexInfo(java.lang.String a0, java.lang.String a1, java.lang.String a2, boolean a3, boolean a4) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getIndexInfo");
    }

    @Override
    public int getJDBCMajorVersion() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getJDBCMajorVersion");
    }

    @Override
    public int getJDBCMinorVersion() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getJDBCMinorVersion");
    }

    @Override
    public int getMaxBinaryLiteralLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxBinaryLiteralLength");
    }

    @Override
    public int getMaxCatalogNameLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCatalogNameLength");
    }

    @Override
    public int getMaxCharLiteralLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCharLiteralLength");
    }

    @Override
    public int getMaxColumnNameLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnNameLength");
    }

    @Override
    public int getMaxColumnsInGroupBy() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInGroupBy");
    }

    @Override
    public int getMaxColumnsInIndex() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInIndex");
    }

    @Override
    public int getMaxColumnsInOrderBy() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInOrderBy");
    }

    @Override
    public int getMaxColumnsInSelect() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInSelect");
    }

    @Override
    public int getMaxColumnsInTable() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxColumnsInTable");
    }

    @Override
    public int getMaxConnections() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxConnections");
    }

    @Override
    public int getMaxCursorNameLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxCursorNameLength");
    }

    @Override
    public int getMaxIndexLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxIndexLength");
    }

    @Override
    public int getMaxProcedureNameLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxProcedureNameLength");
    }

    @Override
    public int getMaxRowSize() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxRowSize");
    }

    @Override
    public int getMaxSchemaNameLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxSchemaNameLength");
    }

    @Override
    public int getMaxStatementLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxStatementLength");
    }

    @Override
    public int getMaxStatements() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxStatements");
    }

    @Override
    public int getMaxTableNameLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxTableNameLength");
    }

    @Override
    public int getMaxTablesInSelect() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxTablesInSelect");
    }

    @Override
    public int getMaxUserNameLength() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getMaxUserNameLength");
    }

    @Override
    public java.lang.String getNumericFunctions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getNumericFunctions");
    }

    @Override
    public java.sql.ResultSet getProcedureColumns(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureColumns");
    }

    @Override
    public java.lang.String getProcedureTerm() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getProcedureTerm");
    }

    @Override
    public java.sql.ResultSet getProcedures(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getProcedures");
    }

    @Override
    public java.sql.ResultSet getPseudoColumns(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getPseudoColumns");
    }

    @Override
    public int getResultSetHoldability() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getResultSetHoldability");
    }

    @Override
    public java.sql.RowIdLifetime getRowIdLifetime() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getRowIdLifetime");
    }

    @Override
    public java.lang.String getSQLKeywords() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSQLKeywords");
    }

    @Override
    public int getSQLStateType() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSQLStateType");
    }

    @Override
    public java.lang.String getSchemaTerm() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSchemaTerm");
    }

    @Override
    public java.sql.ResultSet getSchemas() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSchemas");
    }

    @Override
    public java.sql.ResultSet getSchemas(java.lang.String a0, java.lang.String a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSchemas");
    }

    @Override
    public java.lang.String getSearchStringEscape() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSearchStringEscape");
    }

    @Override
    public java.lang.String getStringFunctions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getStringFunctions");
    }

    @Override
    public java.sql.ResultSet getSuperTables(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTables");
    }

    @Override
    public java.sql.ResultSet getSuperTypes(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSuperTypes");
    }

    @Override
    public java.lang.String getSystemFunctions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getSystemFunctions");
    }

    @Override
    public java.sql.ResultSet getTablePrivileges(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTablePrivileges");
    }

    @Override
    public java.sql.ResultSet getTableTypes() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTableTypes");
    }

    @Override
    public java.sql.ResultSet getTables(java.lang.String a0, java.lang.String a1, java.lang.String a2, java.lang.String[] a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTables");
    }

    @Override
    public java.lang.String getTimeDateFunctions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTimeDateFunctions");
    }

    @Override
    public java.sql.ResultSet getTypeInfo() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getTypeInfo");
    }

    @Override
    public java.sql.ResultSet getUDTs(java.lang.String a0, java.lang.String a1, java.lang.String a2, int[] a3) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getUDTs");
    }

    @Override
    public java.lang.String getURL() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getURL");
    }

    @Override
    public java.lang.String getUserName() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getUserName");
    }

    @Override
    public java.sql.ResultSet getVersionColumns(java.lang.String a0, java.lang.String a1, java.lang.String a2) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("getVersionColumns");
    }

    @Override
    public boolean insertsAreDetected(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("insertsAreDetected");
    }

    @Override
    public boolean isCatalogAtStart() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isCatalogAtStart");
    }

    @Override
    public boolean isReadOnly() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("isReadOnly");
    }

    @Override
    public boolean locatorsUpdateCopy() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("locatorsUpdateCopy");
    }

    @Override
    public boolean nullPlusNonNullIsNull() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("nullPlusNonNullIsNull");
    }

    @Override
    public boolean nullsAreSortedAtEnd() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedAtEnd");
    }

    @Override
    public boolean nullsAreSortedAtStart() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedAtStart");
    }

    @Override
    public boolean nullsAreSortedHigh() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedHigh");
    }

    @Override
    public boolean nullsAreSortedLow() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("nullsAreSortedLow");
    }

    @Override
    public boolean othersDeletesAreVisible(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("othersDeletesAreVisible");
    }

    @Override
    public boolean othersInsertsAreVisible(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("othersInsertsAreVisible");
    }

    @Override
    public boolean othersUpdatesAreVisible(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("othersUpdatesAreVisible");
    }

    @Override
    public boolean ownDeletesAreVisible(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("ownDeletesAreVisible");
    }

    @Override
    public boolean ownInsertsAreVisible(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("ownInsertsAreVisible");
    }

    @Override
    public boolean ownUpdatesAreVisible(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("ownUpdatesAreVisible");
    }

    @Override
    public boolean storesLowerCaseIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("storesLowerCaseIdentifiers");
    }

    @Override
    public boolean storesLowerCaseQuotedIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("storesLowerCaseQuotedIdentifiers");
    }

    @Override
    public boolean storesMixedCaseIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("storesMixedCaseIdentifiers");
    }

    @Override
    public boolean storesMixedCaseQuotedIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("storesMixedCaseQuotedIdentifiers");
    }

    @Override
    public boolean storesUpperCaseIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("storesUpperCaseIdentifiers");
    }

    @Override
    public boolean storesUpperCaseQuotedIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("storesUpperCaseQuotedIdentifiers");
    }

    @Override
    public boolean supportsANSI92EntryLevelSQL() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92EntryLevelSQL");
    }

    @Override
    public boolean supportsANSI92FullSQL() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92FullSQL");
    }

    @Override
    public boolean supportsANSI92IntermediateSQL() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsANSI92IntermediateSQL");
    }

    @Override
    public boolean supportsAlterTableWithAddColumn() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsAlterTableWithAddColumn");
    }

    @Override
    public boolean supportsAlterTableWithDropColumn() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsAlterTableWithDropColumn");
    }

    @Override
    public boolean supportsBatchUpdates() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsBatchUpdates");
    }

    @Override
    public boolean supportsCatalogsInDataManipulation() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInDataManipulation");
    }

    @Override
    public boolean supportsCatalogsInIndexDefinitions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInIndexDefinitions");
    }

    @Override
    public boolean supportsCatalogsInPrivilegeDefinitions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInPrivilegeDefinitions");
    }

    @Override
    public boolean supportsCatalogsInProcedureCalls() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInProcedureCalls");
    }

    @Override
    public boolean supportsCatalogsInTableDefinitions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsCatalogsInTableDefinitions");
    }

    @Override
    public boolean supportsColumnAliasing() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsColumnAliasing");
    }

    @Override
    public boolean supportsConvert() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsConvert");
    }

    @Override
    public boolean supportsConvert(int a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsConvert");
    }

    @Override
    public boolean supportsCoreSQLGrammar() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsCoreSQLGrammar");
    }

    @Override
    public boolean supportsCorrelatedSubqueries() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsCorrelatedSubqueries");
    }

    @Override
    public boolean supportsDataDefinitionAndDataManipulationTransactions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsDataDefinitionAndDataManipulationTransactions");
    }

    @Override
    public boolean supportsDataManipulationTransactionsOnly() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsDataManipulationTransactionsOnly");
    }

    @Override
    public boolean supportsDifferentTableCorrelationNames() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsDifferentTableCorrelationNames");
    }

    @Override
    public boolean supportsExpressionsInOrderBy() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsExpressionsInOrderBy");
    }

    @Override
    public boolean supportsExtendedSQLGrammar() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsExtendedSQLGrammar");
    }

    @Override
    public boolean supportsFullOuterJoins() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsFullOuterJoins");
    }

    @Override
    public boolean supportsGetGeneratedKeys() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsGetGeneratedKeys");
    }

    @Override
    public boolean supportsGroupBy() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupBy");
    }

    @Override
    public boolean supportsGroupByBeyondSelect() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupByBeyondSelect");
    }

    @Override
    public boolean supportsGroupByUnrelated() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsGroupByUnrelated");
    }

    @Override
    public boolean supportsIntegrityEnhancementFacility() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsIntegrityEnhancementFacility");
    }

    @Override
    public boolean supportsLikeEscapeClause() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsLikeEscapeClause");
    }

    @Override
    public boolean supportsLimitedOuterJoins() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsLimitedOuterJoins");
    }

    @Override
    public boolean supportsMinimumSQLGrammar() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsMinimumSQLGrammar");
    }

    @Override
    public boolean supportsMixedCaseIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsMixedCaseIdentifiers");
    }

    @Override
    public boolean supportsMixedCaseQuotedIdentifiers() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsMixedCaseQuotedIdentifiers");
    }

    @Override
    public boolean supportsMultipleOpenResults() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleOpenResults");
    }

    @Override
    public boolean supportsMultipleResultSets() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleResultSets");
    }

    @Override
    public boolean supportsMultipleTransactions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsMultipleTransactions");
    }

    @Override
    public boolean supportsNamedParameters() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsNamedParameters");
    }

    @Override
    public boolean supportsNonNullableColumns() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsNonNullableColumns");
    }

    @Override
    public boolean supportsOpenCursorsAcrossCommit() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenCursorsAcrossCommit");
    }

    @Override
    public boolean supportsOpenCursorsAcrossRollback() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenCursorsAcrossRollback");
    }

    @Override
    public boolean supportsOpenStatementsAcrossCommit() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenStatementsAcrossCommit");
    }

    @Override
    public boolean supportsOpenStatementsAcrossRollback() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsOpenStatementsAcrossRollback");
    }

    @Override
    public boolean supportsOrderByUnrelated() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsOrderByUnrelated");
    }

    @Override
    public boolean supportsOuterJoins() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsOuterJoins");
    }

    @Override
    public boolean supportsPositionedDelete() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsPositionedDelete");
    }

    @Override
    public boolean supportsPositionedUpdate() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsPositionedUpdate");
    }

    @Override
    public boolean supportsResultSetConcurrency(int a0, int a1) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetConcurrency");
    }

    @Override
    public boolean supportsResultSetHoldability(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetHoldability");
    }

    @Override
    public boolean supportsResultSetType(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsResultSetType");
    }

    @Override
    public boolean supportsSavepoints() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSavepoints");
    }

    @Override
    public boolean supportsSchemasInDataManipulation() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInDataManipulation");
    }

    @Override
    public boolean supportsSchemasInIndexDefinitions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInIndexDefinitions");
    }

    @Override
    public boolean supportsSchemasInPrivilegeDefinitions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInPrivilegeDefinitions");
    }

    @Override
    public boolean supportsSchemasInProcedureCalls() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInProcedureCalls");
    }

    @Override
    public boolean supportsSchemasInTableDefinitions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSchemasInTableDefinitions");
    }

    @Override
    public boolean supportsSelectForUpdate() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSelectForUpdate");
    }

    @Override
    public boolean supportsStatementPooling() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsStatementPooling");
    }

    @Override
    public boolean supportsStoredFunctionsUsingCallSyntax() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsStoredFunctionsUsingCallSyntax");
    }

    @Override
    public boolean supportsStoredProcedures() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsStoredProcedures");
    }

    @Override
    public boolean supportsSubqueriesInComparisons() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInComparisons");
    }

    @Override
    public boolean supportsSubqueriesInExists() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInExists");
    }

    @Override
    public boolean supportsSubqueriesInIns() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInIns");
    }

    @Override
    public boolean supportsSubqueriesInQuantifieds() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsSubqueriesInQuantifieds");
    }

    @Override
    public boolean supportsTableCorrelationNames() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsTableCorrelationNames");
    }

    @Override
    public boolean supportsTransactionIsolationLevel(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsTransactionIsolationLevel");
    }

    @Override
    public boolean supportsTransactions() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsTransactions");
    }

    @Override
    public boolean supportsUnion() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsUnion");
    }

    @Override
    public boolean supportsUnionAll() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("supportsUnionAll");
    }

    @Override
    public boolean updatesAreDetected(int a0) throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("updatesAreDetected");
    }

    @Override
    public boolean usesLocalFilePerTable() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("usesLocalFilePerTable");
    }

    @Override
    public boolean usesLocalFiles() throws java.sql.SQLException {
        throw new SQLFeatureNotSupportedException("usesLocalFiles");
    }

}
