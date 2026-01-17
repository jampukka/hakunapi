package fi.nls.hakunapi.csv;

import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;

import fi.nls.hakunapi.core.FeatureWriter;
import fi.nls.hakunapi.core.FloatingPointFormatter;
import fi.nls.hakunapi.core.SRIDCode;
import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.core.schemas.Link;
import fi.nls.hakunapi.core.util.DefaultFloatingPointFormatter;
import fi.nls.hakunapi.core.util.FixedFloatingPoint3Formatter;
import fi.nls.hakunapi.core.util.PackedLocalDate;
import fi.nls.hakunapi.core.util.PackedLocalTime;

public abstract class CSVFeatureWriterBase implements FeatureWriter {

    protected static final EnumSet<HakunaPropertyType> ALLOWED_TYPES = EnumSet.of(
            HakunaPropertyType.BOOLEAN,
            HakunaPropertyType.DATE,
            HakunaPropertyType.DOUBLE,
            HakunaPropertyType.FLOAT,
            HakunaPropertyType.INT,
            HakunaPropertyType.LONG,
            HakunaPropertyType.STRING,
            HakunaPropertyType.TIMESTAMP,
            HakunaPropertyType.TIMESTAMPTZ,
            HakunaPropertyType.GEOMETRY,
            HakunaPropertyType.UUID
    );

    protected FloatingPointFormatter formatter; 

    protected CSVWriter csv;
    protected int srid;
    protected StringBuilder strBuf = new StringBuilder(32);

    public void setFormatter(FloatingPointFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public void init(OutputStream out, SRIDCode srid) throws Exception {
        FloatingPointFormatter f = formatter;
        if (f == null) {
            f = srid.isDegrees() ? DefaultFloatingPointFormatter.DEFAULT_DEGREES : FixedFloatingPoint3Formatter.INSTANCE;
        }
        this.csv = new CSVWriter(out, f);
        this.srid = srid.getSrid();
    }

    @Override
    public void close() throws Exception {
        this.csv.close();
    }

    @Override
    public void end(boolean timeStamp, List<Link> links, int numberReturned) throws Exception {
        // NOP
    }

    @Override
    public void writeTimeStamp() throws Exception {
        // NOP
    }

    @Override
    public void writeLinks(List<Link> link) throws Exception {
        // NOP
    }

    @Override
    public void writeNumberReturned(int numberReturned) throws Exception {
        // NOP
    }

    @Override
    public void writeProperty(String name, HakunaGeometry geometry) throws Exception {
        writeGeometry(name, geometry);
    }

    @Override
    public void writeGeometry(String name, HakunaGeometry geometry) throws Exception {
        if (geometry == null) {
            writeNullProperty(name);
        } else {
            csv.writeGeometry(geometry);
        }
    }

    @Override
    public void writeProperty(String name, LocalDate value) throws Exception {
        if (value == null) {
            writeNullProperty(name);
        } else {
            csv.writeLocalDate(value);
        }
    }

    @Override
    public void writeProperty(String name, LocalDateTime value) throws Exception {
        if (value == null) {
            writeNullProperty(name);
        } else {
            csv.writeLocalDateTime(value);
        }
    }
    
    @Override
    public void writeTimestampProperty(String name, int date, long time) throws Exception {
        int y = PackedLocalDate.getYear(date);
        int m = PackedLocalDate.getMonth(date);
        int d = PackedLocalDate.getDay(date);
        int hh = PackedLocalTime.getHour(time);
        int mm = PackedLocalTime.getMins(time);
        int ss = PackedLocalTime.getSecs(time);
        int nano = PackedLocalTime.getNano(time);
        csv.writeLocalDateTime(y, m, d, hh, mm, ss, nano);
    }


    @Override
    public void writeProperty(String name, Instant value) throws Exception {
        if (value == null) {
            writeNullProperty(name);
        } else {
            strBuf.setLength(0);
            DateTimeFormatter.ISO_INSTANT.formatTo(value, strBuf);
            csv.writeASCIIString(strBuf);
        }
    }

    @Override
    public void writeAttribute(String name, String value) throws Exception {
        writeProperty(name, value);
    }

    @Override
    public void writeProperty(String name, String value) throws Exception {
        if (value == null) {
            csv.writeNull();
        } else {
            csv.writeString(value);
        }
    }

    @Override
    public void writeProperty(String name, boolean value) throws Exception {
        csv.writeBoolean(value);
    }

    @Override
    public void writeProperty(String name, int value) throws Exception {
        csv.writeNumber(value);
    }

    @Override
    public void writeProperty(String name, long value) throws Exception {
        csv.writeNumber(value);
    }

    @Override
    public void writeProperty(String name, float value) throws Exception {
        csv.writeNumber(value);
    }

    @Override
    public void writeProperty(String name, double value) throws Exception {
        csv.writeNumber(value);
    }

    @Override
    public void writeNullProperty(String name) throws Exception {
        csv.writeNull();
    }

    @Override
    public void writeStartObject(String name) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeCloseObject() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeStartArray(String name) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeCloseArray() throws Exception {
        throw new UnsupportedOperationException();
    }

}
