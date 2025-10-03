package fi.nls.hakunapi.flatgeobuf.geometry;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.wololo.flatgeobuf.generated.Geometry;

import com.google.flatbuffers.DoubleVector;
import com.google.flatbuffers.IntVector;

import fi.nls.hakunapi.core.GeometryWriter;
import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.geom.HakunaGeometryFactory;
import fi.nls.hakunapi.core.geom.HakunaGeometryJTS;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;

public class HakunaPolygonGeometryFgb implements HakunaGeometry {

    private final int srid;
    private final Geometry g;
    private final IntVector ends;
    private final DoubleVector xy;

    public HakunaPolygonGeometryFgb(int srid, Geometry g) {
        this.srid = srid;
        this.g = g;
        this.ends = new IntVector();
        this.xy = new DoubleVector();
    }

    @Override
    public byte[] toEWKB() {
        return new HakunaGeometryJTS(toJTSGeometry()).toEWKB();
    }

    @Override
    public void write(GeometryWriter writer) throws Exception {
        boolean hasInteriorRings = g.endsVector(ends) != null;

        g.xyVector(xy);

        writer.init(HakunaGeometryType.POLYGON, srid, 2);
        writer.startRing();
        if (!hasInteriorRings) {
            writer.startRing();
            for (int i = 0, n = xy.length(); i < n;) {
                writer.writeCoordinate(xy.get(i++), xy.get(i++));
            }
            writer.endRing();
        } else {
            int i = 0;
            for (int ring = 0, n = ends.length(); ring < n; ring++) {
                int j = ends.get(ring) * 2;
                writer.startRing();
                for (; i < j;) {
                    writer.writeCoordinate(xy.get(i++), xy.get(i++));
                }
                writer.endRing();
            }
        }
        writer.endRing();
        writer.end();
    }

    @Override
    public org.locationtech.jts.geom.Geometry toJTSGeometry() {
        org.locationtech.jts.geom.Geometry geometry = deserializeGeometry();
        geometry.setSRID(srid);
        return geometry;
    }

    private org.locationtech.jts.geom.Geometry deserializeGeometry() {
        int endsLength = g.endsLength();

        LinearRing shell;
        if (endsLength == 0) {
            shell = HakunaGeometryFactory.GF.createLinearRing(deserializeCoordinateSeq(g, 0, g.xyLength()));
        } else {
            int e = (int) g.ends(0) * 2;
            shell = HakunaGeometryFactory.GF.createLinearRing(deserializeCoordinateSeq(g, 0, e));
        }

        LinearRing[] holes = null;
        if (endsLength > 1) {
            holes = new LinearRing[endsLength - 1];
            int s = (int) g.ends(0) * 2;
            for (int i = 1; i < endsLength; i++) {
                int e = (int) g.ends(i) * 2;
                holes[i - 1] = HakunaGeometryFactory.GF.createLinearRing(deserializeCoordinateSeq(g, s, e - s));
                s = e;
            }
        }
        return HakunaGeometryFactory.GF.createPolygon(shell, holes);
    }

    private static CoordinateSequence deserializeCoordinateSeq(Geometry g, int off, int len) {
        double[] coords = new double[len];
        for (int i = 0; i < len; i++) {
            coords[i] = g.xy(off + i);
        }
        return new PackedCoordinateSequence.Double(coords, 2, 0);
    }

}
