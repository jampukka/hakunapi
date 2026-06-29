package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBDisjoint extends DuckDBGeometryFunction {
    
    @Override
    public String getFunctionName() {
        return "ST_Disjoint";
    }

}
