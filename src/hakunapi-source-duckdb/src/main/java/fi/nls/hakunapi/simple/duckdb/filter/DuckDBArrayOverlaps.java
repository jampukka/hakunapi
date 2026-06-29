package fi.nls.hakunapi.simple.duckdb.filter;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyArray;

public class DuckDBArrayOverlaps implements SQLFilter {

    @Override
    public String toSQL(Filter filter) {
        HakunaProperty prop = filter.getProp();
        // DuckDB has no postgres-style && operator; list_has_any tests list overlap.
        return String.format("list_has_any(\"%s\".\"%s\", ?)", prop.getTable(), prop.getColumn());
    }

    @Override
    public int bind(Filter filter, Connection c, PreparedStatement ps, int i) throws SQLException {
        HakunaPropertyArray prop = (HakunaPropertyArray) filter.getProp();
        List<Object> arr = (List<Object>) filter.getValue();
        Array array = toSQLArray(c, prop, arr);
        ps.setArray(i++, array);
        return i;
    }

    private Array toSQLArray(Connection c, HakunaPropertyArray prop, List<Object> arr) throws SQLException {
        switch (prop.getComponentType()) {
        case BOOLEAN:
            return c.createArrayOf("BOOLEAN", arr.toArray());
        case INT:
            return c.createArrayOf("INTEGER", arr.toArray());
        case LONG:
            return c.createArrayOf("BIGINT", arr.toArray());
        case FLOAT:
            return c.createArrayOf("FLOAT", arr.toArray());
        case DOUBLE:
            return c.createArrayOf("DOUBLE", arr.toArray());
        case STRING:
            return c.createArrayOf("VARCHAR", arr.toArray());
        default:
            throw new IllegalArgumentException("No handler for array of type " + prop.getComponentType());
        }
    }

}
