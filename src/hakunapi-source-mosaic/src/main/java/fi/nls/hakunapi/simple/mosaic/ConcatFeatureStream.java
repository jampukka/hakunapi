package fi.nls.hakunapi.simple.mosaic;

import java.util.List;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ValueProvider;

/**
 * A {@link FeatureStream} that concatenates several child streams end to end — the {@code UNION ALL}
 * of the mosaic tiles. Rows are emitted in child order with no deduplication (tiles are assumed
 * non-overlapping), and each child is opened lazily, only when the previous one is drained.
 *
 * <p>Children are supplied as {@link StreamSupplier}s rather than already-open streams so that a tile
 * is not queried until it is actually reached: if the caller stops early (e.g. a page limit is hit)
 * the not-yet-reached tiles are never opened. At most one child stream is open at a time, so an
 * arbitrary number of tiles streams through in O(1) resources.
 *
 * <p>{@link #close()} closes the currently open child (if any); tiles that were never opened need no
 * closing.
 */
public class ConcatFeatureStream implements FeatureStream {

    /** Opens a tile's stream. Deferred so unreached tiles are never queried. */
    @FunctionalInterface
    public interface StreamSupplier {
        FeatureStream open() throws Exception;
    }

    private final List<StreamSupplier> suppliers;
    private int nextIndex;
    private FeatureStream current;
    private boolean advanced;

    public ConcatFeatureStream(List<StreamSupplier> suppliers) {
        this.suppliers = suppliers;
    }

    /**
     * Ensure {@link #current} points at a child with a pending row, opening subsequent children as
     * earlier ones drain. Closes each drained child before moving on. Idempotent within a
     * {@code hasNext()}/{@code next()} pair via the {@code advanced} guard.
     */
    private void advance() throws Exception {
        if (advanced) {
            return;
        }
        while (true) {
            if (current != null) {
                if (current.hasNext()) {
                    advanced = true;
                    return;
                }
                current.close();
                current = null;
            }
            if (nextIndex >= suppliers.size()) {
                advanced = true;
                return;
            }
            current = suppliers.get(nextIndex++).open();
        }
    }

    @Override
    public boolean hasNext() {
        try {
            advance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return current != null && current.hasNext();
    }

    @Override
    public ValueProvider next() {
        try {
            advance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (current == null) {
            throw new java.util.NoSuchElementException();
        }
        ValueProvider row = current.next();
        // Consumed a row: the next hasNext()/next() must re-check whether this child still has rows
        // (and roll over to the next tile if not).
        advanced = false;
        return row;
    }

    /**
     * Close the currently open child, if any. Tiles not yet opened hold no resources. The supplier
     * list is advanced past the end so a later close is a no-op.
     */
    @Override
    public void close() throws Exception {
        nextIndex = suppliers.size();
        if (current != null) {
            FeatureStream c = current;
            current = null;
            c.close();
        }
    }

}
