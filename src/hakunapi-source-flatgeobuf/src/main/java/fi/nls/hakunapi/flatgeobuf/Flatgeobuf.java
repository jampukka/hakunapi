package fi.nls.hakunapi.flatgeobuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.stream.LongStream;

import org.locationtech.jts.geom.Envelope;
import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.PackedRTree;

public class Flatgeobuf {
    
    public final HeaderMeta meta;
    public final LargeFile file;
    public final long fileSize;
    public final long geometryIndexSize;
    public final long featuresOffset;
    
    public Flatgeobuf(LargeFile file) throws IllegalArgumentException, IOException, Exception {
        this.file = file;
        this.fileSize = file.getSize();
        this.meta = readHeader(file);
        this.geometryIndexSize = PackedRTree.calcSize((int) this.meta.featuresCount, this.meta.indexNodeSize);
        this.featuresOffset = meta.offset + geometryIndexSize;
    }

    public static HeaderMeta readHeader(LargeFile file) throws IOException {
        ByteBuffer start = file.getBytes(0L, 12, new MutableInt());
        int headerSize = start.getInt(8);
        ByteBuffer headerBytes = file.getBytes(0L, 8 + 4 + headerSize, new MutableInt());
        return HeaderMeta.read(headerBytes);
    }
    
    public LongStream boundingBoxStream(Envelope e) {
        return FlatgeobufGeometryIndex.bboxStream(file, this.meta.offset, (int) meta.featuresCount, meta.indexNodeSize, e).map(indexOffset -> featuresOffset + indexOffset);
    }

}
