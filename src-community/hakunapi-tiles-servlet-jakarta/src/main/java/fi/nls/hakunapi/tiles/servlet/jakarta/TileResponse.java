package fi.nls.hakunapi.tiles.servlet.jakarta;

import java.io.IOException;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.tiles.Tile;

/**
 * Turns a {@link Tile} into a streamed HTTP response, shared by the three tile
 * resources (collection vector, dataset vector, collection map).
 *
 * <p>The body is handed to JAX-RS as a {@link StreamingOutput} over
 * {@link Tile#writeTo}, not as a {@code byte[]}: a generating source encodes the
 * tile a layer at a time straight into the response, so neither the whole tile
 * nor all of its layers are ever held in memory. A dataset tile of many
 * collections depends on this.
 *
 * <p>The cost is the usual streaming one: the status line is already sent when
 * the body starts encoding, so a failure part way through cannot become a 500.
 * It is logged and the connection breaks mid-tile, which is what a client sees
 * as a truncated tile. Nothing before the first write can fail here - the tile
 * address is resolved and the source has already accepted it - so the exposure
 * is a mid-stream store or encoding failure, and buffering the whole tile to
 * turn that into a clean 500 is exactly the cost this class exists to avoid.
 */
public final class TileResponse {

    private static final Logger LOG = LoggerFactory.getLogger(TileResponse.class);

    private TileResponse() {}

    /**
     * A 200 response streaming {@code tile}, with the tile's own media type and
     * content encoding when it declares them.
     *
     * @param tile             the tile to stream
     * @param defaultMediaType media type to use when the tile declares none
     */
    public static Response ok(Tile tile, String defaultMediaType) {
        StreamingOutput body = out -> {
            try {
                tile.writeTo(out);
            } catch (Exception e) {
                // Already committed: the response status and headers are sent, so
                // this cannot be turned into an error response. Log and let the
                // stream break.
                LOG.warn("Failed to write tile", e);
                throw new IOException(e);
            }
        };
        Response.ResponseBuilder rb = Response.ok(body)
                .type(tile.getMediaType() != null ? tile.getMediaType() : defaultMediaType);
        if (tile.getContentEncoding() != null) {
            rb.header("Content-Encoding", tile.getContentEncoding());
        }
        return rb.build();
    }

}
