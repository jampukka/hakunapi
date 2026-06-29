package fi.nls.hakunapi.simple.duckdb.filter;

public class SQLGreaterThanOrEqualTo extends SQLComparison {

    @Override
    public String getOp() {
        return ">=";
    }

}
