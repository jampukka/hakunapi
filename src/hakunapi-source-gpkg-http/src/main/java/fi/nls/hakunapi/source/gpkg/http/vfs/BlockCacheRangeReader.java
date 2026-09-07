package fi.nls.hakunapi.source.gpkg.http.vfs;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads through fixed-size blocks, so one request serves the pages next to the
 * one asked for.
 *
 * SQLite asks for one page at a time, and over HTTP the cost of a read is
 * almost entirely the round trip rather than the bytes: measured against
 * UpCloud object storage a Range request took ~200 ms whether it asked for
 * 4 KiB or 512 KiB, against ~70 us for the same request to nginx on the same
 * host. So a block can cover the pages after the one asked for at no extra
 * cost, and where reads run forward that turns many requests into one.
 *
 * Whether they do run forward is the thing to measure rather than assume. On
 * MTK they largely do not: of 218 blocks one 31-collection vector tile
 * fetched, 86 were single blocks with no neighbour and only 3 runs reached 6
 * blocks, because a tile is one rtree descent per collection and each descent
 * lands somewhere unrelated to the others. Measured hits per fetch on that
 * tile: 4 KiB blocks 1.0, 64 KiB 3.2, 128 KiB 4.5. Bigger blocks do fetch
 * fewer times - and in a fixed memory budget they hold correspondingly fewer
 * blocks, which is what decides whether a second look at the same view is
 * served from memory. At 128 MB the 4 KiB configuration was the one that
 * stopped evicting, and it was the fastest of the three in a browser.
 *
 * So this is two things at once and the balance between them is per file: a
 * read-ahead, which wants large blocks, and a cache, which wants many. It is
 * not a second page cache in the sense of duplicating SQLite's judgement - it
 * caches byte ranges, and SQLite's own page cache sits above deciding which
 * pages are worth keeping. What it adds over that cache is being outside the
 * handle mutex: a page cache hit is serialised against every other query on
 * that handle, a block cache hit is not.
 *
 * Sizing is the caller's, and there is no default - see
 * {@code GpkgHttpSimpleSource}. The number to watch is whether it evicts:
 * {@link #hits()}, {@link #misses()} and the {@code blocks held} log line
 * exist so that is checked rather than guessed. A cache sitting permanently at
 * its maximum is throwing away blocks the next tile is about to read.
 *
 * What it cannot do is make the working set smaller. One cold z14 tile read a
 * few hundred distinct places in a 49 GB file to build 91 kB of MVT, with no
 * block fetched twice. That count follows the number of tables a tile queries,
 * so reducing it is the caller's problem, not this class's.
 *
 * Blocks are evicted least-recently-used. Concurrent reads are expected - the
 * data source shares one sqlite3 handle across request threads - and a miss is
 * fetched outside the lock, so a slow request does not block readers of other
 * blocks. A block already being fetched is not fetched again: the second thread
 * waits for the first one's result. Concurrent tiles read the same rtree pages,
 * so those are the blocks most likely to be wanted twice at once.
 */
public class BlockCacheRangeReader implements RangeReader {

    private static final Logger LOG = LoggerFactory.getLogger(BlockCacheRangeReader.class);

    /**
     * Fetches between progress lines. The bench needs to see the hit rate while
     * a tile is being built, not only at shutdown, and one line per fetch would
     * be one line per round trip.
     */
    private static final int LOG_EVERY = 200;

    private final RangeReader delegate;
    private final int blockSize;
    private final int blockShift;
    private final int maxBlocks;
    private final long size;

    private final ReentrantLock lock = new ReentrantLock();
    private final LinkedHashMap<Long, byte[]> blocks;
    private final HashMap<Long, Pending> inFlight = new HashMap<>();

    private long hits;
    private long misses;

    /**
     * @param blockSize bytes per block; rounded up to a power of two so the
     *        block of an offset is a shift rather than a division
     * @param cacheBytes total bytes to keep; at least one block is kept
     */
    public BlockCacheRangeReader(RangeReader delegate, int blockSize, long cacheBytes) throws IOException {
        if (blockSize <= 0) {
            throw new IllegalArgumentException("blockSize must be positive");
        }
        this.delegate = delegate;
        this.blockSize = Integer.highestOneBit(blockSize) == blockSize
                ? blockSize
                : Integer.highestOneBit(blockSize) << 1;
        this.blockShift = Integer.numberOfTrailingZeros(this.blockSize);
        this.maxBlocks = (int) Math.max(1, Math.min(cacheBytes / this.blockSize, Integer.MAX_VALUE));
        this.size = delegate.size();
        this.blocks = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
                return size() > maxBlocks;
            }
        };
        LOG.info("Block cache: {} KiB blocks, up to {} of them ({} MiB)",
                this.blockSize / 1024, maxBlocks, (long) maxBlocks * this.blockSize / (1024 * 1024));
    }

    @Override
    public int read(long srcOffset, byte[] dst, int dstOff, int len) throws IOException {
        return read(srcOffset, len, dst, dstOff, null);
    }

    @Override
    public int read(long srcOffset, MemorySegment dst, int len) throws IOException {
        return read(srcOffset, len, null, 0, dst);
    }

    /**
     * Serves a read from the blocks covering it, fetching those it does not have.
     * Exactly one of dstArray and dstSegment is non-null; they are handled
     * together because the block walk is the same and SQLite's xRead takes the
     * segment path.
     */
    private int read(long srcOffset, int len, byte[] dstArray, int dstOff, MemorySegment dstSegment)
            throws IOException {
        if (srcOffset >= size) {
            return 0;
        }
        int want = (int) Math.min(len, size - srcOffset);
        int done = 0;
        while (done < want) {
            long offset = srcOffset + done;
            long blockIndex = offset >>> blockShift;
            int within = (int) (offset & (blockSize - 1));
            byte[] block = block(blockIndex);
            int n = Math.min(want - done, block.length - within);
            if (n <= 0) {
                break; // end of file inside this block
            }
            if (dstArray != null) {
                System.arraycopy(block, within, dstArray, dstOff + done, n);
            } else {
                MemorySegment.copy(block, within, dstSegment, ValueLayout.JAVA_BYTE, done, n);
            }
            done += n;
        }
        return done;
    }

    /**
     * The block at blockIndex, fetching it on a miss. The fetch happens outside
     * the lock: it is a network round trip, and blocking every other reader for
     * its duration would defeat the point of sharing one handle across threads.
     * A block another thread is already fetching is waited for rather than
     * fetched again.
     */
    private byte[] block(long blockIndex) throws IOException {
        Pending running;
        lock.lock();
        try {
            byte[] cached = blocks.get(blockIndex);
            if (cached != null) {
                hits++;
                return cached;
            }
            running = inFlight.get(blockIndex);
            if (running != null) {
                // Served by a request already on the wire, so it counts as a
                // hit: what hits() measures is reads that cost no round trip.
                hits++;
            } else {
                misses++;
                if (misses % LOG_EVERY == 0) {
                    LOG.info("Block cache: {} hits, {} fetches, {} blocks held",
                            hits, misses, blocks.size());
                }
                inFlight.put(blockIndex, new Pending());
            }
        } finally {
            lock.unlock();
        }
        if (running != null) {
            return running.await();
        }

        byte[] block = null;
        IOException failure = null;
        try {
            block = fetch(blockIndex);
        } catch (IOException e) {
            failure = e;
        } catch (Throwable t) {
            // The waiters are parked on this block and nothing else will wake
            // them, so they get released even when this thread is unwinding on
            // something it has no business swallowing.
            release(blockIndex, null, new IOException("Failed to fetch block " + blockIndex, t));
            throw t;
        }
        release(blockIndex, block, failure);
        if (failure != null) {
            throw failure;
        }
        return block;
    }

    /**
     * Publishes the outcome of a fetch: the block into the cache, and either it
     * or the failure to whoever is waiting for it. Signalling happens outside
     * the lock - the waiters need the {@link Pending}, not the cache, and waking
     * them while holding the lock would have them contend for it immediately.
     */
    private void release(long blockIndex, byte[] block, IOException failure) {
        Pending started;
        lock.lock();
        try {
            started = inFlight.remove(blockIndex);
            if (block != null) {
                blocks.put(blockIndex, block);
            }
        } finally {
            lock.unlock();
        }
        if (started != null) {
            started.complete(block, failure);
        }
    }

    /** One block's worth of bytes from the delegate. */
    private byte[] fetch(long blockIndex) throws IOException {
        long start = blockIndex << blockShift;
        int length = (int) Math.min(blockSize, size - start);
        byte[] block = new byte[length];
        int read = 0;
        while (read < length) {
            int n = delegate.read(start + read, block, read, length - read);
            if (n <= 0) {
                break;
            }
            read += n;
        }
        if (read < length) {
            // Short read of a block that is not the last one: the file changed
            // under us, or storage lied about its size. Either way the page this
            // serves would be silently truncated.
            throw new IOException("Short read of block " + blockIndex + " at " + start
                    + ": expected " + length + " bytes, got " + read);
        }
        return block;
    }

    /** Cache hits since opening, for the bench. */
    public long hits() {
        lock.lock();
        try {
            return hits;
        } finally {
            lock.unlock();
        }
    }

    /** Block fetches since opening, i.e. requests actually issued. */
    public long misses() {
        lock.lock();
        try {
            return misses;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            blocks.clear();
        } finally {
            lock.unlock();
        }
        LOG.info("Block cache: {} hits, {} fetches", hits, misses);
        delegate.close();
    }

    /**
     * A fetch another thread has already started. The thread that put it in
     * {@link #inFlight} fills it in and signals; everyone else waits, so one
     * block is one request no matter how many tiles want it at once.
     *
     * The rtree pages near the root are read by every query in every tile, so
     * without this a burst of concurrent tiles turns one round trip into one per
     * tile - the requests that matter most are exactly the contended ones.
     */
    private static final class Pending {

        private byte[] block;
        private IOException failure;
        private boolean done;

        synchronized byte[] await() throws IOException {
            boolean interrupted = false;
            while (!done) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (failure != null) {
                // Rethrow a copy: the waiter's stack trace should be its own,
                // and one IOException instance must not be shared by n threads.
                throw new IOException(failure.getMessage(), failure);
            }
            return block;
        }

        synchronized void complete(byte[] block, IOException failure) {
            this.block = block;
            this.failure = failure;
            this.done = true;
            notifyAll();
        }

    }

}
