package fi.nls.hakunapi.simple.duckdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Properties;

import org.junit.Test;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;

public class DuckDBSimpleSourceTest {

    private static final int[] SRIDS = { 4326 };

    /**
     * Writes a GeoParquet-ish file (geometry stored as WKB blob + a covering bbox struct) using
     * DuckDB itself, then parses and inspects the resulting feature type.
     */
    @Test
    public void parsesGeoParquetWithBboxCovering() throws Exception {
        Path parquet = writeParquet(true);
        try (DuckDBSimpleSource source = new DuckDBSimpleSource()) {
            Properties props = new Properties();
            props.put("collections", "roads");
            props.put("collections.roads.table", parquet.toString().replace('\\', '/'));
            props.put("collections.roads.id.mapping", "id");
            props.put("collections.roads.geometry.mapping", "geom");
            props.put("collections.roads.geometry.type", "POINT");
            props.put("collections.roads.srid.storage", "4326");
            props.put("collections.roads.properties", "name");

            HakunaConfigParser cfg = new HakunaConfigParser(props);
            SQLFeatureType ft = (SQLFeatureType) source.parse(cfg, parquet, "roads", SRIDS);

            assertEquals(HakunaPropertyType.LONG, ft.getId().getType());

            HakunaPropertyGeometry geom = ft.getGeom();
            assertNotNull(geom);
            assertEquals(HakunaGeometryType.POINT, geom.getGeometryType());
            assertEquals(4326, geom.getStorageSRID());

            assertEquals(1, ft.getProperties().size());
            HakunaProperty name = ft.getProperties().get(0);
            assertEquals("name", name.getName());
            assertEquals(HakunaPropertyType.STRING, name.getType());

            assertEquals("bbox", ft.getBboxColumn());
        }
    }

    @Test
    public void noBboxColumnDetectedWhenAbsent() throws Exception {
        Path parquet = writeParquet(false);
        try (DuckDBSimpleSource source = new DuckDBSimpleSource()) {
            Properties props = new Properties();
            props.put("collections", "roads");
            props.put("collections.roads.table", parquet.toString().replace('\\', '/'));
            props.put("collections.roads.id.mapping", "id");
            props.put("collections.roads.geometry.mapping", "geom");
            props.put("collections.roads.geometry.type", "POINT");
            props.put("collections.roads.srid.storage", "4326");
            props.put("collections.roads.properties", "name");

            HakunaConfigParser cfg = new HakunaConfigParser(props);
            SQLFeatureType ft = (SQLFeatureType) source.parse(cfg, parquet, "roads", SRIDS);

            assertNull(ft.getBboxColumn());
        }
    }

    @Test
    public void discoversPropertiesWithWildcard() throws Exception {
        Path parquet = writeParquet(false);
        try (DuckDBSimpleSource source = new DuckDBSimpleSource()) {
            Properties props = new Properties();
            props.put("collections", "roads");
            props.put("collections.roads.table", parquet.toString().replace('\\', '/'));
            props.put("collections.roads.id.mapping", "id");
            props.put("collections.roads.geometry.mapping", "geom");
            props.put("collections.roads.geometry.type", "POINT");
            props.put("collections.roads.srid.storage", "4326");
            props.put("collections.roads.properties", "*");

            HakunaConfigParser cfg = new HakunaConfigParser(props);
            SQLFeatureType ft = (SQLFeatureType) source.parse(cfg, parquet, "roads", SRIDS);

            // id + geom reserved, only "name" remains
            assertEquals(1, ft.getProperties().size());
            assertEquals("name", ft.getProperties().get(0).getName());
        }
    }

    private static Path writeParquet(boolean withBbox) throws Exception {
        Path dir = Files.createTempDirectory("duckdb-test");
        Path parquet = dir.resolve("roads.parquet");
        parquet.toFile().deleteOnExit();
        dir.toFile().deleteOnExit();

        String bboxSelect = withBbox
                ? ", {'xmin': ST_XMin(g), 'ymin': ST_YMin(g), 'xmax': ST_XMax(g), 'ymax': ST_YMax(g)} AS bbox"
                : "";

        try (Connection c = java.sql.DriverManager.getConnection("jdbc:duckdb:");
                Statement st = c.createStatement()) {
            st.execute("INSTALL spatial");
            st.execute("LOAD spatial");
            st.execute(
                "COPY (SELECT id, name, ST_AsWKB(g) AS geom" + bboxSelect + " FROM (VALUES " +
                "  (1::BIGINT, 'a', ST_Point(1, 2))," +
                "  (2::BIGINT, 'b', ST_Point(3, 4))" +
                ") AS t(id, name, g)) TO '" + parquet.toString().replace('\\', '/') +
                "' (FORMAT parquet)");
        }
        return parquet;
    }

}
