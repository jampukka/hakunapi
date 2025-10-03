package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.stream.LongStream;

import org.locationtech.jts.geom.Envelope;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.PackedRTree;

public class Flatgeobuf {
    
    public final HeaderMeta meta;

    private final LargeFile file;
    private final long fileSize;
    private final long geometryIndexSize;
    private final long featuresOffset;
    
    public Flatgeobuf(LargeFile file) throws IllegalArgumentException, IOException, Exception {
        this.file = file;
        this.fileSize = file.getSize();
        this.meta = readHeader(file);
        this.geometryIndexSize = PackedRTree.calcSize((int) this.meta.featuresCount, this.meta.indexNodeSize);
        this.featuresOffset = meta.offset + geometryIndexSize;
    }

    public static HeaderMeta readHeader(LargeFile file) throws IOException {
        ByteBuffer start = file.getBytes(0L, 12);
        int headerSize = start.getInt(8);
        ByteBuffer headerBytes = file.getBytes(0L, 8 + 4 + headerSize);
        return HeaderMeta.read(headerBytes);
    }
    
    public Iterator<ByteBuffer> boundingBoxSearch(Envelope e) {
        return boundingBoxStream(e).mapToObj(indexOffset -> readFeatureBytes(featuresOffset + indexOffset)).iterator();
    }
    
    private ByteBuffer readFeatureBytes(long offset) {
        return file.getBytes(offset + 4, file.getInt(offset));
    }
    
    public LongStream boundingBoxStream(Envelope e) {
        return FlatgeobufGeometryIndex.bboxStream(file, this.meta.offset, (int) meta.featuresCount, meta.indexNodeSize, e);
    }
    
    public Iterator<ByteBuffer> all() {
        return new FgbFeatureIterator();
    }
    
    public final class FgbFeatureIterator implements Iterator<ByteBuffer> {
        
        private long off;
        
        private FgbFeatureIterator() {
            this.off = featuresOffset;
        }
        
        @Override
        public boolean hasNext() {
            return off < fileSize;
        }

        @Override
        public ByteBuffer next() {
            long localOff = off;
            int size = file.getInt(localOff);
            localOff += Integer.BYTES;
            ByteBuffer featureBytes = file.getBytes(localOff, size);
            off = localOff + size;
            return featureBytes;
        }

    }

}
