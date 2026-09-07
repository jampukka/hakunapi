package fi.nls.hakunapi.tiles.source.vectortile.mvt;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A protobuf serialization buffer filled <em>back to front</em> into a linked
 * chain of fixed-size byte chunks, designed to stream to an {@link OutputStream}.
 *
 * <p>Building a length-delimited protobuf message back-to-front means a nested
 * message's body is already buffered by the time its length prefix is written, so
 * the length is known with no size pre-pass, no backpatch and no copy - the single
 * hardest cost of a forward encoder. Using a chain of chunks (rather than one
 * front-growing array) avoids reallocating and copying the whole tile as it grows:
 * when the current chunk fills, a new chunk is linked in front of it.
 *
 * <h2>Layout</h2>
 * Each chunk is filled from its high end toward index 0 ({@code pos} decrements,
 * valid bytes are {@code [pos, end)}). The chain head is the most recently
 * allocated chunk and holds the <em>lowest</em> logical addresses; older chunks
 * hold higher addresses. {@link #writeTo} walks head&rarr;tail, emitting each
 * chunk's valid range in ascending order, which yields a correct forward stream.
 *
 * <h2>Usage discipline</h2>
 * Emit a message's fields in <em>reverse</em> order; for each length-delimited
 * field prepend body first, then its length varint, then its tag (see
 * {@link #prependLengthDelimitedHeader}). Multi-byte primitives (varints, tags,
 * fixed fields) are never split across a chunk boundary; only bulk
 * {@link #prependBytes} may span chunks (raw copy, order preserved).
 *
 * <p>Not thread-safe; one instance per worker thread, reused across tiles via
 * {@link #reset()} (chunks are pooled and reused).
 */
public final class BackwardProtoBuf {

    public static final int WIRE_VARINT = 0;
    public static final int WIRE_FIXED64 = 1;
    public static final int WIRE_LEN = 2;
    public static final int WIRE_FIXED32 = 5;

    private final int chunkSize;

    private static final class Chunk {
        final byte[] data;
        int pos;        // write cursor; valid range is [pos, data.length)
        Chunk prev;     // older chunk (higher logical address)

        Chunk(int size) {
            this.data = new byte[size];
            this.pos = size;
        }
    }

    private Chunk head;          // newest chunk (lowest logical address)
    /**
     * Chunks kept in the pool between messages. At the default 8 kB chunk this
     * retains ~256 kB per buffer, enough that an ordinary tile never allocates a
     * chunk after the first few, while a multi-megabyte layer's chain is
     * released instead of being held per worker thread.
     */
    private static final int MAX_POOLED_CHUNKS = 32;

    private Chunk pool;          // singly-linked (via prev) free list for reuse
    private int length;          // running total of valid bytes across all chunks

    public BackwardProtoBuf() {
        this(8192);
    }

    public BackwardProtoBuf(int chunkSize) {
        this.chunkSize = Math.max(64, chunkSize);
        this.head = new Chunk(this.chunkSize);
    }

    /** Recycle all chunks (return to the pool) for a new message. */
    public void reset() {
        Chunk c = head;
        // splice the whole chain onto the pool
        while (c.prev != null) {
            c = c.prev;
        }
        c.prev = pool;
        pool = head;
        head = newChunk();
        length = 0;
    }

    /**
     * Drop pooled chunks past {@link #MAX_POOLED_CHUNKS}. The buffer is reused
     * across tiles, so without this one multi-megabyte layer would leave its
     * whole chunk chain pooled for as long as the owning thread lives.
     */
    public void trim() {
        Chunk c = pool;
        int n = 1;
        while (c != null && n < MAX_POOLED_CHUNKS) {
            c = c.prev;
            n++;
        }
        if (c != null) {
            c.prev = null;
        }
    }

    private Chunk newChunk() {
        Chunk c = pool;
        if (c != null) {
            pool = c.prev;
            c.prev = null;
            c.pos = c.data.length;
            return c;
        }
        return new Chunk(chunkSize);
    }

    /** Total valid bytes across all chunks. */
    public int length() {
        return length;
    }

    /** Ensure the head chunk has room for a single primitive of {@code need} bytes. */
    private void ensurePrimitive(int need) {
        if (head.pos < need) {
            Chunk c = newChunk();
            c.prev = head;
            head = c;
        }
    }

    public void prependByte(int b) {
        ensurePrimitive(1);
        head.data[--head.pos] = (byte) b;
        length++;
    }

    /**
     * Prepend {@code len} bytes from {@code src[off..off+len)}, order preserved,
     * spilling across chunks as needed. The block ends at the current write
     * position and starts {@code len} bytes earlier in logical order.
     */
    public void prependBytes(byte[] src, int off, int len) {
        length += len;
        int remaining = len;
        while (remaining > 0) {
            if (head.pos == 0) {
                Chunk c = newChunk();
                c.prev = head;
                head = c;
            }
            int take = Math.min(remaining, head.pos);
            // The last 'take' bytes of the source block go into this chunk's tail.
            head.pos -= take;
            System.arraycopy(src, off + remaining - take, head.data, head.pos, take);
            remaining -= take;
        }
    }

    /**
     * Prepend an unsigned varint so that, read in ascending order, it is a
     * standard protobuf varint (7-bit groups, least-significant first, MSB
     * continuation bit on all but the last).
     */
    public void prependVarint(long value) {
        long v = value;
        int n = 1;
        long t = v >>> 7;
        while (t != 0) {
            n++;
            t >>>= 7;
        }
        ensurePrimitive(n);
        length += n;
        // Prepend most-significant group first (highest address), so the
        // least-significant group lands at the lowest address.
        for (int i = n - 1; i >= 0; i--) {
            int group = (int) ((v >>> (7 * i)) & 0x7f);
            if (i != n - 1) {
                group |= 0x80; // continuation on all but the most-significant group
            }
            head.data[--head.pos] = (byte) group;
        }
    }

    public void prependTag(int fieldNumber, int wireType) {
        prependVarint(((long) fieldNumber << 3) | wireType);
    }

    /** Prepend a length-delimited field header; caller already prepended the body. */
    public void prependLengthDelimitedHeader(int fieldNumber, int bodyLen) {
        prependVarint(bodyLen);
        prependTag(fieldNumber, WIRE_LEN);
    }

    public void prependVarintField(int fieldNumber, long value) {
        prependVarint(value);
        prependTag(fieldNumber, WIRE_VARINT);
    }

    public void prependFixed64Field(int fieldNumber, long bits) {
        ensurePrimitive(8);
        for (int i = 7; i >= 0; i--) {
            head.data[--head.pos] = (byte) (bits >>> (8 * i));
        }
        length += 8;
        prependTag(fieldNumber, WIRE_FIXED64);
    }

    public void prependFixed32Field(int fieldNumber, int bits) {
        ensurePrimitive(4);
        for (int i = 3; i >= 0; i--) {
            head.data[--head.pos] = (byte) (bits >>> (8 * i));
        }
        length += 4;
        prependTag(fieldNumber, WIRE_FIXED32);
    }

    /** Stream all buffered bytes to {@code out} in ascending (forward) order. */
    public void writeTo(OutputStream out) throws IOException {
        for (Chunk c = head; c != null; c = c.prev) {
            out.write(c.data, c.pos, c.data.length - c.pos);
        }
    }

    /** Collect all buffered bytes into a new array (mainly for tests). */
    public byte[] toByteArray() {
        int len = length();
        byte[] out = new byte[len];
        int p = 0;
        for (Chunk c = head; c != null; c = c.prev) {
            int clen = c.data.length - c.pos;
            System.arraycopy(c.data, c.pos, out, p, clen);
            p += clen;
        }
        return out;
    }
}
