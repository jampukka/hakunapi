package fi.nls.hakunapi.tiles.source.vectortile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.tiles.TileBody;

/**
 * Test {@link VectorTileGenerator} that captures the {@link TileContext} the glue
 * computes, so tests can assert the tile-address -> ground-bbox resolution.
 * Discovered via the test {@code META-INF/services} entry under type id
 * {@code recording}.
 */
public class RecordingGenerator implements VectorTileGenerator {

    /** Last context seen; static so tests can read it off a ServiceLoader instance. */
    static volatile TileContext last;

    @Override
    public String getType() {
        return "recording";
    }

    @Override
    public Optional<TileBody> generate(TileContext ctx, List<FeatureType> collections,
            String mediaType, Map<String, String> requestParams) {
        last = ctx;
        return Optional.of(out -> { /* empty */ });
    }

}
