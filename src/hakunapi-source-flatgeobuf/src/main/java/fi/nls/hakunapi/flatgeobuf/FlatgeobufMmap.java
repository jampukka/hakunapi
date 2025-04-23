package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import org.locationtech.jts.geom.Envelope;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.PackedRTree;
import org.wololo.flatgeobuf.generated.Feature;

public class FlatgeobufMmap implements AutoCloseable {

    private final FileChannel fc;
    private final ByteBuffer mmap;
    private final HeaderMeta meta;

    public FlatgeobufMmap(Path path) throws IOException, IllegalArgumentException {
        this(path, null);
    }

    public FlatgeobufMmap(Path path, HeaderMeta meta) throws IOException, IllegalArgumentException {
        long size = Files.size(path);
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Files larger than int max size currently not supported");
        }
        this.fc = FileChannel.open(path);
        this.mmap = fc.map(MapMode.READ_ONLY, 0L, size).order(ByteOrder.LITTLE_ENDIAN);
        this.meta = meta != null ? meta : HeaderMeta.read(mmap);
        mmap.position(0);
    }

    public HeaderMeta getHeaderMeta() {
        return meta;
    }
    
    public Iterator<Feature> boundingBoxSearch(Envelope e) {
        return PackedRTree.search(mmap, meta.offset, (int) meta.featuresCount, meta.indexNodeSize, e).stream()
                .map(x -> readFeature((int) x.offset))
                .iterator();
    }

    public Feature readFeature(int offset) {
        return Feature.getRootAsFeature(mmap.slice().position(offset + 4));
    }

    @Override
    public void close() throws Exception {
        fc.close();
    }
    
    public FgbFeatureIterator all(int offset) {
        long geometryindexSize = PackedRTree.calcSize((int) meta.featuresCount, meta.indexNodeSize);
        long featuresOffset = meta.offset + geometryindexSize;
        return new FgbFeatureIterator(featuresOffset, offset, (int) meta.featuresCount, mmap);
    }
    
    public final class FgbFeatureIterator implements Iterator<Feature> {
        
        private final int featuresCount;
        private final ByteBuffer mmap;
        private final Feature f;

        private int byteOffset;
        private int i;
        
        private FgbFeatureIterator(long featuresOffset, int offset, int featuresCount, ByteBuffer mmap) {
            this.byteOffset = (int) featuresOffset;
            this.featuresCount = featuresCount;
            this.f = new Feature();
            this.mmap = mmap;
            if (offset >= featuresCount)  {
                i = offset;
            } else {
                for (i = 0; i < offset; i++) {
                    int size = mmap.getInt(byteOffset);
                    byteOffset += 4;
                    byteOffset += size;
                }
            }
        }
        
        public int getNextFeatureByteOffset() {
            return byteOffset;
        }

        @Override
        public boolean hasNext() {
            return i < featuresCount;
        }

        @Override
        public Feature next() {
            int size = mmap.getInt(byteOffset);
            byteOffset += 4;
            Feature.getRootAsFeature(mmap.duplicate().position(byteOffset), f);
            byteOffset += size;
            i++;
            return f;
        }

    }

}
