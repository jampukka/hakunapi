package fi.nls.hakunapi.core.util;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.io.NumberOutput;

import fi.nls.hakunapi.core.FloatingPointFormatter;

public class FixedFloatingPoint3Formatter implements FloatingPointFormatter {
    
    public static final FixedFloatingPoint3Formatter INSTANCE = new FixedFloatingPoint3Formatter(0, 5, 0, 8);

    private final int minDecimalsFloat;
    private final int maxDecimalsFloat;
    private final int minDecimalsDouble;
    private final int maxDecimalsDouble;
    private final byte[] buf;
    
    public FixedFloatingPoint3Formatter(int minDecimalsFloat, int maxDecimalsFloat,
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
        off = NumberOutput.outputLong((long) ((v * 1000.0) + 0.5), b, off);
        b[off - 0] = b[off - 1];
        b[off - 1] = b[off - 2];
        b[off - 2] = b[off - 3];
        b[off - 3] = '.';
        return off + 1;
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
        off = NumberOutput.outputLong((long) ((v * 1000.0) + 0.5), b, off);
        b[off - 0] = b[off - 1];
        b[off - 1] = b[off - 2];
        b[off - 2] = b[off - 3];
        b[off - 3] = '.';
        return off + 1;
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
