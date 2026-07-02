package fi.nls.hakunapi.simple.mosaic;

import java.util.List;
import java.util.Map;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.simple.HakunaPropertyGeometry;

/**
 * A {@link FeatureType} whose {@code /items} response is the {@code UNION ALL} of a dynamic,
 * bbox-selected set of tile files (see {@link MosaicFeatureProducer}).
 *
 * <p>The mosaic publishes one shared schema. Since the tile files are parsed with that same schema,
 * the mosaic delegates its published id/geometry/properties to a {@code prototype} tile feature type
 * parsed once at startup from a representative file — mirroring how {@code UnionFeatureType} delegates
 * to its master, so the prototype's {@link HakunaProperty} instances stay bound to the backend that
 * owns them rather than being re-bound here.
 */
public class MosaicFeatureType extends SimpleFeatureType {

    private final FeatureType prototype;
    private final MosaicIndex index;
    private final Map<String, MosaicBackend> backends;

    public MosaicFeatureType(FeatureType prototype, MosaicIndex index, Map<String, MosaicBackend> backends) {
        this.prototype = prototype;
        this.index = index;
        this.backends = backends;
    }

    @Override
    public HakunaProperty getId() {
        return prototype.getId();
    }

    @Override
    public HakunaPropertyGeometry getGeom() {
        return prototype.getGeom();
    }

    @Override
    public List<HakunaProperty> getProperties() {
        return prototype.getProperties();
    }

    @Override
    public FeatureProducer getFeatureProducer() {
        return new MosaicFeatureProducer(index, backends);
    }

}
