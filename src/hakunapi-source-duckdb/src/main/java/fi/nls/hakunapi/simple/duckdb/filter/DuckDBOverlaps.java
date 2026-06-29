package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBOverlaps extends DuckDBGeometryFunction {
    
    @Override
    public String getFunctionName() {
        return "ST_Overlaps";
    }

}
