package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBCrosses extends DuckDBGeometryFunction {
    
    @Override
    public String getFunctionName() {
        return "ST_Crosses";
    }

}
