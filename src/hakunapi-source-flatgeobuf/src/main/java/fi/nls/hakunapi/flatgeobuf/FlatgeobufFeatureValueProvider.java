package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.wololo.flatgeobuf.ColumnMeta;
import org.wololo.flatgeobuf.generated.ColumnType;
import org.wololo.flatgeobuf.generated.Feature;
import org.wololo.flatgeobuf.generated.Geometry;

import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.HakunaGeometry;

public class FlatgeobufFeatureValueProvider implements ValueProvider {

    private final Function<ByteBuffer, Object>[] valueExtractors;
    private final Geometry g;
    private final HakunaGeometryFGB fg;
    private final Object[] properties;

    protected FlatgeobufFeatureValueProvider(int geometryType, int srid, List<ColumnMeta> columns) {
        this.valueExtractors = new Function[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            valueExtractors[i] = valueExtractor(columns.get(i));
        }
        this.g = new Geometry();
        this.fg = new HakunaGeometryFGB(geometryType, srid, g);
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
        return fg;
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
