package fi.nls.hakunapi.proj.jhe;

/**
 * The pipeline stages proj-jhe composes transformations from. The formulae stay in
 * EUREFFIN (see JHS-197 through it); a stage owns the loop over a coordinate block
 * and the projection parameters that are invariant across it.
 *
 * The geodetic end of a stage is in radians, so a pipeline whose outer CRS is
 * geographic needs a unit conversion. Rather than paying a separate pass over the
 * block for it, the conversion is folded into the stage that touches those
 * coordinates anyway - hence the isDegrees variant of every projection.
 *
 * Every stage there can be is built once, here: the supported CRSs are a fixed set,
 * so a lookup returns a shared instance and no stage is ever built per request.
 */
public class JHeStages {

    // The table is indexed by SRID, spanning EPSG:3046 (ETRS-TM34, its lowest entry)
    // to EPSG:3885 (ETRS-GK31, its highest), with unsupported SRIDs left null
    private static final int SRID_MIN = 3046;

    // ETRS-GKnn, EPSG:3873-3885, zone nn being the central meridian in degrees
    private static final int GK_MIN = 3873;
    private static final int GK_MAX = 3885;
    private static final int GK_MIN_ZONE = 19;

    private static final int WEB_MERCATOR = 3857;

    private static final JHeStage GEO_RAD_TO_WEB_MERC = (xy, off, n) -> {
        for (int i = 0; i < n; i++) {
            EUREFFIN.geoToWebMercRad(xy[off], xy[off + 1], xy, off);
            off += 2;
        }
    };

    private static final JHeStage GEO_DEG_TO_WEB_MERC = (xy, off, n) -> {
        for (int i = 0; i < n; i++) {
            EUREFFIN.geoToWebMerc(xy[off], xy[off + 1], xy, off);
            off += 2;
        }
    };

    private static final JHeStage WEB_MERC_TO_GEO_RAD = (xy, off, n) -> {
        for (int i = 0; i < n; i++) {
            EUREFFIN.webMercToGeoRad(xy[off], xy[off + 1], xy, off);
            off += 2;
        }
    };

    // EUREFFIN has no degree-reading Web Mercator inverse, so this one stage does
    // the conversion itself rather than delegating the whole point
    private static final JHeStage WEB_MERC_TO_GEO_DEG = (xy, off, n) -> {
        for (int i = 0; i < n; i++) {
            EUREFFIN.webMercToGeoRad(xy[off], xy[off + 1], xy, off);
            xy[off + 0] = Math.toDegrees(xy[off + 0]);
            xy[off + 1] = Math.toDegrees(xy[off + 1]);
            off += 2;
        }
    };

    private static final JHeStage[] TO_GEO_RAD = new JHeStage[GK_MAX - SRID_MIN + 1];
    private static final JHeStage[] TO_GEO_DEG = new JHeStage[GK_MAX - SRID_MIN + 1];
    private static final JHeStage[] FROM_GEO_RAD = new JHeStage[GK_MAX - SRID_MIN + 1];
    private static final JHeStage[] FROM_GEO_DEG = new JHeStage[GK_MAX - SRID_MIN + 1];

    static {
        put(3046, EUREFFIN.k0_TM, EUREFFIN.l0_TM34, EUREFFIN.E0_TM);
        put(3047, EUREFFIN.k0_TM, EUREFFIN.l0_TM35, EUREFFIN.E0_TM);
        put(3048, EUREFFIN.k0_TM, EUREFFIN.l0_TM36, EUREFFIN.E0_TM);
        // ETRS-TM35FIN, the same projection as ETRS-TM35 above
        put(3067, EUREFFIN.k0_TM, EUREFFIN.l0_TM35, EUREFFIN.E0_TM);
        for (int srid = GK_MIN; srid <= GK_MAX; srid++) {
            int nn = srid - GK_MIN + GK_MIN_ZONE;
            put(srid, 1.0, Math.toRadians(nn), 1_000_000 * nn + 500_000);
        }
        put(WEB_MERCATOR, WEB_MERC_TO_GEO_RAD, WEB_MERC_TO_GEO_DEG, GEO_RAD_TO_WEB_MERC, GEO_DEG_TO_WEB_MERC);
    }

    /**
     * Source CRS to geodetic, in degrees when isDegrees and radians otherwise.
     * Throws when the CRS is not one this module supports.
     */
    public static JHeStage toGeodetic(int srid, boolean isDegrees) throws IllegalArgumentException {
        JHeStage stage = get(isDegrees ? TO_GEO_DEG : TO_GEO_RAD, srid);
        if (stage == null) {
            throw new IllegalArgumentException("Could not find transform from " + srid);
        }
        return stage;
    }

    /**
     * Geodetic to target CRS, reading degrees when isDegrees and radians otherwise.
     * Throws when the CRS is not one this module supports.
     */
    public static JHeStage fromGeodetic(int srid, boolean isDegrees) throws IllegalArgumentException {
        JHeStage stage = get(isDegrees ? FROM_GEO_DEG : FROM_GEO_RAD, srid);
        if (stage == null) {
            throw new IllegalArgumentException("Could not find transform to " + srid);
        }
        return stage;
    }

    private static JHeStage get(JHeStage[] stages, int srid) {
        int i = srid - SRID_MIN;
        return i >= 0 && i < stages.length ? stages[i] : null;
    }

    private static void put(int srid, double k0, double l0, double E0) {
        put(srid,
                (xy, off, n) -> {
                    for (int i = 0; i < n; i++) {
                        EUREFFIN.planeToGeoRad(xy[off], xy[off + 1], k0, l0, E0, xy, off);
                        off += 2;
                    }
                },
                (xy, off, n) -> {
                    for (int i = 0; i < n; i++) {
                        EUREFFIN.planeToGeo(xy[off], xy[off + 1], k0, l0, E0, xy, off);
                        off += 2;
                    }
                },
                (xy, off, n) -> {
                    for (int i = 0; i < n; i++) {
                        EUREFFIN.geoToPlaneRad(xy[off], xy[off + 1], k0, l0, E0, xy, off);
                        off += 2;
                    }
                },
                (xy, off, n) -> {
                    for (int i = 0; i < n; i++) {
                        EUREFFIN.geoToPlane(xy[off], xy[off + 1], k0, l0, E0, xy, off);
                        off += 2;
                    }
                });
    }

    private static void put(int srid, JHeStage toRad, JHeStage toDeg, JHeStage fromRad, JHeStage fromDeg) {
        int i = srid - SRID_MIN;
        TO_GEO_RAD[i] = toRad;
        TO_GEO_DEG[i] = toDeg;
        FROM_GEO_RAD[i] = fromRad;
        FROM_GEO_DEG[i] = fromDeg;
    }

    private JHeStages() {
        // Block init
    }

}
