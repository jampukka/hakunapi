package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.stream.LongStream;

import org.locationtech.jts.geom.Envelope;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.PackedRTree;
import org.wololo.flatgeobuf.generated.Feature;

public class Flatgeobuf {
    
    public final HeaderMeta meta;

    private final BufferedFile file;
    private final long geometryIndexSize;
    private final long featuresOffset;
    
    public Flatgeobuf(BufferedFile file) throws IllegalArgumentException, IOException, Exception {
        this.file = file;
        this.meta = readHeader(file);
        this.geometryIndexSize = PackedRTree.calcSize((int) this.meta.featuresCount, this.meta.indexNodeSize);
        this.featuresOffset = meta.offset + geometryIndexSize;
    }

    public static HeaderMeta readHeader(BufferedFile file) throws IOException {
        ByteBuffer start = file.getBytes(0L, 12);
        int headerSize = start.getInt(8);
        ByteBuffer headerBytes = file.getBytes(0L, 8 + 4 + headerSize);
        return HeaderMeta.read(headerBytes);
    }
    
    public Iterator<Feature> boundingBoxSearch(Envelope e) {
        return boundingBoxStream(e).mapToObj(this::readFeature).iterator();
    }
    
    public Feature readFeature(long offset) {
        int featureSize = file.getInt(offset);
        ByteBuffer featureBytes = file.getBytes(offset + 4, featureSize);
        return Feature.getRootAsFeature(featureBytes);
    }
    
    public LongStream boundingBoxStream(Envelope e) {
        return FlatgeobufGeometryIndex.bboxStream(file, meta.offset, (int) meta.featuresCount, meta.indexNodeSize, e);
    }
    
    public Iterator<Feature> all() {
        return new FgbFeatureIterator();
    }
    
    public final class FgbFeatureIterator implements Iterator<Feature> {
        
        private long off;
        private long i;
        
        private FgbFeatureIterator() {
            this.off = featuresOffset;
            this.i = 0L;
        }
        
        @Override
        public boolean hasNext() {
            return i < meta.featuresCount;
        }

        @Override
        public Feature next() {
            int size = file.getInt(off);
            off += Integer.SIZE;
            ByteBuffer featureBytes = file.getBytes(off, size);
            Feature f = Feature.getRootAsFeature(featureBytes);
            off += size;
            i++;
            return f;
        }

    }

}
