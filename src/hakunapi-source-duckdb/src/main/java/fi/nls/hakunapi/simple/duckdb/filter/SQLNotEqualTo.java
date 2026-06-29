package fi.nls.hakunapi.simple.duckdb.filter;

public class SQLNotEqualTo extends SQLComparison {

    @Override
    public String getOp() {
        return "<>";
    }

}
