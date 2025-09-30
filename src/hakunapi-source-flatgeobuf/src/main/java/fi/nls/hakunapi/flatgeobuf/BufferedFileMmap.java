package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;

public class BufferedFileMmap implements BufferedFile {
    
    private final FileChannel fc;
    
    public BufferedFileMmap(Path path) throws IOException {
        this.fc = FileChannel.open(path);
    }
    
    @Override
    public ByteBuffer getBytes(long offset, int len) {
        try {
            return fc.map(MapMode.READ_ONLY, offset, len).order(ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getInt(long offset) {
        return getBytes(offset, 4).getInt(0);
    }

    @Override
    public long getLong(long offset) {
        return getBytes(offset, Long.SIZE).getLong(0);
    }

    @Override
    public double getDouble(long offset) {
        return getBytes(offset, Double.SIZE).getDouble(0);
    }
    
    @Override
    public void close() throws Exception {
        fc.close();
    }

}
