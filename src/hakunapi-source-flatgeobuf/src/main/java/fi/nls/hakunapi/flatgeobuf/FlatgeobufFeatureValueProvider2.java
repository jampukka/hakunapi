package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.wololo.flatgeobuf.ColumnMeta;
import org.wololo.flatgeobuf.generated.ColumnType;
import org.wololo.flatgeobuf.generated.Feature;
import org.wololo.flatgeobuf.generated.Geometry;

import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.HakunaGeometry;

public class FlatgeobufFeatureValueProvider2 implements ValueProvider {

    private final Geometry g;
    private final HakunaGeometryFGB fg;
    private ByteBuffer propertiesBuffer;
    private final byte[] propertyTypes;
    private final int[] propertyOffsets;

    protected FlatgeobufFeatureValueProvider2(int geometryType, int srid, List<ColumnMeta> columns) {
        this.propertyTypes = new byte[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            propertyTypes[i] = columns.get(i).type;
        }
        this.g = new Geometry();
        this.fg = new HakunaGeometryFGB(geometryType, srid, g);
        this.propertyOffsets = new int[columns.size()];
    }

    protected void setFeature(Feature f) {
        f.geometry(g);
        Arrays.fill(propertyOffsets, 0);
        if (f.propertiesLength() > 0) {
            propertiesBuffer = f.propertiesAsByteBuffer();
            while (propertiesBuffer.hasRemaining()) {
                short i = propertiesBuffer.getShort();
                propertyOffsets[i] = propertiesBuffer.position();
                skip(i, propertiesBuffer);
            }
        }
    }

    @Override
    public int size() {
        return 1 + propertyOffsets.length;
    }

    @Override
    public boolean isNull(int i) {
        return i == 0 ? g == null : propertyOffsets[i - 1] == 0;
    }

    @Override
    public Boolean getBoolean(int i) {
        return isNull(i) ? null : propertiesBuffer.get(propertyOffsets[i - 1]) > 0;
    }

    @Override
    public Integer getInt(int i) {
        return isNull(i) ? null : propertiesBuffer.getInt(propertyOffsets[i - 1]);
    }

    @Override
    public Long getLong(int i) {
        return isNull(i) ? null : propertiesBuffer.getLong(propertyOffsets[i - 1]);
    }

    @Override
    public Float getFloat(int i) {
        return isNull(i) ? null : propertiesBuffer.getFloat(propertyOffsets[i - 1]);
    }

    @Override
    public Double getDouble(int i) {
        return isNull(i) ? null : propertiesBuffer.getDouble(propertyOffsets[i - 1]);
    }

    @Override
    public String getString(int i) {
        if (isNull(i)) {
            return null;
        }
        int off = propertyOffsets[i - 1];
        int n = propertiesBuffer.getInt(off);
        byte[] buf = new byte[n];
        propertiesBuffer.get(off + 4, buf, 0, n);
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }

    @Override
    public Instant getInstant(int i) {
        return null;
    }

    @Override
    public LocalDateTime getLocalDateTime(int i) {
        String s = getString(i);
        if (s == null) {
            return null;
        }
        return LocalDateTime.parse(s);
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
        switch (propertyTypes[i]) {
        case ColumnType.Bool:
            return getBoolean(i);
        case ColumnType.Int:
            return getInt(i);
        case ColumnType.Long:
            return getLong(i);
        case ColumnType.Float:
            return getFloat(i);
        case ColumnType.Double:
            return getDouble(i);
        case ColumnType.DateTime:
            return getLocalDateTime(i);
        case ColumnType.String:
            return getString(i);
        default:
            throw new IllegalArgumentException(propertyTypes[i] + " not yet supported");
        }
    }
    
    private void skip(int i, ByteBuffer bb) {
        switch (propertyTypes[i]) {
        case ColumnType.Bool:
            bb.position(bb.position() + 1);
            break;
        case ColumnType.Int:
        case ColumnType.Float:
            bb.position(bb.position() + 4);
            break;
        case ColumnType.Long:
        case ColumnType.Double:
            bb.position(bb.position() + 8);
            break;
        case ColumnType.DateTime:
        case ColumnType.String:
            int n = bb.getInt();
            bb.position(bb.position() + n);
            break;
        default:
            throw new IllegalArgumentException(propertyTypes[i] + " not yet supported");
        }
    }

}
