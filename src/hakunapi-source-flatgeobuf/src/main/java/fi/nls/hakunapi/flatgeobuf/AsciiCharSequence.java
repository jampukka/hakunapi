package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;

public class AsciiCharSequence implements CharSequence {
    
    private final int n;
    private final int off;
    private final ByteBuffer buf;
    
    public AsciiCharSequence(int n, int off, ByteBuffer buf) {
        this.n = n;
        this.off = off;
        this.buf = buf;
    }

    @Override
    public int length() {
        return n;
    }

    @Override
    public char charAt(int index) {
        return (char) buf.get(off + index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        int i = off + start;
        int j = off + end;
        return new AsciiCharSequence(j - i, i, buf);
    }

}
