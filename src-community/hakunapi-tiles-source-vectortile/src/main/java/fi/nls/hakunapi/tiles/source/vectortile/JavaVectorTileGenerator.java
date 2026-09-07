package fi.nls.hakunapi.tiles.source.vectortile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.geom.Bbox;
import fi.nls.hakunapi.core.geom.HakunaGeometry;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.projection.ProjectionHelper;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import fi.nls.hakunapi.tiles.TileBody;
import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtLayerEncoder;
import fi.nls.hakunapi.tiles.source.vectortile.mvt.MvtTileWriter;

/**
 * In-JVM {@link VectorTileGenerator}: encodes a Mapbox Vector Tile entirely in
 * Java from hakunapi's own feature stores, with no PostGIS {@code ST_AsMVT} and
 * no JTS in the geometry path. For each requested collection it queries the
 * collection's {@link fi.nls.hakunapi.core.FeatureProducer producer} by the
 * tile's bounding box, streams the matching features, and feeds each geometry
 * through the per-type MVT geometry writers (clip, snap, simplify, command
 * encode) and each attribute through the {@link MvtLayerEncoder}, emitting one
 * MVT layer per collection.
 *
 * <p>The generator holds no per-tile state of its own: the geometry writers and
 * the single layer encoder are held per worker thread and reset for each tile,
 * so their scratch is grown once rather than on every request. {@code generate}
 * queries nothing itself - it returns a
 * lazy {@link TileBody} that encodes and writes <em>one layer at a time</em>:
 * encode a collection into the encoder, stream that layer to the sink, reset the
 * encoder, move to the next. Peak memory is therefore the largest single layer
 * rather than the whole tile, and the query for collection n+1 does not run
 * until layer n is on the wire - which is what makes a dataset tile of many
 * collections viable.
 *
 * <p>One layer does have to be buffered: a layer's {@code name}, {@code keys} and
 * {@code values} precede its features on the wire but are only known once every
 * feature is encoded, which is why {@link MvtLayerEncoder} builds it backwards.
 */
public class JavaVectorTileGenerator implements VectorTileGenerator {

    public static final String TYPE = "hakunapi";

    /**
     * MVT coordinate extent: geometry within a tile is quantised to an
     * {@code [0, MVT_EXTENT]} integer grid. 4096 is the de-facto standard and is
     * intentionally decoupled from the tile matrix's raster pixel size (256 for
     * WebMercatorQuad) so straight edges don't snap to a coarse grid.
     */
    private static final int MVT_EXTENT = 4096;

    /** Clip buffer in MVT extent units added around the tile ({@code ~1/16} margin). */
    private static final int DEFAULT_BUFFER = 256;

    /**
     * The encode scratch, held per worker thread rather than allocated per tile.
     * Both carry buffers whose size is a property of the data, not of the tile -
     * a large ring, a layer's value table - so recreating them per request meant
     * growing them from their initial capacity on every tile. Each bounds what it
     * retains between tiles (see their trim methods), so a thread's footprint
     * does not become the largest tile ever requested.
     *
     * <p>Instance fields, not static: the generator's lifetime is the service's,
     * and a static ThreadLocal would outlive a redeployed webapp while still
     * referencing its classes.
     */
    private final ThreadLocal<MvtGeometryWriters> writers =
            ThreadLocal.withInitial(MvtGeometryWriters::new);
    private final ThreadLocal<MvtLayerEncoder> encoders =
            ThreadLocal.withInitial(MvtLayerEncoder::new);

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Optional<TileBody> generate(TileContext ctx, List<FeatureType> collections,
            String mediaType, Map<String, String> requestParams) throws Exception {
        if (collections.isEmpty()) {
            return Optional.of(out -> { /* empty tile: no layers */ });
        }

        // Lazy body: encode and write one layer at a time into a single reused
        // encoder, so only one layer is ever buffered and a collection is not
        // queried until the previous layer has been written out.
        return Optional.of(out -> {
            // Fetched here, not in generate(): the body is lazy and runs on
            // whichever thread writes the response.
            MvtGeometryWriters writers = this.writers.get();
            writers.initTile(ctx.minX(), ctx.maxY(), ctx.resolution(),
                    ctx.tileWidth(), ctx.tileHeight(), MVT_EXTENT, DEFAULT_BUFFER);
            MvtLayerEncoder layer = encoders.get();
            try {
                for (int i = 0; i < collections.size(); i++) {
                    encodeLayer(ctx, collections.get(i), writers, layer);
                    layer.finish(); // prepend version/name/extent/keys/values in front of features
                    MvtTileWriter.writeLayer(out, layer);
                }
            } finally {
                // Also on the error path: a tile that failed mid-encode has grown
                // the same buffers a successful one would have.
                writers.trimScratch();
                layer.trim();
            }
        });
    }

    private void encodeLayer(TileContext ctx, FeatureType ft,
            MvtGeometryWriters writers, MvtLayerEncoder layer) throws Exception {
        layer.reset(ft.getName());
        layer.setExtent(MVT_EXTENT);

        HakunaPropertyGeometry geomProp = ft.getGeom();
        if (geomProp == null) {
            return; // non-spatial collection: empty layer
        }

        // Index of the geometry and of each attribute property in the
        // ValueProvider, mirroring GetFeatureCollection's property order:
        // [id, geom, attributes...]. We rebuild the same collection below so the
        // positions line up.
        GetFeatureRequest request = new GetFeatureRequest();
        request.setSRID(ctx.srid());
        request.setBboxSrid(ctx.srid());
        request.setLimit(fi.nls.hakunapi.core.param.LimitParam.UNLIMITED);

        GetFeatureCollection col = new GetFeatureCollection(ft);
        addBboxFilter(ctx, geomProp, col);
        request.addCollection(col);

        // ValueProvider slots line up with col.getProperties() positionally:
        // [orderBy..., id, geom, ...ft.getProperties()] (see GetFeatureCollection).
        // geom and id can each appear more than once (id/geom are in the base AND
        // possibly again in ft.getProperties()); take the FIRST geom as the source
        // of coordinates and mark EVERY id/geom slot as non-attribute so it is not
        // re-emitted as a tag.
        List<HakunaProperty> props = col.getProperties();
        HakunaProperty idProp = ft.getId();
        int geomIdx = props.indexOf(geomProp);

        int n = props.size();
        int[] keyIdxByProp = new int[n];
        for (int i = 0; i < n; i++) {
            HakunaProperty p = props.get(i);
            if (p == geomProp || p == idProp) {
                keyIdxByProp[i] = -1; // geometry or id: never an attribute tag
            } else {
                keyIdxByProp[i] = layer.keyIndex(p.getName());
            }
        }
        int idIdx = props.indexOf(idProp);
        // Null unless the id is numeric: MVT feature ids are uint64, so a
        // string id has no representation and the feature goes out without one.
        HakunaPropertyType idType = null;
        if (idProp != null && (idProp.getType() == HakunaPropertyType.INT
                || idProp.getType() == HakunaPropertyType.LONG)) {
            idType = idProp.getType();
        }

        try (FeatureStream stream = ft.getFeatureProducer().getFeatures(request, col)) {
            while (stream.hasNext()) {
                ValueProvider vp = stream.next();
                encodeFeature(vp, props, geomIdx, idIdx, idType, keyIdxByProp, writers, layer);
            }
        }
    }

    private void encodeFeature(ValueProvider vp, List<HakunaProperty> props,
            int geomIdx, int idIdx, HakunaPropertyType idType, int[] keyIdxByProp,
            MvtGeometryWriters writers, MvtLayerEncoder layer) throws Exception {
        if (geomIdx < 0 || vp.isNull(geomIdx)) {
            return;
        }
        HakunaGeometry geom = vp.getHakunaGeometry(geomIdx);
        if (geom == null) {
            return;
        }
        if (!writers.write(geom) || writers.encoder().length() == 0) {
            return; // unsupported type, or clipped entirely away
        }

        long id = (idType != null && idIdx >= 0 && !vp.isNull(idIdx))
                ? idOf(vp, idIdx, idType) : 0;

        layer.beginFeature();
        for (int i = 0; i < props.size(); i++) {
            int keyIdx = keyIdxByProp[i];
            if (keyIdx < 0 || vp.isNull(i)) {
                continue;
            }
            tagAttribute(layer, keyIdx, props.get(i).getType(), vp, i);
        }
        layer.endFeature(id, writers.mvtType(),
                writers.encoder().commands(), writers.encoder().length());
    }

    /**
     * The feature id, which the caller has already established is a non-null
     * INT or LONG. Read through the typed getter rather than getObject: the
     * latter boxes whatever the driver hands back and then needs an instanceof
     * to get at it again.
     */
    private static long idOf(ValueProvider vp, int i, HakunaPropertyType type) {
        if (type == HakunaPropertyType.LONG) {
            Long l = vp.getLong(i);
            return l != null ? l.longValue() : 0;
        }
        Integer n = vp.getInt(i);
        return n != null ? n.longValue() : 0;
    }

    /**
     * Tags one attribute onto the feature being built. The caller has checked
     * isNull, so a getter returning null here means the source disagrees with
     * the declared type; the value is dropped rather than guessed at.
     *
     * <p>Every numeric case reads its own typed getter. getObject would box the
     * driver's value and cost an instanceof to unbox it - per attribute, per
     * feature, and a tile is tens of thousands of features.
     */
    private static void tagAttribute(MvtLayerEncoder layer, int keyIdx,
            HakunaPropertyType type, ValueProvider vp, int i) {
        switch (type) {
        case STRING:
            String s = vp.getString(i);
            if (s != null) layer.tagString(keyIdx, s);
            break;
        case BOOLEAN:
            Boolean b = vp.getBoolean(i);
            if (b != null) layer.tagBool(keyIdx, b);
            break;
        case INT:
            Integer n = vp.getInt(i);
            if (n != null) layer.tagLong(keyIdx, n.longValue());
            break;
        case LONG:
            Long l = vp.getLong(i);
            if (l != null) layer.tagLong(keyIdx, l.longValue());
            break;
        case FLOAT:
            Float f = vp.getFloat(i);
            if (f != null) layer.tagFloat(keyIdx, f.floatValue());
            break;
        case DOUBLE:
            Double d = vp.getDouble(i);
            if (d != null) layer.tagDouble(keyIdx, d.doubleValue());
            break;
        case DATE:
            // MVT has no date type, so this still goes out as LocalDate#toString's
            // bytes - but the encoder dedups it on the epoch day, so a date it has
            // already seen builds no String and no byte[].
            LocalDate date = vp.getLocalDate(i);
            if (date != null) layer.tagDate(keyIdx, date);
            break;
        default:
            // Fallback: stringify anything else (timestamps, uuid, arrays, json).
            // Through getObject, not getString: a value container's getString
            // casts to String, so a LocalDateTime column would throw. MVT has
            // only the scalar types, so toString is the representation.
            Object o = vp.getObject(i);
            if (o != null) layer.tagString(keyIdx, o.toString());
        }
    }

    private static void addBboxFilter(TileContext ctx, HakunaPropertyGeometry geomProp,
            GetFeatureCollection col) {
        Envelope env = new Envelope(ctx.minX(), ctx.maxX(), ctx.minY(), ctx.maxY());
        Geometry bbox = Bbox.toGeometry(env);
        bbox.setSRID(ctx.srid());
        Geometry storage = ProjectionHelper.reprojectToStorageCRS(geomProp, bbox);
        col.addFilter(Filter.intersectsIndex(geomProp, storage));
    }
}
