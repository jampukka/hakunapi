package fi.nls.hakunapi.source.gpkg.http.vfs;

import java.io.Closeable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;

/**
 * The bytes behind a database file, as SQLite's VFS needs them: read at an
 * offset, and a total size.
 *
 * Implementations are called from SQLite's xRead on whatever thread issued the
 * query, and must be safe for concurrent use.
 */
public interface RangeReader extends Closeable {

    /**
     * Reads len bytes at srcOffset into dst.
     *
     * @return bytes actually read; less than len only at end of file
     */
    int read(long srcOffset, byte[] dst, int dstOff, int len) throws IOException;

    /**
     * Reads len bytes at srcOffset straight into SQLite's buffer.
     *
     * This is the hot path: SQLite issues one xRead per page it needs, so the
     * default's intermediate array would be garbage per read. An implementation
     * holding its pages as arrays should copy from them directly.
     *
     * @return bytes actually read; less than len only at end of file
     */
    default int read(long srcOffset, MemorySegment dst, int len) throws IOException {
        byte[] tmp = new byte[len];
        int n = read(srcOffset, tmp, 0, len);
        MemorySegment.copy(tmp, 0, dst, java.lang.foreign.ValueLayout.JAVA_BYTE, 0, n);
        return n;
    }

    long size() throws IOException;

}
