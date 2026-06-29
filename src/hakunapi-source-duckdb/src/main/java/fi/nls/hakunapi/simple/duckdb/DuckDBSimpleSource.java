package fi.nls.hakunapi.simple.duckdb;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.CaseInsensitiveStrategy;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.SimpleSource;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyNumberEnum;
import fi.nls.hakunapi.core.property.HakunaPropertyStatic;
import fi.nls.hakunapi.core.property.HakunaPropertyStringEnum;
import fi.nls.hakunapi.core.property.HakunaPropertyTransformed;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.core.property.HakunaPropertyWriter;
import fi.nls.hakunapi.core.property.HakunaPropertyWriters;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyInt;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyLong;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyString;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyUUID;
import fi.nls.hakunapi.core.transformer.ValueTransformer;

/**
 * {@link SimpleSource} reading GeoParquet files through DuckDB + its {@code spatial} extension.
 *
 * <p>A collection's {@code table} property points at a parquet file path (or glob); queries run
 * against {@code read_parquet('<path>')}. DuckDB has no geometry catalog, so geometry SRID/type are
 * taken from config ({@code srid.storage}, {@code geometry.type}, {@code geometry.dim}). Column types
 * for non-geometry properties are probed via a {@code LIMIT 0} query.
 */
public class DuckDBSimpleSource implements SimpleSource {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBSimpleSource.class);

    private DuckDBDataSource dataSource;

    @Override
    public String getType() {
        return "duckdb";
    }

    private synchronized DuckDBDataSource getDataSource() throws SQLException {
        if (dataSource == null) {
            dataSource = new DuckDBDataSource();
        }
        return dataSource;
    }

    @Override
    public void close() throws Exception {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public SimpleFeatureType parse(HakunaConfigParser cfg, Path path, String collectionId, int[] srids) throws Exception {
        String p = "collections." + collectionId + ".";
        SQLFeatureType ft = new SQLFeatureType();
        ft.setName(collectionId);

        String parquetPath = cfg.get(p + "table");
        if (parquetPath == null || parquetPath.isEmpty()) {
            throw new IllegalArgumentException("Missing required property " + (p + "table"));
        }

        // The relation alias used in generated SQL. Defaults to the collection id.
        String table = cfg.get(p + "relation", collectionId);

        DuckDBDataSource ds = getDataSource();

        ft.setParquetPath(parquetPath);
        ft.setPrimaryTable(table);
        ft.setDatabase(ds);
        ft.setBboxColumn(cfg.get(p + "geometry.bbox", detectBboxColumn(ds, parquetPath)));

        int sridStorage = Integer.parseInt(cfg.get(p + "srid.storage", "0"));

        String idMapping = cfg.get(p + "id.mapping");
        if (idMapping == null) {
            throw new IllegalArgumentException("Missing required property " + (p + "id.mapping"));
        }
        idMapping = idMapping.replace("\"", "");

        String geometryMapping = cfg.get(p + "geometry.mapping");
        if (geometryMapping != null) {
            geometryMapping = geometryMapping.replace("\"", "");
        }

        // Probe column types for everything except static and geometry mappings.
        Set<String> probeColumns = new HashSet<>();
        probeColumns.add(idMapping);

        String[] properties = cfg.getMultiple(p + "properties");
        if (properties == null || properties.length == 0 || "*".equals(properties[0])) {
            Set<String> reserved = new HashSet<>();
            reserved.add(idMapping);
            if (geometryMapping != null) {
                reserved.add(geometryMapping);
            }
            properties = discoverProperties(ds, parquetPath, c -> !reserved.contains(c));
        }

        for (String property : properties) {
            String mapping = cfg.get(p + "properties." + property + ".mapping", property).replace("\"", "");
            if (!HakunaConfigParser.isStaticMapping(mapping)
                    && !(geometryMapping != null && mapping.equals(geometryMapping))) {
                probeColumns.add(mapping);
            }
        }

        Map<String, HakunaPropertyType> types = getPropertyTypes(ds, parquetPath, probeColumns);
        Map<String, Boolean> nullability = getNullability(ds, parquetPath, probeColumns);

        // Id property
        HakunaPropertyType idColumnType = types.get(idMapping);
        if (idColumnType == null) {
            throw new IllegalArgumentException("Could not determine type for id mapping " + idMapping);
        }
        boolean idNullable = nullability.getOrDefault(idMapping, Boolean.FALSE);
        HakunaPropertyWriter idWriter = HakunaPropertyWriters.getIdPropertyWriter(ft, ft.getName(), "id", idColumnType);
        HakunaProperty idProperty;
        switch (idColumnType) {
        case INT:
            idProperty = new HakunaPropertyInt("id", table, idMapping, idNullable, true, idWriter);
            break;
        case LONG:
            idProperty = new HakunaPropertyLong("id", table, idMapping, idNullable, true, idWriter);
            break;
        case STRING:
            idProperty = new HakunaPropertyString("id", table, idMapping, idNullable, true, idWriter);
            break;
        case UUID:
            idProperty = new HakunaPropertyUUID("id", table, idMapping, idNullable, true, idWriter);
            break;
        default:
            throw new IllegalArgumentException("Invalid id type " + idColumnType);
        }
        ft.setId(idProperty);

        // Geometry property (config-driven SRID + type)
        if (geometryMapping != null) {
            int srid = sridStorage;
            if (srid <= 0) {
                throw new IllegalArgumentException("Missing required property " + (p + "srid.storage")
                        + " for geometry of collection " + collectionId);
            }
            if (Arrays.stream(srids).noneMatch(it -> it == srid)) {
                throw new IllegalArgumentException(String.format(
                        "Geometry storage srid %d for collection %s is missing from configured srid list!",
                        srid, collectionId));
            }
            String typeName = cfg.get(p + "geometry.type");
            if (typeName == null) {
                throw new IllegalArgumentException("Missing required property " + (p + "geometry.type"));
            }
            HakunaGeometryType geometryType = HakunaGeometryType.valueOf(typeName.toUpperCase());
            int dim = Integer.parseInt(cfg.get(p + "geometry.dim", "2"));
            boolean geomNullable = Boolean.parseBoolean(cfg.get(p + "geometry.nullable", "true"));
            HakunaPropertyWriter geomWriter = HakunaPropertyWriters.getGeometryPropertyWriter("geometry", true);
            HakunaPropertyGeometry geometryProperty = new HakunaPropertyGeometry("geometry", table, geometryMapping,
                    geomNullable, geometryType, srids, srid, dim, geomWriter);
            ft.setGeom(geometryProperty);
        }

        // Other properties
        List<HakunaProperty> hakunaProps = new ArrayList<>();
        for (String property : properties) {
            String propertyPrefix = p + "properties." + property + ".";
            String mapping = cfg.get(propertyPrefix + "mapping", property).replace("\"", "");
            String[] enumeration = cfg.getMultiple(propertyPrefix + "enum");
            String transformerClass = cfg.get(propertyPrefix + "transformer");
            String transformerArg = cfg.get(propertyPrefix + "transformer.arg");
            boolean unique = Boolean.parseBoolean(cfg.get(propertyPrefix + "unique", "false"));
            boolean hidden = Boolean.parseBoolean(cfg.get(propertyPrefix + "hidden", "false"));

            if (HakunaConfigParser.isStaticMapping(mapping)) {
                String value = mapping.substring(1, mapping.length() - 1);
                hakunaProps.add(HakunaPropertyStatic.create(property, table, value));
                continue;
            }

            HakunaPropertyType type = types.get(mapping);
            if (type == null) {
                throw new IllegalArgumentException(
                        "Property " + property + " with mapping " + mapping + " has unknown type");
            }
            boolean nullable = nullability.getOrDefault(mapping, Boolean.TRUE);

            HakunaProperty hakunaProperty = cfg.getDynamicProperty(property, table, mapping,
                    Collections.singletonList(type), nullable, unique, hidden);
            if (transformerClass != null) {
                ValueTransformer transformer = cfg.instantiateTransformer(transformerClass);
                hakunaProperty = new HakunaPropertyTransformed(hakunaProperty, transformer);
                transformer.init(hakunaProperty, transformerArg);
            }
            if (enumeration.length > 0) {
                if (hakunaProperty.getType() == HakunaPropertyType.INT
                        || hakunaProperty.getType() == HakunaPropertyType.LONG) {
                    hakunaProperty = new HakunaPropertyNumberEnum(hakunaProperty,
                            Arrays.stream(enumeration).map(Long::parseLong).collect(Collectors.toSet()));
                } else if (hakunaProperty.getType() == HakunaPropertyType.STRING) {
                    hakunaProperty = new HakunaPropertyStringEnum(hakunaProperty,
                            Arrays.stream(enumeration).collect(Collectors.toSet()));
                }
            }
            hakunaProps.add(hakunaProperty);
        }
        ft.setProperties(hakunaProps);

        ft.setCaseInsensitiveStrategy(getCaseInsensitiveStrategy(cfg, p));
        ft.setSourceShouldProject(Boolean.parseBoolean(cfg.get(p + "sourceproj", "false")));

        return ft;
    }

    private String[] discoverProperties(DuckDBDataSource ds, String parquetPath,
            java.util.function.Predicate<String> check) throws SQLException {
        String sql = "SELECT * FROM read_parquet('" + escape(parquetPath) + "') LIMIT 0";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String column = md.getColumnLabel(i);
                if (check.test(column)) {
                    columns.add(column);
                }
            }
            return columns.toArray(new String[0]);
        }
    }

    private Map<String, HakunaPropertyType> getPropertyTypes(DuckDBDataSource ds, String parquetPath,
            Set<String> columns) throws SQLException {
        Map<String, HakunaPropertyType> result = new LinkedHashMap<>();
        if (columns.isEmpty()) {
            return result;
        }
        String sql = selectColumns(parquetPath, columns);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String label = md.getColumnLabel(i);
                HakunaPropertyType type = fromJDBCType(md.getColumnType(i), md.getColumnTypeName(i));
                if (type == null) {
                    throw new IllegalArgumentException("Unknown type " + md.getColumnTypeName(i) + " column " + label);
                }
                result.put(label, type);
            }
        }
        return result;
    }

    private Map<String, Boolean> getNullability(DuckDBDataSource ds, String parquetPath, Set<String> columns)
            throws SQLException {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (columns.isEmpty()) {
            return result;
        }
        String sql = selectColumns(parquetPath, columns);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                boolean nullable = md.isNullable(i) != ResultSetMetaData.columnNoNulls;
                result.put(md.getColumnLabel(i), nullable);
            }
        }
        return result;
    }

    private static String selectColumns(String parquetPath, Set<String> columns) {
        StringBuilder sb = new StringBuilder("SELECT ");
        boolean first = true;
        for (String col : columns) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(col).append('"');
            first = false;
        }
        sb.append(" FROM read_parquet('").append(escape(parquetPath)).append("') LIMIT 0");
        return sb.toString();
    }

    /**
     * Detects a GeoParquet 1.1 covering bbox struct column. Returns {@code "bbox"} when a struct
     * column with that name exists, else {@code null}.
     */
    private String detectBboxColumn(DuckDBDataSource ds, String parquetPath) {
        String sql = "SELECT column_name, column_type FROM (DESCRIBE SELECT * FROM read_parquet('"
                + escape(parquetPath) + "'))";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                String type = rs.getString(2);
                if ("bbox".equalsIgnoreCase(name) && type != null && type.toUpperCase().startsWith("STRUCT")) {
                    return name;
                }
            }
        } catch (SQLException e) {
            LOG.warn("Failed to detect bbox covering column for {}", parquetPath, e);
        }
        return null;
    }

    private CaseInsensitiveStrategy getCaseInsensitiveStrategy(HakunaConfigParser cfg, String p) {
        String caseiStrategy = cfg.get(p + "casei");
        if (caseiStrategy == null) {
            return CaseInsensitiveStrategy.LOWER;
        }
        return Arrays.stream(CaseInsensitiveStrategy.values())
                .filter(x -> x.name().equalsIgnoreCase(caseiStrategy))
                .findAny()
                .get();
    }

    private static String escape(String path) {
        return path.replace("'", "''");
    }

    public static HakunaPropertyType fromJDBCType(int columnType, String columnTypeName) {
        if (columnTypeName != null) {
            String upper = columnTypeName.toUpperCase();
            if (upper.equals("JSON")) {
                return HakunaPropertyType.JSON;
            }
            if (upper.equals("UUID")) {
                return HakunaPropertyType.UUID;
            }
            if (upper.startsWith("GEOMETRY")) {
                return HakunaPropertyType.GEOMETRY;
            }
        }
        switch (columnType) {
        case java.sql.Types.BIT:
        case java.sql.Types.BOOLEAN:
            return HakunaPropertyType.BOOLEAN;
        case java.sql.Types.TINYINT:
        case java.sql.Types.SMALLINT:
        case java.sql.Types.INTEGER:
            return HakunaPropertyType.INT;
        case java.sql.Types.BIGINT:
            return HakunaPropertyType.LONG;
        case java.sql.Types.REAL:
            return HakunaPropertyType.FLOAT;
        case java.sql.Types.FLOAT:
        case java.sql.Types.DECIMAL:
        case java.sql.Types.DOUBLE:
        case java.sql.Types.NUMERIC:
            return HakunaPropertyType.DOUBLE;
        case java.sql.Types.CHAR:
        case java.sql.Types.NCHAR:
        case java.sql.Types.VARCHAR:
        case java.sql.Types.NVARCHAR:
            return HakunaPropertyType.STRING;
        case java.sql.Types.DATE:
            return HakunaPropertyType.DATE;
        case java.sql.Types.TIMESTAMP:
            return HakunaPropertyType.TIMESTAMP;
        case java.sql.Types.TIMESTAMP_WITH_TIMEZONE:
            return HakunaPropertyType.TIMESTAMPTZ;
        default:
            return null;
        }
    }

}
