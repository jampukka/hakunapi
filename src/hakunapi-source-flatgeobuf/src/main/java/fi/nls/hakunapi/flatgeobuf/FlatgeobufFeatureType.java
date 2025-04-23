package fi.nls.hakunapi.flatgeobuf;

import java.io.File;
import java.io.IOException;

import org.wololo.flatgeobuf.HeaderMeta;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.SimpleFeatureType;

public class FlatgeobufFeatureType extends SimpleFeatureType {
    
    protected final File file;
    protected final HeaderMeta meta;

    public FlatgeobufFeatureType(File file) throws IllegalArgumentException, IOException, Exception {
        this.file = file;
        try (FlatgeobufMmap buf = new FlatgeobufMmap(file.toPath())) {
            this.meta = buf.getHeaderMeta();
        }
    }

    @Override
    public FeatureProducer getFeatureProducer() {
        return new FlatgeobufFeatureProducer();
    }
    
    public FlatgeobufMmap open() throws IllegalArgumentException, IOException {
        return new FlatgeobufMmap(file.toPath(), meta);
    }

    public void constructIdIndex() {
        
    }

}
