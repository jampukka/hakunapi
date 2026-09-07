package fi.nls.hakunapi.tiles.source.vectortile.geom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import fi.nls.hakunapi.core.geom.HakunaGeometryType;
import fi.nls.hakunapi.tiles.source.vectortile.geom.MvtCommands.Ring;

public class MvtPolygonGeometryWriterTest {

    private static final int EXTENT = 4096;
    private static final double TILE_SPAN = 1000.0;
    private static final double RES = TILE_SPAN / EXTENT;

    private MvtPolygonGeometryWriter newWriter() {
        MvtPolygonGeometryWriter w = new MvtPolygonGeometryWriter();
        w.initTile(0.0, TILE_SPAN, RES, EXTENT, EXTENT, EXTENT, 0);
        return w;
    }

    private List<Ring> rings(MvtPolygonGeometryWriter w) {
        return MvtCommands.decode(w.encoder().commands(), w.encoder().length());
    }

    @Test
    public void squareInsideMapsToTileSpace() throws Exception {
        MvtPolygonGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.POLYGON, 3857, 2);
        w.startRing(); // polygon wrapper
        w.startRing(); // exterior leaf
        ring(w, 250, 250, 750, 250, 750, 750, 250, 750);
        w.endRing();
        w.endRing();
        w.end();

        List<Ring> rs = rings(w);
        assertEquals(1, rs.size());
        Ring r = rs.get(0);
        assertEquals(4, r.pts.size());
        // world (250,250) -> px 1024, py (1000-250)/RES = 3072
        boolean found = false;
        for (int[] p : r.pts) {
            if (p[0] == 1024 && p[1] == 3072) found = true;
        }
        assertTrue("expected transformed corner (1024,3072)", found);
    }

    @Test
    public void squareCrossingEdgeClippedToExtent() throws Exception {
        MvtPolygonGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.POLYGON, 3857, 2);
        w.startRing();
        w.startRing();
        ring(w, 500, 500, 1500, 500, 1500, 1500, 500, 1500);
        w.endRing();
        w.endRing();
        w.end();

        List<Ring> rs = rings(w);
        assertEquals(1, rs.size());
        for (int[] p : rs.get(0).pts) {
            assertTrue(p[0] >= 0 && p[0] <= EXTENT);
            assertTrue(p[1] >= 0 && p[1] <= EXTENT);
        }
    }

    @Test
    public void degenerateSliverDropped() throws Exception {
        MvtPolygonGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.POLYGON, 3857, 2);
        w.startRing();
        w.startRing();
        ring(w, 100, 100, 100.1, 100, 100.1, 100.1, 100, 100.1);
        w.endRing();
        w.endRing();
        w.end();
        assertEquals(0, rings(w).size());
    }

    @Test
    public void polygonWithHoleEmitsOppositeWinding() throws Exception {
        MvtPolygonGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.POLYGON, 3857, 2);
        w.startRing(); // polygon wrapper
        w.startRing(); // exterior
        ring(w, 100, 100, 900, 100, 900, 900, 100, 900);
        w.endRing();
        w.startRing(); // hole (same source orientation as exterior here)
        ring(w, 300, 300, 700, 300, 700, 700, 300, 700);
        w.endRing();
        w.endRing();
        w.end();

        List<Ring> rs = rings(w);
        assertEquals(2, rs.size());
        // exterior and hole must wind opposite directions
        double aExt = rs.get(0).signedArea();
        double aHole = rs.get(1).signedArea();
        assertTrue("exterior nonzero", aExt != 0);
        assertTrue("hole nonzero", aHole != 0);
        assertTrue("hole opposite winding to exterior", (aExt > 0) != (aHole > 0));
    }

    @Test
    public void multipolygonEmitsTwoExteriors() throws Exception {
        MvtPolygonGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.MULTIPOLYGON, 3857, 2);
        w.startRing(); // multi wrapper
        w.startRing(); // part 1 wrapper
        w.startRing(); // part 1 exterior
        ring(w, 100, 100, 300, 100, 300, 300, 100, 300);
        w.endRing();
        w.endRing();
        w.startRing(); // part 2 wrapper
        w.startRing(); // part 2 exterior
        ring(w, 600, 600, 800, 600, 800, 800, 600, 800);
        w.endRing();
        w.endRing();
        w.endRing();
        w.end();

        assertEquals(2, rings(w).size());
    }

    private static void ring(MvtPolygonGeometryWriter w, double... xy) throws Exception {
        for (int i = 0; i < xy.length; i += 2) {
            w.writeCoordinate(xy[i], xy[i + 1]);
        }
    }
}
