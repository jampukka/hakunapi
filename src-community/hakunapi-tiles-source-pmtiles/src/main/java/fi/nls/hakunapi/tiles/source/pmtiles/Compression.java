package fi.nls.hakunapi.tiles.source.pmtiles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * PMTiles v3 compression type ids (header bytes) and decode helpers.
 *
 * <p>Directories and metadata are always decompressed using
 * {@code internal_compression}. Tile contents are <em>not</em> decompressed by
 * this source: the stored bytes are passed through verbatim and the
 * {@code tile_compression} is advertised via HTTP {@code Content-Encoding}
 * (only gzip is a valid Content-Encoding; other tile compressions are returned
 * raw without an encoding header).
 */
public final class Compression {

    public static final byte UNKNOWN = 0;
    public static final byte NONE = 1;
    public static final byte GZIP = 2;
    public static final byte BROTLI = 3;
    public static final byte ZSTD = 4;

    private Compression() {
    }

    /** Decompress directory/metadata bytes per {@code internal_compression}. */
    public static byte[] decode(byte type, byte[] bytes) throws IOException {
        switch (type) {
            case NONE:
            case UNKNOWN:
                return bytes;
            case GZIP:
                return gunzip(bytes);
            default:
                throw new IOException("Unsupported PMTiles internal compression: " + type
                        + " (only none/gzip supported for directories)");
        }
    }

    /**
     * The HTTP {@code Content-Encoding} for tile bytes stored with the given
     * {@code tile_compression}, or null when none applies. Only gzip maps to a
     * valid Content-Encoding; other compressions are served without a header.
     */
    public static String contentEncoding(byte type) {
        return type == GZIP ? "gzip" : null;
    }

    private static byte[] gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length * 2);
            in.transferTo(out);
            return out.toByteArray();
        }
    }

}
