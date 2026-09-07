package fi.nls.hakunapi.tiles.source.vectortile;

import java.util.List;
import java.util.Set;

import fi.nls.hakunapi.tiles.CollectionTileSource;
import fi.nls.hakunapi.tiles.VectorTileLayer;

/**
 * A {@link VectorTileLayer} whose tiles are rendered from feature collections by
 * a {@link VectorTileGenerator}, carrying the bound generator, the ids of the
 * feature collections rendered into the tile (one MVT layer each), and the
 * passthrough query-param whitelist. Its tiles come from a
 * {@link CollectionTileSource} reached via the tiles service config.
 */
public class VectorTileGeneratorLayer extends VectorTileLayer {

    private final VectorTileGenerator generator;
    private final Set<String> passthroughParams;

    public VectorTileGeneratorLayer(String id, String title, String description, CollectionTileSource source,
            List<String> tileMatrixSetIds, List<String> mediaTypes, double[] bbox,
            VectorTileGenerator generator, List<String> collectionIds, Set<String> passthroughParams) {
        super(id, title, description, source, collectionIds, tileMatrixSetIds, mediaTypes, bbox);
        this.generator = generator;
        this.passthroughParams = Set.copyOf(passthroughParams);
    }

    public VectorTileGenerator getGenerator() {
        return generator;
    }

    public Set<String> getPassthroughParams() {
        return passthroughParams;
    }

}
