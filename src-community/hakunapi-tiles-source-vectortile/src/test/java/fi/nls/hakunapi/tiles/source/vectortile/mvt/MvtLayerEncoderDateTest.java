package fi.nls.hakunapi.tiles.source.vectortile.mvt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtDecoder.Feature;
import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtDecoder.Layer;

/**
 * A date is held in the value table as its epoch day and rendered to ISO-8601
 * only when the layer is written, so tagging one must be indistinguishable on
 * the wire from tagging the string {@link LocalDate#toString()} produces.
 */
public class MvtLayerEncoderDateTest {

    private byte[] tile(MvtLayerEncoder... layers) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MvtTileWriter.write(bos, layers);
        return bos.toByteArray();
    }

    @Test
    public void testDateIsIdenticalToItsToStringOnTheWire() throws Exception {
        LocalDate[] dates = {
            LocalDate.of(2025, 3, 25),
            LocalDate.of(1, 1, 1),
            LocalDate.of(999, 12, 31),
            LocalDate.of(10000, 6, 15),
            LocalDate.of(-44, 3, 15),
            LocalDate.MIN,
            LocalDate.MAX,
        };

        MvtLayerEncoder asDate = new MvtLayerEncoder();
        MvtLayerEncoder asString = new MvtLayerEncoder();
        encode(asDate, dates, true);
        encode(asString, dates, false);

        assertArrayEquals(tile(asString), tile(asDate));
    }

    @Test
    public void testRepeatedDateIsInternedOnce() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        l.reset("t");
        int k = l.keyIndex("alkupvm");
        LocalDate date = LocalDate.of(2020, 2, 29);
        for (int i = 0; i < 100; i++) {
            l.beginFeature();
            l.tagDate(k, date);
            int[] g = {9, 2, 2};
            l.endFeature(i + 1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        }
        l.finish();

        Layer layer = MvtDecoder.decodeTile(tile(l)).get(0);
        assertEquals(1, layer.values.size());
        assertEquals("2020-02-29", layer.values.get(0));
        for (Feature f : layer.features) {
            assertArrayEquals(new int[] {0, 0}, f.tags);
        }
    }

    /**
     * A date and the same date already interned as a plain string are separate
     * slots - one is a number, the other bytes - but both decode to the same
     * text, so the tile stays correct either way.
     */
    @Test
    public void testDateAndEqualStringBothDecodeToTheSameText() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        l.reset("t");
        int k = l.keyIndex("d");
        l.beginFeature();
        l.tagString(k, "2025-03-25");
        int[] g = {9, 2, 2};
        l.endFeature(1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        l.beginFeature();
        l.tagDate(k, LocalDate.of(2025, 3, 25));
        l.endFeature(2, MvtLayerEncoder.GEOM_POINT, g, g.length);
        l.finish();

        Layer layer = MvtDecoder.decodeTile(tile(l)).get(0);
        for (Object v : layer.values) {
            assertEquals("2025-03-25", v);
        }
    }

    /**
     * Distinct dates past the hash table's initial capacity exercise rehash(),
     * which must re-hash a date slot on its epoch day rather than on its bytes.
     */
    @Test
    public void testManyDistinctDatesSurviveRehash() throws Exception {
        int n = 5000;
        MvtLayerEncoder asDate = new MvtLayerEncoder();
        MvtLayerEncoder asString = new MvtLayerEncoder();
        LocalDate[] dates = new LocalDate[n];
        LocalDate date = LocalDate.of(1990, 1, 1);
        for (int i = 0; i < n; i++) {
            dates[i] = date;
            date = date.plusDays(1);
        }
        encode(asDate, dates, true);
        encode(asString, dates, false);

        Layer layer = MvtDecoder.decodeTile(tile(asDate)).get(0);
        assertEquals(n, layer.values.size());
        assertArrayEquals(tile(asString), tile(asDate));
    }

    /** The date scratch is reused across tiles, so a trimmed encoder must repeat. */
    @Test
    public void testReuseAfterTrimProducesIdenticalBytes() throws Exception {
        LocalDate[] dates = {LocalDate.of(2001, 1, 1), LocalDate.of(2002, 2, 2)};

        MvtLayerEncoder fresh = new MvtLayerEncoder();
        encode(fresh, dates, true);
        byte[] expected = tile(fresh);

        MvtLayerEncoder reused = new MvtLayerEncoder();
        encode(reused, new LocalDate[] {LocalDate.of(1999, 9, 9)}, true);
        tile(reused);
        reused.trim();
        encode(reused, dates, true);

        assertArrayEquals(expected, tile(reused));
    }

    /**
     * The value table keys a date on its packed year/month/day, so two different
     * dates must never pack to the same long - a collision would silently emit
     * one date's text for the other.
     */
    @Test
    public void testPackedDatesAreDistinctAcrossTheWholeYearRange() throws Exception {
        int[] years = {-999999999, -10000, -1000, -999, -44, -1, 0, 1, 999, 1000,
                9999, 10000, 999999999};
        List<LocalDate> all = new ArrayList<>();
        for (int i = 0; i < years.length; i++) {
            for (int m = 1; m <= 12; m++) {
                int len = YearMonth.of(years[i], m).lengthOfMonth();
                for (int d = 1; d <= len; d++) {
                    all.add(LocalDate.of(years[i], m, d));
                }
            }
        }
        LocalDate[] dates = all.toArray(new LocalDate[0]);

        MvtLayerEncoder asDate = new MvtLayerEncoder();
        MvtLayerEncoder asString = new MvtLayerEncoder();
        encode(asDate, dates, true);
        encode(asString, dates, false);

        Layer layer = MvtDecoder.decodeTile(tile(asDate)).get(0);
        assertEquals(dates.length, layer.values.size());
        assertArrayEquals(tile(asString), tile(asDate));
    }

    private void encode(MvtLayerEncoder l, LocalDate[] dates, boolean asDate) {
        l.reset("t");
        int k = l.keyIndex("alkupvm");
        for (int i = 0; i < dates.length; i++) {
            l.beginFeature();
            if (asDate) {
                l.tagDate(k, dates[i]);
            } else {
                l.tagString(k, dates[i].toString());
            }
            int[] g = {9, 2, 2};
            l.endFeature(i + 1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        }
        l.finish();
    }

}
