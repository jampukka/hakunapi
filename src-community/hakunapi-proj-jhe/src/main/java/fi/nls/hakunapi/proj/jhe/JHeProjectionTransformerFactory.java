package fi.nls.hakunapi.proj.jhe;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.nls.hakunapi.core.projection.NOPProjectionTransformer;
import fi.nls.hakunapi.core.projection.ProjectionTransformer;
import fi.nls.hakunapi.core.projection.ProjectionTransformerFactory;

public class JHeProjectionTransformerFactory implements ProjectionTransformerFactory {

    private static final Map<Long, ProjectionTransformer> CACHE = new ConcurrentHashMap<>();

    @Override
    public ProjectionTransformer getTransformer(int sridFrom, int sridTo) throws Exception {
        return CACHE.computeIfAbsent(getCacheKey(sridFrom, sridTo), __ -> {
            JHeStage t = JHeMathTransformFactory.findMathTransform(sridFrom, sridTo);
            if (t == null) {
                return NOPProjectionTransformer.INSTANCE;
            }
            return new JHeProjectionTransformer(sridFrom, sridTo, t);
        });
    }

    @Override
    public ProjectionTransformer toCRS84(int sridFrom) throws Exception {
        return getTransformer(sridFrom, 4258);
    }

    @Override
    public ProjectionTransformer fromCRS84(int sridTo) throws Exception {
        return getTransformer(4258, sridTo);
    }

    private Long getCacheKey(int sridFrom, int sridTo) {
        return ((long) sridFrom << 32) | Integer.toUnsignedLong(sridTo);
    }

}
