package fi.nls.hakunapi.tiles.source.vectortile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import fi.nls.hakunapi.core.DatetimeProperty;
import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.OrderBy;
import fi.nls.hakunapi.core.PaginationStrategy;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.geom.HakunaGeometryJTS;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;
import fi.nls.hakunapi.core.param.GetFeatureParam;
import fi.nls.hakunapi.core.projection.ProjectionTransformerFactory;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import fi.nls.hakunapi.core.schemas.Crs;
import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtDecoder;
import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtDecoder.Layer;

public class JavaVectorTileGeneratorTest {

    private static final int SRID = 3857;
    private static final int EXTENT = 4096;
    private static final GeometryFactory GF = new GeometryFactory();

    // Tile world bbox [0,1000] x [0,1000].
    private TileContext ctx() {
        double span = 1000.0;
        return new TileContext(SRID, 0, 0, span, span, EXTENT, EXTENT, span / EXTENT);
    }

    @Test
    public void encodesPolygonLayerWithAttributes() throws Exception {
        Geometry poly = GF.createPolygon(new Coordinate[] {
                new Coordinate(250, 250), new Coordinate(750, 250),
                new Coordinate(750, 750), new Coordinate(250, 750),
                new Coordinate(250, 250)});
        StubFeatureType ft = new StubFeatureType("buildings", HakunaGeometryType.POLYGON,
                Arrays.asList(prop("name", HakunaPropertyType.STRING),
                              prop("height", HakunaPropertyType.DOUBLE)));
        ft.addFeature(7L, new HakunaGeometryJTS(poly), "Town Hall", 42.0);

        byte[] tile = generate(ft);
        List<Layer> layers = MvtDecoder.decodeTile(tile);
        assertEquals(1, layers.size());
        Layer layer = layers.get(0);
        assertEquals("buildings", layer.name);
        assertEquals(EXTENT, layer.extent);
        assertEquals(1, layer.features.size());
        assertEquals(7, layer.features.get(0).id);
        assertEquals(3, layer.features.get(0).type); // polygon
        assertTrue(layer.keys.contains("name"));
        assertTrue(layer.keys.contains("height"));
        assertTrue(layer.values.contains("Town Hall"));
        assertTrue(layer.values.contains(42.0));
    }

    @Test
    public void encodesEveryAttributeType() throws Exception {
        // One property per HakunaPropertyType the encoder handles: each is read
        // through its own typed getter, so a case reading the wrong one - or
        // falling through to the stringify default - shows up here.
        Geometry poly = GF.createPolygon(new Coordinate[] {
                new Coordinate(250, 250), new Coordinate(750, 250),
                new Coordinate(750, 750), new Coordinate(250, 750),
                new Coordinate(250, 250)});
        StubFeatureType ft = new StubFeatureType("mixed", HakunaGeometryType.POLYGON,
                Arrays.asList(prop("s", HakunaPropertyType.STRING),
                              prop("b", HakunaPropertyType.BOOLEAN),
                              prop("i", HakunaPropertyType.INT),
                              prop("l", HakunaPropertyType.LONG),
                              prop("f", HakunaPropertyType.FLOAT),
                              prop("d", HakunaPropertyType.DOUBLE)));
        ft.addFeature(1L, new HakunaGeometryJTS(poly),
                "text", Boolean.TRUE, Integer.valueOf(696), Long.valueOf(9_000_000_000L),
                Float.valueOf(1.5f), Double.valueOf(2.5));

        Layer layer = MvtDecoder.decodeTile(generate(ft)).get(0);
        assertEquals(1, layer.features.size());
        for (String key : new String[] {"s", "b", "i", "l", "f", "d"}) {
            assertTrue("key " + key, layer.keys.contains(key));
        }
        assertTrue("string", layer.values.contains("text"));
        assertTrue("boolean", layer.values.contains(Boolean.TRUE));
        assertTrue("int as long", layer.values.contains(696L));
        assertTrue("long", layer.values.contains(9_000_000_000L));
        assertTrue("float", layer.values.contains(1.5f));
        assertTrue("double", layer.values.contains(2.5));
    }

    @Test
    public void nullAttributesAreOmitted() throws Exception {
        Geometry poly = GF.createPolygon(new Coordinate[] {
                new Coordinate(250, 250), new Coordinate(750, 250),
                new Coordinate(750, 750), new Coordinate(250, 750),
                new Coordinate(250, 250)});
        StubFeatureType ft = new StubFeatureType("nulls", HakunaGeometryType.POLYGON,
                Arrays.asList(prop("present", HakunaPropertyType.INT),
                              prop("absent", HakunaPropertyType.INT)));
        ft.addFeature(1L, new HakunaGeometryJTS(poly), Integer.valueOf(5), null);

        Layer layer = MvtDecoder.decodeTile(generate(ft)).get(0);
        assertEquals(1, layer.features.size());
        // A null is not tagged at all, so the only value in the layer is the
        // present one - no placeholder, and no stringified "null".
        assertEquals(1, layer.values.size());
        assertTrue(layer.values.contains(5L));
    }

    @Test
    public void featureOutsideTileDropped() throws Exception {
        Geometry poly = GF.createPolygon(new Coordinate[] {
                new Coordinate(2000, 2000), new Coordinate(3000, 2000),
                new Coordinate(3000, 3000), new Coordinate(2000, 3000),
                new Coordinate(2000, 2000)});
        StubFeatureType ft = new StubFeatureType("b", HakunaGeometryType.POLYGON,
                Collections.emptyList());
        ft.addFeature(1L, new HakunaGeometryJTS(poly));

        Layer layer = MvtDecoder.decodeTile(generate(ft)).get(0);
        assertEquals(0, layer.features.size());
    }

    @Test
    public void pointAndLineDispatchByType() throws Exception {
        Geometry pt = GF.createPoint(new Coordinate(500, 500));
        Geometry ln = GF.createLineString(new Coordinate[] {
                new Coordinate(100, 100), new Coordinate(900, 900)});
        StubFeatureType ft = new StubFeatureType("mix", HakunaGeometryType.GEOMETRY,
                Collections.emptyList());
        ft.addFeature(1L, new HakunaGeometryJTS(pt));
        ft.addFeature(2L, new HakunaGeometryJTS(ln));

        Layer layer = MvtDecoder.decodeTile(generate(ft)).get(0);
        assertEquals(2, layer.features.size());
        // Feature order on the wire is not significant in MVT (and our backward
        // encoder reverses emission order), so assert dispatch by feature id.
        Map<Long, Integer> typeById = new java.util.HashMap<>();
        layer.features.forEach(f -> typeById.put(f.id, f.type));
        assertEquals(Integer.valueOf(1), typeById.get(1L)); // point -> GEOM_POINT
        assertEquals(Integer.valueOf(2), typeById.get(2L)); // line  -> GEOM_LINE
    }

    /**
     * generate() must not query anything: the body does the work at write time,
     * one layer at a time. Without this a dataset tile of N collections runs N
     * queries and buffers N layers before a byte reaches the client.
     */
    @Test
    public void testGenerateIsLazy() throws Exception {
        StubFeatureType ft = new StubFeatureType("lazy", HakunaGeometryType.POLYGON,
                Collections.emptyList());
        ft.addFeature(1L, new HakunaGeometryJTS(square(100, 100, 900, 900)));

        Optional<fi.nls.hakunapi.tiles.TileBody> body = new JavaVectorTileGenerator().generate(
                ctx(), Collections.singletonList((FeatureType) ft),
                "application/vnd.mapbox-vector-tile", Collections.emptyMap());
        assertEquals(0, ft.queries);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        body.get().writeTo(out);
        assertEquals(1, ft.queries);
    }

    /**
     * Several collections become several layers in one tile, each named after its
     * collection - a dataset tile. All of them are encoded through one reused
     * encoder, so this also covers that reset() leaves no state behind.
     */
    @Test
    public void testManyCollectionsBecomeManyLayers() throws Exception {
        StubFeatureType a = new StubFeatureType("first", HakunaGeometryType.POLYGON,
                Collections.singletonList(prop("only_a", HakunaPropertyType.STRING)));
        a.addFeature(1L, new HakunaGeometryJTS(square(100, 100, 500, 500)), "x");
        StubFeatureType b = new StubFeatureType("second", HakunaGeometryType.POLYGON,
                Collections.singletonList(prop("only_b", HakunaPropertyType.INT)));
        b.addFeature(2L, new HakunaGeometryJTS(square(600, 600, 900, 900)), 7);

        List<FeatureType> both = new ArrayList<>();
        both.add(a);
        both.add(b);
        Optional<fi.nls.hakunapi.tiles.TileBody> body = new JavaVectorTileGenerator().generate(
                ctx(), both, "application/vnd.mapbox-vector-tile", Collections.emptyMap());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        body.get().writeTo(out);

        List<Layer> layers = MvtDecoder.decodeTile(out.toByteArray());
        assertEquals(2, layers.size());
        assertEquals("first", layers.get(0).name);
        assertEquals("second", layers.get(1).name);
        assertEquals(1, layers.get(0).features.size());
        assertEquals(1, layers.get(1).features.size());
        // Keys are per layer: the reused encoder must not leak the first layer's
        // attribute names into the second.
        assertTrue(layers.get(0).keys.contains("only_a"));
        assertTrue(!layers.get(0).keys.contains("only_b"));
        assertTrue(layers.get(1).keys.contains("only_b"));
        assertTrue(!layers.get(1).keys.contains("only_a"));
    }

    /**
     * MVT has no date type, so a DATE goes out as its ISO-8601 string. Every MTK
     * table carries one, and a value container's getString casts to String, so
     * reading one that way threw and no tile was produced at all.
     */
    @Test
    public void testDateAttributeIsStringified() throws Exception {
        StubFeatureType ft = new StubFeatureType("dates", HakunaGeometryType.POLYGON,
                Collections.singletonList(prop("alkupvm", HakunaPropertyType.DATE)));
        ft.addFeature(1L, new HakunaGeometryJTS(square(100, 100, 900, 900)),
                java.time.LocalDate.of(2026, 9, 2));

        Layer layer = MvtDecoder.decodeTile(generate(ft)).get(0);
        assertEquals(1, layer.features.size());
        assertTrue(layer.keys.contains("alkupvm"));
        assertTrue(layer.values.contains("2026-09-02"));
    }

    /** An axis-aligned square ring, closed. */
    private static Geometry square(double minX, double minY, double maxX, double maxY) {
        return GF.createPolygon(new Coordinate[] {
                new Coordinate(minX, minY), new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY), new Coordinate(minX, maxY),
                new Coordinate(minX, minY)});
    }

    private byte[] generate(FeatureType ft) throws Exception {
        Optional<fi.nls.hakunapi.tiles.TileBody> r = new JavaVectorTileGenerator().generate(
                ctx(), Collections.singletonList(ft), "application/vnd.mapbox-vector-tile",
                Collections.emptyMap());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        r.get().writeTo(out);
        return out.toByteArray();
    }

    // --- stubs -----------------------------------------------------------------

    private static final fi.nls.hakunapi.core.property.HakunaPropertyWriter NOOP_WRITER =
            new fi.nls.hakunapi.core.property.HakunaPropertyWriter() {
                public void write(ValueProvider p, int i, fi.nls.hakunapi.core.FeatureWriter w) {}
            };

    private static HakunaProperty prop(String name, HakunaPropertyType type) {
        return new StubProperty(name, type);
    }

    private static final class Row implements ValueProvider {
        final Object[] vals; // [id, geom, attrs...]
        Row(Object[] vals) { this.vals = vals; }
        public int size() { return vals.length; }
        public boolean isNull(int i) { return vals[i] == null; }
        public Boolean getBoolean(int i) { return (Boolean) vals[i]; }
        public Integer getInt(int i) { return vals[i] instanceof Integer ? (Integer) vals[i] : null; }
        public Long getLong(int i) {
            if (vals[i] instanceof Long) return (Long) vals[i];
            if (vals[i] instanceof Integer) return ((Integer) vals[i]).longValue();
            return null;
        }
        public Float getFloat(int i) { return (Float) vals[i]; }
        public Double getDouble(int i) { return (Double) vals[i]; }
        public String getString(int i) { return vals[i] == null ? null : vals[i].toString(); }
        public Instant getInstant(int i) { return (Instant) vals[i]; }
        public java.time.LocalDateTime getLocalDateTime(int i) {
            return vals[i] instanceof java.time.LocalDateTime ? (java.time.LocalDateTime) vals[i] : null;
        }
        public java.time.LocalDate getLocalDate(int i) {
            return vals[i] instanceof java.time.LocalDate ? (java.time.LocalDate) vals[i] : null;
        }
        public HakunaGeometry getHakunaGeometry(int i) { return (HakunaGeometry) vals[i]; }
        public Object[] getArray(int i) { return null; }
        public java.util.UUID getUUID(int i) { return null; }
        public Object getObject(int i) { return vals[i]; }
    }

    private static final class StubStream implements FeatureStream {
        final Iterator<Row> it;
        StubStream(List<Row> rows) { this.it = rows.iterator(); }
        public boolean hasNext() { return it.hasNext(); }
        public ValueProvider next() { return it.next(); }
        public void close() {}
    }

    private static final class StubProperty implements HakunaProperty {
        final String name; final HakunaPropertyType type;
        StubProperty(String name, HakunaPropertyType type) { this.name = name; this.type = type; }
        public String getName() { return name; }
        public String getTable() { return null; }
        public String getColumn() { return name; }
        public HakunaPropertyType getType() { return type; }
        public boolean nullable() { return true; }
        public boolean unique() { return false; }
        public io.swagger.v3.oas.models.media.Schema<?> getSchema() { return null; }
        public boolean isStatic() { return false; }
        public FeatureType getFeatureType() { return null; }
        public void setFeatureType(FeatureType ft) {}
        public Object getValue(ValueProvider p, int i) { return null; }
        public int getDimension() { return 0; }
        public fi.nls.hakunapi.core.property.HakunaPropertyWriter getPropertyWriter() { return null; }
        public fi.nls.hakunapi.core.ValueMapper getMapper(
                Map<fi.nls.hakunapi.core.util.StringPair, Integer> columnToIndex,
                int out, fi.nls.hakunapi.core.QueryContext ctx) { return null; }
        public Object toInner(String value) { return value; }
    }

    private static final class StubFeatureType implements FeatureType {
        final String name;
        final HakunaPropertyGeometry geom;
        final HakunaProperty id;
        final List<HakunaProperty> props;
        final List<Row> rows = new ArrayList<>();
        /** How many times the producer has been asked for features. */
        int queries;

        StubFeatureType(String name, HakunaGeometryType geomType, List<HakunaProperty> attrs) {
            this.name = name;
            this.id = new StubProperty("id", HakunaPropertyType.LONG);
            this.geom = new HakunaPropertyGeometry("geom", "t", "geom", true,
                    geomType, new int[] {SRID}, SRID, 2, NOOP_WRITER);
            this.props = new ArrayList<>();
            this.props.add(this.geom);
            this.props.addAll(attrs);
        }

        void addFeature(Long fid, HakunaGeometry g, Object... attrs) {
            // Mirror GetFeatureCollection.getProperties() layout exactly:
            //   getPropertiesBase = [id, geom]  (no orderBy here)
            //   + ft.getProperties() = [geom, attrs...]
            // => ValueProvider slots = [id, geom, geom, attrs...]. The generator
            //    must dedupe the repeated geom slot; this row exercises that.
            Object[] vals = new Object[3 + attrs.length];
            vals[0] = fid;       // id slot
            vals[1] = g;         // base geom slot
            vals[2] = g;         // ft.getProperties() geom slot (duplicate)
            System.arraycopy(attrs, 0, vals, 3, attrs.length);
            rows.add(new Row(vals));
        }

        public String getName() { return name; }
        public String getNS() { return null; }
        public String getSchemaLocation() { return null; }
        public String getTitle() { return name; }
        public String getDescription() { return null; }
        public Map<String, Object> getMetadata() { return Collections.emptyMap(); }
        public HakunaProperty getId() { return id; }
        public HakunaPropertyGeometry getGeom() { return geom; }
        public List<HakunaProperty> getProperties() { return props; }
        public List<HakunaProperty> getQueryableProperties() { return props; }
        public List<DatetimeProperty> getDatetimeProperties() { return Collections.emptyList(); }
        public double[] getSpatialExtent() { return null; }
        public Instant[] getTemporalExtent() { return null; }
        public List<GetFeatureParam> getParameters() { return Collections.emptyList(); }
        public List<Filter> getStaticFilters() { return Collections.emptyList(); }
        public ProjectionTransformerFactory getProjectionTransformerFactory() { return null; }
        public PaginationStrategy getPaginationStrategy() { return null; }
        public List<OrderBy> getDefaultOrderBy() { return Collections.emptyList(); }
        public FeatureProducer getFeatureProducer() {
            return new FeatureProducer() {
                public int getNumberMatched(GetFeatureRequest r, GetFeatureCollection c) { return rows.size(); }
                public FeatureStream getFeatures(GetFeatureRequest r, GetFeatureCollection c) {
                    queries++;
                    return new StubStream(rows);
                }
            };
        }
    }
}
