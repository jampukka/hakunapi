package fi.nls.hakunapi.simple.mosaic;

import fi.nls.hakunapi.core.FeatureType;

/**
 * Adapter that turns a mosaic tile — identified only by a file {@code path} at request time — into a
 * queryable {@link FeatureType} bound to the mosaic's shared, declared schema.
 *
 * <p>The mosaic owns the schema (id/geometry/properties) and forwards it to every tile; tiles are
 * <em>trusted</em> to conform. A backend implementation builds a per-tile feature type by delegating
 * to its own {@link fi.nls.hakunapi.core.SimpleSource#parse} against a synthetic single-collection
 * config whose only per-tile difference is the file path. Resulting feature types are cached by path
 * (see {@link CachingMosaicBackend}) so repeated requests over the same tiles reuse them.
 */
public interface MosaicBackend extends AutoCloseable {

    /** The catalog {@code backend} value this adapter handles (e.g. {@code duckdb}). */
    String getType();

    /**
     * Build (or return a cached) feature type for the tile file at {@code path}, carrying the mosaic's
     * shared schema. Implementations may probe the file once per distinct path.
     */
    FeatureType tileFeatureType(String path) throws Exception;

    @Override
    default void close() throws Exception {
        // Backends holding an engine/datasource override this.
    }

}
