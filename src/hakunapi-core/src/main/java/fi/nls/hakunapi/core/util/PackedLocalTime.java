package fi.nls.hakunapi.core.util;

public class PackedLocalTime {
    
    private static final int SHIFT_NANO = 32;
    private static final int SHIFT_HOUR = 12;
    private static final int SHIFT_MINS = 6;
    private static final int SHIFT_SECS = 0;
    private static final int MASK_NANO = (1 << 30) - 1;
    private static final int MASK_HOUR = 31;
    private static final int MASK_MINS = 63;
    private static final int MASK_SECS = 63;
    private static final long CLEAR_NANO = ~(((long) MASK_NANO) << SHIFT_NANO);
    private static final int CLEAR_HOUR = ~(MASK_HOUR << SHIFT_HOUR);
    private static final int CLEAR_MINS = ~(MASK_MINS << SHIFT_MINS);
    private static final int CLEAR_SECS = ~(MASK_SECS << SHIFT_SECS);
    
    public static final int getHour(long time) {
        return (int) ((time >>> SHIFT_HOUR) & MASK_HOUR);
    }
    
    public static final int getMins(long time) {
        return (int) ((time >>> SHIFT_MINS) & MASK_MINS);
    }
    
    public static final int getSecs(long time) {
        return (int) (time & MASK_SECS);
    }
    
    public static final int getNano(long time) {
        return (int) ((time >>> SHIFT_NANO) & MASK_NANO);
    }
    
    public static final long setHour(long time, int hour) {
        return (time & CLEAR_HOUR) | (hour << SHIFT_HOUR);
    }
    
    public static final long setMins(long time, int mins) {
        return (time & CLEAR_MINS) | (mins << SHIFT_MINS);
    }
    
    public static final long setSecs(long time, int secs) {
        return (time & CLEAR_SECS) | secs;
    }
    
    public static final long setNano(long time, int nano) {
        return (time & CLEAR_NANO) | (((long) nano) << SHIFT_NANO);
    }
    
    public static final long of(int hour, int mins, int secs, int nano) {
        return (((long) nano) << SHIFT_NANO) | (hour << SHIFT_HOUR) | (mins << SHIFT_MINS) | secs;    
    }
    
    public static void main(String[] args) {
        long time = 0;
        time = setHour(time, 15);
        time = setMins(time, 12);
        time = setSecs(time, 59);
        time = setNano(time, 33333);
        System.out.println(getHour(time));
        System.out.println(getMins(time));
        System.out.println(getSecs(time));
        System.out.println(getNano(time));

        time = setMins(time, 58);
        time = setSecs(time, 3);
        time = setNano(time, 999999999);
        
        System.out.println(getHour(time));
        System.out.println(getMins(time));
        System.out.println(getSecs(time));
        System.out.println(getNano(time));
        
        time = of(1, 8, 15, 777777777);
        
        System.out.println(getHour(time));
        System.out.println(getMins(time));
        System.out.println(getSecs(time));
        System.out.println(getNano(time));
    }

}
