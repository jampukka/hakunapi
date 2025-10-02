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

    private static final int getPageIndex(final long offset) {
        return (int) (offset / PAGE_SIZE);
    }

    private static final int getPageOffset(final long offset) {
        return (int) (offset % PAGE_SIZE);
    }

    @Override
    public ByteBuffer getBytes(long offset, int len) {
        int p1 = getPageIndex(offset);
        int p2 = getPageIndex(offset + len);
        int off1 = getPageOffset(offset);

        if (p1 == p2) {
            return mmaps[p1].duplicate().position(off1).order(ByteOrder.LITTLE_ENDIAN);
        }

        ByteBuffer bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
        byte[] arr = bb.array();

        int n = PAGE_SIZE - off1;
        mmaps[p1].duplicate().position(off1).get(arr, 0, n);
        mmaps[p2].duplicate().get(arr, n, len - n);

        return bb;
    }

    @Override
    public int getInt(long offset) {
        return mmaps[getPageIndex(offset)].getInt(getPageOffset(offset));
    }

    @Override
    public long getLong(long offset) {
        return mmaps[getPageIndex(offset)].getLong(getPageOffset(offset));
    }

    @Override
    public double getDouble(long offset) {
        return mmaps[getPageIndex(offset)].getDouble(getPageOffset(offset));
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
