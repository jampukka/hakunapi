package fi.nls.hakunapi.simple.postgis;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

import fi.nls.hakunapi.core.BranchableFeatureType;
import fi.nls.hakunapi.core.CaseInsensitiveStrategy;
import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.join.Join;

public class SQLFeatureType extends SimpleFeatureType implements BranchableFeatureType {

    private String dbSchema;
    private String primaryTable;
    private List<Join> joins;
    private DataSource ds;
    private CaseInsensitiveStrategy caseInsensitiveStrategy;
    private boolean sourceWillProject;

    public String getDbSchema() {
        return dbSchema;
    }

    public void setDbSchema(String dbSchema) {
        this.dbSchema = dbSchema;
    }

    public String getPrimaryTable() {
        return primaryTable;
    }

    public void setPrimaryTable(String primaryTable) {
        this.primaryTable = primaryTable;
    }

    public List<Join> getJoins() {
        return joins;
    }

    public void setJoins(List<Join> joins) {
        this.joins = joins;
    }

    public DataSource getDatabase() {
        return ds;
    }

    public void setDatabase(DataSource ds) {
        this.ds = ds;
    }

    @Override
    public FeatureProducer getFeatureProducer() {
        return new SimplePostGIS(ds);
    }

    /**
     * Build a branch producer reading this feature type's table from another database. Uses a
     * non-pooled {@link PGSimpleDataSource}, so nothing needs closing at the pool level; the JDBC
     * connection is opened and closed per stream by {@link SimplePostGIS}/{@code BufferedResultSet}.
     * Recognised props: {@code jdbcUrl} (required), {@code username}, {@code password}.
     */
    @Override
    public FeatureProducer getBranchFeatureProducer(Map<String, String> resolvedDbProps) {
        String jdbcUrl = resolvedDbProps.get("jdbcUrl");
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            throw new IllegalArgumentException("branch db props missing required 'jdbcUrl'");
        }
        PGSimpleDataSource branchDs = new PGSimpleDataSource();
        branchDs.setUrl(jdbcUrl);
        String username = resolvedDbProps.get("username");
        if (username != null) {
            branchDs.setUser(username);
        }
        String password = resolvedDbProps.get("password");
        if (password != null) {
            branchDs.setPassword(password);
        }
        return new SimplePostGIS(branchDs);
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
