package fi.nls.hakunapi.flatgeobuf;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.HakunaGeometry;

public class ValueProviderFacade implements ValueProvider {
    
    private final ValueProvider provider;
    private final int[] map;
    
    public ValueProviderFacade(ValueProvider provider, int[] map) {
        this.provider = provider;
        this.map = map;
    }

    @Override
    public int size() {
        return map.length;
    }

    @Override
    public boolean isNull(int i) {
        return provider.isNull(map[i]);
    }

    @Override
    public Boolean getBoolean(int i) {
        return provider.getBoolean(map[i]);
    }

    @Override
    public Integer getInt(int i) {
        return provider.getInt(map[i]);
    }

    @Override
    public Long getLong(int i) {
        return provider.getLong(map[i]);
    }

    @Override
    public Float getFloat(int i) {
        return provider.getFloat(map[i]);
    }

    @Override
    public Double getDouble(int i) {
        return provider.getDouble(map[i]);
    }

    @Override
    public String getString(int i) {
        return provider.getString(map[i]);
    }

    @Override
    public Instant getInstant(int i) {
        return provider.getInstant(map[i]);
    }

    @Override
    public LocalDateTime getLocalDateTime(int i) {
        return provider.getLocalDateTime(map[i]);
    }

    @Override
    public LocalDate getLocalDate(int i) {
        return provider.getLocalDate(map[i]);
    }

    @Override
    public HakunaGeometry getHakunaGeometry(int i) {
        return provider.getHakunaGeometry(map[i]);
    }
    
    @Override
    public int getPrimitiveInt(int i) {
        return provider.getPrimitiveInt(map[i]);
    }
    
    @Override
    public int getPrimitiveLocalDateTimeDate(int i) {
        return provider.getPrimitiveLocalDateTimeDate(map[i]);
    }
    
    @Override
    public long getPrimitiveLocalDateTimeTime(int i) {
        return provider.getPrimitiveLocalDateTimeTime(map[i]);
    }

    @Override
    public Object[] getArray(int i) {
        return provider.getArray(map[i]);
    }

    @Override
    public UUID getUUID(int i) {
        return provider.getUUID(map[i]);
    }

    @Override
    public Object getObject(int i) {
        return provider.getObject(map[i]);
    }

}
