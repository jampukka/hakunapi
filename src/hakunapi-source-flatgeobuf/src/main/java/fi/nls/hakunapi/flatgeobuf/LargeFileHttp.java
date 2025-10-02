package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LargeFileHttp implements LargeFile {

    private static final int PAGE_SIZE = 256 * 1024;

    private final URL url;
    private final int lastPageIndex;
    private final long fileSize;
    private final ByteBuffer[] mapped;

    public LargeFileHttp(URL url) throws IOException {
        this.url = url;
        this.fileSize = getFileSize(url);
        int numPages = (int) (fileSize / PAGE_SIZE);
        if (numPages * PAGE_SIZE != fileSize) {
            numPages++;
        }
        this.mapped = new ByteBuffer[numPages];
        this.lastPageIndex = numPages - 1;
    }

    private static long getFileSize(URL url) {
        try {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("HEAD");
            return c.getContentLengthLong();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] getHttpBytes(URL url, long offset, int len) {
        try {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            String bytes = String.format("bytes=%d-%d", offset, offset + len - 1);
            c.setRequestProperty("Range", bytes);
            try (InputStream in = c.getInputStream()) {
                byte[] buf = new byte[len];
                in.readNBytes(buf, 0, len);
                return buf;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ByteBuffer loadPage(int pageIndex) {
        if (mapped[pageIndex] == null) {
            int size = PAGE_SIZE;
            if (pageIndex == lastPageIndex) {
                size = (int) (fileSize - pageIndex * PAGE_SIZE);
            }
            byte[] arr = getHttpBytes(url, pageIndex * PAGE_SIZE, size);
            mapped[pageIndex] = ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN);
        }
        return mapped[pageIndex];
    }

    private int getPageIndex(long offset) {
        return (int) (offset / PAGE_SIZE);
    }

    private int getPageOffset(long offset) {
        return (int) (offset % PAGE_SIZE);
    }

    private ByteBuffer getPage(long offset) {
        return loadPage(getPageIndex(offset));
    }

    @Override
    public ByteBuffer getBytes(long offset, int len) {
        int startPage = getPageIndex(offset);
        int endPage = getPageIndex(offset + len);
        if (startPage == endPage) {
            return loadPage(startPage).duplicate().position(getPageOffset(offset)).order(ByteOrder.LITTLE_ENDIAN);
        }
        return getBytesBetweenPages(offset, len);
    }

    private ByteBuffer getBytesBetweenPages(long offset, int len) {
        int startPage = getPageIndex(offset);
        int endPage = getPageIndex(offset + len);

        ByteBuffer bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
        byte[] arr = bb.array();

        // First page
        int firstPageOffset = getPageOffset(offset);
        int off = 0;
        int firstPageBytes = PAGE_SIZE - firstPageOffset;
        ByteBuffer page = loadPage(startPage).duplicate();
        page.position(firstPageOffset);
        page.get(arr, off, firstPageBytes);
        off += firstPageBytes;

        // Full pages
        for (int pageIdx = startPage + 1; pageIdx < endPage; pageIdx++) {
            loadPage(pageIdx).duplicate().get(arr, off, PAGE_SIZE);
            off += PAGE_SIZE;
        }

        // Last Page
        loadPage(endPage).duplicate().get(arr, off, len - off);

        return bb;
    }

    @Override
    public int getInt(long offset) {
        return getPage(offset).getInt(getPageOffset(offset));
    }

    @Override
    public long getLong(long offset) {
        return getPage(offset).getLong(getPageOffset(offset));
    }

    @Override
    public double getDouble(long offset) {
        return getPage(offset).getDouble(getPageOffset(offset));
    }

    @Override
    public void close() throws Exception {
        // NOP
    }

    @Override
    public long getSize() {
        return fileSize;
    }

}
