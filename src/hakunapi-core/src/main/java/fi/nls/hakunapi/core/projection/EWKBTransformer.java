package fi.nls.hakunapi.core.projection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import fi.nls.hakunapi.core.geom.HakunaGeometryEWKB;
import fi.nls.hakunapi.core.geom.WKBBacked;

/**
 * Reprojects a geometry's coordinates in place in its WKB bytes, without
 * materializing a JTS geometry.
 *
 * <p>WKB's body layout is the same whichever envelope it arrives in - EWKB from
 * PostGIS and the WKB inside a GeoPackage blob differ only in the header - and a
 * reprojection changes no structure at all: part counts, ring counts and
 * coordinate counts are untouched, only the {@code double} pairs change. So the
 * whole operation is a walk over the coordinate runs, one
 * {@link ProjectionTransformer#transformInPlace} call per ring.
 *
 * <p>The per-ring call is the point. The JTS route ({@link JTSTransformer})
 * transforms one coordinate per {@code filter} call, each one a
 * {@code getOrdinate}/{@code setOrdinate} pair plus a
 * {@code transformInPlace(arr, 0, 1)} over a single coordinate; here a ring's
 * ordinates are read into a reused scratch array and transformed in one call.
 * It also has to build the JTS geometry to apply the filter to - a packed
 * {@code double[]} per ring (see HakunaCoordinateSequenceFactory) plus a
 * {@code Geometry} wrapper per part - only for the consumer to read the
 * coordinates straight back out.
 *
 * <p>2D only: {@link #transform(WKBBacked)} rejects Z/M geometry, whose callers
 * keep to the JTS path.
 *
 * <p>The scratch array makes an instance stateful and therefore not
 * thread-safe - one per query, like the transformer it wraps.
 */
public class EWKBTransformer {

    /** Scratch ordinates: covers a 64-coordinate ring without allocating. */
    private static final int XY_LEN = 128;

    private final ProjectionTransformer t;
    private final double[] xy = new double[XY_LEN];

    public EWKBTransformer(ProjectionTransformer transformer) {
        this.t = transformer;
    }

    /**
     * Reprojects a {@link WKBBacked} geometry in place, starting from its body:
     * the header - EWKB flags and SRID, or a GeoPackage {@code GP} header - has
     * already been consumed by the backing and is left alone. The target SRID is
     * therefore not recorded in the bytes; a caller that needs it carried tracks
     * it alongside, as the property and query context already do.
     *
     * <p>The geometry's buffer is shared, so this reads and writes through a
     * duplicate: position and byte order stay the owner's.
     *
     * @throws IllegalArgumentException if the geometry is not 2D
     */
    public void transform(WKBBacked geom) throws Exception {
        int dimension = geom.getDimension();
        if (dimension != 2) {
            throw new IllegalArgumentException("Expected 2D geometry, got dimension " + dimension);
        }
        ByteBuffer src = geom.getBuffer();
        ByteBuffer bb = src.duplicate();
        bb.order(src.order());
        bb.position(geom.getDataStart());
        transformBody(bb, geom.getGeometryType());
    }

    /**
     * Reprojects a standalone EWKB (or plain WKB) byte array in place, header
     * included: an SRID in the header, if present, is overwritten with the
     * transformer's target SRID.
     *
     * @throws IllegalArgumentException if the geometry is not 2D
     */
    public void transformEWKB(byte[] ewkb) throws Exception {
        ByteBuffer bb = ByteBuffer.wrap(ewkb);
        if (bb.get() != 0) {
            bb.order(ByteOrder.LITTLE_ENDIAN);
        }
        int extType = bb.getInt();
        if ((extType & HakunaGeometryEWKB.EWKB_HAS_Z) != 0
                || (extType & HakunaGeometryEWKB.EWKB_HAS_M) != 0
                || (extType & 0xFFFF) / 1000 != 0) {
            throw new IllegalArgumentException("Expected 2D geometry");
        }
        if ((extType & HakunaGeometryEWKB.EWKB_HAS_SRID) != 0) {
            // Overwrite the SRID information
            bb.putInt(t.getToSRID());
        }
        transformBody(bb, extType & 0x7);
    }

    /**
     * Walks the WKB body from the buffer's current position, reprojecting every
     * coordinate run. Every member of a MULTI* re-states its own byte order, so
     * the order is set per part rather than once.
     */
    private void transformBody(ByteBuffer bb, int type) throws Exception {
        int numPoints, numRings, numGeoms;
        switch (type) {
        case 1:
            transformRing(bb, 1);
            break;
        case 2:
            numPoints = bb.getInt();
            transformRing(bb, numPoints);
            break;
        case 3:
            numRings = bb.getInt();
            for (int ring = 0; ring < numRings; ring++) {
                numPoints = bb.getInt();
                transformRing(bb, numPoints);
            }
            break;
        case 4:
            numGeoms = bb.getInt();
            for (int i = 0; i < numGeoms; i++) {
                readMemberHeader(bb);
                transformRing(bb, 1);
            }
            break;
        case 5:
            numGeoms = bb.getInt();
            for (int i = 0; i < numGeoms; i++) {
                readMemberHeader(bb);
                numPoints = bb.getInt();
                transformRing(bb, numPoints);
            }
            break;
        case 6:
            numGeoms = bb.getInt();
            for (int i = 0; i < numGeoms; i++) {
                readMemberHeader(bb);
                numRings = bb.getInt();
                for (int j = 0; j < numRings; j++) {
                    numPoints = bb.getInt();
                    transformRing(bb, numPoints);
                }
            }
            break;
        default:
            // TODO handle GeometryCollection
            throw new IllegalArgumentException("unsupported WKB type " + type);
        }
    }

    /** A MULTI* member's own byte order flag and type; the layout is known already. */
    private static void readMemberHeader(ByteBuffer bb) {
        bb.order(bb.get() != 0 ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        bb.getInt();
    }

    /**
     * Reprojects {@code n} coordinates starting at the buffer's position, and
     * leaves the position past them.
     *
     * <p>Read and write are absolute {@code getDouble}/{@code putDouble} over the
     * run: a {@code DoubleBuffer} view would allocate, twice, for every ring.
     */
    private void transformRing(ByteBuffer bb, int n) throws Exception {
        int len = 2 * n;
        double[] xy = len > XY_LEN ? new double[len] : this.xy;
        int pos = bb.position();
        for (int i = 0, p = pos; i < len; i += 2, p += 2 * Double.BYTES) {
            xy[i] = bb.getDouble(p);
            xy[i + 1] = bb.getDouble(p + Double.BYTES);
        }
        t.transformInPlace(xy, 0, n);
        for (int i = 0, p = pos; i < len; i += 2, p += 2 * Double.BYTES) {
            bb.putDouble(p, xy[i]);
            bb.putDouble(p + Double.BYTES, xy[i + 1]);
        }
        bb.position(pos + Double.BYTES * len);
    }

}
