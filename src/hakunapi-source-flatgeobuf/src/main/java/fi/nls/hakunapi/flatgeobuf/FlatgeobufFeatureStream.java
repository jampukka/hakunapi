package fi.nls.hakunapi.flatgeobuf;

import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.generated.Feature;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.util.U;

public class FlatgeobufFeatureStream implements FeatureStream {

    private final FlatgeobufMmap fgb;
    private final Iterator<Feature> featureIterator;
    private final Predicate<ValueProvider> filterFn;
    private final FlatgeobufFeatureValueProvider provider;
    private final ValueProviderFacade next;
    private boolean closed;


    public FlatgeobufFeatureStream(HeaderMeta meta, FlatgeobufMmap fgb, Function<FlatgeobufMmap, Iterator<Feature>> featureLoop, int offset, Predicate<ValueProvider> filterFn, int[] indexMap) {
        this.fgb = fgb;
        this.featureIterator = featureLoop.apply(fgb);
        this.filterFn = filterFn;
        this.provider = new FlatgeobufFeatureValueProvider(meta.geometryType, meta.srid, meta.columns);
        this.next = new ValueProviderFacade(provider, indexMap);
        for (int i = 0; i < offset && hasNext(); i++); // Skip offset
    }

    @Override
    public void close() {
        closed = true;
        U.closeSilent(fgb);
    }

    @Override
    public boolean hasNext() {
        if (closed) {
            return false;
        }
        while (featureIterator.hasNext()) {
            provider.setFeature(featureIterator.next());
            if (filterFn.test(provider)) {
                return true;
            }
        }
        close();
        return false;
    }

    @Override
    public ValueProvider next() {
        return next;
    }

}
