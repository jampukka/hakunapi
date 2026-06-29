package fi.nls.hakunapi.simple.duckdb.filter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;

import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.simple.duckdb.SQLFeatureType;

/**
 * BBOX spatial filter for DuckDB-backed GeoParquet.
 *
 * <p>The performance of GeoParquet reads comes from row-group / page pruning. When the file carries
 * a GeoParquet 1.1 covering bbox struct column ({@code struct<xmin,ymin,xmax,ymax>}) we emit a plain
 * predicate against those struct fields, which DuckDB pushes down to Parquet statistics to skip whole
 * row groups. An exact {@code ST_Intersects} refines the survivors.
 *
 * <p>When no covering column is configured we fall back to {@code ST_Intersects} only and rely on
 * GeoParquet 2.0 native GEOMETRY column statistics (newer DuckDB) for pruning.
 */
public class DuckDBIntersectsIndex implements SQLFilter {

    @Override
    public String toSQL(Filter filter) {
        HakunaProperty prop = filter.getProp();
        String bboxColumn = bboxColumn(prop);

        String intersects = String.format("ST_Intersects(\"%s\".\"%s\", ST_GeomFromWKB(?))",
                prop.getTable(), prop.getColumn());

        if (bboxColumn == null) {
            return intersects;
        }

        // Covering struct predicate first so DuckDB can prune row groups before the exact test.
        // Two bboxes intersect iff a.xmin <= b.xmax AND a.xmax >= b.xmin (and likewise for y).
        String table = prop.getTable();
        String struct = String.format(
                "\"%s\".\"%s\".xmin <= ? AND \"%s\".\"%s\".xmax >= ? AND \"%s\".\"%s\".ymin <= ? AND \"%s\".\"%s\".ymax >= ?",
                table, bboxColumn, table, bboxColumn, table, bboxColumn, table, bboxColumn);

        return "(" + struct + " AND " + intersects + ")";
    }

    @Override
    public int bind(Filter filter, Connection c, PreparedStatement ps, int i) throws SQLException {
        HakunaProperty prop = filter.getProp();
        Geometry bbox = (Geometry) filter.getValue();

        if (bboxColumn(prop) != null) {
            Envelope e = bbox.getEnvelopeInternal();
            // Order matches the struct predicate: xmin<=maxx, xmax>=minx, ymin<=maxy, ymax>=miny.
            ps.setDouble(i++, e.getMaxX());
            ps.setDouble(i++, e.getMinX());
            ps.setDouble(i++, e.getMaxY());
            ps.setDouble(i++, e.getMinY());
        }

        int outputDimension = bbox.getDimension() > 2 ? 3 : 2;
        byte[] wkb = new WKBWriter(outputDimension, false).write(bbox);
        ps.setBytes(i++, wkb);

        return i;
    }

    private static String bboxColumn(HakunaProperty prop) {
        SQLFeatureType ft = (SQLFeatureType) prop.getFeatureType();
        return ft.getBboxColumn();
    }

}
