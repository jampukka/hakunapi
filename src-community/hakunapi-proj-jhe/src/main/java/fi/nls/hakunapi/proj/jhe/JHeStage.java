package fi.nls.hakunapi.proj.jhe;

/**
 * One step of a coordinate transformation pipeline. Transforms n easting-first
 * coordinate pairs in place.
 */
@FunctionalInterface
public interface JHeStage {

    public void apply(double[] xy, int off, int n);

}
