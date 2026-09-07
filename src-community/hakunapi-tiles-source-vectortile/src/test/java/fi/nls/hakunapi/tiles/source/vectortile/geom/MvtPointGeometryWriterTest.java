package fi.nls.hakunapi.tiles.source.vectortile.geom;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import fi.nls.hakunapi.core.geom.HakunaGeometryType;
import fi.nls.hakunapi.tiles.source.vectortile.geom.MvtCommands.Ring;

public class MvtPointGeometryWriterTest {

    private static final int EXTENT = 4096;
    private static final double TILE_SPAN = 1000.0;
    private static final double RES = TILE_SPAN / EXTENT;

    private MvtPointGeometryWriter newWriter() {
        MvtPointGeometryWriter w = new MvtPointGeometryWriter();
        w.initTile(0.0, TILE_SPAN, RES, EXTENT, EXTENT, EXTENT, 0);
        return w;
    }

    private List<Ring> decode(MvtPointGeometryWriter w) {
        return MvtCommands.decode(w.encoder().commands(), w.encoder().length());
    }

    @Test
    public void singlePointTransformsToTileSpace() throws Exception {
        MvtPointGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.POINT, 3857, 2);
        w.writeCoordinate(250, 250);
        w.end();

        List<Ring> rs = decode(w);
        assertEquals(1, rs.size());
        int[] p = rs.get(0).pts.get(0);
        // world (250,250) -> px 1024, py (1000-250)/RES = 3072
        assertEquals(1024, p[0]);
        assertEquals(3072, p[1]);
    }

    @Test
    public void pointOutsideRejected() throws Exception {
        MvtPointGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.POINT, 3857, 2);
        w.writeCoordinate(2000, 2000); // outside the tile bbox
        w.end();
        assertEquals(0, w.encoder().length());
    }

    @Test
    public void bufferKeepsNearbyPoint() throws Exception {
        MvtPointGeometryWriter w = new MvtPointGeometryWriter();
        w.initTile(0.0, TILE_SPAN, RES, EXTENT, EXTENT, EXTENT, 64); // 64px buffer
        w.resetFeature();
        w.init(HakunaGeometryType.POINT, 3857, 2);
        // just past the right edge: world x slightly > 1000 -> px slightly > 4096
        w.writeCoordinate(1005, 500); // px = 1005/RES ~ 4116, within 4096+64
        w.end();
        assertEquals(1, decode(w).size());
    }

    @Test
    public void multiPointEmitsSingleMoveToRun() throws Exception {
        MvtPointGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.MULTIPOINT, 3857, 2);
        w.startRing();
        w.writeCoordinate(100, 100);
        w.writeCoordinate(200, 200);
        w.writeCoordinate(300, 300);
        w.endRing();
        w.end();

        // one MoveTo command word with count 3 -> 1 + 3*2 = 7 ints
        assertEquals(7, w.encoder().length());
        int cmd = w.encoder().commands()[0];
        assertEquals(MvtGeometryEncoder.CMD_MOVETO, cmd & 0x7);
        assertEquals(3, cmd >> 3);
        assertEquals(3, decode(w).size());
    }

    @Test
    public void consecutiveDuplicateDropped() throws Exception {
        MvtPointGeometryWriter w = newWriter();
        w.resetFeature();
        w.init(HakunaGeometryType.MULTIPOINT, 3857, 2);
        w.startRing();
        w.writeCoordinate(500, 500);
        w.writeCoordinate(500.01, 500.01); // snaps to same pixel
        w.writeCoordinate(600, 600);
        w.endRing();
        w.end();
        assertEquals(2, decode(w).size());
    }
}
