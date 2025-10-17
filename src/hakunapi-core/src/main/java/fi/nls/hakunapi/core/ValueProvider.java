package fi.nls.hakunapi.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import fi.nls.hakunapi.core.geom.HakunaGeometry;

public interface ValueProvider {
    
    public int size();
    public boolean isNull(int i);
    public Boolean getBoolean(int i);
    public Integer getInt(int i);
    public Long getLong(int i);
    public Float getFloat(int i);
    public Double getDouble(int i);
    public String getString(int i);
    public Instant getInstant(int i);
    public LocalDateTime getLocalDateTime(int i);
    public LocalDate getLocalDate(int i);
    public HakunaGeometry getHakunaGeometry(int i);
    public Object[] getArray(int i);
    public UUID getUUID(int i);
    public Object getObject(int i);
    public default byte[] getJSON(int i)  { return null; }
    
    public default int getPrimitiveInt(int i) {
        return getInt(i);
    } 

    public default int getPrimitiveLocalDateTimeDate(int i) { return -1; }
    public default long getPrimitiveLocalDateTimeTime(int i) { return -1L; }

}
