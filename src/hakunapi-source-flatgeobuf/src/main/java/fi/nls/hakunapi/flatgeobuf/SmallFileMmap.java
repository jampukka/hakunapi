package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;

public class SmallFileMmap implements LargeFile {

    private final FileChannel fc;
    private final long fileSize;
    private final ByteBuffer mmap;

    public SmallFileMmap(Path path) throws IOException {
        this.fc = FileChannel.open(path);
        this.fileSize = fc.size();
        if (fileSize > Integer.MAX_VALUE) {
            throw new IllegalArgumentException();
        }
        mmap = fc.map(MapMode.READ_ONLY, 0L, fileSize).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public ByteBuffer getBytes(long offset, int len, MutableInt posOut) {
        posOut.v = (int) offset;
        return mmap;
    }
    
    @Override
    public int getInt(long offset) {
        return mmap.getInt((int) offset);
    }

    @Override
    public long getLong(long offset) {
        return mmap.getLong((int) offset);
    }

    @Override
    public double getDouble(long offset) {
        return mmap.getDouble((int) offset);
    }

    @Override
    public void close() throws Exception {
        fc.close();
    }

    @Override
    public long getSize() {
        return fileSize;
    }

}
