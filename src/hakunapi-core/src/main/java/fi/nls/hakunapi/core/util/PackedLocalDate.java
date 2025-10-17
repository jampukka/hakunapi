package fi.nls.hakunapi.core.util;

public class PackedLocalDate {
    
    private static final int SHIFT_YEAR = 9;
    private static final int SHIFT_MONTH = 5;
    private static final int MASK_DAY = 31;
    private static final int MASK_MONTH = 15 << SHIFT_MONTH;
    private static final int MASK_YEAR = 0xFFFFFFFF & ~MASK_MONTH & ~MASK_DAY;
    
    public static final int getYear(int localDate) {
        return localDate >> SHIFT_YEAR;
    }
    
    public static final int getMonth(int localDate) {
        return (localDate & MASK_MONTH) >> SHIFT_MONTH;
    }
    
    public static final int getDay(int localDate) {
        return localDate & MASK_DAY;
    }
    
    public static final int setYear(int localDate, int year) {
        return localDate & ~MASK_YEAR | (year << SHIFT_YEAR);
    }
    
    public static final int setMonth(int localDate, int month) {
        return localDate & ~MASK_MONTH | (month << SHIFT_MONTH);
    }
    
    public static final int setDay(int localDate, int day) {
        return localDate & ~MASK_DAY | day; 
    }
    
    public static final int of(int year, int month, int day) {
        return (year << SHIFT_YEAR) | (month << SHIFT_MONTH) | day;
    }
    
    public static void main(String[] args) {
        int localDate = 0;
        localDate = setMonth(localDate, 12);
        localDate = setYear(localDate, -999999);
        localDate = setDay(localDate, 31);
        System.out.println(getYear(localDate));
        System.out.println(getMonth(localDate));
        System.out.println(getDay(localDate));
        
        localDate = of(1987, 12, 31);
        System.out.println(getYear(localDate));
        System.out.println(getMonth(localDate));
        System.out.println(getDay(localDate));
    }

}
