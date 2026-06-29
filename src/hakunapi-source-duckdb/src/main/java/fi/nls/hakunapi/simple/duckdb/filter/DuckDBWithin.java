package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBWithin extends DuckDBGeometryFunction {
    
    @Override
    public String getFunctionName() {
        return "ST_Within";
    }

}
