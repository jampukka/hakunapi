package fi.nls.hakunapi.tiles.source.vectortile.mvt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BackwardProtoBufTest {

    @Test
    public void varintSingleByte() {
        BackwardProtoBuf b = new BackwardProtoBuf();
        b.prependVarint(1);
        assertArrayEquals(new byte[] {0x01}, b.toByteArray());
    }

    @Test
    public void varint300() {
        // 300 = 0b100101100 -> 0xAC 0x02 (canonical protobuf example)
        BackwardProtoBuf b = new BackwardProtoBuf();
        b.prependVarint(300);
        assertArrayEquals(new byte[] {(byte) 0xAC, 0x02}, b.toByteArray());
    }

    @Test
    public void varintLargeRoundTrips() {
        long v = 0xDEADBEEFCAFEL;
        BackwardProtoBuf b = new BackwardProtoBuf();
        b.prependVarint(v);
        byte[] enc = b.toByteArray();
        assertEquals(v, readVarint(enc));
    }

    @Test
    public void tagThenBodyIsForwardOrder() {
        // field 4, LEN, body {0xAA,0xBB}: prepend body then header.
        BackwardProtoBuf b = new BackwardProtoBuf();
        b.prependBytes(new byte[] {(byte) 0xAA, (byte) 0xBB}, 0, 2);
        b.prependLengthDelimitedHeader(4, 2);
        // tag = (4<<3)|2 = 0x22, len=2, then body
        assertArrayEquals(new byte[] {0x22, 0x02, (byte) 0xAA, (byte) 0xBB}, b.toByteArray());
    }

    @Test
    public void bulkBytesSpanChunks() {
        BackwardProtoBuf b = new BackwardProtoBuf(64); // small chunks
        byte[] data = new byte[200];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        b.prependBytes(data, 0, data.length);
        assertEquals(200, b.length());
        assertArrayEquals(data, b.toByteArray());
    }

    @Test
    public void fixed32LittleEndian() {
        BackwardProtoBuf b = new BackwardProtoBuf();
        b.prependFixed32Field(1, 0x01020304);
        // tag=(1<<3)|5=0x0D, then LE bytes 04 03 02 01
        assertArrayEquals(new byte[] {0x0D, 0x04, 0x03, 0x02, 0x01}, b.toByteArray());
    }

    @Test
    public void resetReusesChunks() {
        BackwardProtoBuf b = new BackwardProtoBuf(64);
        b.prependBytes(new byte[100], 0, 100);
        assertEquals(100, b.length());
        b.reset();
        assertEquals(0, b.length());
        b.prependVarint(7);
        assertArrayEquals(new byte[] {0x07}, b.toByteArray());
    }

    private static long readVarint(byte[] a) {
        long result = 0;
        int shift = 0;
        for (byte value : a) {
            int bb = value & 0xff;
            result |= (long) (bb & 0x7f) << shift;
            if ((bb & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return result;
    }
}
