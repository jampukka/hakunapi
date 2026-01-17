package fi.nls.hakunapi.core.util;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.io.NumberOutput;

import fi.nls.hakunapi.core.FloatingPointFormatter;

public class FixedFloatingPoint3FormatterInt implements FloatingPointFormatter {
    
    public static final FixedFloatingPoint3FormatterInt INSTANCE = new FixedFloatingPoint3FormatterInt(0, 5, 0, 8);
    
    // Lookup table for 3-digit pairs "000" to "999"
    private static final int[] DIGIT_TRIPLETS = new int[1000];
    static {
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                for (int k = 0; k < 10; k++) {
                    int v = ('0' + i) << 16 | ('0' + j) << 8 | ('0' + k);
                    DIGIT_TRIPLETS[i * 100 + j * 10 + k] = v;
                }
            }
        }
    }

    private final int minDecimalsFloat;
    private final int maxDecimalsFloat;
    private final int minDecimalsDouble;
    private final int maxDecimalsDouble;
    private final byte[] buf;
    
    public FixedFloatingPoint3FormatterInt(int minDecimalsFloat, int maxDecimalsFloat,
            int minDecimalsDouble, int maxDecimalsDouble) {
        this.minDecimalsFloat = minDecimalsFloat;
        this.maxDecimalsFloat = maxDecimalsFloat;
        this.minDecimalsDouble = minDecimalsDouble;
        this.maxDecimalsDouble = maxDecimalsDouble;
        this.buf = new byte[32];
    }
    
    @Override
    public int maxDecimalsFloat() {
        return maxDecimalsFloat;
    }

    @Override
    public int maxDecimalsDouble() {
        return maxDecimalsDouble;
    }

    @Override
    public int maxDecimalsOrdinate() {
        return 3;
    }

    @Override
    public int writeFloat(float f, byte[] b, int off) {
        return DToA.ftoa(f, b, off, minDecimalsFloat, maxDecimalsFloat);
    }

    @Override
    public int writeDouble(double v, byte[] b, int off) {
        return DToA.dtoa(v, b, off, minDecimalsDouble, maxDecimalsDouble);
    }

    @Override
    public int writeOrdinate(double v, byte[] b, int off) {
        if (v < 0) {
            b[off++] = '-';
            v = -v;
        }
        long l = (long) v;
        int decimal = (int) ((v - l) * 1000.0 + 0.5);
        if (decimal >= 1000) {
            decimal = 0;
            l++;
        }
        off = NumberOutput.outputLong(l, b, off);
        b[off++] = '.';
        int str = DIGIT_TRIPLETS[decimal];
        b[off++] = (byte) (str >> 16);
        b[off++] = (byte) (str >> 8);
        b[off++] = (byte) str;
        return off;
    }

    @Override
    public int writeFloat(float f, char[] arr, int off) {
        return DToA.ftoa(f, arr, off, minDecimalsFloat, maxDecimalsFloat);
    }

    @Override
    public int writeDouble(double d, char[] arr, int off) {
        return DToA.dtoa(d, arr, off, minDecimalsDouble, maxDecimalsDouble);
    }

    @Override
    public int writeOrdinate(double v, char[] b, int off) {
        if (v < 0) {
            b[off++] = '-';
            v = -v;
        }
        long l = (long) v;
        int decimal = (int) ((v - l) * 1000.0 + 0.5);
        if (decimal >= 1000) {
            decimal = 0;
            l++;
        }
        off = NumberOutput.outputLong(l, b, off);
        b[off++] = '.';
        int str = DIGIT_TRIPLETS[decimal];
        b[off++] = (char) (str >> 16);
        b[off++] = (char) (str >> 8);
        b[off++] = (char) str;
        return off;

    }

    @Override
    public String writeFloat(float f) {
        int len = writeFloat(f, buf, 0);
        return new String(buf, 0, len, StandardCharsets.US_ASCII);
    }

    @Override
    public String writeDouble(double d) {
        int len = writeDouble(d, buf, 0);
        return new String(buf, 0, len, StandardCharsets.US_ASCII);
    }

    @Override
    public String writeOrdinate(double x) {
        int len = writeOrdinate(x, buf, 0);
        return new String(buf, 0, len, StandardCharsets.US_ASCII);
    }

}
