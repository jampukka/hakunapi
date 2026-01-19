package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import fi.nls.hakunapi.core.cache.HttpPageCache;

public class LargeFileHttp implements LargeFile {

    private static final int PAGE_SIZE = 1024 * 1024; // 1MB pages

    private final URL url;
    private final String urlString;
    private final int lastPageIndex;
    private final long fileSize;

    public LargeFileHttp(URL url) throws IOException {
        this.url = url;
        this.urlString = url.toString();
        this.fileSize = getFileSize(url);
        int numPages = (int) (fileSize / PAGE_SIZE);
        if (numPages * PAGE_SIZE != fileSize) {
            numPages++;
        }
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
        return HttpPageCache.getOrLoad(urlString, pageIndex, this::fetchPageSync);
    }

    private ByteBuffer fetchPageSync(int pageIndex) {
        int size = (pageIndex == lastPageIndex)
                ? (int) (fileSize - (long) pageIndex * PAGE_SIZE)
                : PAGE_SIZE;
        byte[] arr = getHttpBytes(url, (long) pageIndex * PAGE_SIZE, size);
        return ByteBuffer.wrap(arr).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public int getInt(long offset) {
        int pageIndex = (int) (offset >>> 20);
        int pageOffset = (int) (offset & 0xFFFFF);
        return loadPage(pageIndex).getInt(pageOffset);
    }

    @Override
    public long getLong(long offset) {
        int pageIndex = (int) (offset >>> 20);
        int pageOffset = (int) (offset & 0xFFFFF);
        return loadPage(pageIndex).getLong(pageOffset);
    }

    @Override
    public double getDouble(long offset) {
        int pageIndex = (int) (offset >>> 20);
        int pageOffset = (int) (offset & 0xFFFFF);
        return loadPage(pageIndex).getDouble(pageOffset);
    }

    @Override
    public void close() throws Exception {
        // Don't invalidate shared cache on close - other requests may be using it
    }

    @Override
    public long getSize() {
        return fileSize;
    }

    @Override
    public ByteBuffer getBytes(long offset, int len, MutableInt posOut) {
        int startPage = (int) (offset >>> 20);
        int endPage = (int) ((offset + len) >>> 20);
        int off = (int) (offset & 0xFFFFF);

        if (startPage == endPage) {
            posOut.v = off;
            return loadPage(startPage);
        }

        // Cross-page read - need to allocate a new buffer
        ByteBuffer bb = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
        byte[] arr = bb.array();

        // First page
        int firstPageBytes = PAGE_SIZE - off;
        loadPage(startPage).get(off, arr, 0, firstPageBytes);
        int arrOff = firstPageBytes;

        // Full pages
        for (int pageIdx = startPage + 1; pageIdx < endPage; pageIdx++) {
            loadPage(pageIdx).get(0, arr, arrOff, PAGE_SIZE);
            arrOff += PAGE_SIZE;
        }

        // Last page
        loadPage(endPage).get(0, arr, arrOff, len - arrOff);

        posOut.v = 0;
        return bb;
    }

}
