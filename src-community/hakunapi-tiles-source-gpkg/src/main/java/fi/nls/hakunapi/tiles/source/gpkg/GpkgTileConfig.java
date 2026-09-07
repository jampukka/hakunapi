package fi.nls.hakunapi.tiles.source.gpkg;

import java.sql.Connection;
import java.util.Map;

/**
 * Parsed settings for one GeoPackage-backed tile layer.
 *
 * @param connection a read-only JDBC connection to the GeoPackage file (held
 *                   open for the source's lifetime)
 * @param tableName  the tile pyramid user table (its {@code gpkg_contents}
 *                   {@code table_name})
 * @param mediaType  tile media type advertised to clients (sniffed from the
 *                   pyramid's tiles, optionally overridden by config)
 * @param zoomByTms  per tile matrix set id, a map from hakunapi tile matrix id
 *                   to the GeoPackage {@code zoom_level} serving it (resolved by
 *                   resolution match at parse time)
 */
public record GpkgTileConfig(
        Connection connection,
        String tableName,
        String mediaType,
        Map<String, Map<String, Integer>> zoomByTms) {
}
