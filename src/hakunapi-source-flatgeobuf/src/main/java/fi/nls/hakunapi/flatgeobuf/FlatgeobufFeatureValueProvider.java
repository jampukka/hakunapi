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
import org.wololo.flatgeobuf.generated.GeometryType;

import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.util.PackedLocalDate;
import fi.nls.hakunapi.core.util.PackedLocalTime;
import fi.nls.hakunapi.flatgeobuf.geometry.HakunaPolygonGeometryFgb;

public class FlatgeobufFeatureValueProvider implements ValueProvider {

    private final Feature f;
    private final Geometry g;
    private final HakunaGeometry fg;
    private final byte[] propertyTypes;
    private final int[] propertyOffsets;
    
    private ByteBuffer bb; 

    protected FlatgeobufFeatureValueProvider(int geometryType, int srid, List<ColumnMeta> columns) {
        this.propertyTypes = new byte[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            propertyTypes[i] = columns.get(i).type;
        }
        this.f = new Feature();
        this.g = new Geometry();
        this.fg = initHakunapiGeometry(geometryType, srid, g);
        this.propertyOffsets = new int[columns.size()];
    }
    
    private static HakunaGeometry initHakunapiGeometry(int geometryType, int srid, Geometry g) {
        switch (geometryType) {
        case GeometryType.Polygon:
            return new HakunaPolygonGeometryFgb(srid, g);
        /*
        case GeometryType.Point:
            writePoint(writer, true);
            break;
        case GeometryType.LineString:
            writeLineString(writer, true);
            break;
        case GeometryType.MultiPoint:
            writeMultiPoint(g, srid, writer, true);
            break;
        case GeometryType.MultiLineString:
            writeMultiLineString(g, srid, writer, true);
            break;
        case GeometryType.MultiPolygon:
            writeMultiPolygon(g, srid, writer, true);
            break;
            */
        default:
            throw new RuntimeException("Unknown geometry type");
        }
    }

    protected void setFeature(ByteBuffer bb, int position) {
        this.bb = bb;

        int bb_pos = position + bb.getInt(position);
        f.__init(bb_pos, bb);
        
        int vtable_start = bb_pos - bb.getInt(bb_pos);

        f.geometry(g);
        
        Arrays.fill(propertyOffsets, 0);
        int op = bb.getShort(vtable_start + 6);
        if (op == 0) return;
        op += bb_pos;
        int propertiesPtr = op + bb.getInt(op);
        int propertiesEnd = op + bb.getInt(propertiesPtr) + 4;
        propertiesPtr += 4;

        while (propertiesPtr < propertiesEnd) {
            short i = bb.getShort(propertiesPtr);
            propertiesPtr += Short.BYTES;
            propertyOffsets[i] = propertiesPtr;
            propertiesPtr += byteLength(propertyTypes[i], bb, propertiesPtr);
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
        return bb.get(propertyOffsets[i - 1]) > 0;
    }

    @Override
    public Integer getInt(int i) {
        return bb.getInt(propertyOffsets[i - 1]);
    }

    @Override
    public Long getLong(int i) {
        return bb.getLong(propertyOffsets[i - 1]);
    }

    @Override
    public Float getFloat(int i) {
        return bb.getFloat(propertyOffsets[i - 1]);
    }

    @Override
    public Double getDouble(int i) {
        return bb.getDouble(propertyOffsets[i - 1]);
    }
    
    @Override
    public int getPrimitiveInt(int i) {
        return bb.getInt(propertyOffsets[i - 1]);
    }
    
    @Override
    public int getPrimitiveLocalDateTimeDate(int i) {
        int off = propertyOffsets[i - 1];
        if (off == 0) {
            return 0;
        }
        off += 4;
        int yy = (bb.get(off +  0) - '0') * 1000 + (bb.get(off +  1) - '0') * 100 + (bb.get(off + 2) - '0') * 10 + (bb.get(off + 3) - '0');
        int mo = (bb.get(off +  5) - '0') *   10 + (bb.get(off +  6) - '0');
        int dd = (bb.get(off +  8) - '0') *   10 + (bb.get(off +  9) - '0');
        return PackedLocalDate.of(yy, mo, dd);
    }
    
    @Override
    public long getPrimitiveLocalDateTimeTime(int i) {
        int off = propertyOffsets[i - 1];
        if (off == 0) {
            return 0L;
        }
        int n = bb.getInt(off);
        off += 4;
        int hh = (bb.get(off + 11) - '0') *   10 + (bb.get(off + 12) - '0');
        int mi = (bb.get(off + 14) - '0') *   10 + (bb.get(off + 15) - '0');
        int ss = (bb.get(off + 17) - '0') *   10 + (bb.get(off + 18) - '0');
        int ns = 0;
        int j = 19;
        if (bb.get(off + j) == '.') {
            j++;
            for (; j < n; j++) {
                ns = ns * 10 + (bb.get(off + j) - '0');
            }
        }
        return PackedLocalTime.of(hh, mi, ss, ns);
    }

    @Override
    public String getString(int i) {
        int off = propertyOffsets[i - 1];
        if (off == 0) {
            return null;
        }
        int n = bb.getInt(off);
        byte[] buf = new byte[n];
        bb.get(off + 4, buf, 0, n);
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
        int n = bb.getInt(off);
        return parseLocalDateTime(bb, n, off + 4);
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
    
    private static int byteLength(final byte columnType, final ByteBuffer bb, int pos) {
        switch (columnType) {
        case ColumnType.Bool:
            return 1;
        case ColumnType.Int:
        case ColumnType.Float:
            return 4;
        case ColumnType.Long:
        case ColumnType.Double:
            return 8;
        case ColumnType.DateTime:
        case ColumnType.String:
            return 4 + bb.getInt(pos);
        default:
            throw new IllegalArgumentException(columnType + " not yet supported");
        }
    }

}
