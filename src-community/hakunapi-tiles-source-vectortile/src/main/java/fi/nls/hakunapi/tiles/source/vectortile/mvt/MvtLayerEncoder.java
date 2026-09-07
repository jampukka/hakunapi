package fi.nls.hakunapi.tiles.source.vectortile.mvt;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import fi.nls.hakunapi.core.util.LocalDateOutput;

/**
 * Encodes one MVT {@code Layer} back-to-front into its own
 * {@link BackwardProtoBuf}. Features are appended as they are produced; on
 * {@link #finish()} the layer's {@code keys}, {@code values}, {@code name},
 * {@code extent} and {@code version} are prepended in front of the feature block,
 * so the finished layer body reads (ascending) as
 * {@code version,name,extent,keys...,values...,features...}.
 *
 * <h2>Keys and values</h2>
 * Keys are the layer's attribute names. They are interned to small integer
 * indices: a caller that knows the collection schema can supply the index
 * directly via {@link #keyIndex(String)} once and reuse it. Values are
 * content-deduplicated across the whole layer (a repeated value is stored once
 * and referenced by index), trading a probe per attribute for a smaller tile.
 * The value dedup is allocation-free on the hot path: numeric values are interned
 * by their raw bits and strings by their UTF-8 content through a single primitive
 * open-addressing table, so tagging a number or a repeated string boxes nothing
 * and a distinct string is UTF-8 encoded exactly once.
 *
 * <p>A feature's {@code tags} are a packed {@code [keyIndex, valueIndex]*}. The
 * caller drives one feature at a time: {@link #beginFeature()}, zero or more
 * {@code tag*} attribute calls, then {@link #endFeature} with the encoded
 * geometry and type.
 *
 * <p>Not thread-safe; one per worker thread, reset via {@link #reset(String)}.
 */
public final class MvtLayerEncoder {

    // vector_tile.Layer field numbers
    private static final int F_NAME = 1;
    private static final int F_FEATURES = 2;
    private static final int F_KEYS = 3;
    private static final int F_VALUES = 4;
    private static final int F_EXTENT = 5;
    private static final int F_VERSION = 15;

    // vector_tile.Feature field numbers
    private static final int FE_ID = 1;
    private static final int FE_TAGS = 2;
    private static final int FE_TYPE = 3;
    private static final int FE_GEOMETRY = 4;

    public static final int GEOM_POINT = 1;
    public static final int GEOM_LINE = 2;
    public static final int GEOM_POLYGON = 3;

    private static final int MVT_VERSION = 2;

    /**
     * Distinct values whose table capacity is kept between tiles. Above this the
     * table is released by {@link #trim()}: a layer of a few hundred thousand
     * distinct ids would otherwise pin megabytes per worker thread.
     */
    private static final int RETAINED_VALUES = 16384;

    /**
     * Internal slot type for a date: stored as its packed year/month/day in
     * {@link #valueBits} with no bytes of its own, and rendered as ISO-8601
     * straight into the output buffer at {@link #finish()}. Outside
     * {@link MvtValue}'s 1-7 wire field numbers so it can never collide with one.
     *
     * <p>Packed rather than kept as an epoch day so that rendering needs no
     * {@link LocalDate} back: the whole point of the slot is that a date costs
     * no allocation, and {@code ofEpochDay} would put one back per distinct date.
     */
    private static final int SLOT_DATE = -1;

    private final BackwardProtoBuf buf = new BackwardProtoBuf();

    /** Renders a date's ISO bytes at finish() time; a date never owns a byte[]. */
    private final byte[] dateScratch = new byte[LocalDateOutput.MAX_BYTE_LEN];

    private String name;
    private int extent = 4096;

    // Keys: ordered distinct names + name->index.
    private final Map<String, Integer> keyIndex = new HashMap<>();
    private byte[][] keyUtf8 = new byte[16][];
    private int keyCount;

    // Values: ordered distinct values, replayed into the buffer at finish(). All
    // dedup is boxing-free and shares one global value index space (valueType drives
    // which parallel array a slot reads at finish()):
    //   - numeric values (int/double/float/bool) store raw bits in valueBits,
    //   - string values store their UTF-8 bytes in valueUtf8 (encoded once here),
    // and a single open-addressing hash table dedups both: numeric keys mix
    // (type,bits), string keys hash the UTF-8 content; a slot holds valueIndex+1
    // (0 = empty).
    private int[] valueType = new int[16];
    private long[] valueBits = new long[16];      // raw bits for numeric values
    private byte[][] valueUtf8 = new byte[16][];  // UTF-8 bytes for string values
    private int valueCount;

    private int[] valueHashSlots = new int[32];   // value index + 1, 0 = empty
    private int valueHashMask = valueHashSlots.length - 1;

    // Current feature tag accumulator (packed key,value indices).
    private int[] tags = new int[32];
    private int tagCount;

    public void reset(String layerName) {
        buf.reset();
        this.name = layerName;
        keyIndex.clear();
        keyCount = 0;
        valueCount = 0;
        java.util.Arrays.fill(valueHashSlots, 0);
        tagCount = 0;
    }

    /**
     * Bound what the encoder keeps between tiles. Retaining the capacity is what
     * makes the table growth a one-off, but retaining it without a ceiling would
     * let the largest layer ever encoded set the owning thread's footprint - and
     * {@link #reset(String)} clears the hash table proportionally to its length,
     * so an outsized table would also cost every later layer.
     *
     * <p>Value slots hold references to the strings they interned, so a retained
     * table is cleared rather than merely rewound: those bytes must not outlive
     * the tile that produced them.
     */
    public void trim() {
        buf.trim();
        if (valueType.length > RETAINED_VALUES) {
            valueType = new int[RETAINED_VALUES];
            valueBits = new long[RETAINED_VALUES];
            valueUtf8 = new byte[RETAINED_VALUES][];
        } else {
            java.util.Arrays.fill(valueUtf8, 0, valueCount, null);
        }
        if (valueHashSlots.length > RETAINED_VALUES * 2) {
            valueHashSlots = new int[RETAINED_VALUES * 2];
            valueHashMask = valueHashSlots.length - 1;
        }
    }

    public void setExtent(int extent) {
        this.extent = extent;
    }

    public int length() {
        return buf.length();
    }

    // --- key / value interning -------------------------------------------------

    /** Intern an attribute name, returning its layer key index. */
    public int keyIndex(String key) {
        Integer idx = keyIndex.get(key);
        if (idx != null) {
            return idx;
        }
        int i = keyCount;
        if (i == keyUtf8.length) {
            byte[][] g = new byte[i << 1][];
            System.arraycopy(keyUtf8, 0, g, 0, i);
            keyUtf8 = g;
        }
        keyUtf8[i] = key.getBytes(StandardCharsets.UTF_8);
        keyIndex.put(key, i);
        keyCount = i + 1;
        return i;
    }

    /**
     * Intern a numeric value (its raw bits + MVT type) with no boxing. Probes the
     * shared open-addressing table for an existing (type,bits) slot, else appends a
     * new value. {@code bits} is the value's exact bit pattern:
     * {@code doubleToLongBits} / {@code floatToIntBits} (zero-extended) / the long
     * itself / {@code 0|1} for bool.
     */
    private int internNumeric(int type, long bits) {
        int h = hashNumeric(type, bits) & valueHashMask;
        while (true) {
            int slot = valueHashSlots[h];
            if (slot == 0) {
                int i = appendNumeric(type, bits);
                putSlot(h, i);
                return i;
            }
            int vi = slot - 1;
            if (valueType[vi] == type && valueUtf8[vi] == null && valueBits[vi] == bits) {
                return vi;
            }
            h = (h + 1) & valueHashMask;
        }
    }

    /** Intern a string value by its UTF-8 content (encoded once here), no boxing. */
    private int internString(byte[] utf8) {
        int h = hashBytes(utf8) & valueHashMask;
        while (true) {
            int slot = valueHashSlots[h];
            if (slot == 0) {
                int i = appendString(utf8);
                putSlot(h, i);
                return i;
            }
            int vi = slot - 1;
            if (valueType[vi] == MvtValue.STRING
                    && java.util.Arrays.equals(valueUtf8[vi], utf8)) {
                return vi;
            }
            h = (h + 1) & valueHashMask;
        }
    }

    private int appendNumeric(int type, long bits) {
        int i = ensureValueSlot();
        valueType[i] = type;
        valueBits[i] = bits;
        valueUtf8[i] = null;
        valueCount = i + 1;
        return i;
    }

    private int appendString(byte[] utf8) {
        int i = ensureValueSlot();
        valueType[i] = MvtValue.STRING;
        valueUtf8[i] = utf8;
        valueCount = i + 1;
        return i;
    }

    private int ensureValueSlot() {
        int i = valueCount;
        if (i == valueType.length) {
            int cap = i << 1;
            int[] gt = new int[cap];
            long[] gb = new long[cap];
            byte[][] gu = new byte[cap][];
            System.arraycopy(valueType, 0, gt, 0, i);
            System.arraycopy(valueBits, 0, gb, 0, i);
            System.arraycopy(valueUtf8, 0, gu, 0, i);
            valueType = gt;
            valueBits = gb;
            valueUtf8 = gu;
        }
        return i;
    }

    /** Record value index {@code i} in hash slot {@code h}, growing the table first. */
    private void putSlot(int h, int i) {
        valueHashSlots[h] = i + 1;
        // Grow + rehash when the table passes ~75% load.
        if ((valueCount << 2) >= (valueHashSlots.length * 3)) {
            rehash();
        }
    }

    private void rehash() {
        int[] old = valueHashSlots;
        int cap = old.length << 1;
        valueHashSlots = new int[cap];
        valueHashMask = cap - 1;
        for (int s = 0; s < valueCount; s++) {
            int h = (valueType[s] == MvtValue.STRING ? hashBytes(valueUtf8[s])
                    : hashNumeric(valueType[s], valueBits[s])) & valueHashMask;
            while (valueHashSlots[h] != 0) {
                h = (h + 1) & valueHashMask;
            }
            valueHashSlots[h] = s + 1;
        }
    }

    private static int hashNumeric(int type, long bits) {
        long h = bits * 0x9E3779B97F4A7C15L + type;
        return (int) (h ^ (h >>> 32));
    }

    private static int hashBytes(byte[] a) {
        int h = 1;
        for (int i = 0; i < a.length; i++) {
            h = 31 * h + a[i];
        }
        return h;
    }

    // --- attribute tagging -----------------------------------------------------

    private void addTag(int keyIdx, int valueIdx) {
        if (tagCount + 2 > tags.length) {
            int[] g = new int[tags.length << 1];
            System.arraycopy(tags, 0, g, 0, tagCount);
            tags = g;
        }
        tags[tagCount++] = keyIdx;
        tags[tagCount++] = valueIdx;
    }

    public void tagString(int keyIdx, String value) {
        tagStringUtf8(keyIdx, value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Tag a string attribute by its UTF-8 bytes directly. Lets a caller that
     * already holds the encoded bytes (e.g. a column read straight as UTF-8) skip
     * the {@code String} entirely. The interner takes ownership of {@code utf8}
     * (stored and emitted as-is; do not mutate after the call).
     */
    public void tagStringUtf8(int keyIdx, byte[] utf8) {
        addTag(keyIdx, internString(utf8));
    }

    public void tagDouble(int keyIdx, double value) {
        addTag(keyIdx, internNumeric(MvtValue.DOUBLE, Double.doubleToLongBits(value)));
    }

    public void tagFloat(int keyIdx, float value) {
        addTag(keyIdx, internNumeric(MvtValue.FLOAT, Float.floatToIntBits(value) & 0xffffffffL));
    }

    public void tagLong(int keyIdx, long value) {
        addTag(keyIdx, internNumeric(MvtValue.INT, value));
    }

    public void tagBool(int keyIdx, boolean value) {
        addTag(keyIdx, internNumeric(MvtValue.BOOL, value ? 1L : 0L));
    }

    /**
     * Tag a date attribute. Goes out as the ISO-8601 string
     * {@link LocalDate#toString()} would produce - MVT has no date value type -
     * but the value table holds only the epoch day, so neither a repeated nor a
     * distinct date allocates: the bytes are rendered once, at finish().
     */
    public void tagDate(int keyIdx, LocalDate date) {
        addTag(keyIdx, internNumeric(SLOT_DATE, packDate(date)));
    }

    /** year in the high bits, then month, then day - one long, comparable as one. */
    private static long packDate(LocalDate date) {
        return ((long) date.getYear() << 9) | (date.getMonthValue() << 5) | date.getDayOfMonth();
    }

    // --- feature emission ------------------------------------------------------

    public void beginFeature() {
        tagCount = 0;
    }

    /**
     * Finish the current feature, prepending its body (id, tags, type, geometry)
     * as one {@code Layer.features} entry. Geometry is the MVT command stream
     * (one uint32 per int) from the geometry writers.
     *
     * @param id        feature id (0 to omit semantics handled by caller)
     * @param geomType  {@link #GEOM_POINT}/{@link #GEOM_LINE}/{@link #GEOM_POLYGON}
     * @param geometry  command ints
     * @param geomLen   number of valid ints in {@code geometry}
     */
    public void endFeature(long id, int geomType, int[] geometry, int geomLen) {
        int before = buf.length();

        // Emit feature fields in reverse (geometry highest address ... id lowest).
        // geometry (packed uint32)
        int geomBytes = packedVarintSize(geometry, geomLen);
        prependPackedVarints(geometry, geomLen);
        buf.prependLengthDelimitedHeader(FE_GEOMETRY, geomBytes);

        // type
        buf.prependVarintField(FE_TYPE, geomType);

        // tags (packed uint32)
        int tagBytes = packedVarintSizeI(tags, tagCount);
        prependPackedVarintsI(tags, tagCount);
        buf.prependLengthDelimitedHeader(FE_TAGS, tagBytes);

        // id
        if (id != 0) {
            buf.prependVarintField(FE_ID, id);
        }

        int featureLen = buf.length() - before;
        buf.prependLengthDelimitedHeader(F_FEATURES, featureLen);
    }

    // --- layer finish ----------------------------------------------------------

    /** Prepend keys/values/name/extent/version in front of the feature block. */
    public void finish() {
        // values: emit in reverse index order so ascending wire == index order.
        for (int i = valueCount - 1; i >= 0; i--) {
            int before = buf.length();
            prependValueBody(i);
            int len = buf.length() - before;
            buf.prependLengthDelimitedHeader(F_VALUES, len);
        }
        // keys: reverse order, same reasoning.
        for (int i = keyCount - 1; i >= 0; i--) {
            byte[] k = keyUtf8[i];
            buf.prependBytes(k, 0, k.length);
            buf.prependLengthDelimitedHeader(F_KEYS, k.length);
        }
        // Always written, even at the protobuf default of 4096. Skipping it is
        // legal, but a decoder that reads the field without applying the default
        // then divides by an absent extent: OpenLayers' MVT format produces NaN
        // for every coordinate that way. Three bytes once per layer.
        buf.prependVarintField(F_EXTENT, extent);
        byte[] nm = name.getBytes(StandardCharsets.UTF_8);
        buf.prependBytes(nm, 0, nm.length);
        buf.prependLengthDelimitedHeader(F_NAME, nm.length);
        buf.prependVarintField(F_VERSION, MVT_VERSION);
    }

    private void prependValueBody(int i) {
        int type = valueType[i];
        switch (type) {
        case SLOT_DATE:
            // Held packed; the string form is produced only here, once per
            // distinct date, into scratch the encoder already owns.
            long packed = valueBits[i];
            int len = LocalDateOutput.outputLocalDate(
                    (int) (packed >> 9), (int) (packed >> 5) & 0xf, (int) (packed & 0x1f),
                    dateScratch, 0);
            buf.prependBytes(dateScratch, 0, len);
            buf.prependLengthDelimitedHeader(MvtValue.STRING, len);
            break;
        case MvtValue.STRING:
            byte[] s = valueUtf8[i];
            MvtValue.prependString(buf, s);
            break;
        case MvtValue.DOUBLE:
            MvtValue.prependDouble(buf, Double.longBitsToDouble(valueBits[i]));
            break;
        case MvtValue.FLOAT:
            MvtValue.prependFloat(buf, Float.intBitsToFloat((int) valueBits[i]));
            break;
        case MvtValue.INT:
            MvtValue.prependInt(buf, valueBits[i]);
            break;
        case MvtValue.BOOL:
            MvtValue.prependBool(buf, valueBits[i] != 0);
            break;
        default:
            throw new IllegalStateException("value type " + type);
        }
    }

    /** Stream this finished layer to {@code out} as a {@code Tile.layers} entry. */
    public void writeTo(OutputStream out) throws IOException {
        buf.writeTo(out);
    }

    // --- packed varint helpers -------------------------------------------------

    private static int varintSize(long v) {
        int n = 1;
        long t = v >>> 7;
        while (t != 0) {
            n++;
            t >>>= 7;
        }
        return n;
    }

    private static int packedVarintSize(int[] a, int len) {
        int n = 0;
        for (int i = 0; i < len; i++) {
            n += varintSize(a[i] & 0xffffffffL);
        }
        return n;
    }

    private static int packedVarintSizeI(int[] a, int len) {
        return packedVarintSize(a, len);
    }

    private void prependPackedVarints(int[] a, int len) {
        // reverse so ascending order matches a[0..len)
        for (int i = len - 1; i >= 0; i--) {
            buf.prependVarint(a[i] & 0xffffffffL);
        }
    }

    private void prependPackedVarintsI(int[] a, int len) {
        prependPackedVarints(a, len);
    }
}
