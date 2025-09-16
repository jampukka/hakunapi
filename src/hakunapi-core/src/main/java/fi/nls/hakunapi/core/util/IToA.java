package fi.nls.hakunapi.core.util;

import com.fasterxml.jackson.core.io.NumberOutput;

public final class IToA {

    public static final int itoa(int v, byte[] b, int off) {
        return NumberOutput.outputInt(v, b, off);
    }

    public static final int itoa(int v, char[] b, int off) {
        return NumberOutput.outputInt(v, b, off);
    }

    public static final int ltoa(long v, byte[] b, int off) {
        return NumberOutput.outputLong(v, b, off);
    }

    public static final int ltoa(long v, char[] b, int off) {
        return NumberOutput.outputLong(v, b, off);
    }

}
