package fi.nls.hakunapi.simple.mosaic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.filter.Filter;
import fi.nls.hakunapi.core.filter.FilterOp;
import fi.nls.hakunapi.core.request.GetFeatureCollection;
import fi.nls.hakunapi.core.request.GetFeatureRequest;
import fi.nls.hakunapi.simple.mosaic.MosaicIndex.TileRef;

/**
 * A {@link FeatureProducer} that answers a request by querying the {@link MosaicIndex} for the tiles
 * intersecting the request bbox, then returning the {@code UNION ALL} of those tiles' rows via a lazy
 * {@link ConcatFeatureStream}.
 *
 * <p>The bbox is derived from the incoming spatial filters (an {@code INTERSECTS}/{@code WITHIN}/…
 * predicate carries a JTS geometry whose envelope is the query box). If the request has no spatial
 * filter the bbox is {@code null} and every tile is selected. Each selected tile is turned into a
 * {@link FeatureType} by its backend adapter and rebound to the incoming collection with
 * {@link GetFeatureCollection#withFt}, so all tiles are queried with the mosaic's shared schema,
 * filters and ordering.
 *
 * <p>{@code numberMatched} is out of scope (a cross-file distinct/total count) and returns {@code -1}.
 */
public class MosaicFeatureProducer implements FeatureProducer {

    private final MosaicIndex index;
    private final Map<String, MosaicBackend> backends;

    public MosaicFeatureProducer(MosaicIndex index, Map<String, MosaicBackend> backends) {
        this.index = index;
        this.backends = backends;
    }

    @Override
    public FeatureStream getFeatures(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        double[] bbox = extractBbox(col.getFilters());
        List<TileRef> refs = index.select(bbox);

        List<ConcatFeatureStream.StreamSupplier> suppliers = new ArrayList<>(refs.size());
        for (TileRef ref : refs) {
            MosaicBackend backend = backends.get(ref.backend);
            if (backend == null) {
                throw new IllegalStateException("Mosaic index references unknown backend '" + ref.backend
                        + "' for tile " + ref.path);
            }
            // Bind once here (probes/caches the tile feature type), defer only the stream open so the
            // query does not run until the concat reaches this tile.
            FeatureType tileFt = backend.tileFeatureType(ref.path);
            FeatureProducer producer = tileFt.getFeatureProducer();
            GetFeatureCollection tileCol = col.withFt(tileFt);
            suppliers.add(() -> producer.getFeatures(request, tileCol));
        }
        return new ConcatFeatureStream(suppliers);
    }

    /**
     * {@code numberMatched} is not computed for mosaic responses (a cross-file count is out of scope);
     * {@code -1} signals "omit".
     */
    @Override
    public int getNumberMatched(GetFeatureRequest request, GetFeatureCollection col) throws Exception {
        return -1;
    }

    /**
     * The AND-combined envelope of every spatial predicate in {@code filters}, as
     * {minx, miny, maxx, maxy}, or {@code null} if there is no spatial filter (⇒ select all tiles).
     */
    static double[] extractBbox(List<Filter> filters) {
        Envelope acc = null;
        for (Filter f : filters) {
            Envelope e = envelopeOf(f);
            if (e == null) {
                continue;
            }
            if (acc == null) {
                acc = new Envelope(e);
            } else {
                // AND semantics: the query region is the intersection of the predicates.
                acc = acc.intersection(e);
            }
        }
        if (acc == null || acc.isNull()) {
            return acc == null ? null : new double[] { 0, 0, 0, 0 };
        }
        return new double[] { acc.getMinX(), acc.getMinY(), acc.getMaxX(), acc.getMaxY() };
    }

    /** The bounding envelope of a spatial filter's geometry, or {@code null} for non-spatial ops. */
    private static Envelope envelopeOf(Filter f) {
        if (f == null) {
            return null;
        }
        if (f.getOp() == FilterOp.AND) {
            @SuppressWarnings("unchecked")
            List<Filter> sub = (List<Filter>) f.getValue();
            return extractBboxEnvelope(sub);
        }
        if (isSpatial(f.getOp()) && f.getValue() instanceof Geometry) {
            return ((Geometry) f.getValue()).getEnvelopeInternal();
        }
        return null;
    }

    private static Envelope extractBboxEnvelope(List<Filter> filters) {
        Envelope acc = null;
        for (Filter f : filters) {
            Envelope e = envelopeOf(f);
            if (e == null) {
                continue;
            }
            acc = acc == null ? new Envelope(e) : acc.intersection(e);
        }
        return acc;
    }

    private static boolean isSpatial(FilterOp op) {
        switch (op) {
        case INTERSECTS:
        case INTERSECTS_INDEX:
        case WITHIN:
        case CONTAINS:
        case OVERLAPS:
        case EQUALS:
        case CROSSES:
        case TOUCHES:
            return true;
        default:
            return false;
        }
    }

}
