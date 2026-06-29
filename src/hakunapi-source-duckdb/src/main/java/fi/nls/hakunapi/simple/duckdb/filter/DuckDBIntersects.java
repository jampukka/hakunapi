package fi.nls.hakunapi.simple.duckdb.filter;

public class DuckDBIntersects extends DuckDBGeometryFunction {

    @Override
    public String getFunctionName() {
        return "ST_Intersects";
    }

}
