package fi.nls.hakunapi.tiles.source.vectortile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.tiles.TileBody;

/**
 * Generates a vector tile (MVT) on demand for a {@link VectorTileGeneratorSource},
 * rather than reading precomputed bytes from a store. This is the swappable
 * backend behind the generator tile source: the source owns the OGC plumbing
 * (config, address resolution, media-type negotiation, passthrough params) and
 * delegates the actual "features in this bounding box -> encoded vector tile"
 * step to a generator.
 *
 * <p>A generator is a pure function {@code (tile extent, collections) -> tile body}:
 * it holds no per-layer state and owns no resources, so an instance is a
 * stateless singleton. All inputs arrive per call; any backend resources (e.g. a
 * database connection) come via the {@link FeatureType}'s
 * {@link FeatureType#getFeatureProducer() producer}, whose lifecycle the feature
 * service owns - not the generator.
 *
 * <p>Implementations are discovered on the classpath via the Java
 * {@link java.util.ServiceLoader} (see {@link VectorTileGeneratorProvider}) and
 * selected from configuration by their {@link #getType() type} id
 * ({@code tiles.layers.<id>.vectortile.generator}). Concrete generators (e.g.
 * PostGIS {@code ST_AsMVT}, an in-JVM MVT encoder over hakunapi's feature stores)
 * live in their own modules and ship a {@code META-INF/services/} entry; this
 * interface only defines the contract.
 */
public interface VectorTileGenerator {

    /**
     * @return the configuration type id of this generator (matched against
     *         {@code tiles.layers.<id>.vectortile.generator})
     */
    String getType();

    /**
     * Generate one multi-layer vector tile for the given tile address.
     *
     * <p>The generator is handed a {@link TileContext}: the tile's bounding box
     * (in the tile matrix set CRS), CRS SRID, pixel size and ground resolution,
     * all computed by the {@link VectorTileGeneratorSource}. It does not see the
     * tile address (matrix set / zoom / row / column) - the glue has already
     * turned that into a bounding box - so a generator only needs to know which
     * patch of the world to encode and at what resolution.
     *
     * <p>A single tile can render several feature collections, one per MVT layer.
     * The generator receives the resolved {@link FeatureType}s for the tile layer
     * and is responsible for the fan-out: it queries each collection's
     * {@link FeatureType#getFeatureProducer() producer} for the tile's bounding
     * box and emits all resulting MVT layers in one tile. Doing the fan-out here
     * (rather than encoding one collection at a time and concatenating) lets a
     * backend batch the work - e.g. a single PostGIS {@code ST_AsMVT} statement
     * producing every layer at once.
     *
     * @param ctx           the resolved spatial extent of the tile
     * @param collections   the feature collections to render, one MVT layer each;
     *                      never null (may be empty)
     * @param mediaType     the requested tile media type
     * @param requestParams whitelisted inbound query params; never null
     * @return a {@link TileBody} that streams the encoded tile on demand, or empty
     *         if no tile is produced. The body is lazy: returning it does not
     *         materialise the tile - the source wraps it in a {@code Tile} and the
     *         bytes are produced only when something calls
     *         {@link TileBody#writeTo(java.io.OutputStream)} (e.g. straight to the
     *         gzipped HTTP response).
     */
    Optional<TileBody> generate(TileContext ctx, List<FeatureType> collections,
            String mediaType, Map<String, String> requestParams) throws Exception;

}
