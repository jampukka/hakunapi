package fi.nls.hakunapi.tiles.source.vectortile;

/**
 * A single tile's spatial extent, fully resolved into ground coordinates by the
 * {@link VectorTileGeneratorSource}. A {@link TileGenerator} receives this instead of
 * a tile address, so it never deals with tile matrix sets or row/column
 * arithmetic: it only needs to know which patch of the world to encode, in what
 * CRS, at what resolution and pixel size.
 *
 * @param srid       EPSG SRID of the tile matrix set CRS (the CRS {@code bbox} is in)
 * @param minX       tile bounding box min X, in the TMS CRS
 * @param minY       tile bounding box min Y, in the TMS CRS
 * @param maxX       tile bounding box max X, in the TMS CRS
 * @param maxY       tile bounding box max Y, in the TMS CRS
 * @param tileWidth  tile width in pixels (the MVT/raster extent across X)
 * @param tileHeight tile height in pixels (the MVT/raster extent across Y)
 * @param resolution ground units per pixel at this zoom level (cell size)
 */
public record TileContext(
        int srid,
        double minX,
        double minY,
        double maxX,
        double maxY,
        int tileWidth,
        int tileHeight,
        double resolution) {
}
