package fi.nls.hakunapi.source.gpkg.http.vfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Reads a local file. Not the point of this module, but it makes the VFS
 * testable without a server and gives a baseline to compare remote reads
 * against.
 */
public class FileRangeReader implements RangeReader {

    private final FileChannel channel;
    private final long size;

    public FileRangeReader(Path path) throws IOException {
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        this.size = channel.size();
    }

    @Override
    public int read(long srcOffset, byte[] dst, int dstOff, int len) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(dst, dstOff, len);
        int total = 0;
        while (buf.hasRemaining()) {
            int n = channel.read(buf, srcOffset + total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

}
