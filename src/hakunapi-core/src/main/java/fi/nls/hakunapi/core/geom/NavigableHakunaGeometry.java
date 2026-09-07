package fi.nls.hakunapi.core.geom;

/**
 * A random-access view over a {@link HakunaGeometry}'s structure, for consumers
 * that walk the coordinates directly (vector-tile encoding, bbox, simplify)
 * rather than through the streaming {@link fi.nls.hakunapi.core.GeometryWriter}
 * push model.
 *
 * <p>The shape is the OGC nesting, flattened to two levels plus a bulk
 * coordinate read:
 * <ul>
 *   <li><b>Parts</b> &mdash; {@link #getNumGeometries()} sub-geometries. A
 *       MULTI* geometry has one part per member; a simple geometry has exactly
 *       one part, and {@link #getGeometryN(int) getGeometryN(0)} returns a part
 *       view of itself, so consumers can always descend uniformly before reading
 *       rings.</li>
 *   <li><b>Rings</b> &mdash; within a part, {@link #getNumRings()} contiguous
 *       coordinate runs. A polygon part has the exterior ring at index 0 followed
 *       by its holes; a line or point part has a single ring (the vertices, or
 *       the one point).</li>
 *   <li><b>Coordinates</b> &mdash; {@link #copyRingXY} bulk-copies a ring's x,y
 *       ordinates (stride 2, any z/m dropped) into a caller-owned array. This is
 *       the performance primitive: one virtual call per ring, the body a tight
 *       loop the backing can keep cache-friendly (a {@code System.arraycopy} for
 *       a columnar store, a single {@code ByteBuffer} run for WKB), with no
 *       per-coordinate dispatch and no intermediate coordinate objects.</li>
 * </ul>
 *
 * <p>Backings that are already random-access (JTS, a future FlatGeobuf store)
 * implement this directly and return {@code this} from {@link #toNavigable()}.
 * A sequential backing such as EWKB returns a wrapper that has scanned its
 * structure offsets once; either way {@link #toNavigable()} is cheap to call
 * repeatedly. A navigable view is not required to be thread-safe.
 */
public interface NavigableHakunaGeometry extends HakunaGeometry {

    /** Number of parts: members of a MULTI*, otherwise 1. */
    int getNumGeometries();

    /**
     * Part {@code n} as its own navigable view. For a simple geometry the only
     * valid call is {@code getGeometryN(0)}, which returns a part view (possibly
     * {@code this}). Always descend to a part before reading rings.
     */
    NavigableHakunaGeometry getGeometryN(int n);

    /** Number of rings in this part: polygon exterior+holes, else 1. */
    int getNumRings();

    /** Number of coordinates in ring {@code r}. */
    int getRingSize(int r);

    /**
     * Bulk-copy {@code count} coordinates of ring {@code r}, starting at
     * coordinate {@code from}, as interleaved x,y pairs into {@code dst} from
     * {@code dstOff}. Writes {@code 2 * count} doubles; any z/m is dropped.
     */
    void copyRingXY(int r, double[] dst, int dstOff, int from, int count);

    /**
     * Per-coordinate transform applied while a ring is read into a {@code float}
     * sink. Receives one source coordinate (x,y) and writes its transformed,
     * narrowed result to {@code dst[off]}, {@code dst[off + 1]}. Lets a consumer
     * (e.g. world-&gt;tile pixel) fuse its transform into the bulk read so the
     * backing's coordinates are visited exactly once, with no double[]
     * intermediate.
     */
    @FunctionalInterface
    interface XYToFloat {
        void apply(double x, double y, float[] dst, int off);
    }

    /**
     * Like {@link #copyRingXY(int, double[], int, int, int)} but applies
     * {@code fn} to each coordinate and writes the result as {@code float} into
     * {@code dst} (interleaved, {@code 2 * count} floats). One pass over the
     * backing's coordinates, no double[] intermediate.
     */
    void copyRingXY(int r, float[] dst, int dstOff, int from, int count, XYToFloat fn);

    /** A navigable geometry is already navigable. */
    @Override
    default NavigableHakunaGeometry toNavigable() {
        return this;
    }
}
