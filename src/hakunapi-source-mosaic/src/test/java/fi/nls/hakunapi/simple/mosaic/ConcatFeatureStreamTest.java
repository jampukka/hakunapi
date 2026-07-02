package fi.nls.hakunapi.simple.mosaic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.HakunaGeometry;

public class ConcatFeatureStreamTest {

    @Test
    public void concatenatesInOrderNoDedup() throws Exception {
        FakeStream a = new FakeStream(1, 2);
        FakeStream b = new FakeStream(2, 3); // duplicate id 2 kept: UNION ALL
        ConcatFeatureStream s = concat(a, b);

        assertEquals(Arrays.asList(1L, 2L, 2L, 3L), drain(s));
    }

    @Test
    public void emptyChildrenAreSkipped() throws Exception {
        ConcatFeatureStream s = concat(new FakeStream(), new FakeStream(7), new FakeStream());
        assertEquals(Arrays.asList(7L), drain(s));
    }

    @Test
    public void noSuppliersIsEmpty() throws Exception {
        ConcatFeatureStream s = new ConcatFeatureStream(new ArrayList<>());
        assertFalse(s.hasNext());
    }

    @Test
    public void opensLazilyOneAtATimeAndClosesDrainedChildren() throws Exception {
        FakeStream a = new FakeStream(1);
        FakeStream b = new FakeStream(2);
        ConcatFeatureStream s = concat(a, b);

        assertTrue(s.hasNext());
        assertTrue("first child opened", a.opened);
        assertFalse("second child not opened until reached", b.opened);

        s.next(); // consume a's only row
        assertTrue(s.hasNext()); // rolls over to b
        assertTrue("first child closed on rollover", a.closed);
        assertTrue("second child now opened", b.opened);
        s.next();
        assertFalse(s.hasNext());
        assertTrue(b.closed);
    }

    @Test
    public void earlyCloseDoesNotOpenUnreachedTiles() throws Exception {
        FakeStream a = new FakeStream(1);
        FakeStream b = new FakeStream(2);
        ConcatFeatureStream s = concat(a, b);

        s.hasNext();
        s.close();
        assertTrue("open child closed", a.closed);
        assertFalse("unreached child never opened", b.opened);
    }

    private static ConcatFeatureStream concat(FakeStream... streams) {
        List<ConcatFeatureStream.StreamSupplier> suppliers = new ArrayList<>();
        for (FakeStream fs : streams) {
            suppliers.add(fs::open);
        }
        return new ConcatFeatureStream(suppliers);
    }

    private static List<Long> drain(FeatureStream s) {
        List<Long> out = new ArrayList<>();
        while (s.hasNext()) {
            out.add(s.next().getLong(0));
        }
        return out;
    }

    /** Minimal id-only stream; records open/close for lazy-lifecycle assertions. */
    private static final class FakeStream implements FeatureStream {
        private final long[] ids;
        private int pos;
        boolean opened;
        boolean closed;

        FakeStream(long... ids) {
            this.ids = ids;
        }

        FeatureStream open() {
            opened = true;
            return this;
        }

        @Override
        public boolean hasNext() {
            return pos < ids.length;
        }

        @Override
        public ValueProvider next() {
            return new IdRow(ids[pos++]);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** ValueProvider exposing a single long id at index 0. */
    private static final class IdRow implements ValueProvider {
        private final long id;

        IdRow(long id) {
            this.id = id;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public Long getLong(int i) {
            return id;
        }

        @Override
        public Object getObject(int i) {
            return id;
        }

        @Override public boolean isNull(int i) { return false; }
        @Override public Boolean getBoolean(int i) { return null; }
        @Override public Integer getInt(int i) { return (int) id; }
        @Override public Float getFloat(int i) { return null; }
        @Override public Double getDouble(int i) { return null; }
        @Override public String getString(int i) { return null; }
        @Override public Instant getInstant(int i) { return null; }
        @Override public LocalDateTime getLocalDateTime(int i) { return null; }
        @Override public LocalDate getLocalDate(int i) { return null; }
        @Override public HakunaGeometry getHakunaGeometry(int i) { return null; }
        @Override public Object[] getArray(int i) { return null; }
        @Override public UUID getUUID(int i) { return null; }
    }

}
