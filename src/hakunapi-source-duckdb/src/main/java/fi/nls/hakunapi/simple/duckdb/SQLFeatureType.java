package fi.nls.hakunapi.simple.duckdb;

import javax.sql.DataSource;

import fi.nls.hakunapi.core.CaseInsensitiveStrategy;
import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.SimpleFeatureType;

public class SQLFeatureType extends SimpleFeatureType {

    /**
     * Relation the queries run against. For DuckDB GeoParquet this is the table
     * alias the source assigns to {@code read_parquet('<path>')}.
     */
    private String primaryTable;

    /** Path or glob to the backing GeoParquet file(s). */
    private String parquetPath;

    /**
     * Name of the GeoParquet 1.1 covering bbox struct column, if present.
     * When set the BBOX filter emits a struct predicate for row-group pruning.
     */
    private String bboxColumn;

    private DataSource ds;
    private CaseInsensitiveStrategy caseInsensitiveStrategy;
    private boolean sourceWillProject;

    public String getPrimaryTable() {
        return primaryTable;
    }

    public void setPrimaryTable(String primaryTable) {
        this.primaryTable = primaryTable;
    }

    public String getParquetPath() {
        return parquetPath;
    }

    public void setParquetPath(String parquetPath) {
        this.parquetPath = parquetPath;
    }

    public String getBboxColumn() {
        return bboxColumn;
    }

    public void setBboxColumn(String bboxColumn) {
        this.bboxColumn = bboxColumn;
    }

    public DataSource getDatabase() {
        return ds;
    }

    public void setDatabase(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public FeatureProducer getFeatureProducer() {
        return new SimpleDuckDB(ds);
    }

    public CaseInsensitiveStrategy getCaseInsensitiveStrategy() {
        return caseInsensitiveStrategy;
    }

    public void setCaseInsensitiveStrategy(CaseInsensitiveStrategy caseInsensitiveStrategy) {
        this.caseInsensitiveStrategy = caseInsensitiveStrategy;
    }

    @Override
    public boolean isSourceWillProject() {
        return sourceWillProject;
    }

    public void setSourceShouldProject(boolean sourceWillProject) {
        this.sourceWillProject = sourceWillProject;
    }

}
