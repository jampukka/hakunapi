package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;

public class LargeFileMmap implements LargeFile {

    private static final int PAGE_SIZE = Integer.MAX_VALUE;

    private final FileChannel fc;
    private final long fileSize;
    private final ByteBuffer[] mmaps;

    public LargeFileMmap(Path path) throws IOException {
        this.fc = FileChannel.open(path);
        this.fileSize = fc.size();
        int numPages = (int) (fileSize / PAGE_SIZE);
        if (numPages * PAGE_SIZE != fileSize) {
            numPages++;
        }
        this.mmaps = new ByteBuffer[numPages];
        int i = 0;
        for (; i < numPages - 1; i++) {
            mmaps[i] = fc.map(MapMode.READ_ONLY, i * PAGE_SIZE, PAGE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        }
        int lastPageSize = (int) (fileSize - i * PAGE_SIZE);
        mmaps[i] = fc.map(MapMode.READ_ONLY, i * PAGE_SIZE, lastPageSize).order(ByteOrder.LITTLE_ENDIAN);
    }

    /*
    private static final int getPageIndex(final long offset) {
        return (int) (offset / PAGE_SIZE);
    }

    private static final int getPageOffset(final long offset) {
        return (int) (offset % PAGE_SIZE);
    }
    */

    @Override
    public ByteBuffer getBytes(long offset, int len, MutableInt posOut) {
        int p1 = (int) (offset >>> 31);
        int p2 = (int) (offset + len >>> 31);
        int off = (int) (offset & 0x7FFFFFFF);

        if (p1 == p2) {
            posOut.v = off;
            return mmaps[p1];
        }

        ByteBuffer bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
        byte[] arr = bb.array();

        int n = PAGE_SIZE - off;
        mmaps[p1].get(off, arr, 0, n);
        mmaps[p2].get(0, arr, n, len - n);
        posOut.v = 0;

        return bb;
    }
    
    @Override
    public int getInt(long offset) {
        return mmaps[(int) (offset >>> 31)].getInt((int) (offset & 0x7FFFFFFF));
    }

    @Override
    public long getLong(long offset) {
        return mmaps[(int) (offset >>> 31)].getLong((int) (offset & 0x7FFFFFFF));
    }

    @Override
    public double getDouble(long offset) {
        return mmaps[(int) (offset >>> 31)].getDouble((int) (offset & 0x7FFFFFFF));
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
