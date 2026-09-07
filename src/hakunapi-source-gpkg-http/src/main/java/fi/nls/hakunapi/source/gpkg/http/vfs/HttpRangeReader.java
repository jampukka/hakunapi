package fi.nls.hakunapi.source.gpkg.http.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a remote database over HTTP Range requests: one request per read, for
 * exactly the bytes SQLite asked for.
 *
 * There is deliberately no cache and no read-ahead here: this class does one
 * request for one read and nothing else. Both belong to
 * {@link BlockCacheRangeReader}, which wraps this one when the database is far
 * enough away to be worth it - keeping them apart is what lets the same reader
 * serve a file on the same network, where fetching bytes nobody asked for is a
 * loss rather than a win.
 *
 * Read amplification did turn out to be worth paying for over the open
 * internet, against the guess this class was first written on. A GeoPackage's
 * page size is whatever its writer chose, and a b-tree descent is a poor bet
 * for the next read being adjacent - but a 4 KiB Range request to object
 * storage measured ~100 ms, so the bet does not have to pay off often. See
 * {@link BlockCacheRangeReader} for what it measured.
 *
 * HttpURLConnection rather than HttpClient: every read here is synchronous and
 * blocking, so the reactive BodySubscriber machinery is pure overhead - a
 * CompletableFuture, a Flow.Subscription and reassembled ByteBuffer chunks per
 * database page. An InputStream read into the destination allocates none of
 * that, and the JDK's keep-alive pool reuses the socket, which measured as the
 * larger win by far. Nothing is lost in giving up HTTP/2 multiplexing: SQLite
 * reads sequentially within a connection, so concurrency here is one request
 * per thread, not concurrent reads on one.
 */
public class HttpRangeReader implements RangeReader {

    private static final Logger LOG = LoggerFactory.getLogger(HttpRangeReader.class);

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final URL url;
    private final long size;

    /**
     * Staging buffer for the segment path. InputStream cannot write into a
     * MemorySegment directly, and a fresh array per read would be garbage on the
     * hot path; SQLite reads a page at a time, so this settles at the database's
     * page size.
     *
     * Per thread because that is the granularity of concurrent use: a
     * connection's reads are sequential, and parallel requests each get their
     * own connection on their own thread. Per instance too, so two databases
     * with different page sizes do not share one buffer, and so the buffers go
     * away with the reader rather than outliving it in a pooled request thread.
     */
    private final ThreadLocal<byte[]> staging = new ThreadLocal<>();

    public HttpRangeReader(URI uri) throws IOException {
        this.url = uri.toURL();
        this.size = readSize();
        LOG.info("Opened {} ({} bytes)", uri, size);
    }

    /**
     * Determines the file length. A HEAD is enough for well-behaved storage; we
     * fall back to a one-byte range whose Content-Range carries the total, which
     * also proves range support up front.
     */
    private long readSize() throws IOException {
        HttpURLConnection conn = open();
        try {
            conn.setRequestMethod("HEAD");
            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK
                    && "bytes".equalsIgnoreCase(conn.getHeaderField("Accept-Ranges"))) {
                long length = conn.getContentLengthLong();
                if (length > 0) {
                    return length;
                }
            }
        } finally {
            conn.disconnect();
        }
        return readSizeFromContentRange();
    }

    private long readSizeFromContentRange() throws IOException {
        HttpURLConnection conn = open();
        try {
            conn.setRequestProperty("Range", "bytes=0-0");
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_PARTIAL) {
                throw new IOException("Range requests are not supported by " + url + " (status " + status + ")");
            }
            String contentRange = conn.getHeaderField("Content-Range");
            if (contentRange == null) {
                throw new IOException("No Content-Range in 206 response from " + url);
            }
            int slash = contentRange.lastIndexOf('/');
            if (slash < 0) {
                throw new IOException("Malformed Content-Range from " + url + ": " + contentRange);
            }
            try {
                return Long.parseLong(contentRange.substring(slash + 1));
            } catch (NumberFormatException e) {
                throw new IOException("Unknown total length in Content-Range from " + url + ": " + contentRange);
            }
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection open() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        return conn;
    }

    /**
     * Issues one Range request for exactly the requested bytes, clamped to the
     * end of the file, and fills dst.
     *
     * The connection is not disconnected: that would close the socket and give up
     * keep-alive. Draining the stream to EOF returns it to the JDK's pool.
     */
    private int fetch(long srcOffset, byte[] dst, int dstOff, int len) throws IOException {
        long last = Math.min(srcOffset + len, size) - 1;
        HttpURLConnection conn = open();
        conn.setRequestProperty("Range", "bytes=" + srcOffset + "-" + last);
        int status = conn.getResponseCode();
        if (status != HttpURLConnection.HTTP_PARTIAL) {
            conn.disconnect();
            throw new IOException("Expected 206 from " + url + " but got " + status);
        }
        try (InputStream in = conn.getInputStream()) {
            int read = 0;
            while (read < len) {
                int n = in.read(dst, dstOff + read, len - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            return read;
        }
    }

    @Override
    public int read(long srcOffset, byte[] dst, int dstOff, int len) throws IOException {
        if (srcOffset >= size) {
            return 0;
        }
        return fetch(srcOffset, dst, dstOff, len);
    }

    /**
     * The hot path. Staged through a reused per-thread array because InputStream
     * has no MemorySegment overload; the copy out of it is a bulk memcpy.
     */
    @Override
    public int read(long srcOffset, MemorySegment dst, int len) throws IOException {
        if (srcOffset >= size) {
            return 0;
        }
        byte[] buf = staging.get();
        if (buf == null || buf.length < len) {
            buf = new byte[len];
            staging.set(buf);
        }
        int n = fetch(srcOffset, buf, 0, len);
        MemorySegment.copy(buf, 0, dst, ValueLayout.JAVA_BYTE, 0, n);
        return n;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void close() {
        // The staging buffers belong to the request threads, not to this one;
        // they go away with the reader
    }

}
