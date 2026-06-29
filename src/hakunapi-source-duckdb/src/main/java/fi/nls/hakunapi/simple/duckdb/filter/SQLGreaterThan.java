package fi.nls.hakunapi.simple.duckdb.filter;

public class SQLGreaterThan extends SQLComparison {

    @Override
    public String getOp() {
        return ">";
    }

}
