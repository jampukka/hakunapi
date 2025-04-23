package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.wololo.flatgeobuf.ColumnMeta;
import org.wololo.flatgeobuf.generated.ColumnType;
import org.wololo.flatgeobuf.generated.Feature;
import org.wololo.flatgeobuf.generated.Geometry;
import org.wololo.flatgeobuf.generated.GeometryType;

import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.geom.HakunaGeometryFactory;
import fi.nls.hakunapi.core.geom.HakunaGeometryJTS;

public class FlatgeobufFeatureValueProvider implements ValueProvider {

    private final int geometryType;
    private final Function<ByteBuffer, Object>[] valueExtractors;
    private final Geometry g;
    private final Object[] properties;

    protected FlatgeobufFeatureValueProvider(int geometryType, List<ColumnMeta> columns) {
        this.geometryType = geometryType;
        this.valueExtractors = new Function[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            valueExtractors[i] = valueExtractor(columns.get(i));
        }
        this.g = new Geometry();
        this.properties = new Object[columns.size()];
    }

    protected void setFeature(Feature f) {
        f.geometry(g);
        for (int i = 0; i < properties.length; i++) {
            properties[i] = null;
        }
        if (f.propertiesLength() > 0) {
            ByteBuffer propertiesBuffer = f.propertiesAsByteBuffer();
            while (propertiesBuffer.hasRemaining()) {
                short i = propertiesBuffer.getShort();
                properties[i] = valueExtractors[i].apply(propertiesBuffer);
            }
        }
    }
    
    private static Function<ByteBuffer, Object> valueExtractor(ColumnMeta meta) {
        switch (meta.type) {
        case ColumnType.Bool:
            return bb -> bb.get() > 0;
        case ColumnType.Byte:
            return ByteBuffer::get;
        case ColumnType.Short:
            return ByteBuffer::getShort;
        case ColumnType.Int:
            return ByteBuffer::getInt;
        case ColumnType.Long:
            return ByteBuffer::getLong;
        case ColumnType.Float:
            return ByteBuffer::getFloat;
        case ColumnType.Double:
            return ByteBuffer::getDouble;
        case ColumnType.DateTime:
            return FlatgeobufFeatureValueProvider::readDateTime;
        case ColumnType.String:
            return FlatgeobufFeatureValueProvider::readString;
        default:
            throw new IllegalArgumentException(meta.type + " not yet supported");
        }
    }

    /*
    private Object readValue(ByteBuffer propertiesBuffer, ColumnMeta meta) {
        switch (meta.type) {
        case ColumnType.Bool:
            return propertiesBuffer.get() > 0;
        case ColumnType.Byte:
            return propertiesBuffer.get();
        case ColumnType.Short:
            return propertiesBuffer.getShort();
        case ColumnType.Int:
            return propertiesBuffer.getInt();
        case ColumnType.Long:
            return propertiesBuffer.getLong();
        case ColumnType.Float:
            return propertiesBuffer.getFloat();
        case ColumnType.Double:
            return propertiesBuffer.getDouble();
        case ColumnType.DateTime:
            return readDateTime(propertiesBuffer);
        case ColumnType.String:
            return readString(propertiesBuffer);
        default:
            throw new IllegalArgumentException(meta.type + " not yet supported");
        }
    }
    */

    private static final LocalDateTime readDateTime(ByteBuffer propertiesBuffer) {
        String str = readString(propertiesBuffer);
        return LocalDateTime.parse(str);
    }

    private static final String readString(ByteBuffer propertiesBuffer) {
        int n = propertiesBuffer.getInt();
        byte[] buf = new byte[n];
        propertiesBuffer.get(buf, 0, n);
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }

    @Override
    public int size() {
        return 1 + properties.length;
    }

    @Override
    public boolean isNull(int i) {
        return i == 0 ? g == null : properties[i - 1] == null;
    }

    @Override
    public Boolean getBoolean(int i) {
        return (Boolean) properties[i - 1];
    }

    @Override
    public Integer getInt(int i) {
        return (Integer) properties[i - 1];
    }

    @Override
    public Long getLong(int i) {
        return (Long) properties[i - 1];
    }

    @Override
    public Float getFloat(int i) {
        return (Float) properties[i - 1];
    }

    @Override
    public Double getDouble(int i) {
        return (Double) properties[i - 1];
    }

    @Override
    public String getString(int i) {
        return (String) properties[i - 1];
    }

    @Override
    public Instant getInstant(int i) {
        return null;
    }

    @Override
    public LocalDateTime getLocalDateTime(int i) {
        return (LocalDateTime) properties[i - 1];
    }

    @Override
    public LocalDate getLocalDate(int i) {
        return null;
    }

    @Override
    public HakunaGeometry getHakunaGeometry(int i) {
        if (i != 0) {
            throw new IllegalArgumentException();
        }
        // Rewrite GeometryConversions to something more efficient
        return new HakunaGeometryJTS(deserialize(g, geometryType));
    }

    private static org.locationtech.jts.geom.Geometry deserialize(Geometry geometry, int geometryType) {
        switch (geometryType) {
        case GeometryType.Unknown:
            return null;
        case GeometryType.Point:
            return deserializePoint(geometry);
        case GeometryType.LineString:
            return deserializeLineString(geometry);
        case GeometryType.Polygon:
            return deserializePolygon(geometry);
        case GeometryType.MultiPoint:
            return deserializeMultiPoint(geometry);
        case GeometryType.MultiLineString:
            return deserializeMultiLineString(geometry);
        case GeometryType.MultiPolygon:
            return deserializeMultiPolygon(geometry);
        default:
            throw new RuntimeException("Unknown geometry type");
        }
    }

    private static CoordinateSequence deserializeCoordinateSeq(Geometry g, int off, int len) {
        double[] coords = new double[len];
        for (int i = 0; i < len; i++) {
            coords[i] = g.xy(off + i);
        }
        return new PackedCoordinateSequence.Double(coords, 2, 0);
    }

    private static Point deserializePoint(Geometry g) {
        return HakunaGeometryFactory.GF.createPoint(deserializeCoordinateSeq(g, 0, 2));
    }

    private static LineString deserializeLineString(Geometry g) {
        return HakunaGeometryFactory.GF.createLineString(deserializeCoordinateSeq(g, 0, g.xyLength()));
    }

    private static Polygon deserializePolygon(Geometry g) {
        int endsLength = g.endsLength();

        LinearRing shell;
        if (endsLength == 0) {
            shell = HakunaGeometryFactory.GF.createLinearRing(deserializeCoordinateSeq(g, 0, g.xyLength()));
        } else {
            int e = (int) g.ends(0) * 2;
            shell = HakunaGeometryFactory.GF.createLinearRing(deserializeCoordinateSeq(g, 0, e));
        }

        LinearRing[] holes = null;
        if (endsLength > 1) {
            holes = new LinearRing[endsLength - 1];
            int s = (int) g.ends(0) * 2;
            for (int i = 1; i < endsLength; i++) {
                int e = (int) g.ends(i) * 2;
                holes[i - 1] = HakunaGeometryFactory.GF.createLinearRing(deserializeCoordinateSeq(g, s, e - s));
                s = e;
            }
        }
        return HakunaGeometryFactory.GF.createPolygon(shell, holes);
    }

    private static MultiPoint deserializeMultiPoint(Geometry g) {
        Geometry t = new Geometry();
        int n = g.partsLength();
        Point[] a = new Point[n];
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            a[i] = deserializePoint(t);
        }
        return HakunaGeometryFactory.GF.createMultiPoint(a);
    }

    private static MultiLineString deserializeMultiLineString(Geometry g) {
        Geometry t = new Geometry();
        int n = g.partsLength();
        LineString[] a = new LineString[n];
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            a[i] = deserializeLineString(t);
        }
        return HakunaGeometryFactory.GF.createMultiLineString(a);
    }

    private static MultiPolygon deserializeMultiPolygon(Geometry g) {
        Geometry t = new Geometry();
        int n = g.partsLength();
        Polygon[] a = new Polygon[n];
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            a[i] = deserializePolygon(t);
        }
        return HakunaGeometryFactory.GF.createMultiPolygon(a);
    }


    @Override
    public Object[] getArray(int i) {
        return null;
    }

    @Override
    public UUID getUUID(int i) {
        return null;
    }

    @Override
    public Object getObject(int i) {
        return properties[i - 1];
    }

}
