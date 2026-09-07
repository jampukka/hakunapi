package fi.nls.hakunapi.tiles.source.vectortile.mvt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.Test;

import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtDecoder.Feature;
import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtDecoder.Layer;

public class MvtLayerEncoderTest {

    private byte[] tile(MvtLayerEncoder... layers) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        MvtTileWriter.write(bos, layers);
        return bos.toByteArray();
    }

    @Test
    public void singlePointFeatureRoundTrips() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        l.reset("points");
        int kName = l.keyIndex("name");
        l.beginFeature();
        l.tagString(kName, "A");
        // geometry: MoveTo(1) to (5,7): cmd=(1|1<<3)=9, zigzag(5)=10, zigzag(7)=14
        int[] geom = {9, 10, 14};
        l.endFeature(42, MvtLayerEncoder.GEOM_POINT, geom, geom.length);
        l.finish();

        List<Layer> layers = MvtDecoder.decodeTile(tile(l));
        assertEquals(1, layers.size());
        Layer layer = layers.get(0);
        assertEquals("points", layer.name);
        assertEquals(2, layer.version);
        assertEquals(4096, layer.extent);
        assertEquals(1, layer.keys.size());
        assertEquals("name", layer.keys.get(0));
        assertEquals(1, layer.values.size());
        assertEquals("A", layer.values.get(0));

        assertEquals(1, layer.features.size());
        Feature f = layer.features.get(0);
        assertEquals(42, f.id);
        assertEquals(MvtLayerEncoder.GEOM_POINT, f.type);
        assertArrayEquals(new int[] {0, 0}, f.tags); // key0, value0
        assertArrayEquals(geom, f.geometry);
    }

    @Test
    public void valuesAreDedupedAcrossFeatures() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        l.reset("t");
        int kCat = l.keyIndex("cat");
        for (int i = 0; i < 3; i++) {
            l.beginFeature();
            l.tagString(kCat, "road"); // same value 3x
            int[] g = {9, 2, 2};
            l.endFeature(i + 1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        }
        l.finish();

        Layer layer = MvtDecoder.decodeTile(tile(l)).get(0);
        assertEquals(1, layer.values.size()); // deduped to one
        assertEquals("road", layer.values.get(0));
        for (Feature f : layer.features) {
            assertArrayEquals(new int[] {0, 0}, f.tags);
        }
    }

    @Test
    public void mixedValueTypesPreserved() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        l.reset("t");
        int kS = l.keyIndex("s");
        int kD = l.keyIndex("d");
        int kI = l.keyIndex("i");
        int kB = l.keyIndex("b");
        l.beginFeature();
        l.tagString(kS, "x");
        l.tagDouble(kD, 3.5);
        l.tagLong(kI, 7);
        l.tagBool(kB, true);
        int[] g = {9, 0, 0};
        l.endFeature(1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        l.finish();

        Layer layer = MvtDecoder.decodeTile(tile(l)).get(0);
        assertEquals(4, layer.keys.size());
        assertEquals(4, layer.values.size());
        // values emitted in index order: x, 3.5, 7L, true
        assertEquals("x", layer.values.get(0));
        assertEquals(3.5, (Double) layer.values.get(1), 0);
        assertEquals(7L, ((Long) layer.values.get(2)).longValue());
        assertEquals(Boolean.TRUE, layer.values.get(3));

        Feature f = layer.features.get(0);
        assertArrayEquals(new int[] {0, 0, 1, 1, 2, 2, 3, 3}, f.tags);
    }

    @Test
    public void multipleLayersInTile() throws Exception {
        MvtLayerEncoder a = new MvtLayerEncoder();
        a.reset("a");
        a.beginFeature();
        int[] g = {9, 0, 0};
        a.endFeature(1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        a.finish();

        MvtLayerEncoder b = new MvtLayerEncoder();
        b.reset("b");
        b.beginFeature();
        b.endFeature(2, MvtLayerEncoder.GEOM_POINT, g, g.length);
        b.finish();

        List<Layer> layers = MvtDecoder.decodeTile(tile(a, b));
        assertEquals(2, layers.size());
        assertEquals("a", layers.get(0).name);
        assertEquals("b", layers.get(1).name);
    }

    @Test
    public void customExtentEmitted() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        l.reset("t");
        l.setExtent(8192);
        l.beginFeature();
        int[] g = {9, 0, 0};
        l.endFeature(1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        l.finish();

        Layer layer = MvtDecoder.decodeTile(tile(l)).get(0);
        assertEquals(8192, layer.extent);
    }

    @Test
    public void resetReusesEncoder() throws Exception {
        MvtLayerEncoder l = new MvtLayerEncoder();
        l.reset("first");
        l.beginFeature();
        int[] g = {9, 0, 0};
        l.endFeature(1, MvtLayerEncoder.GEOM_POINT, g, g.length);
        l.finish();
        int firstLen = l.length();
        assertTrue(firstLen > 0);

        l.reset("second");
        l.beginFeature();
        l.endFeature(9, MvtLayerEncoder.GEOM_POINT, g, g.length);
        l.finish();

        Layer layer = MvtDecoder.decodeTile(tile(l)).get(0);
        assertEquals("second", layer.name);
        assertEquals(1, layer.features.size());
        assertEquals(9, layer.features.get(0).id);
    }
}
