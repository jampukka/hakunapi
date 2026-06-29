package fi.nls.hakunapi.simple.duckdb.filter;

public class SQLEqualTo extends SQLComparison {

    @Override
    public String getOp() {
        return "=";
    }

}
