package fi.nls.hakunapi.tiles.source.gpkg;

/**
 * Sniffs the media type of a GeoPackage tile blob from its leading bytes.
 *
 * <p>The GeoPackage tile format is not recorded per-tile in the standard
 * tables; the pyramid table simply stores image blobs (PNG/JPEG, or WEBP via
 * the {@code gpkg_webp} extension). The advertised media type is therefore
 * derived from the blob's magic bytes, unless overridden by config.
 */
final class GpkgFormat {

    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";
    static final String WEBP = "image/webp";

    private GpkgFormat() {
    }

    /**
     * @param tile the raw tile blob
     * @return the sniffed media type, or {@code null} if unrecognised
     */
    static String sniff(byte[] tile) {
        if (tile == null || tile.length < 12) {
            return null;
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((tile[0] & 0xFF) == 0x89 && tile[1] == 'P' && tile[2] == 'N' && tile[3] == 'G') {
            return PNG;
        }
        // JPEG: FF D8 FF
        if ((tile[0] & 0xFF) == 0xFF && (tile[1] & 0xFF) == 0xD8 && (tile[2] & 0xFF) == 0xFF) {
            return JPEG;
        }
        // WEBP: "RIFF" .... "WEBP"
        if (tile[0] == 'R' && tile[1] == 'I' && tile[2] == 'F' && tile[3] == 'F'
                && tile[8] == 'W' && tile[9] == 'E' && tile[10] == 'B' && tile[11] == 'P') {
            return WEBP;
        }
        return null;
    }

}
