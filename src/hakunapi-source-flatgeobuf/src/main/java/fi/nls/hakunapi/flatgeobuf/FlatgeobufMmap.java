package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;

import org.locationtech.jts.geom.Envelope;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.PackedRTree;
import org.wololo.flatgeobuf.generated.Feature;

public class FlatgeobufMmap implements AutoCloseable {

    private final FileChannel fc;
    private final ByteBuffer mmap;
    private final HeaderMeta meta;
    private final long featuresOffset;

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
        this.featuresOffset = this.meta.offset + PackedRTree.calcSize((int) this.meta.featuresCount, this.meta.indexNodeSize);
        mmap.position(0);
    }

    public HeaderMeta getHeaderMeta() {
        return meta;
    }
    
    public Iterator<Feature> boundingBoxSearch(Envelope e) {
        return Arrays.stream(FlatgeobufGeometryIndex.bbox(mmap.duplicate().order(ByteOrder.LITTLE_ENDIAN), meta.offset, (int) meta.featuresCount, meta.indexNodeSize, e))
                .mapToObj(x -> readFeature((int) x))
                .iterator();
    }

    public Feature readFeature(int offset) {
        return Feature.getRootAsFeature(mmap.duplicate().position((int) (featuresOffset + offset + 4)));
    }

    @Override
    public void close() throws Exception {
        fc.close();
    }
    
    public FgbFeatureIterator all() {
        return new FgbFeatureIterator(featuresOffset, (int) meta.featuresCount, mmap);
    }
    
    public final class FgbFeatureIterator implements Iterator<Feature> {
        
        private final int featuresCount;
        private final ByteBuffer mmap;
        private final Feature f;

        private int byteOffset;
        private int i;
        
        private FgbFeatureIterator(long featuresOffset, int featuresCount, ByteBuffer mmap) {
            this.byteOffset = (int) featuresOffset;
            this.featuresCount = featuresCount;
            this.f = new Feature();
            this.mmap = mmap;
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
