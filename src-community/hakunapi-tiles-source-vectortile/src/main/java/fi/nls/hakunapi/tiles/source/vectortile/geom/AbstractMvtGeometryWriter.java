package fi.nls.hakunapi.tiles.source.vectortile.geom;

import fi.nls.hakunapi.core.GeometryWriter;
import fi.nls.hakunapi.core.geom.HakunaGeometryType;

/**
 * Base for the per-type MVT geometry writers. A writer is the streaming sink a
 * feature producer drives via {@link GeometryWriter}: world coordinates arrive
 * one ring/part at a time and are pushed through the tile pipeline
 * (world&nbsp;-&gt;&nbsp;tile-pixel transform, rect clip, grid snap, simplify)
 * into MVT command output, with no intermediate geometry objects.
 *
 * <p>The geometry type of a collection is fixed, so the calling code picks the
 * matching concrete writer ({@link MvtPolygonGeometryWriter},
 * {@code MvtLineGeometryWriter}, {@code MvtPointGeometryWriter}) once and reuses
 * it across every feature in that collection. That keeps the per-coordinate hot
 * loop free of type dispatch and lets each subclass specialise its clip and
 * emit. This base owns only the shared scratch state and the transform/snap
 * helpers; subclasses own clip and MVT emission.
 *
 * <p>Not thread-safe: a single instance carries mutable scratch and is meant to
 * be held in a thread-local (one per worker thread) by the generator.
 *
 * <p>Coordinates are transformed into tile-pixel space on the way in. Tile space
 * has its origin at the tile's top-left, x growing right, y growing down (the
 * MVT/raster convention), so the world y axis is flipped:
 * <pre>
 *   px = (x - minX) / resolution
 *   py = (maxY - y) / resolution
 * </pre>
 * One tile-pixel unit equals one MVT extent unit, so the usual tile extent of
 * 4096 corresponds to {@code tileWidth == tileHeight == 4096} in the context.
 */
public abstract class AbstractMvtGeometryWriter implements GeometryWriter {

    /** Initial scratch capacity in coordinate pairs; grows as needed. */
    private static final int INITIAL_COORDS = 256;

    /**
     * Scratch capacity retained between tiles, in coordinate pairs. A writer is
     * reused across tiles, so a buffer at or below this stays as it is and never
     * grows again; anything above is released by {@link #trimScratch()} so one
     * outsized ring cannot pin memory per worker thread for the process' life.
     */
    private static final int RETAINED_COORDS = 16384;

    // Transform from world coordinates to tile-pixel space, set per tile.
    protected double originX;   // world minX
    protected double originY;   // world maxY (top edge, because y is flipped)
    protected double invRes;    // 1 / resolution

    // Clip rectangle in tile-pixel space (extent plus a buffer), set per tile.
    // Kept as float to compare directly against the float coordinate buffers.
    protected float clipMinX;
    protected float clipMinY;
    protected float clipMaxX;
    protected float clipMaxY;

    /**
     * Primary ring scratch in tile-pixel space: x0,y0,x1,y1,... A ring/part is
     * accumulated here on the way in, then stages rewrite it (the clip stage
     * ping-pongs with {@link #scratchB}).
     *
     * <p>Coordinates are stored as {@code float}: after the world-&gt;tile-pixel
     * transform every value lies within roughly {@code [-buffer, extent+buffer]}
     * (the producer queries by tile bbox, so vertices are near the tile), well
     * inside float's exact-integer range (2^24), so narrowing loses nothing that
     * the integer grid snap would not discard anyway. The transform math itself
     * runs in double (see {@link #toTileX}); only the result is narrowed.
     */
    protected float[] scratch;
    /** Number of valid coordinate pairs currently held in {@link #scratch}. */
    protected int n;

    /** Secondary buffer for stages that cannot run in place (e.g. clip). */
    protected float[] scratchB;
    /** Tertiary buffer: clip ping-pong partner of {@link #scratchB}. */
    protected float[] scratchC;

    /**
     * Bounding box of the ring currently in {@link #scratch}, in tile-pixel space,
     * computed by {@link #loadRing}. Drives two clip fast paths against the clip
     * rect (extent + buffer):
     * <ul>
     *   <li><b>fully outside</b> (bbox disjoint from the rect on any axis): the
     *       ring contributes nothing, not even crossing segments, so the whole
     *       ring is dropped before clip/snap/emit;</li>
     *   <li><b>fully inside</b> (bbox within the rect on every axis): the ring is
     *       unchanged by Sutherland&ndash;Hodgman, so the four clip passes are
     *       skipped and snap/simplify/emit run straight on it.</li>
     * </ul>
     * A bbox that straddles an edge falls through to the full clip.
     *
     * <p>Valid only on the pull path: {@link #loadRing} computes it, the push path
     * ({@link #pushTile}) does not, so {@link #ringBboxValid} gates the fast paths.
     */
    protected float ringMinX, ringMinY, ringMaxX, ringMaxY;
    /** True when {@link #ringMinX}..{@link #ringMaxY} describe {@link #scratch}. */
    protected boolean ringBboxValid;


    protected AbstractMvtGeometryWriter() {
        this.scratch = new float[INITIAL_COORDS * 2];
        this.scratchB = new float[INITIAL_COORDS * 2];
        this.scratchC = new float[INITIAL_COORDS * 2];
    }

    /**
     * Configure the writer for one tile. Computes the world-&gt;tile-pixel
     * transform and the clip rectangle (tile extent grown by {@code bufferPixels}
     * on every side, so geometry just outside the tile still contributes the
     * segments that cross the edge).
     *
     * <p>The MVT coordinate {@code extent} is decoupled from the tile matrix's
     * raster pixel size: WebMercatorQuad reports a 256-pixel tile, but MVT
     * geometry is quantised to {@code extent} units (conventionally 4096) so
     * straight boundaries stay crisp instead of snapping to a coarse 256 grid.
     * The transform therefore scales the tile's ground span onto {@code [0,
     * extent]} rather than onto {@code [0, tileWidthPixels]}.
     *
     * @param minX         tile bbox min X in the tile matrix set CRS
     * @param maxY         tile bbox max Y in the tile matrix set CRS
     * @param resolution   ground units per tile pixel
     * @param tileWidth    tile width in raster pixels (from the tile matrix)
     * @param tileHeight   tile height in raster pixels (from the tile matrix)
     * @param extent       MVT coordinate extent to map the tile onto (e.g. 4096)
     * @param bufferPixels clip buffer in MVT extent units (>= 0)
     */
    public void initTile(double minX, double maxY, double resolution,
            int tileWidth, int tileHeight, int extent, int bufferPixels) {
        this.originX = minX;
        this.originY = maxY;
        // Map the tile's ground span (tileWidth * resolution) onto [0, extent]
        // instead of [0, tileWidth pixels], so one MVT unit is extent-scaled.
        this.invRes = extent / (tileWidth * resolution);
        this.clipMinX = -bufferPixels;
        this.clipMinY = -bufferPixels;
        this.clipMaxX = (float) extent + bufferPixels;
        this.clipMaxY = (float) extent + bufferPixels;
        // Capture the transform once; px=(x-originX)*invRes, py=(originY-y)*invRes.
        final double ox = originX, oy = originY, ir = invRes;
        this.toTile = (x, y, dst, off) -> {
            dst[off]     = (float) ((x - ox) * ir);
            dst[off + 1] = (float) ((oy - y) * ir);
        };
    }

    /**
     * Release scratch grown past {@link #RETAINED_COORDS} back to that capacity.
     * Called at the end of a tile: an ordinary tile's buffers are below the
     * ceiling and survive, so the writer stops allocating them after its first
     * few tiles, while a single huge ring does not become the thread's permanent
     * footprint.
     */
    public final void trimScratch() {
        int cap = RETAINED_COORDS * 2;
        if (scratch.length > cap) {
            scratch = new float[cap];
        }
        if (scratchB.length > cap) {
            scratchB = new float[cap];
        }
        if (scratchC.length > cap) {
            scratchC = new float[cap];
        }
    }

    protected final double toTileX(double worldX) {
        return (worldX - originX) * invRes;
    }

    protected final double toTileY(double worldY) {
        return (originY - worldY) * invRes;
    }

    /** Reset the ring scratch to empty before accumulating a new ring/part. */
    protected final void resetRing() {
        n = 0;
        ringBboxValid = false;
    }

    /**
     * Append one tile-pixel coordinate pair to {@link #scratch}, growing it. The
     * transformed value arrives as {@code double} (transform math) and is narrowed
     * to {@code float} for storage.
     */
    protected final void pushTile(double tx, double ty) {
        int need = (n + 1) * 2;
        if (need > scratch.length) {
            scratch = grow(scratch, need);
        }
        scratch[n * 2] = (float) tx;
        scratch[n * 2 + 1] = (float) ty;
        n++;
    }

    /**
     * World-&gt;tile transform fused into the navigable's float ring read: each
     * source (x,y) is mapped to tile-pixel space and written as float. Allocated
     * once (in {@link #initTile}) so the pull path adds no per-ring lambda.
     */
    private fi.nls.hakunapi.core.geom.NavigableHakunaGeometry.XYToFloat toTile;

    /**
     * Pull-path ring load: read ring {@code r} of {@code part} straight into
     * {@link #scratch} as tile-pixel {@code float}s, applying the world-&gt;tile
     * transform during the copy ({@link #toTile}). One pass over the backing's
     * coordinates, no double[] intermediate and no per-coordinate virtual
     * dispatch. Leaves {@link #n} = the vertex count.
     */
    protected final void loadRing(fi.nls.hakunapi.core.geom.NavigableHakunaGeometry part, int r) {
        int sz = part.getRingSize(r);
        if (sz * 2 > scratch.length) {
            scratch = grow(scratch, sz * 2);
        }
        part.copyRingXY(r, scratch, 0, 0, sz, toTile);
        n = sz;
        scanRingBbox(sz);
    }

    /**
     * Compute the tile-pixel bbox of the {@code sz} pairs now in {@link #scratch}
     * into {@link #ringMinX}..{@link #ringMaxY}. A tight counted min/max loop over
     * the float buffer (no carried branch the JIT cannot hoist), feeding the clip
     * fast paths in the subclasses.
     */
    private void scanRingBbox(int sz) {
        float mnx = scratch[0], mny = scratch[1];
        float mxx = mnx, mxy = mny;
        for (int i = 1; i < sz; i++) {
            float x = scratch[i * 2];
            float y = scratch[i * 2 + 1];
            if (x < mnx) mnx = x; else if (x > mxx) mxx = x;
            if (y < mny) mny = y; else if (y > mxy) mxy = y;
        }
        ringMinX = mnx; ringMinY = mny;
        ringMaxX = mxx; ringMaxY = mxy;
        ringBboxValid = true;
    }

    protected static float[] grow(float[] a, int need) {
        int len = a.length;
        while (len < need) {
            len <<= 1;
        }
        float[] b = new float[len];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    /** Ensure {@link #scratchB} can hold at least {@code pairs} coordinate pairs. */
    /** Ensure both clip ping-pong buffers hold at least {@code pairs} pairs. */
    protected final void ensureB(int pairs) {
        if (pairs * 2 > scratchB.length) {
            scratchB = grow(scratchB, pairs * 2);
        }
        if (pairs * 2 > scratchC.length) {
            scratchC = grow(scratchC, pairs * 2);
        }
    }

    // --- GeometryWriter coordinate overloads: drop Z/M, transform, accumulate ---

    @Override
    public void writeCoordinate(double x, double y) throws Exception {
        pushTile(toTileX(x), toTileY(y));
    }

    @Override
    public void writeCoordinate(double x, double y, double z) throws Exception {
        pushTile(toTileX(x), toTileY(y));
    }

    @Override
    public void writeCoordinate(double x, double y, double z, double m) throws Exception {
        pushTile(toTileX(x), toTileY(y));
    }

    @Override
    public void init(HakunaGeometryType type, int srid, int dimension) throws Exception {
        // Subclasses override if they need per-geometry setup; default no-op.
    }

}
