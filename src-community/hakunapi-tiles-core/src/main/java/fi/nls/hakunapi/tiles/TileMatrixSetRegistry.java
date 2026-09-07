package fi.nls.hakunapi.tiles;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry of the {@link TileMatrixSet} definitions known to a service. Backs
 * the OGC API - Tiles shared {@code /tileMatrixSets} resources. Always contains
 * at least the well-known {@code WebMercatorQuad} set via
 * {@link DefaultTileMatrixSetRegistry}.
 */
public interface TileMatrixSetRegistry {

    Collection<TileMatrixSet> list();

    Optional<TileMatrixSet> get(String tileMatrixSetId);

}
