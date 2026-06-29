package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBEquals extends DuckDBGeometryFunction {
    
    @Override
    public String getFunctionName() {
        return "ST_Equals";
    }

}
