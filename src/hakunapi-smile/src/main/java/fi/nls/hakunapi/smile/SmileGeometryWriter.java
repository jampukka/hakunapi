package fi.nls.hakunapi.smile;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.SerializableString;

import fi.nls.hakunapi.core.GeometryWriter;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;

public class SmileGeometryWriter implements GeometryWriter {

    private final JsonGenerator json;
    private final SerializableString fieldName;
    private final double[] c;

    public SmileGeometryWriter(JsonGenerator json, SerializableString fieldName) {
        this.json = json;
        this.fieldName = fieldName;
        this.c = new double[4];
    }

    @Override
    public void init(HakunaGeometryType type, int srid, int dimension) throws Exception {
        json.writeName(fieldName);
        json.writeStartObject();
        json.writeName(GeoJSONStrings.TYPE);
        json.writeString(getGeoJSONType(type));
        json.writeName(GeoJSONStrings.COORDINATES);
    }

    private SerializableString getGeoJSONType(HakunaGeometryType type) {
        switch (type) {
        case POINT:
            return GeoJSONStrings.POINT;
        case LINESTRING:
            return GeoJSONStrings.LINESTRING;
        case POLYGON:
            return GeoJSONStrings.POLYGON;
        case MULTIPOINT:
            return GeoJSONStrings.MULTI_POINT;
        case MULTILINESTRING:
            return GeoJSONStrings.MULTI_LINESTRING;
        case MULTIPOLYGON:
            return GeoJSONStrings.MULTI_POLYGON;
        case GEOMETRYCOLLECTION:
            throw new IllegalArgumentException("Geometry collection not yet supported!");
        default:
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void end() throws Exception {
        json.writeEndObject();
    }

    @Override
    public void writeCoordinate(double x, double y) throws Exception {
        c[0] = x;
        c[1] = y;
        json.writeArray(c, 0, 2);
        /*
        json.writeStartArray();
        json.writeNumber(x);
        json.writeNumber(y);
        json.writeEndArray();
        */
    }

    @Override
    public void writeCoordinate(double x, double y, double z) throws Exception {
        c[0] = x;
        c[1] = y;
        c[2] = z;
        json.writeArray(c, 0, 3);
        /*
        json.writeStartArray();
        json.writeNumber(x);
        json.writeNumber(y);
        json.writeNumber(z);
        json.writeEndArray();
        */
    }

    @Override
    public void writeCoordinate(double x, double y, double z, double m) throws Exception {
        c[0] = x;
        c[1] = y;
        c[2] = z;
        c[3] = m;
        json.writeArray(c, 0, 4);
        /*
        json.writeStartArray();
        json.writeNumber(x);
        json.writeNumber(y);
        json.writeNumber(z);
        json.writeNumber(m);
        json.writeEndArray();
        */
    }

    @Override
    public void startRing() throws Exception {
        json.writeStartArray();
    }

    @Override
    public void endRing() throws Exception {
        json.writeEndArray();
    }

}
