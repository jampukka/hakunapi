package fi.nls.hakunapi.tiles.source.vectortile.mvt;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Streams a finished MVT {@code Tile} to an {@link OutputStream}. A Tile is just
 * a sequence of {@code repeated Layer layers = 3} entries; each layer is already
 * encoded (back-to-front) in its own {@link MvtLayerEncoder}, so the tile level
 * writes, per layer, the {@code Tile.layers} tag and length varint forward, then
 * streams the layer's buffered chunks straight through. No whole-tile buffer and
 * no copy of layer bodies.
 */
public final class MvtTileWriter {

    private static final int F_LAYERS = 3;

    private MvtTileWriter() {}

    /** Write the given layers as one Tile message to {@code out}. */
    public static void write(OutputStream out, MvtLayerEncoder... layers) throws IOException {
        for (MvtLayerEncoder layer : layers) {
            writeLayer(out, layer);
        }
    }

    /** Write a single layer as a {@code Tile.layers} entry. */
    public static void writeLayer(OutputStream out, MvtLayerEncoder layer) throws IOException {
        int len = layer.length();
        writeTag(out, F_LAYERS, BackwardProtoBuf.WIRE_LEN);
        writeVarint(out, len);
        layer.writeTo(out);
    }

    private static void writeTag(OutputStream out, int field, int wire) throws IOException {
        writeVarint(out, ((long) field << 3) | wire);
    }

    private static void writeVarint(OutputStream out, long value) throws IOException {
        long v = value;
        while ((v & ~0x7fL) != 0) {
            out.write((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }
}
