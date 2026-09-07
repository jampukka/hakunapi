package fi.nls.hakunapi.tiles.source.pmtiles;

import java.util.Set;

/**
 * Parsed settings for one PMTiles-backed tile layer.
 *
 * @param archive    the opened PMTiles archive (local file or remote https)
 * @param mediaType  tile media type advertised to clients (from the archive's
 *                   tile type, optionally overridden by config)
 * @param tmsIds     hakunapi tile matrix set ids this layer is served in; all
 *                   must use numeric (XYZ) zoom-level ids
 */
public record PMTilesConfig(
        PMTilesArchive archive,
        String mediaType,
        Set<String> tmsIds) {
}
