package fi.nls.hakunapi.tiles;

import java.io.OutputStream;

/**
 * A {@link Tile} backed by a lazy {@link TileBody}. Used by generating sources
 * (e.g. the in-JVM MVT encoder): the source supplies the tile address, media type
 * and content encoding, and a {@link TileBody} that encodes the bytes only when
 * {@link #writeTo} is called. Nothing is materialised until then; {@link #getBytes()}
 * falls back to the buffering default.
 */
public class BodyTile implements Tile {

    private final String tileMatrixSetId;
    private final String tileMatrix;
    private final long tileRow;
    private final long tileCol;
    private final String mediaType;
    private final String contentEncoding;
    private final TileBody body;

    public BodyTile(String tileMatrixSetId, String tileMatrix, long tileRow, long tileCol,
            String mediaType, TileBody body) {
        this(tileMatrixSetId, tileMatrix, tileRow, tileCol, mediaType, null, body);
    }

    public BodyTile(String tileMatrixSetId, String tileMatrix, long tileRow, long tileCol,
            String mediaType, String contentEncoding, TileBody body) {
        this.tileMatrixSetId = tileMatrixSetId;
        this.tileMatrix = tileMatrix;
        this.tileRow = tileRow;
        this.tileCol = tileCol;
        this.mediaType = mediaType;
        this.contentEncoding = contentEncoding;
        this.body = body;
    }

    @Override
    public String getTileMatrixSetId() {
        return tileMatrixSetId;
    }

    @Override
    public String getTileMatrix() {
        return tileMatrix;
    }

    @Override
    public long getTileRow() {
        return tileRow;
    }

    @Override
    public long getTileCol() {
        return tileCol;
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String getContentEncoding() {
        return contentEncoding;
    }

    @Override
    public void writeTo(OutputStream out) throws Exception {
        body.writeTo(out);
    }

}
