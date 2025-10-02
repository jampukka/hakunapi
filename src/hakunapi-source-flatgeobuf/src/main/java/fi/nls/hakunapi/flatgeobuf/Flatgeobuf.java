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
    
    public Iterator<Feature> boundingBoxSearch(Envelope e) {
        Feature f = new Feature();
        return boundingBoxStream(e).mapToObj(indexOffset -> readFeature(featuresOffset + indexOffset, f)).iterator();
    }
    
    public Feature readFeature(long offset, Feature f) {
        int featureSize = file.getInt(offset);
        ByteBuffer featureBytes = file.getBytes(offset + 4, featureSize);
        return Feature.getRootAsFeature(featureBytes, f);
    }
    
    public LongStream boundingBoxStream(Envelope e) {
        return FlatgeobufGeometryIndex.bboxStream(file, this.meta.offset, (int) meta.featuresCount, meta.indexNodeSize, e);
    }
    
    public Iterator<Feature> all() {
        return new FgbFeatureIterator();
    }
    
    public final class FgbFeatureIterator implements Iterator<Feature> {
        
        private long off;
        
        private FgbFeatureIterator() {
            this.off = featuresOffset;
        }
        
        @Override
        public boolean hasNext() {
            return off < fileSize;
        }

        @Override
        public Feature next() {
            long localOff = off;
            int size = file.getInt(localOff);
            localOff += Integer.BYTES;
            ByteBuffer featureBytes = file.getBytes(localOff, size);
            Feature f = Feature.getRootAsFeature(featureBytes);
            off = localOff + size;
            return f;
        }

    }

}
