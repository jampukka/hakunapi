package fi.nls.hakunapi.tiles.source.vectortile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.tiles.TileBody;

/**
 * A placeholder {@link VectorTileGenerator} that emits an empty Mapbox Vector
 * Tile for every address, ignoring the tile's bounding box and the underlying
 * data entirely. An MVT is a protobuf {@code Tile} message whose {@code layers}
 * are a repeated field, so a tile with no layers serialises to zero bytes -
 * which is exactly what this generator returns: a well-formed, empty vector
 * tile.
 *
 * <p>It exists so the generator tile source can be wired up and exercised
 * end-to-end before a real encoding backend (PostGIS {@code ST_AsMVT}, an in-JVM
 * MVT encoder) is plugged in.
 */
public class EmptyVectorTileGenerator implements VectorTileGenerator {

    public static final String TYPE = "empty";

    /** An empty MVT: a protobuf Tile with no layers serialises to zero bytes. */
    private static final TileBody EMPTY_MVT = out -> { /* no layers */ };

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Optional<TileBody> generate(TileContext ctx, List<FeatureType> collections,
            String mediaType, Map<String, String> requestParams) {
        return Optional.of(EMPTY_MVT);
    }

}
