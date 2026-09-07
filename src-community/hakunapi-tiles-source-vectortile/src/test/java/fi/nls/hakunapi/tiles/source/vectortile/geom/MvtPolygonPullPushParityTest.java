package fi.nls.hakunapi.tiles.source.vectortile.geom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;

import fi.nls.hakunapi.core.geom.HakunaGeometryEWKB;
import fi.nls.hakunapi.core.geom.HakunaGeometryJTS;
import fi.nls.hakunapi.core.geom.NavigableHakunaGeometry;

/**
 * The pull path ({@link MvtPolygonGeometryWriter#writeNavigable}) and the push
 * path ({@code geometry.write(writer)}) must produce byte-identical MVT command
 * streams. This pins that, and doubles as a micro-benchmark when run with
 * {@code -Dmvt.bench=true}.
 */
public class MvtPolygonPullPushParityTest {

    private static final int EXTENT = 4096;
    private static final double TILE_SPAN = 1000.0;
    private static final double RES = TILE_SPAN / EXTENT;
    private static final GeometryFactory GF = new GeometryFactory();

    private MvtPolygonGeometryWriter newWriter() {
        MvtPolygonGeometryWriter w = new MvtPolygonGeometryWriter();
        w.initTile(0.0, TILE_SPAN, RES, EXTENT, EXTENT, EXTENT, 0);
        return w;
    }

    private int[] push(MvtPolygonGeometryWriter w, Geometry g) throws Exception {
        w.resetFeature();
        new HakunaGeometryJTS(g).write(w);
        w.end();
        return java.util.Arrays.copyOf(w.encoder().commands(), w.encoder().length());
    }

    private int[] pull(MvtPolygonGeometryWriter w, NavigableHakunaGeometry nav) {
        w.resetFeature();
        w.writeNavigable(nav);
        return java.util.Arrays.copyOf(w.encoder().commands(), w.encoder().length());
    }

    private void parity(Geometry g) throws Exception {
        MvtPolygonGeometryWriter w = newWriter();
        int[] viaPush = push(w, g);
        // pull over both navigable backings
        int[] viaJts = pull(w, new HakunaGeometryJTS(g).toNavigable());
        byte[] ewkb = new HakunaGeometryJTS(g).toEWKB();
        int[] viaEwkb = pull(w, new HakunaGeometryEWKB(ewkb).toNavigable());
        assertArrayEquals("pull(JTS) != push", viaPush, viaJts);
        assertArrayEquals("pull(EWKB) != push", viaPush, viaEwkb);
    }

    @Test public void squareParity() throws Exception {
        parity(square(250, 250, 500));
    }

    @Test public void squareWithHoleParity() throws Exception {
        parity(squareWithHole());
    }

    @Test public void crossingEdgeParity() throws Exception {
        parity(square(800, 800, 400)); // spills past the tile, exercises clip
    }

    @Test public void multiPolygonParity() throws Exception {
        Polygon a = square(100, 100, 200);
        Polygon b = square(600, 600, 300);
        parity(GF.createMultiPolygon(new Polygon[] {a, b}));
    }

    @Test public void randomParity() throws Exception {
        Random rnd = new Random(42);
        for (int it = 0; it < 50; it++) {
            int n = 5 + rnd.nextInt(20);
            Coordinate[] cs = new Coordinate[n + 1];
            for (int i = 0; i < n; i++) {
                cs[i] = new Coordinate(rnd.nextInt(1200) - 100, rnd.nextInt(1200) - 100);
            }
            cs[n] = cs[0];
            try {
                Polygon p = GF.createPolygon(cs);
                if (!p.isValid()) continue;
                parity(p);
            } catch (RuntimeException ignore) {
                // skip self-intersecting random rings
            }
        }
    }

    @Test public void benchmark() throws Exception {
        if (!Boolean.getBoolean("mvt.bench")) {
            return;
        }
        // A chunky multipolygon: several parts, each a many-vertex ring + a hole.
        Polygon[] parts = new Polygon[8];
        Random rnd = new Random(7);
        for (int p = 0; p < parts.length; p++) {
            int cx = 100 + p * 90, cy = 100 + (p % 3) * 250;
            parts[p] = ringPolygon(cx, cy, 60, 64, rnd);
        }
        byte[] ewkb = new HakunaGeometryJTS(GF.createMultiPolygon(parts)).toEWKB();
        // Real path: EWKB from the DB is parsed by the hakunapi WKBReader into a
        // JTS geometry backed by PackedCoordinateSequence.Double (one double[]).
        // That is the geometry both paths run on; pull bulk-reads the packed array.
        Geometry g = new fi.nls.hakunapi.core.geom.HakunaGeometryEWKB(ewkb).toJTSGeometry();
        HakunaGeometryJTS gjts = new HakunaGeometryJTS(g);

        MvtPolygonGeometryWriter w = newWriter();
        int iters = 200_000;

        for (int i = 0; i < 20_000; i++) { push(w, g); pull(w, gjts.toNavigable()); }

        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) push(w, g);
        long pushNs = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        for (int i = 0; i < iters; i++) pull(w, gjts.toNavigable());
        long pullNs = System.nanoTime() - t1;

        System.out.printf("%nJTS(packed)  push: %.0f ns/op   pull: %.0f ns/op   x%.2f%n",
                pushNs / (double) iters, pullNs / (double) iters, pushNs / (double) pullNs);

        // EWKB byte backing: push = HakunaGeometryEWKB.write byte walk (1 pass,
        // no virtual dispatch); pull = NavigableEWKB (scan amortized below).
        fi.nls.hakunapi.core.geom.HakunaGeometryEWKB ge =
                new fi.nls.hakunapi.core.geom.HakunaGeometryEWKB(ewkb);
        fi.nls.hakunapi.core.geom.NavigableHakunaGeometry navE = ge.toNavigable();
        for (int i = 0; i < 20_000; i++) {
            w.resetFeature(); ge.write(w); w.end();
            pull(w, navE);
        }
        long t2 = System.nanoTime();
        for (int i = 0; i < iters; i++) { w.resetFeature(); ge.write(w); w.end(); }
        long pushENs = System.nanoTime() - t2;
        long t3 = System.nanoTime();
        for (int i = 0; i < iters; i++) pull(w, navE);
        long pullENs = System.nanoTime() - t3;
        System.out.printf("EWKB(bytes)  push: %.0f ns/op   pull: %.0f ns/op   x%.2f  (scan amortized)%n",
                pushENs / (double) iters, pullENs / (double) iters, pushENs / (double) pullENs);
    }

    // --- geometry helpers ------------------------------------------------------

    private static Polygon square(double x, double y, double s) {
        return GF.createPolygon(new Coordinate[] {
                new Coordinate(x, y), new Coordinate(x + s, y),
                new Coordinate(x + s, y + s), new Coordinate(x, y + s),
                new Coordinate(x, y)});
    }

    private static Polygon squareWithHole() {
        LinearRing shell = GF.createLinearRing(new Coordinate[] {
                new Coordinate(100, 100), new Coordinate(900, 100),
                new Coordinate(900, 900), new Coordinate(100, 900),
                new Coordinate(100, 100)});
        LinearRing hole = GF.createLinearRing(new Coordinate[] {
                new Coordinate(400, 400), new Coordinate(600, 400),
                new Coordinate(600, 600), new Coordinate(400, 600),
                new Coordinate(400, 400)});
        return GF.createPolygon(shell, new LinearRing[] {hole});
    }

    private static Polygon ringPolygon(double cx, double cy, double rad, int n, Random rnd) {
        Coordinate[] cs = new Coordinate[n + 1];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            double rr = rad * (0.7 + 0.3 * rnd.nextDouble());
            cs[i] = new Coordinate(cx + rr * Math.cos(a), cy + rr * Math.sin(a));
        }
        cs[n] = cs[0];
        return GF.createPolygon(cs);
    }
}
