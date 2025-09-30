package fi.nls.hakunapi.flatgeobuf;

import fi.nls.hakunapi.core.FeatureProducer;
import fi.nls.hakunapi.core.SimpleFeatureType;

public class FlatgeobufFeatureType extends SimpleFeatureType {
    
    protected final Flatgeobuf fgb;

    public FlatgeobufFeatureType(Flatgeobuf fgb) {
        this.fgb = fgb;
    }
    
    @Override
    public FeatureProducer getFeatureProducer() {
        return new FlatgeobufFeatureProducer();
    }

}
