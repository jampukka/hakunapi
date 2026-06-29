package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBTouches extends DuckDBGeometryFunction {
    
    @Override
    public String getFunctionName() {
        return "ST_Touches";
    }

}
