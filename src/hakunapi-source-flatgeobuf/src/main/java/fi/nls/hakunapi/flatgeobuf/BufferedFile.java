package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;

public interface BufferedFile extends AutoCloseable {

    public int getInt(long offset);
    public long getLong(long offset);
    public double getDouble(long offset);
    public ByteBuffer getBytes(long offset, int len);

}
