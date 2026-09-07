package fi.nls.hakunapi.tiles.source.vectortile.geom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import fi.nls.hakunapi.core.geom.HakunaGeometryType;
import fi.nls.hakunapi.tiles.source.vectortile.geom.MvtCommands.Ring;

public class MvtLineGeometryWriterTest {

    private static final int EXTENT = 4096;
    private static final double TILE_SPAN = 1000.0;
    private static final double RES = TILE_SPAN / EXTENT;

    private MvtLineGeometryWriter newWriter() {
        MvtLineGeometryWriter w = new MvtLineGeometryWriter();
        w.initTile(0.0, TILE_SPAN, RES, EXTENT, EXTENT, EXTENT, 0);
        return w;
    }

    private List<Ring> parts(MvtLineGeometryWriter w) {
        return MvtCommands.decode(w.encoder().commands(), w.encoder().length());
    }

    private void line(MvtLineGeometryWriter w, double... xy) throws Exception {
        w.startRing();
        for (int i = 0; i < xy.length; i += 2) {
            w.writeCoordinate(xy[i], xy[i + 1]);
        }
        w.endRing();
    }

    @Test
    public void simpleLineInsideOnePart() throws Exception {
        MvtLineGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.LINESTRING, 3857, 2);
        line(w, 100, 100, 900, 100, 900, 900);
        w.end();

        List<Ring> ps = parts(w);
        assertEquals(1, ps.size());
        assertEquals(3, ps.get(0).pts.size());
        // first MoveTo: world (100,100) -> px 409.6->410, py (1000-100)/RES=3686.4->3686
        int[] p0 = ps.get(0).pts.get(0);
        assertEquals(410, p0[0]);
        assertEquals(3686, p0[1]);
    }

    @Test
    public void lineExitingAndReenteringSplitsIntoTwoParts() throws Exception {
        MvtLineGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.LINESTRING, 3857, 2);
        // inside -> way outside (right) -> back inside: two visible parts
        line(w, 100, 500, 5000, 500, 100, 600);
        w.end();

        List<Ring> ps = parts(w);
        assertEquals(2, ps.size());
        // every emitted vertex within extent
        for (Ring r : ps) {
            for (int[] p : r.pts) {
                assertTrue(p[0] >= 0 && p[0] <= EXTENT);
                assertTrue(p[1] >= 0 && p[1] <= EXTENT);
            }
        }
    }

    @Test
    public void lineFullyOutsideEmitsNothing() throws Exception {
        MvtLineGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.LINESTRING, 3857, 2);
        line(w, 2000, 2000, 3000, 3000);
        w.end();
        assertEquals(0, w.encoder().length());
    }

    @Test
    public void collinearMidpointsDropped() throws Exception {
        MvtLineGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.LINESTRING, 3857, 2);
        // straight horizontal line with a redundant midpoint
        line(w, 100, 500, 500, 500, 900, 500);
        w.end();

        List<Ring> ps = parts(w);
        assertEquals(1, ps.size());
        assertEquals(2, ps.get(0).pts.size()); // midpoint dropped
    }

    @Test
    public void multiLineStringEmitsPartPerLine() throws Exception {
        MvtLineGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.MULTILINESTRING, 3857, 2);
        w.startRing(); // multi wrapper
        line(w, 100, 100, 300, 100);
        line(w, 600, 600, 800, 800);
        w.endRing();
        w.end();

        assertEquals(2, parts(w).size());
    }

    @Test
    public void clippedLineStartsAtRectBoundary() throws Exception {
        MvtLineGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.LINESTRING, 3857, 2);
        // starts outside left, ends inside -> first vertex clamped to x=0
        line(w, -500, 500, 500, 500);
        w.end();

        List<Ring> ps = parts(w);
        assertEquals(1, ps.size());
        assertEquals(0, ps.get(0).pts.get(0)[0]); // entered at left edge x=0
    }
}
