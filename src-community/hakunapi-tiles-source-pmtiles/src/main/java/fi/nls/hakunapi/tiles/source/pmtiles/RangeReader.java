package fi.nls.hakunapi.tiles.source.pmtiles;

/**
 * Reads a contiguous byte range from a PMTiles archive. PMTiles is a
 * cloud-native single-file format: the header, directories and tile data are
 * all addressed by absolute offset, so the only access primitive a backing
 * store must provide is "give me {@code length} bytes starting at
 * {@code offset}".
 *
 * <p>Implementations: {@link FileRangeReader} (local filesystem) and
 * {@link HttpRangeReader} (remote https via HTTP {@code Range} requests).
 */
public interface RangeReader extends AutoCloseable {

    /**
     * @param offset absolute byte offset into the archive
     * @param length number of bytes to read
     * @return exactly {@code length} bytes
     * @throws java.io.IOException if fewer than {@code length} bytes are available
     */
    byte[] readRange(long offset, int length) throws Exception;

    @Override
    default void close() throws Exception {
        // NOP
    }

}
