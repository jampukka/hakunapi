package fi.nls.hakunapi.simple.duckdb.filter;

public class SQLLessThan extends SQLComparison {

    @Override
    public String getOp() {
        return "<";
    }

}
