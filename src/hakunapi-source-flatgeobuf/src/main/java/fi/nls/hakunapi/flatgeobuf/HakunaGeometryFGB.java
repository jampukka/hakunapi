package fi.nls.hakunapi.flatgeobuf;

import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.wololo.flatgeobuf.generated.Geometry;
import org.wololo.flatgeobuf.generated.GeometryType;

import fi.nls.hakunapi.core.GeometryWriter;
import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.geom.HakunaGeometryFactory;
import fi.nls.hakunapi.core.geom.HakunaGeometryJTS;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;

public class HakunaGeometryFGB implements HakunaGeometry {

    private final int geometryType;
    private final int srid;
    private final Geometry g;

    public HakunaGeometryFGB(int geometryType, int srid, Geometry g) {
        this.geometryType = geometryType;
        this.srid = srid;
        this.g = g;
    }

    @Override
    public byte[] toEWKB() {
        return new HakunaGeometryJTS(toJTSGeometry()).toEWKB();
    }

    @Override
    public void write(GeometryWriter writer) throws Exception {
        switch (geometryType) {
        case GeometryType.Unknown:
            return;
        case GeometryType.Point:
            writePoint(g, srid, writer, true);
            break;
        case GeometryType.LineString:
            writeLineString(g, srid, writer, true);
            break;
        case GeometryType.Polygon:
            writePolygon(g, srid, writer, true);
            break;
        case GeometryType.MultiPoint:
            writeMultiPoint(g, srid, writer, true);
            break;
        case GeometryType.MultiLineString:
            writeMultiLineString(g, srid, writer, true);
            break;
        case GeometryType.MultiPolygon:
            writeMultiPolygon(g, srid, writer, true);
            break;
        default:
            throw new RuntimeException("Unknown geometry type");
        }
    }

    private static final void writeCoordinateSeq(final Geometry g, final GeometryWriter writer, int off, final int end) throws Exception {
        for (; off < end;) {
            double x = g.xy(off++);
            double y = g.xy(off++);
            writer.writeCoordinate(x, y);
        }
    }

    private static final void writePoint(final Geometry g, final int srid, final GeometryWriter writer, final boolean init) throws Exception {
        if (init) {
            writer.init(HakunaGeometryType.POINT, srid, 2);
        }

        writer.writeCoordinate(g.xy(0), g.xy(1));

        if (init) {
            writer.end();
        }
    }

    private static final void writeLineString(final Geometry g, final int srid, final GeometryWriter writer, final boolean init) throws Exception {
        if (init) {
            writer.init(HakunaGeometryType.LINESTRING, srid, 2);
        }

        writer.startRing();
        writeCoordinateSeq(g, writer, 0, g.xyLength());
        writer.endRing();

        if (init) {
            writer.end();
        }
    }

    private static final void writePolygon(final Geometry g, final int srid, final GeometryWriter writer, final boolean init) throws Exception {
        if (init) {
            writer.init(HakunaGeometryType.POLYGON, srid, 2);
        }

        final int endsLength = g.endsLength();

        writer.startRing();
        if (endsLength == 0) {
            writer.startRing();
            writeCoordinateSeq(g, writer, 0, g.xyLength());
            writer.endRing();
        } else {
            int s = 0;
            writer.startRing();
            for (int i = 0; i < endsLength; i++) {
                int e = (int) g.ends(0) * 2;
                writeCoordinateSeq(g, writer, s, e);
                s = e;
            }
            writer.endRing();
        }
        writer.endRing();

        if (init) {
            writer.end();
        }
    }

    private static final void writeMultiPoint(final Geometry g, final int srid, final GeometryWriter writer, final boolean init) throws Exception {
        if (init) {
            writer.init(HakunaGeometryType.MULTIPOINT, srid, 2);
        }

        final Geometry t = new Geometry();
        final int n = g.partsLength();
        writer.startRing();
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            writePoint(t, srid, writer, false);
        }
        writer.endRing();

        if (init) {
            writer.end();
        }
    }

    private static final void writeMultiLineString(final Geometry g, final int srid, final GeometryWriter writer, final boolean init) throws Exception {
        if (init) {
            writer.init(HakunaGeometryType.MULTILINESTRING, srid, 2);
        }

        final Geometry t = new Geometry();
        final int n = g.partsLength();
        writer.startRing();
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            writeLineString(t, srid, writer, false);
        }
        writer.endRing();

        if (init) {
            writer.end();
        }
    }

    private static final void writeMultiPolygon(final Geometry g, final int srid, final GeometryWriter writer, final boolean init) throws Exception {
        if (init) {
            writer.init(HakunaGeometryType.MULTIPOLYGON, srid, 2);
        }

        final Geometry t = new Geometry();
        final int n = g.partsLength();
        writer.startRing();
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            writePolygon(t, srid, writer, false);
        }
        writer.endRing();

        if (init) {
            writer.end();
        }
    }

    @Override
    public org.locationtech.jts.geom.Geometry toJTSGeometry() {
        org.locationtech.jts.geom.Geometry geometry = deserializeGeometry();
        geometry.setSRID(srid);
        return geometry;
    }

    private org.locationtech.jts.geom.Geometry deserializeGeometry() {
        switch (geometryType) {
        case GeometryType.Unknown:
            return null;
        case GeometryType.Point:
            return deserializePoint(g);
        case GeometryType.LineString:
            return deserializeLineString(g);
        case GeometryType.Polygon:
            return deserializePolygon(g);
        case GeometryType.MultiPoint:
            return deserializeMultiPoint(g);
        case GeometryType.MultiLineString:
            return deserializeMultiLineString(g);
        case GeometryType.MultiPolygon:
            return deserializeMultiPolygon(g);
        default:
            throw new RuntimeException("Unknown geometry type");
        }
    }

    private static CoordinateSequence deserializeCoordinateSeq(Geometry g, int off, int len) {
        double[] coords = new double[len];
        for (int i = 0; i < len; i++) {
            coords[i] = g.xy(off + i);
        }
        return new PackedCoordinateSequence.Double(coords, 2, 0);
    }

    private static Point deserializePoint(Geometry g) {
        return HakunaGeometryFactory.GF.createPoint(deserializeCoordinateSeq(g, 0, 2));
    }

    private static LineString deserializeLineString(Geometry g) {
        return HakunaGeometryFactory.GF.createLineString(deserializeCoordinateSeq(g, 0, g.xyLength()));
    }

    private static Polygon deserializePolygon(Geometry g) {
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

    private static MultiPoint deserializeMultiPoint(Geometry g) {
        Geometry t = new Geometry();
        int n = g.partsLength();
        Point[] a = new Point[n];
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            a[i] = deserializePoint(t);
        }
        return HakunaGeometryFactory.GF.createMultiPoint(a);
    }

    private static MultiLineString deserializeMultiLineString(Geometry g) {
        Geometry t = new Geometry();
        int n = g.partsLength();
        LineString[] a = new LineString[n];
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            a[i] = deserializeLineString(t);
        }
        return HakunaGeometryFactory.GF.createMultiLineString(a);
    }

    private static MultiPolygon deserializeMultiPolygon(Geometry g) {
        Geometry t = new Geometry();
        int n = g.partsLength();
        Polygon[] a = new Polygon[n];
        for (int i = 0; i < n; i++) {
            g.parts(t, i);
            a[i] = deserializePolygon(t);
        }
        return HakunaGeometryFactory.GF.createMultiPolygon(a);
    }

}
