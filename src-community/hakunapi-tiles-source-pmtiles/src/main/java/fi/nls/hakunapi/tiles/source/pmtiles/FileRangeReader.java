package fi.nls.hakunapi.tiles.source.pmtiles;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link RangeReader} over a local PMTiles file. Backed by a single
 * {@link SeekableByteChannel}; access is synchronized so one source instance
 * can serve concurrent tile requests from the same open file.
 */
public class FileRangeReader implements RangeReader {

    private final SeekableByteChannel channel;

    public FileRangeReader(Path path) throws IOException {
        this.channel = Files.newByteChannel(path);
    }

    @Override
    public synchronized byte[] readRange(long offset, int length) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(length);
        channel.position(offset);
        while (buf.hasRemaining()) {
            if (channel.read(buf) < 0) {
                throw new EOFException("PMTiles range past end of file: offset=" + offset + " length=" + length);
            }
        }
        return buf.array();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

}
