package fi.nls.hakunapi.simple.duckdb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.nls.hakunapi.core.OrderBy;
import fi.nls.hakunapi.core.QueryContext;
import fi.nls.hakunapi.core.ValueMapper;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.filter.FilterOp;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyComposite;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;
import fi.nls.hakunapi.core.schemas.Crs;
import fi.nls.hakunapi.core.util.StringPair;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBArrayOverlaps;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBContains;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBCrosses;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBDisjoint;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBEquals;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBIntersects;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBIntersectsIndex;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBOverlaps;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBTouches;
import fi.nls.hakunapi.simple.duckdb.filter.DuckDBWithin;
import fi.nls.hakunapi.simple.duckdb.filter.SQLAnd;
import fi.nls.hakunapi.simple.duckdb.filter.SQLEqualTo;
import fi.nls.hakunapi.simple.duckdb.filter.SQLFilter;
import fi.nls.hakunapi.simple.duckdb.filter.SQLGreaterThan;
import fi.nls.hakunapi.simple.duckdb.filter.SQLGreaterThanOrEqualTo;
import fi.nls.hakunapi.simple.duckdb.filter.SQLIsNotNull;
import fi.nls.hakunapi.simple.duckdb.filter.SQLIsNull;
import fi.nls.hakunapi.simple.duckdb.filter.SQLLessThan;
import fi.nls.hakunapi.simple.duckdb.filter.SQLLessThanOrEqualTo;
import fi.nls.hakunapi.simple.duckdb.filter.SQLLike;
import fi.nls.hakunapi.simple.duckdb.filter.SQLNot;
import fi.nls.hakunapi.simple.duckdb.filter.SQLNotEqualTo;
import fi.nls.hakunapi.simple.duckdb.filter.SQLOr;

public class DuckDBUtil {

    private static final EnumMap<FilterOp, SQLFilter> FILTERS;
    static {
        FILTERS = new EnumMap<>(FilterOp.class);
        FILTERS.put(FilterOp.EQUAL_TO, new SQLEqualTo());
        FILTERS.put(FilterOp.NOT_EQUAL_TO, new SQLNotEqualTo());
        FILTERS.put(FilterOp.GREATER_THAN, new SQLGreaterThan());
        FILTERS.put(FilterOp.GREATER_THAN_OR_EQUAL_TO, new SQLGreaterThanOrEqualTo());
        FILTERS.put(FilterOp.LESS_THAN, new SQLLessThan());
        FILTERS.put(FilterOp.LESS_THAN_OR_EQUAL_TO, new SQLLessThanOrEqualTo());

        FILTERS.put(FilterOp.LIKE, new SQLLike());

        FILTERS.put(FilterOp.NULL, new SQLIsNull());
        FILTERS.put(FilterOp.NOT_NULL, new SQLIsNotNull());

        FILTERS.put(FilterOp.OR, new SQLOr(FILTERS));
        FILTERS.put(FilterOp.AND, new SQLAnd(FILTERS));
        FILTERS.put(FilterOp.NOT, new SQLNot(FILTERS));

        FILTERS.put(FilterOp.INTERSECTS_INDEX, new DuckDBIntersectsIndex());
        FILTERS.put(FilterOp.INTERSECTS, new DuckDBIntersects());
        FILTERS.put(FilterOp.EQUALS, new DuckDBEquals());
        FILTERS.put(FilterOp.DISJOINT, new DuckDBDisjoint());
        FILTERS.put(FilterOp.TOUCHES, new DuckDBTouches());
        FILTERS.put(FilterOp.WITHIN, new DuckDBWithin());
        FILTERS.put(FilterOp.OVERLAPS, new DuckDBOverlaps());
        FILTERS.put(FilterOp.CROSSES, new DuckDBCrosses());
        FILTERS.put(FilterOp.CONTAINS, new DuckDBContains());

        FILTERS.put(FilterOp.ARRAY_OVERLAPS, new DuckDBArrayOverlaps());
    }

    public static List<ValueMapper> select(StringBuilder query, List<HakunaProperty> properties, QueryContext ctx) throws Exception {
        query.append("SELECT ");

        Map<StringPair, Integer> columnToIndex = new HashMap<>();
        for (HakunaProperty property : properties) {
            addToSelect(query, columnToIndex, property, ctx);
        }
        // Remove trailing comma
        query.setLength(query.length() - 1);

        List<ValueMapper> mappers = new ArrayList<>();
        int iValueContainer = 0;
        for (HakunaProperty property : properties) {
            mappers.add(property.getMapper(columnToIndex, iValueContainer++, ctx));
        }
        return mappers;
    }

    private static void addToSelect(StringBuilder query, Map<StringPair, Integer> columnToIndex, HakunaProperty property, QueryContext ctx) {
        if (property instanceof HakunaPropertyComposite) {
            HakunaPropertyComposite composite = (HakunaPropertyComposite) property;
            for (HakunaProperty part : composite.getParts()) {
                addToSelect(query, columnToIndex, part, ctx);
            }
        } else {
            String table = property.getTable();
            for (String column : property.getColumns()) {
                StringPair key = new StringPair(table, column);
                if (!columnToIndex.containsKey(key)) {
                    int i = columnToIndex.size();
                    columnToIndex.put(key, i);
                    String s = SQLUtil.toSQL(table, column);
                    if (property.getType() == HakunaPropertyType.GEOMETRY) {
                        HakunaPropertyGeometry g = (HakunaPropertyGeometry) property;
                        // DuckDB spatial emits plain WKB; HakunaGeometryEWKB parses it (SRID 0).
                        if (ctx.isSourceShouldProjectToSrid() && ctx.getSRID() != g.getStorageSRID()) {
                            int srid = ctx.getSRID() == Crs.CRS84_SRID ? 4326 : ctx.getSRID();
                            s = String.format("ST_AsWKB(ST_Transform(%s, 'EPSG:%d', 'EPSG:%d'))",
                                    s, g.getStorageSRID(), srid);
                        } else {
                            s = String.format("ST_AsWKB(%s)", s);
                        }
                    }
                    query.append(s);
                    query.append(',');
                }
            }
        }
    }

    /**
     * DuckDB GeoParquet relation: {@code read_parquet('<path>') AS "table"}. The alias lets the rest
     * of the query (and the filter SQL) reference columns the same way the PostGIS source does.
     */
    public static void from(StringBuilder query, String parquetPath, String table) {
        query.append(" FROM read_parquet(");
        query.append('\'').append(parquetPath.replace("'", "''")).append('\'');
        query.append(") AS ");
        query.append('"').append(table).append('"');
    }

    public static void where(StringBuilder q, List<Filter> filters) {
        boolean first = true;
        for (Filter filter : filters) {
            String sql = FILTERS.get(filter.getOp()).toSQL(filter);
            if (sql == null) {
                continue;
            }
            if (first) {
                q.append(" WHERE ");
                first = false;
            } else {
                q.append(" AND " );
            }
            q.append(sql);
        }
    }

    public static void orderBy(StringBuilder queryBuilder, List<OrderBy> orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        queryBuilder.append(" ORDER BY ");
        for (int i = 0; i < orderBy.size(); i++) {
            HakunaProperty prop = orderBy.get(i).getProperty();
            boolean asc = orderBy.get(i).isAscending();
            if (i > 0) {
                queryBuilder.append(',');
            }
            queryBuilder.append('"');
            queryBuilder.append(prop.getTable());
            queryBuilder.append('"');
            queryBuilder.append('.');
            queryBuilder.append('"');
            queryBuilder.append(prop.getColumn());
            queryBuilder.append('"');
            queryBuilder.append(asc ? " ASC" : " DESC");
        }
    }

    public static void offset(StringBuilder q, int offset) {
        q.append(" OFFSET ").append(offset);
    }

    public static void limit(StringBuilder q, int limit) {
        q.append(" LIMIT ").append(limit);
    }

    public static void bind(Connection c, PreparedStatement ps, List<Filter> filters) throws SQLException {
        int i = 1;
        for (Filter filter : filters) {
            i = FILTERS.get(filter.getOp()).bind(filter, c, ps, i);
        }
    }

}
