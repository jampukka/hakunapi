package fi.nls.hakunapi.tiles.source.pmtiles;

import java.io.IOException;

/**
 * A decoded PMTiles v3 directory: parallel arrays of entries sorted ascending
 * by {@code tileId}. An entry with {@code runLength == 0} is a pointer to a
 * leaf directory (its {@code offset}/{@code length} address the leaf within the
 * leaf-directories section); otherwise it addresses {@code runLength}
 * consecutive tile ids sharing the same tile-data {@code offset}/{@code length}.
 *
 * <p>The on-disk encoding is a sequence of varints: entry count, then all
 * {@code tileId} deltas, then all {@code runLength}s, then all {@code length}s,
 * then all {@code offset}s (where a stored 0 means "immediately after the
 * previous entry", i.e. contiguous packing).
 */
public final class Directory {

    private final long[] tileIds;
    private final long[] runLengths;
    private final long[] offsets;
    private final long[] lengths;

    private Directory(long[] tileIds, long[] runLengths, long[] offsets, long[] lengths) {
        this.tileIds = tileIds;
        this.runLengths = runLengths;
        this.offsets = offsets;
        this.lengths = lengths;
    }

    public static Directory parse(byte[] bytes) throws IOException {
        Varint v = new Varint(bytes);
        int n = (int) v.next();
        long[] tileIds = new long[n];
        long[] runLengths = new long[n];
        long[] offsets = new long[n];
        long[] lengths = new long[n];

        long lastId = 0;
        for (int i = 0; i < n; i++) {
            lastId += v.next();
            tileIds[i] = lastId;
        }
        for (int i = 0; i < n; i++) {
            runLengths[i] = v.next();
        }
        for (int i = 0; i < n; i++) {
            lengths[i] = v.next();
        }
        for (int i = 0; i < n; i++) {
            long o = v.next();
            if (o == 0) {
                // Contiguous with the previous entry (spec: stored 0 means
                // "sum of the previous entry's offset and length").
                offsets[i] = i > 0 ? offsets[i - 1] + lengths[i - 1] : 0;
            } else {
                offsets[i] = o - 1;
            }
        }
        return new Directory(tileIds, runLengths, offsets, lengths);
    }

    /**
     * Find the entry covering {@code tileId}, or null if absent. Binary search
     * for the greatest entry with {@code tileId <= target}; a leaf pointer
     * ({@code runLength == 0}) always matches, a tile run matches only when the
     * target falls within {@code [tileId, tileId + runLength)}.
     */
    public Entry find(long tileId) {
        int lo = 0;
        int hi = tileIds.length - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (tileIds[mid] <= tileId) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (found < 0) {
            return null;
        }
        long rl = runLengths[found];
        if (rl == 0) {
            return new Entry(tileIds[found], 0, offsets[found], lengths[found]);
        }
        if (tileId < tileIds[found] + rl) {
            return new Entry(tileIds[found], rl, offsets[found], lengths[found]);
        }
        return null;
    }

    /** A single directory entry. {@code runLength == 0} marks a leaf-directory pointer. */
    public record Entry(long tileId, long runLength, long offset, long length) {
        public boolean isLeaf() {
            return runLength == 0;
        }
    }

    /** Minimal little-endian protobuf varint reader over a byte array. */
    private static final class Varint {
        private final byte[] buf;
        private int pos;

        Varint(byte[] buf) {
            this.buf = buf;
        }

        long next() throws IOException {
            long result = 0;
            int shift = 0;
            while (true) {
                if (pos >= buf.length) {
                    throw new IOException("Truncated PMTiles directory varint");
                }
                int b = buf[pos++] & 0xff;
                result |= (long) (b & 0x7f) << shift;
                if ((b & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
        }
    }

}
