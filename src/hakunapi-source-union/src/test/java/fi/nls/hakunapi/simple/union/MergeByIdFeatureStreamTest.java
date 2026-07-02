package fi.nls.hakunapi.simple.union;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.Test;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.HakunaGeometry;

public class MergeByIdFeatureStreamTest {

    /** A row carrying an id at position 0 and a "source tag" at position 1 to prove which side won. */
    private static final class Row implements ValueProvider {
        final Object id;
        final String tag;

        Row(Object id, String tag) {
            this.id = id;
            this.tag = tag;
        }

        @Override
        public Object getObject(int i) {
            return i == 0 ? id : tag;
        }

        @Override public int size() { return 2; }
        @Override public boolean isNull(int i) { return getObject(i) == null; }
        @Override public String getString(int i) { return String.valueOf(getObject(i)); }
        @Override public Long getLong(int i) { return ((Number) getObject(i)).longValue(); }
        @Override public Boolean getBoolean(int i) { return null; }
        @Override public Integer getInt(int i) { return null; }
        @Override public Float getFloat(int i) { return null; }
        @Override public Double getDouble(int i) { return null; }
        @Override public Instant getInstant(int i) { return null; }
        @Override public LocalDateTime getLocalDateTime(int i) { return null; }
        @Override public LocalDate getLocalDate(int i) { return null; }
        @Override public HakunaGeometry getHakunaGeometry(int i) { return null; }
        @Override public Object[] getArray(int i) { return null; }
        @Override public UUID getUUID(int i) { return null; }
    }

    private static final class FakeStream implements FeatureStream {
        final Iterator<Row> it;
        boolean closed;
        RuntimeException closeError;

        FakeStream(List<Row> rows) {
            this.it = rows.iterator();
        }

        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public ValueProvider next() { return it.next(); }

        @Override
        public void close() {
            closed = true;
            if (closeError != null) {
                throw closeError;
            }
        }
    }

    private static Row r(Object id, String tag) {
        return new Row(id, tag);
    }

    private static List<Row> drain(MergeByIdFeatureStream m) {
        List<Row> out = new ArrayList<>();
        while (m.hasNext()) {
            out.add((Row) m.next());
        }
        return out;
    }

    private static String[] tags(List<Row> rows) {
        return rows.stream().map(row -> row.tag).toArray(String[]::new);
    }

    private static Object[] ids(List<Row> rows) {
        return rows.stream().map(row -> row.id).toArray();
    }

    @Test
    public void interleavesByIdAscending() {
        FakeStream high = new FakeStream(Arrays.asList(r(1L, "H"), r(3L, "H"), r(5L, "H")));
        FakeStream low = new FakeStream(Arrays.asList(r(2L, "L"), r(4L, "L"), r(6L, "L")));
        List<Row> out = drain(new MergeByIdFeatureStream(high, low, 0));
        assertArrayEquals(new Object[] { 1L, 2L, 3L, 4L, 5L, 6L }, ids(out));
    }

    @Test
    public void highPriorityWinsTieAndBothAdvance() {
        FakeStream high = new FakeStream(Arrays.asList(r(1L, "H"), r(2L, "H"), r(3L, "H")));
        FakeStream low = new FakeStream(Arrays.asList(r(2L, "L"), r(3L, "L"), r(4L, "L")));
        List<Row> out = drain(new MergeByIdFeatureStream(high, low, 0));
        // id 2 and 3 collide -> H wins, L dropped; 4 only in low; no duplicate ids.
        assertArrayEquals(new Object[] { 1L, 2L, 3L, 4L }, ids(out));
        assertArrayEquals(new String[] { "H", "H", "H", "L" }, tags(out));
    }

    @Test
    public void drainsRemainderWhenHighEmpty() {
        FakeStream high = new FakeStream(Arrays.asList(r(5L, "H")));
        FakeStream low = new FakeStream(Arrays.asList(r(1L, "L"), r(2L, "L"), r(5L, "L"), r(9L, "L")));
        List<Row> out = drain(new MergeByIdFeatureStream(high, low, 0));
        assertArrayEquals(new Object[] { 1L, 2L, 5L, 9L }, ids(out));
        // id 5 collision -> H wins.
        assertArrayEquals(new String[] { "L", "L", "H", "L" }, tags(out));
    }

    @Test
    public void oneSideEmpty() {
        FakeStream high = new FakeStream(new ArrayList<>());
        FakeStream low = new FakeStream(Arrays.asList(r(1L, "L"), r(2L, "L")));
        List<Row> out = drain(new MergeByIdFeatureStream(high, low, 0));
        assertArrayEquals(new Object[] { 1L, 2L }, ids(out));
    }

    @Test
    public void bothEmpty() {
        MergeByIdFeatureStream m =
                new MergeByIdFeatureStream(new FakeStream(new ArrayList<>()), new FakeStream(new ArrayList<>()), 0);
        assertFalse(m.hasNext());
        try {
            m.next();
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
            // ok
        }
    }

    @Test
    public void stringIdsUseNaturalOrder() {
        FakeStream high = new FakeStream(Arrays.asList(r("a", "H"), r("c", "H")));
        FakeStream low = new FakeStream(Arrays.asList(r("b", "L"), r("c", "L")));
        List<Row> out = drain(new MergeByIdFeatureStream(high, low, 0));
        assertArrayEquals(new Object[] { "a", "b", "c" }, ids(out));
        assertArrayEquals(new String[] { "H", "L", "H" }, tags(out));
    }

    @Test
    public void closeClosesBothChildren() throws Exception {
        FakeStream high = new FakeStream(Arrays.asList(r(1L, "H")));
        FakeStream low = new FakeStream(Arrays.asList(r(2L, "L")));
        MergeByIdFeatureStream m = new MergeByIdFeatureStream(high, low, 0);
        m.close();
        assertTrue(high.closed);
        assertTrue(low.closed);
    }

    @Test
    public void closeClosesLowEvenWhenHighThrows() {
        FakeStream high = new FakeStream(Arrays.asList(r(1L, "H")));
        FakeStream low = new FakeStream(Arrays.asList(r(2L, "L")));
        high.closeError = new RuntimeException("high boom");
        MergeByIdFeatureStream m = new MergeByIdFeatureStream(high, low, 0);
        try {
            m.close();
            fail("expected the high-child close failure to propagate");
        } catch (Exception e) {
            assertEquals("high boom", e.getMessage());
        }
        assertTrue("low child must still be closed", low.closed);
    }
}
