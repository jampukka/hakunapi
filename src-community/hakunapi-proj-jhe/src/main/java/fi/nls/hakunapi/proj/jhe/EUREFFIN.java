package fi.nls.hakunapi.proj.jhe;

import static java.lang.Math.*;

/**
 * https://www.suomidigi.fi/ohjeet-ja-tuki/jhs-suositukset/jhs-197-euref-fin-koordinaattijarjestelmat-niihin-liittyvat-muunnokset-ja-karttalehtijako
 */
public class EUREFFIN {

    public static final double k0_TM = 0.9996;
    public static final double l0_TM34 = toRadians(21.0);
    public static final double l0_TM35 = toRadians(27.0);
    public static final double l0_TM36 = toRadians(33.0);
    public static final double E0_TM = 500000.0;

    private static final double a = 6378137.0;
    private static final double inv_a = 1.0 / 6378137.0;
    private static final double f = 1.0 / 298.257222101;

    private static final double n = f / (2.0 - f);
    private static final double n2 = n * n;
    private static final double n3 = n * n * n;
    private static final double n4 = n * n * n * n;

    private static final double A1 = (a / (1.0 + n)) * (1.0 + (n2 / 4.0) + (n4 / 64.0));

    private static final double h1 = (n / 2.0) - (2.0 * n2 / 3.0) + (37.0 * n3 / 96.0) - (n4 / 360.0);
    private static final double h2 = (n2 / 48.0) + (n3 / 15.0) - (437.0 * n4 / 1440.0);
    private static final double h3 = (17.0 * n3 / 480.0) - (37.0 * n4 / 840.0);
    private static final double h4 = (4397.0 * n4 / 161280.0);

    private static final double h1_ = (n / 2.0) - (2.0 * n2 / 3.0) + (5.0 * n3 / 16.0) + (41.0 * n4 / 180.0);
    private static final double h2_ = (13.0 * n2 / 48.0) - (3.0 * n3 / 5.0) + (557.0 * n4 / 1440.0);
    private static final double h3_ = (61.0 * n3 / 240.0) - (103.0 * n4 / 140.0);
    private static final double h4_ = (49561.0 * n4 / 161280.0);

    // Geodetic latitude to conformal latitude, chi = phi + sum(c_k * sin(2k * phi)),
    // and back, phi = chi + sum(d_k * sin(2k * chi)). Karney 2011, "Transverse
    // Mercator with an accuracy of a few nanometers"; these are the JHS-197 chapter
    // 2.2 / 2.3 isometric latitude and its inverse, as a series rather than as
    // logarithms and an iteration. Worst case against those, globally: chi 0.6 nm,
    // phi 4 um -- the inverse direction is the weaker of the two.
    private static final double c1 = -(2.0 * n) + (2.0 * n2 / 3.0) + (4.0 * n3 / 3.0) - (82.0 * n4 / 45.0);
    private static final double c2 = (5.0 * n2 / 3.0) - (16.0 * n3 / 15.0) - (13.0 * n4 / 9.0);
    private static final double c3 = -(26.0 * n3 / 15.0) + (34.0 * n4 / 21.0);
    private static final double c4 = (1237.0 * n4 / 630.0);

    private static final double d1 = (2.0 * n) - (2.0 * n2 / 3.0) - (2.0 * n3) + (116.0 * n4 / 45.0);
    private static final double d2 = (7.0 * n2 / 3.0) - (8.0 * n3 / 5.0) - (227.0 * n4 / 45.0);
    private static final double d3 = (56.0 * n3 / 15.0) - (136.0 * n4 / 35.0);
    private static final double d4 = (4279.0 * n4 / 630.0);

    private static final double atanh(final double x) { return 0.5 * log((1.0 + x) / (1.0 - x)); }

    public static void geoToPlane(double lon, double lat, double k0, double l0, double E0, double[] out, int off) {
        geoToPlaneRad(toRadians(lon), toRadians(lat), k0, l0, E0, out, off);
    }

    public static void geoToPlaneRad(double lonRad, double latRad, double k0, double l0, double E0, double[] out, int off) {
        // Conformal latitude directly, rather than through asinh and atanh
        double B = latRad + clenshawSin(sin(latRad), cos(latRad), c1, c2, c3, c4);

        double l = lonRad - l0;
        double n_ = atanh(cos(B) * sin(l));
        double ks_ = asin(sin(B) * cosh(n_));

        // ks + i*nn = (ks_ + i*n_) + sum(h_k_ * sin(2k * (ks_ + i*n_)))
        clenshaw(ks_, n_, h1_, h2_, h3_, h4_, out, off);
        double ks = ks_ + out[off + 0];
        double nn = n_ + out[off + 1];

        double A1k0 = A1 * k0;
        out[off + 0] = A1k0 * nn + E0;
        out[off + 1] = A1k0 * ks;
    }

    public static void planeToGeo(double E, double N, double k0, double l0, double E0, double[] out, int off) {
        planeToGeoRad(E, N, k0, l0, E0, out, off);
        out[off + 0] = toDegrees(out[off + 0]);
        out[off + 1] = toDegrees(out[off + 1]);
    }

    public static void planeToGeoRad(double E, double N, double k0, double l0, double E0, double[] out, int off) {
        double invA1k0 = 1.0 / (A1 * k0);
        double ks = N * invA1k0;
        double nn = (E - E0) * invA1k0;

        clenshaw(ks, nn, h1, h2, h3, h4, out, off);
        double ks_ = ks - out[off + 0];
        double nn_ = nn - out[off + 1];

        // sinB is the asin argument itself, and cosB is needed for l regardless,
        // so expanding B to geodetic below costs no further call into Math
        double sinB = sin(ks_) / cosh(nn_);
        double B = asin(sinB);
        double cosB = cos(B);
        double l = asin(tanh(nn_) / cosB);

        out[off + 0] = l0 + l;
        out[off + 1] = B + clenshawSin(sinB, cosB, d1, d2, d3, d4);
    }

    /**
     * sum(g_k * sin(2k * x)) for k = 1..4, given sin(x) and cos(x). Same Clenshaw
     * recurrence as clenshaw() with the imaginary part dropped: four sin calls
     * become a handful of multiplies, exactly (to a rounding error).
     */
    private static double clenshawSin(double sinX, double cosX, double g1, double g2, double g3, double g4) {
        double sin2x = 2.0 * sinX * cosX;
        double a = 2.0 * (1.0 - 2.0 * sinX * sinX); // 2 * cos(2x)

        double u4 = g4;
        double u3 = a * u4 + g3;
        double u2 = a * u3 - u4 + g2;
        double u1 = a * u2 - u3 + g1;

        return sin2x * u1;
    }

    /**
     * sum(g_k * sin(2k * z)) for k = 1..4 and z = x + i*y, written to out as
     * (real, imaginary). Both JHS-197 series expansions are that sum: the real part
     * is sum(g_k sin(2k x) cosh(2k y)) and the imaginary part
     * sum(g_k cos(2k x) sinh(2k y)). Evaluated term by term that is 16 calls into
     * Math; Clenshaw recursion over the complex argument needs one sin/cos and one
     * sinh/cosh, and agrees with the direct sum to within a rounding error.
     */
    private static void clenshaw(double x, double y, double g1, double g2, double g3, double g4,
            double[] out, int off) {
        double sinX = sin(x);
        double cosX = cos(x);
        double sinhY = sinh(y);
        double coshY = cosh(y);

        double sin2x = 2.0 * sinX * cosX;
        double cos2x = 1.0 - 2.0 * sinX * sinX;
        double sinh2y = 2.0 * sinhY * coshY;
        double cosh2y = 1.0 + 2.0 * sinhY * sinhY;

        // ar + i*ai = 2 * cos(2z), the Clenshaw recurrence coefficient
        double ar = 2.0 * cos2x * cosh2y;
        double ai = -2.0 * sin2x * sinh2y;

        // u_k = (2 cos 2z) u_{k+1} - u_{k+2} + g_k, downwards from k = 4
        double u4r = g4;
        double u4i = 0.0;
        double u3r = ar * u4r + g3;
        double u3i = ai * u4r;
        double u2r = ar * u3r - ai * u3i - u4r + g2;
        double u2i = ar * u3i + ai * u3r - u4i;
        double u1r = ar * u2r - ai * u2i - u3r + g1;
        double u1i = ar * u2i + ai * u2r - u3i;

        // sum = sin(2z) * u1
        double s2r = sin2x * cosh2y;
        double s2i = cos2x * sinh2y;
        out[off + 0] = s2r * u1r - s2i * u1i;
        out[off + 1] = s2r * u1i + s2i * u1r;
    }

    public static void geoToTM35fin(double lon, double lat, double[] out, int off) {
        geoToTM35finRad(toRadians(lon), toRadians(lat), out, off);
    }

    public static void geoToTM35finRad(double lonRad, double latRad, double[] out, int off) {
        geoToPlaneRad(lonRad, latRad, k0_TM, l0_TM35, E0_TM, out, off);
    }

    public static void tm35finToGeo(double E, double N, double[] out, int off) {
        planeToGeo(E, N, k0_TM, l0_TM35, E0_TM, out, off);
    }

    public static void tm35finToGeoRad(double E, double N, double[] out, int off) {
        planeToGeoRad(E, N, k0_TM, l0_TM35, E0_TM, out, off);
    }

    public static void geoToWebMerc(double lon, double lat, double[] out, int off) {
        geoToWebMercRad(toRadians(lon), toRadians(lat), out, off);
    }

    public static void geoToWebMercRad(double lonRad, double latRad, double[] out, int off) {
        // Ignore differences between etrs89 and wgs84
        out[off + 0] = lonRad * a;
        out[off + 1] = log(tan(PI / 4.0 + latRad / 2.0)) * a;
    }

    public static void webMercToGeoRad(double x, double y, double[] out, int off) {
        // Ignore differences between etrs89 and wgs84
        out[off + 0] = x * inv_a;
        out[off + 1] = atan(exp(y * inv_a)) * 2 - PI / 2.0;
    }

    public static void tm35finToWebMerc(double E, double N, double[] out, int off) {
        tm35finToGeoRad(E, N, out, off);
        geoToWebMercRad(out[off], out[off + 1], out, off);
    }

    public static void webMercToTM35fin(double x, double y, double[] out, int off) {
        webMercToGeoRad(x, y, out, off);
        geoToTM35finRad(out[off], out[off + 1], out, off);
    }

    public static void gkNNtoGeo(int nn, double E, double N, double[] out, int off) {
        double k0 = 1.0;
        double l0 = toRadians(nn);
        double E0 = 1_000_000 * nn + 500_000;
        planeToGeo(E, N, k0, l0, E0, out, off);
    }

    public static void gkNNtoGeoRad(int nn, double E, double N, double[] out, int off) {
        double k0 = 1.0;
        double l0 = toRadians(nn);
        double E0 = 1_000_000 * nn + 500_000;
        planeToGeoRad(E, N, k0, l0, E0, out, off);
    }

    public static void geoToGKnn(int nn, double lon, double lat, double[] out, int off) {
        geoToGKnnRad(nn, toRadians(lon), toRadians(lat), out, off);
    }

    public static void geoToGKnnRad(int nn, double lonRad, double latRad, double[] out, int off) {
        double k0 = 1.0;
        double l0 = toRadians(nn);
        double E0 = 1_000_000 * nn + 500_000;
        geoToPlaneRad(lonRad, latRad, k0, l0, E0, out, off);
    }

    public static int tm35finToGKnn(double E, double N, double[] out, int off) {
        tm35finToGeoRad(E, N, out, off);
        double lonRad = out[off];
        double latRad = out[off + 1];
        int nn = (int) Math.round(toDegrees(lonRad));
        geoToGKnnRad(nn, lonRad, latRad, out, off);
        return nn;
    }

    public static void tm35finToGKnn(double E, double N, int nn, double[] out, int off) {
        tm35finToGeoRad(E, N, out, off);
        geoToGKnnRad(nn, out[off], out[off + 1], out, off);
    }

    public static void gkNNtoTM35fin(int nn, double E, double N, double[] out, int off) {
        gkNNtoGeoRad(nn, E, N, out, off);
        geoToTM35finRad(out[off], out[off + 1], out, off);
    }

    public static void tm35finToTM34(double E, double N, double[] out, int off) {
        planeToGeoRad(E, N, k0_TM, l0_TM35, E0_TM, out, off);
        geoToPlaneRad(out[off], out[off + 1], k0_TM, l0_TM34, E0_TM, out, off);
    }

    public static void tm34toTM35fin(double E, double N, double[] out, int off) {
        planeToGeoRad(E, N, k0_TM, l0_TM34, E0_TM, out, off);
        geoToPlaneRad(out[off], out[off + 1], k0_TM, l0_TM35, E0_TM, out, off);
    }

    public static void tm35finToTM35(double E, double N, double[] out, int off) {
        out[off++] = E;
        out[off++] = N;
    }

    public static void tm35toTM35fin(double E, double N, double[] out, int off) {
        out[off++] = E;
        out[off++] = N;
    }

    public static void tm35finToTM36(double E, double N, double[] out, int off) {
        planeToGeoRad(E, N, k0_TM, l0_TM35, E0_TM, out, off);
        geoToPlaneRad(out[off], out[off + 1], k0_TM, l0_TM36, E0_TM, out, off);
    }

    public static void tm36toTM35fin(double E, double N, double[] out, int off) {
        planeToGeoRad(E, N, k0_TM, l0_TM36, E0_TM, out, off);
        geoToPlaneRad(out[off], out[off + 1], k0_TM, l0_TM35, E0_TM, out, off);
    }

}