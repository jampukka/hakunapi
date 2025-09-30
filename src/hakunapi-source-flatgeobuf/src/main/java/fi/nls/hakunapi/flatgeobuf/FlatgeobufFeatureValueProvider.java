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

public class FlatgeobufFeatureValueProvider implements ValueProvider {

    private final Geometry g;
    private final HakunaGeometryFgb fg;
    private ByteBuffer propertiesBuffer;
    private final byte[] propertyTypes;
    private final int[] propertyOffsets;

    protected FlatgeobufFeatureValueProvider(int geometryType, int srid, List<ColumnMeta> columns) {
        this.propertyTypes = new byte[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            propertyTypes[i] = columns.get(i).type;
        }
        this.g = new Geometry();
        this.fg = new HakunaGeometryFgb(geometryType, srid, g);
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
        return propertiesBuffer.get(propertyOffsets[i - 1]) > 0;
    }

    @Override
    public Integer getInt(int i) {
        return propertiesBuffer.getInt(propertyOffsets[i - 1]);
    }

    @Override
    public Long getLong(int i) {
        return propertiesBuffer.getLong(propertyOffsets[i - 1]);
    }

    @Override
    public Float getFloat(int i) {
        return propertiesBuffer.getFloat(propertyOffsets[i - 1]);
    }

    @Override
    public Double getDouble(int i) {
        return propertiesBuffer.getDouble(propertyOffsets[i - 1]);
    }

    @Override
    public String getString(int i) {
        int off = propertyOffsets[i - 1];
        if (off == 0) {
            return null;
        }
        int n = propertiesBuffer.getInt(off);
        byte[] buf = new byte[n];
        propertiesBuffer.position(off + 4);
        propertiesBuffer.get(buf, 0, n);
        return new String(buf, 0, n, StandardCharsets.UTF_8);
    }

    @Override
    public Instant getInstant(int i) {
        return null;
    }

    @Override
    public LocalDateTime getLocalDateTime(int i) {
        int off = propertyOffsets[i - 1];
        if (off == 0) {
            return null;
        }
        int n = propertiesBuffer.getInt(off);
        return parseLocalDateTime(propertiesBuffer, n, off + 4);
    }

    private static LocalDateTime parseLocalDateTime(ByteBuffer bb, int n, int off) {
        int yy = (bb.get(off +  0) - '0') * 1000 + (bb.get(off +  1) - '0') * 100 + (bb.get(off + 2) - '0') * 10 + (bb.get(off + 3) - '0');
        int mo = (bb.get(off +  5) - '0') *   10 + (bb.get(off +  6) - '0');
        int dd = (bb.get(off +  8) - '0') *   10 + (bb.get(off +  9) - '0');
        int hh = (bb.get(off + 11) - '0') *   10 + (bb.get(off + 12) - '0');
        int mi = (bb.get(off + 14) - '0') *   10 + (bb.get(off + 15) - '0');
        int ss = (bb.get(off + 17) - '0') *   10 + (bb.get(off + 18) - '0');
        int ns = 0;
        int i = 19;
        if (bb.get(off + i) == '.') {
            i++;
            for (; i < n; i++) {
                ns = ns * 10 + (bb.get(off + i) - '0');
            }
        }
        return LocalDateTime.of(yy, mo, dd, hh, mi, ss, ns);
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
