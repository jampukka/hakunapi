package fi.nls.hakunapi.core.util;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.core.io.NumberOutput;

public class LocalDateOutput {
    
    // year (10) + month (2) + day (2) + separators (2)
    // 10 characters for year allows for a range of [-999999999, Integer.MAX_VALUE]
    public static final int MAX_BYTE_LEN = 16;
    // hour (2) + minutes (2) + seconds (2) + nanos (9) + separator (3)
    public static final int MAX_BYTE_LEN_TIME = 18;
    
    public static int outputLocalDate(LocalDate value, byte[] buf, int pos) {
        return outputLocalDate(value.getYear(), value.getMonthValue(), value.getDayOfMonth(), buf, pos);
    }
    
    public static int outputLocalDate(int year, int month, int day, byte[] buf, int pos) {
        pos = NumberOutput.outputInt(year, buf, pos);
        buf[pos++] = '-';
        pos = outputTwoDigitInt(month, buf, pos);
        buf[pos++] = '-';
        return outputTwoDigitInt(day, buf, pos);
    }
    
    public static int outputLocalTime(LocalTime value, byte[] buf, int pos) {
        return outputLocalTime(value.getHour(), value.getMinute(), value.getSecond(), value.getNano(), buf, pos);
    }

    public static int outputLocalTime(int hours, int minutes, int seconds, int nanos, byte[] buf, int pos) {
        pos = outputTwoDigitInt(hours, buf, pos);
        buf[pos++] = ':';
        pos = outputTwoDigitInt(minutes, buf, pos);
        buf[pos++] = ':';
        pos = outputTwoDigitInt(seconds, buf, pos);

        if (nanos > 0) {
            int end = NumberOutput.outputInt(nanos + 1_000_000_000, buf, pos);
            buf[pos] = '.';
            pos = end;
        }
        return pos;
    }
    
    private static int outputTwoDigitInt(int v, byte[] buf, int pos) {
        int tens = '0';
        while (v >= 10) {
            tens++;
            v -= 10;
        }
        buf[pos++] = (byte) tens;
        buf[pos++] = (byte) ('0' + v);
        return pos;
    }

}
