package fi.nls.hakunapi.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyWriters;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyLong;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;

public class UnionFeatureProducerTest {

    private static final class Row implements ValueProvider {
        final Object id;
        final String tag;

        Row(Object id, String tag) {
            this.id = id;
            this.tag = tag;
        }

        @Override public Object getObject(int i) { return i == 0 ? id : tag; }
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

        FakeStream(List<Row> rows) { this.it = rows.iterator(); }
        @Override public boolean hasNext() { return it.hasNext(); }
        @Override public ValueProvider next() { return it.next(); }
        @Override public void close() { closed = true; }
    }

    /** Records which ft it was asked for so we can assert the col was re-bound per child. */
    private static final class FakeProducer implements FeatureProducer {
        final List<Row> rows;
        final boolean throwOnOpen;
        FeatureType seenFt;
        FakeStream lastStream;

        FakeProducer(List<Row> rows, boolean throwOnOpen) {
            this.rows = rows;
            this.throwOnOpen = throwOnOpen;
        }

        @Override
        public FeatureStream getFeatures(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
            seenFt = col.getFt();
            if (throwOnOpen) {
                throw new IllegalStateException("open boom");
            }
            lastStream = new FakeStream(rows);
            return lastStream;
        }

        @Override
        public int getNumberMatched(GetFeatureRequest request, GetFeatureCollection col) {
            return -1;
        }
    }

    private static HakunaProperty newId() {
        return new HakunaPropertyLong("id", "t", "id", false, true, HakunaPropertyWriters.HIDDEN);
    }

    private static FeatureType ftWithId() {
        SimpleFeatureType ft = new SimpleFeatureType() {
            @Override public FeatureProducer getFeatureProducer() { return null; }
        };
        ft.setId(newId());
        ft.setProperties(new ArrayList<>());
        ft.setStaticFilters(new ArrayList<>());
        return ft;
    }

    private static GetFeatureCollection col(FeatureType unionFt) {
        GetFeatureCollection c = new GetFeatureCollection(unionFt);
        // id at position 0; a distinct instance so per-ft binding does not clash. idPosition falls
        // back to name match, which is what real (mixed-backend) participants rely on anyway.
        c.setProperties(new ArrayList<>(Collections.singletonList(newId())));
        return c;
    }

    private static List<Row> drain(FeatureStream s) {
        List<Row> out = new ArrayList<>();
        while (s.hasNext()) {
            out.add((Row) s.next());
        }
        return out;
    }

    @Test
    public void fansToBothChildrenAndMergesBranchWins() throws Exception {
        FeatureType branchFt = ftWithId();
        FeatureType masterFt = ftWithId();
        FeatureType unionFt = ftWithId();

        FakeProducer branch = new FakeProducer(Arrays.asList(new Row(2L, "B"), new Row(3L, "B")), false);
        FakeProducer master = new FakeProducer(Arrays.asList(new Row(1L, "M"), new Row(2L, "M")), false);

        UnionFeatureProducer union = new UnionFeatureProducer(branchFt, branch, masterFt, master);
        List<Row> out = drain(union.getFeatures(new GetFeatureRequest(), col(unionFt)));

        // ids 1(M),2(collision->B),3(B); no dup id.
        assertArrayEquals(new Object[] { 1L, 2L, 3L }, out.stream().map(r -> r.id).toArray());
        assertArrayEquals(new String[] { "M", "B", "B" }, out.stream().map(r -> r.tag).toArray(String[]::new));

        // Each child saw a collection bound to its OWN ft, not the union ft.
        assertEquals(branchFt, branch.seenFt);
        assertEquals(masterFt, master.seenFt);
    }

    @Test
    public void closesBranchStreamIfMasterOpenFails() throws Exception {
        FeatureType branchFt = ftWithId();
        FeatureType masterFt = ftWithId();
        FeatureType unionFt = ftWithId();

        FakeProducer branch = new FakeProducer(Arrays.asList(new Row(1L, "B")), false);
        FakeProducer master = new FakeProducer(Collections.emptyList(), true);

        UnionFeatureProducer union = new UnionFeatureProducer(branchFt, branch, masterFt, master);
        try {
            union.getFeatures(new GetFeatureRequest(), col(unionFt));
            fail("expected the master-open failure to propagate");
        } catch (IllegalStateException e) {
            assertEquals("open boom", e.getMessage());
        }
        // Branch stream opened first; must be closed when master open throws so it does not leak.
        assertTrue("branch producer was invoked", branch.lastStream != null);
        assertTrue("leaked branch stream on master-open failure", branch.lastStream.closed);
    }

    @Test
    public void numberMatchedOmitted() throws Exception {
        FeatureType ft = ftWithId();
        UnionFeatureProducer union = new UnionFeatureProducer(ft,
                new FakeProducer(Collections.emptyList(), false), ft,
                new FakeProducer(Collections.emptyList(), false));
        assertEquals(-1, union.getNumberMatched(new GetFeatureRequest(), col(ft)));
    }
}
