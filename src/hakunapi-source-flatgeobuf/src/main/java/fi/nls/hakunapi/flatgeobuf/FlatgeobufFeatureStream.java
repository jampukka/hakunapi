package fi.nls.hakunapi.flatgeobuf;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Predicate;

import org.wololo.flatgeobuf.generated.Feature;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ValueProvider;

public class FlatgeobufFeatureStream implements FeatureStream {

    private final Iterator<Feature> featureIterator;
    private final Predicate<ValueProvider> filterFn;
    private final FlatgeobufFeatureValueProvider provider;
    private final ValueProviderFacade next;

    private boolean closed;
    private boolean buffered;

    public FlatgeobufFeatureStream(FlatgeobufOffheap fgb, Function<FlatgeobufOffheap, Iterator<Feature>> featureLoop, int offset, Predicate<ValueProvider> filterFn, int[] indexMap) {
        this.featureIterator = featureLoop.apply(fgb);
        this.filterFn = filterFn;
        this.provider = new FlatgeobufFeatureValueProvider(fgb.getHeaderMeta().geometryType, fgb.getHeaderMeta().srid, fgb.getHeaderMeta().columns);
        this.next = new ValueProviderFacade(provider, indexMap);
        for (int i = 0; i < offset && readNext(); i++); // Skip offset
    }

    @Override
    public void close() {
        closed = true;
        // U.closeSilent(fgb);
    }

    @Override
    public boolean hasNext() {
        return !closed && (buffered || readNext());
    }

    private boolean readNext() {
        while (featureIterator.hasNext()) {
            provider.setFeature(featureIterator.next());
            if (filterFn.test(provider)) {
                return buffered = true;
            }
        }
        close();
        return false;
    }

    @Override
    public ValueProvider next() {
        if (!buffered && !readNext()) {
            throw new NoSuchElementException();
        }
        buffered = false;
        return next;
    }

}
