package fi.nls.hakunapi.core;

import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.schemas.Link;
import fi.nls.hakunapi.core.util.PackedLocalDate;
import fi.nls.hakunapi.core.util.PackedLocalTime;

public interface FeatureWriter extends AutoCloseable {

    public void init(OutputStream out, SRIDCode srid) throws Exception;
    
    public void end(boolean timeStamp, List<Link> links, int numberReturned) throws Exception;

    public void writeTimeStamp() throws Exception;
    public void writeLinks(List<Link> link) throws Exception;
    public void writeNumberReturned(int numberReturned) throws Exception;
    public default void writeMetadata(String key, Map<String, Object> metadata) throws Exception {};

    public void writeAttribute(String name, String value) throws Exception;

    public void writeGeometry(String name, HakunaGeometry geometry) throws Exception;
    public void writeProperty(String name, HakunaGeometry geometry) throws Exception;
    public void writeProperty(String name, String value) throws Exception;
    public void writeProperty(String name, LocalDate value) throws Exception;
    public void writeProperty(String name, LocalDateTime value) throws Exception;
    public void writeProperty(String name, Instant value) throws Exception;
    public void writeProperty(String name, boolean value) throws Exception;
    public void writeProperty(String name, int value) throws Exception;
    public void writeProperty(String name, long value) throws Exception;
    public void writeProperty(String name, float value) throws Exception;
    public void writeProperty(String name, double value) throws Exception;
    public void writeNullProperty(String name) throws Exception;

    public default void writeTimestampProperty(String name, int date, long time) throws Exception {
        int y = PackedLocalDate.getYear(date);
        int m = PackedLocalDate.getMonth(date);
        int d = PackedLocalDate.getDay(date);
        int hh = PackedLocalTime.getHour(time);
        int mm = PackedLocalTime.getMins(time);
        int ss = PackedLocalTime.getSecs(time);
        int nano = PackedLocalTime.getNano(time);
        writeProperty(name, LocalDateTime.of(y, m, d, hh, mm, ss, nano));
    }

    public void writeStartObject(String name) throws Exception;
    public void writeCloseObject() throws Exception;

    public void writeStartArray(String name) throws Exception;
    public void writeCloseArray() throws Exception;
    public default void  writeJsonProperty(String name, byte[] bytes) throws Exception {};
}
