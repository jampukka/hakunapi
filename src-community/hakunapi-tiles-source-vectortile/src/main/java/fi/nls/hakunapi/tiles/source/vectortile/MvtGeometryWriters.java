package fi.nls.hakunapi.tiles.source.vectortile;

import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.tiles.source.vectortile.geom.MvtGeometryEncoder;
import fi.nls.hakunapi.tiles.source.vectortile.geom.MvtLineGeometryWriter;
import fi.nls.hakunapi.tiles.source.vectortile.geom.MvtPointGeometryWriter;
import fi.nls.hakunapi.tiles.source.vectortile.geom.MvtPolygonGeometryWriter;
import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtLayerEncoder;

/**
 * Holds the three per-type MVT geometry writers (point, line, polygon) for one
 * worker thread and dispatches a {@link HakunaGeometry} to the right one by its
 * WKB type. The writers carry reusable scratch, so a single set is initialised
 * once per tile ({@link #initTile}) and reused across every feature; this bundle
 * just routes each geometry and exposes the resulting MVT command stream.
 *
 * <p>Dispatch is by the geometry's actual WKB type rather than the collection's
 * declared type, so it is correct even for collections declared with the generic
 * {@code GEOMETRY} type or holding mixed geometries.
 */
final class MvtGeometryWriters {

    private final MvtPointGeometryWriter point = new MvtPointGeometryWriter();
    private final MvtLineGeometryWriter line = new MvtLineGeometryWriter();
    private final MvtPolygonGeometryWriter polygon = new MvtPolygonGeometryWriter();

    /** MVT GeomType of the most recently written geometry. */
    private int mvtType;
    /** Encoder of the most recently written geometry. */
    private MvtGeometryEncoder encoder;

    void initTile(double minX, double maxY, double resolution,
            int tileWidth, int tileHeight, int extent, int bufferPixels) {
        point.initTile(minX, maxY, resolution, tileWidth, tileHeight, extent, bufferPixels);
        line.initTile(minX, maxY, resolution, tileWidth, tileHeight, extent, bufferPixels);
        polygon.initTile(minX, maxY, resolution, tileWidth, tileHeight, extent, bufferPixels);
    }

    /**
     * Encode one geometry into MVT commands. After the call {@link #mvtType()} and
     * {@link #encoder()} describe the result. Returns false if the WKB type is not
     * a supported simple/multi geometry (e.g. a geometry collection), in which
     * case the feature should be skipped.
     */
    boolean write(HakunaGeometry geometry) throws Exception {
        int wkb = geometry.getWKBType() & 0x7; // basic type, strip Z/M/SRID flags
        switch (wkb) {
        case 1: // POINT
        case 4: // MULTIPOINT
            point.resetFeature();
            point.writeNavigable(geometry.toNavigable());
            point.end();
            mvtType = MvtLayerEncoder.GEOM_POINT;
            encoder = point.encoder();
            return true;
        case 2: // LINESTRING
        case 5: // MULTILINESTRING
            line.resetFeature();
            line.writeNavigable(geometry.toNavigable());
            mvtType = MvtLayerEncoder.GEOM_LINE;
            encoder = line.encoder();
            return true;
        case 3: // POLYGON
        case 6: // MULTIPOLYGON
            // Pull path: hakunapi parses geometries into PackedCoordinateSequence.
            // Double (one double[]), so the navigable bulk-reads the raw array a
            // ring at a time, beating the push path's per-vertex getX/getY virtual
            // dispatch (measured ~1.24x on a multipolygon).
            polygon.resetFeature();
            polygon.writeNavigable(geometry.toNavigable());
            mvtType = MvtLayerEncoder.GEOM_POLYGON;
            encoder = polygon.encoder();
            return true;
        default:
            return false; // GEOMETRYCOLLECTION or unknown: skip
        }
    }

    /** Release scratch grown past the retained ceiling; see the writers. */
    void trimScratch() {
        point.trimScratch();
        line.trimScratch();
        polygon.trimScratch();
    }

    int mvtType() {
        return mvtType;
    }

    MvtGeometryEncoder encoder() {
        return encoder;
    }
}
