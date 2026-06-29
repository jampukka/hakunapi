package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBContains extends DuckDBGeometryFunction {
    
    @Override
    public String getFunctionName() {
        return "ST_Contains";
    }

}
