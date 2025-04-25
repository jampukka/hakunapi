package fi.nls.hakunapi.flatgeobuf;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.wololo.flatgeobuf.HeaderMeta;
import org.wololo.flatgeobuf.generated.Feature;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ObjectArrayValueContainer;
import fi.nls.hakunapi.core.ValueContainer;
import fi.nls.hakunapi.core.ValueMapper;
import fi.nls.hakunapi.core.ValueProvider;
import fi.nls.hakunapi.core.util.U;

public class FlatgeobufFeatureStream implements FeatureStream {

    private final FlatgeobufMmap fgb;
    private final Function<FlatgeobufMmap, Iterator<Feature>> featureLoop;
    private final Predicate<ValueProvider> filterFn;
    private final FlatgeobufFeatureValueProvider2 provider;
    private final List<ValueMapper> valueMappers;
    private final ValueContainer next;
    private Iterator<Feature> featureIterator;
    private boolean closed;


    public FlatgeobufFeatureStream(HeaderMeta meta, FlatgeobufMmap fgb, Function<FlatgeobufMmap, Iterator<Feature>> featureLoop, Predicate<ValueProvider> filterFn, List<ValueMapper> valueMappers) {
        this.fgb = fgb;
        this.featureLoop = featureLoop;
        this.filterFn = filterFn;
        this.provider = new FlatgeobufFeatureValueProvider2(meta.geometryType, meta.srid, meta.columns);
        this.next = new ObjectArrayValueContainer(1 + meta.columns.size());
        this.valueMappers = valueMappers;
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
        if (featureIterator == null) {
            featureIterator = featureLoop.apply(fgb);
        }
        while (featureIterator.hasNext()) {
            provider.setFeature(featureIterator.next());
            if (filterFn.test(provider)) {
                for (ValueMapper mapper : valueMappers) {
                    mapper.accept(provider, next);
                }
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
