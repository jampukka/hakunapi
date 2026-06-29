package fi.nls.hakunapi.simple.duckdb.filter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;

import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.property.HakunaProperty;

public abstract class DuckDBGeometryFunction implements SQLFilter {

    public abstract String getFunctionName();

    @Override
    public String toSQL(Filter filter) {
        HakunaProperty prop = filter.getProp();
        // DuckDB spatial ST_GeomFromWKB takes a single WKB blob argument (no SRID).
        return String.format("%s(\"%s\".\"%s\", ST_GeomFromWKB(?))",
                getFunctionName(), prop.getTable(), prop.getColumn());
    }

    @Override
    public int bind(Filter filter, Connection c, PreparedStatement ps, int i) throws SQLException {
        Geometry geom = (Geometry) filter.getValue();

        int outputDimension = geom.getDimension() > 2 ? 3 : 2;
        byte[] wkb = new WKBWriter(outputDimension, false).write(geom);
        ps.setBytes(i++, wkb);

        return i;
    }

}
