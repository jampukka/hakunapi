package fi.nls.hakunapi.core.geom;

import java.nio.ByteBuffer;

/**
 * A geometry whose coordinates are already WKB bytes in a buffer, positioned
 * past the header.
 *
 * <p>WKB's body layout is the same whichever envelope it arrives in: EWKB from
 * PostGIS and the WKB inside a GeoPackage blob differ only in the header the
 * reader has already consumed. A backing that can name the buffer, where the
 * body starts, the raw geometry type and the coordinate dimension can therefore
 * be walked by {@link NavigableEWKB} directly, without materializing JTS
 * objects first.
 *
 * <p>That matters on the tile path, where a single tile may carry tens of
 * thousands of features: {@link HakunaGeometry#toNavigable()}'s default route
 * builds a whole JTS object tree per feature only to read coordinates back out
 * of it.
 */
public interface WKBBacked extends HakunaGeometry {

    /**
     * The buffer holding the geometry. Shared and not to be mutated: its
     * position and byte order belong to the owner, so a reader duplicates it.
     */
    public ByteBuffer getBuffer();

    /** Byte offset of the geometry body, past the byte order flag and type. */
    public int getDataStart();

    /** Raw WKB geometry type, 1-7, with no SRID or dimension flags. */
    public int getGeometryType();

    /** Ordinates per coordinate: 2, 3 (Z or M) or 4 (ZM). */
    @Override
    public int getDimension();

    /**
     * Walks the WKB body in place: no JTS, no copy of the coordinates.
     * Overriding this is not expected.
     */
    @Override
    public default NavigableHakunaGeometry toNavigable() {
        return new NavigableEWKB(this);
    }

}
