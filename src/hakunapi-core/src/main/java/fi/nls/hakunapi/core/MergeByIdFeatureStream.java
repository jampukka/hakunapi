package fi.nls.hakunapi.core;

import java.util.Comparator;

/**
 * A {@link FeatureStream} that merges two id-sorted child streams into a single UNION-distinct
 * stream, deduplicating by feature id with a fixed priority: on an id collision the higher-priority
 * child (index 0) wins and the lower-priority row is dropped.
 *
 * <p>This is the shared merge core behind both the static {@code UnionFeatureType} and the dynamic
 * {@code ?branch=} overlay. See {@code docs/design/union-overlay.md} §3.
 *
 * <p><b>Precondition (not checked):</b> both children emit rows sorted ascending by id under one
 * shared total order, and expose the id at the same positional index {@code idPos}. If a child
 * violates the ordering the merged output is silently wrong.
 *
 * <p>Memory is O(1): at most one look-ahead {@link ValueProvider} (the "head") is held per child, so
 * an arbitrarily large child streams through.
 */
public class MergeByIdFeatureStream implements FeatureStream {

    /** index 0 = high priority (wins ties), index 1 = low priority. */
    private final FeatureStream high;
    private final FeatureStream low;
    private final int idPos;
    private final Comparator<Object> idComparator;

    private ValueProvider highHead;
    private ValueProvider lowHead;
    private boolean primed;

    public MergeByIdFeatureStream(FeatureStream high, FeatureStream low, int idPos) {
        this(high, low, idPos, defaultIdComparator());
    }

    public MergeByIdFeatureStream(FeatureStream high, FeatureStream low, int idPos,
            Comparator<Object> idComparator) {
        this.high = high;
        this.low = low;
        this.idPos = idPos;
        this.idComparator = idComparator;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Comparator<Object> defaultIdComparator() {
        // Ids share a type + ordering across children (locked precondition), so natural ordering of
        // the Comparable id value is the shared total order.
        return (a, b) -> ((Comparable) a).compareTo(b);
    }

    private void prime() {
        if (!primed) {
            highHead = high.hasNext() ? high.next() : null;
            lowHead = low.hasNext() ? low.next() : null;
            primed = true;
        }
    }

    @Override
    public boolean hasNext() {
        prime();
        return highHead != null || lowHead != null;
    }

    @Override
    public ValueProvider next() {
        prime();
        if (highHead == null && lowHead == null) {
            throw new java.util.NoSuchElementException();
        }
        // One side drained: emit the other.
        if (lowHead == null) {
            return advanceHigh();
        }
        if (highHead == null) {
            return advanceLow();
        }
        int cmp = idComparator.compare(highHead.getObject(idPos), lowHead.getObject(idPos));
        if (cmp < 0) {
            return advanceHigh();
        }
        if (cmp > 0) {
            return advanceLow();
        }
        // Equal id: high priority wins, both advance (dedup + override in one step).
        ValueProvider winner = advanceHigh();
        lowHead = low.hasNext() ? low.next() : null;
        return winner;
    }

    private ValueProvider advanceHigh() {
        ValueProvider out = highHead;
        highHead = high.hasNext() ? high.next() : null;
        return out;
    }

    private ValueProvider advanceLow() {
        ValueProvider out = lowHead;
        lowHead = low.hasNext() ? low.next() : null;
        return out;
    }

    /**
     * Close both children. If both throw, the low child's failure is added as a suppressed exception
     * to the high child's, so neither stream leaks.
     */
    @Override
    public void close() throws Exception {
        try {
            high.close();
        } catch (Exception e) {
            try {
                low.close();
            } catch (Exception e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
        low.close();
    }

}
