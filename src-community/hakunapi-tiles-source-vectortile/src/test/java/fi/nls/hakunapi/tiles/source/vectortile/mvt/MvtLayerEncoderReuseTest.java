package fi.nls.hakunapi.tiles.source.vectortile.mvt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.Test;

import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtDecoder.Layer;

/**
 * The encoder is held per worker thread and reused across tiles, so a tile
 * encoded on a used encoder must be identical to the same tile encoded on a
 * fresh one - including after a layer large enough to trip the retained-capacity
 * ceiling in {@link MvtLayerEncoder#trim()}.
 */
public class MvtLayerEncoderReuseTest {

    /** Distinct values comfortably past RETAINED_VALUES (16384). */
    private static final int OVER_CEILING = 40000;

    private byte[] tile(MvtLayerEncoder l) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MvtTileWriter.write(bos, l);
        return bos.toByteArray();
    }

    /** A small layer with a mix of string and numeric values. */
    private void encodeSmall(MvtLayerEncoder l) {
        l.reset("small");
        int kName = l.keyIndex("name");
        int kNum = l.keyIndex("num");
        for (int i = 0; i < 32; i++) {
            l.beginFeature();
            l.tagString(kName, "v" + (i % 5));
            l.tagLong(kNum, i % 7);
            int[] geom = {9, 10 + i, 14};
            l.endFeature(i + 1, MvtLayerEncoder.GEOM_POINT, geom, geom.length);
        }
        l.finish();
    }

    /** A layer whose distinct value count grows the table past the ceiling. */
    private void encodeLarge(MvtLayerEncoder l) {
        l.reset("large");
        int kId = l.keyIndex("id");
        for (int i = 0; i < OVER_CEILING; i++) {
            l.beginFeature();
            l.tagLong(kId, i);
            int[] geom = {9, 10, 14};
            l.endFeature(i + 1, MvtLayerEncoder.GEOM_POINT, geom, geom.length);
        }
        l.finish();
    }

    @Test
    public void reuseAfterTrimProducesIdenticalBytes() throws Exception {
        MvtLayerEncoder fresh = new MvtLayerEncoder();
        encodeSmall(fresh);
        byte[] expected = tile(fresh);

        MvtLayerEncoder reused = new MvtLayerEncoder();
        encodeSmall(reused);
        tile(reused);
        reused.trim();
        encodeSmall(reused);
        assertArrayEquals(expected, tile(reused));
    }

    @Test
    public void reuseAfterAnOversizedLayerProducesIdenticalBytes() throws Exception {
        MvtLayerEncoder fresh = new MvtLayerEncoder();
        encodeSmall(fresh);
        byte[] expected = tile(fresh);

        MvtLayerEncoder reused = new MvtLayerEncoder();
        encodeLarge(reused);
        tile(reused);
        reused.trim();
        encodeSmall(reused);
        assertArrayEquals(expected, tile(reused));
    }

    @Test
    public void anOversizedLayerItselfRoundTrips() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        encodeLarge(l);
        List<Layer> layers = MvtDecoder.decodeTile(tile(l));
        assertEquals(1, layers.size());
        assertEquals(OVER_CEILING, layers.get(0).values.size());
        assertEquals(OVER_CEILING, layers.get(0).features.size());
    }

    @Test
    public void trimDoesNotRetainInternedStrings() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        encodeSmall(l);
        tile(l);
        l.trim();
        // A second tile must intern its strings afresh rather than find stale
        // slots from the first: same input, same output.
        encodeSmall(l);
        byte[] second = tile(l);
        MvtLayerEncoder fresh = new MvtLayerEncoder();
        encodeSmall(fresh);
        assertArrayEquals(tile(fresh), second);
    }
}
