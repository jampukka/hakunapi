package fi.nls.hakunapi.tiles.source.pmtiles;

import java.util.Optional;

/**
 * One opened PMTiles archive: a {@link RangeReader} plus the parsed
 * {@link PMTilesHeader} and root {@link Directory}, with on-demand traversal of
 * leaf directories. The header and root directory are read once at open time
 * and kept in memory (both are small and hot); leaf directories and tile bytes
 * are fetched per request via the reader.
 *
 * <p>PMTiles permits an arbitrary depth of leaf directories. In practice tools
 * emit at most one leaf level, but this follows the chain to a bounded depth to
 * stay correct without risking an unbounded loop on a malformed archive.
 */
public class PMTilesArchive implements AutoCloseable {

    private static final int MAX_LEAF_DEPTH = 4;

    private final RangeReader reader;
    private final PMTilesHeader header;
    private final Directory rootDir;

    private PMTilesArchive(RangeReader reader, PMTilesHeader header, Directory rootDir) {
        this.reader = reader;
        this.header = header;
        this.rootDir = rootDir;
    }

    public static PMTilesArchive open(RangeReader reader) throws Exception {
        byte[] headerBytes = reader.readRange(0, PMTilesHeader.LENGTH);
        PMTilesHeader header = PMTilesHeader.parse(headerBytes);
        byte[] rootRaw = reader.readRange(header.rootDirOffset(), (int) header.rootDirLength());
        Directory rootDir = Directory.parse(Compression.decode(header.internalCompression(), rootRaw));
        return new PMTilesArchive(reader, header, rootDir);
    }

    public PMTilesHeader getHeader() {
        return header;
    }

    /**
     * Fetch the raw (still tile-compressed) bytes for an XYZ tile address, or
     * empty if the archive holds no tile there.
     */
    public Optional<byte[]> getTile(int z, long x, long y) throws Exception {
        long tileId = TileId.fromZXY(z, x, y);
        Directory dir = rootDir;
        for (int depth = 0; depth <= MAX_LEAF_DEPTH; depth++) {
            Directory.Entry e = dir.find(tileId);
            if (e == null) {
                return Optional.empty();
            }
            if (!e.isLeaf()) {
                return Optional.of(reader.readRange(header.tileDataOffset() + e.offset(), (int) e.length()));
            }
            byte[] leafRaw = reader.readRange(header.leafDirsOffset() + e.offset(), (int) e.length());
            dir = Directory.parse(Compression.decode(header.internalCompression(), leafRaw));
        }
        throw new IllegalStateException("PMTiles leaf directory chain exceeded depth " + MAX_LEAF_DEPTH);
    }

    @Override
    public void close() throws Exception {
        reader.close();
    }

}
