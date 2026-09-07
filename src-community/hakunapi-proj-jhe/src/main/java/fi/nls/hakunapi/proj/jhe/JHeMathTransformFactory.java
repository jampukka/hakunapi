package fi.nls.hakunapi.proj.jhe;

public class JHeMathTransformFactory {

    /** The transform for an SRID pair, or null when the pair needs no transform. */
    public static JHeStage findMathTransform(int from, int to) {
        from = normalize(from);
        to = normalize(to);

        if (from == to) {
            return null;
        }

        JHeStage[] stages = findStages(from, to);
        if (stages.length == 1) {
            return stages[0];
        }
        return (xy, off, n) -> {
            for (int i = 0; i < stages.length; i++) {
                stages[i].apply(xy, off, n);
            }
        };
    }

    /**
     * The pipeline for an SRID pair: source CRS to geodetic radians, then geodetic
     * radians to target CRS. A geographic end contributes no stage of its own - the
     * degree conversion is folded into the projection stage next to it, so a pair
     * with one geographic end is a single pass over the coordinates.
     */
    public static JHeStage[] findStages(int from, int to) {
        from = normalize(from);
        to = normalize(to);

        if (from == 4258) {
            return new JHeStage[] { JHeStages.fromGeodetic(to, true) };
        }
        if (to == 4258) {
            return new JHeStage[] { JHeStages.toGeodetic(from, true) };
        }
        return new JHeStage[] { JHeStages.toGeodetic(from, false), JHeStages.fromGeodetic(to, false) };
    }

    // 4326 and 84 are treated as EUREF-FIN, the ellipsoid difference is ignored
    private static int normalize(int srid) {
        return srid == 84 || srid == 4326 ? 4258 : srid;
    }

    private JHeMathTransformFactory() {
        // Block init
    }

}
