package fi.nls.hakunapi.simple.duckdb.filter;

public class SQLLessThanOrEqualTo extends SQLComparison {

    @Override
    public String getOp() {
        return "<=";
    }

}
