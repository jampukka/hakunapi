package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import org.wololo.flatgeobuf.HeaderMeta;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ValueProvider;

public class FlatgeobufFeatureStream implements FeatureStream {

    private final Iterator<ByteBuffer> featureIterator;
    private final Predicate<ValueProvider> filterFn;
    private final FlatgeobufFeatureValueProvider provider;
    private final ValueProviderFacade next;

    private boolean closed;
    private boolean buffered;

    public FlatgeobufFeatureStream(HeaderMeta meta, Iterator<ByteBuffer> featureIterator, int offset, Predicate<ValueProvider> filterFn, int[] indexMap) {
        this.featureIterator = featureIterator;
        this.filterFn = filterFn;
        this.provider = new FlatgeobufFeatureValueProvider(meta.geometryType, meta.srid, meta.columns);
        this.next = new ValueProviderFacade(provider, indexMap);
        for (int i = 0; i < offset && readNext(); i++); // Skip offset
    }

    @Override
    public void close() {
        closed = true;
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
