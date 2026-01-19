package fi.nls.hakunapi.flatgeobuf;

import java.nio.ByteBuffer;
import java.util.PrimitiveIterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import org.locationtech.jts.geom.Envelope;

import fi.nls.hakunapi.core.FeatureStream;
import fi.nls.hakunapi.core.ValueProvider;

public class FlatgeobufFeatureStream implements FeatureStream {

    private final Flatgeobuf fgb;
    private final PrimitiveIterator.OfLong offsetIterator;
    private final Predicate<ValueProvider> filterFn;
    private final FlatgeobufFeatureValueProvider provider;
    private final ValueProviderFacade next;
    private final MutableInt pos;

    private boolean closed;
    private boolean buffered;

    public FlatgeobufFeatureStream(Flatgeobuf fgb, Envelope bboxQuery, int offset, Predicate<ValueProvider> filterFn, int[] indexMap) {
        this.fgb = fgb;
        this.offsetIterator = fgb.boundingBoxStream(bboxQuery).iterator();
        this.filterFn = filterFn;
        this.pos = new MutableInt();
        this.provider = new FlatgeobufFeatureValueProvider(fgb.meta.geometryType, fgb.meta.srid, fgb.meta.columns);
        this.next = new ValueProviderFacade(provider, indexMap);
        for (int i = 0; i < offset && readNext(); i++); // Skip offset
        buffered = false;
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
        while (offsetIterator.hasNext()) {
            long off = offsetIterator.nextLong();
            int len = fgb.file.getInt(off);
            off += 4;
            ByteBuffer bb = fgb.file.getBytes(off, len, pos);
            provider.setFeature(bb, pos.v);
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
