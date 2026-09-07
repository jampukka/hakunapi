package fi.nls.hakunapi.tiles;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

/**
 * A single encoded tile: its address within a {@link TileMatrixSet} plus a way to
 * write the raw encoded body and its media type. Format-agnostic - the body may be
 * vector (MVT) or raster (PNG/JPEG/...) depending on the {@link TileSource}.
 *
 * <p>The body is exposed as {@link #writeTo(OutputStream)} rather than a byte[] so
 * a generating source (e.g. the in-JVM MVT encoder) can stream straight to the
 * response - through gzip and out the socket - without ever materialising the whole
 * tile. Sources that already hold the bytes (WMTS/GPKG/PMTiles) implement this with
 * {@link BytesTile}. Callers that genuinely need an array (a tile cache, tests) can
 * use {@link #getBytes()}, which buffers via {@link #writeTo}; byte-backed tiles
 * override it to return their array with no copy.
 */
public interface Tile {

    String getTileMatrixSetId();

    String getTileMatrix();

    long getTileRow();

    long getTileCol();

    String getMediaType();

    /** HTTP Content-Encoding of the body (e.g. {@code gzip}); null when uncompressed. */
    String getContentEncoding();

    /** Stream the encoded tile body to {@code out}. May be called once. */
    void writeTo(OutputStream out) throws Exception;

    /**
     * Materialise the body into a byte array. Default buffers {@link #writeTo};
     * byte-backed implementations override to return their array directly.
     */
    default byte[] getBytes() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        writeTo(b);
        return b.toByteArray();
    }

}
