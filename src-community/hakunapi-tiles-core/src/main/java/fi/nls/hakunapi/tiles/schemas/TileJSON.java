package fi.nls.hakunapi.tiles.schemas;

import java.util.List;

/**
 * TileJSON 2.2.0 description of a tileset.
 *
 * <p>Not an OGC resource: TileJSON is the Mapbox-originated format a MapLibre /
 * Mapbox GL style fetches through {@code sources.<id>.url}, and it is what gives
 * a browser client the tile URL template, zoom range and bounds. It is served
 * alongside the OGC {@link TileSet}, never instead of it.
 *
 * <p>Property names are the wire names TileJSON specifies, which is why the
 * accessors are spelled {@code getVector_layers} and {@code getTilejson} rather
 * than in Java's camel case - this codebase carries no Jackson annotations, so
 * the getter name is the JSON name.
 *
 * @see <a href="https://github.com/mapbox/tilejson-spec/tree/master/2.2.0">TileJSON 2.2.0</a>
 */
public class TileJSON {

    /** Whole-world bounds in WGS84, used when nothing declares an extent. */
    public static final double[] WORLD = { -180.0, -85.051129, 180.0, 85.051129 };

    private final String tilejson;
    private final String name;
    private final String description;
    private final String scheme;
    private final String format;
    private final List<String> tiles;
    private final int minzoom;
    private final int maxzoom;
    private final double[] bounds;
    private final List<VectorLayerJSON> vectorLayers;

    public TileJSON(String name, String description, List<String> tiles, int minzoom, int maxzoom,
            double[] bounds, List<VectorLayerJSON> vectorLayers) {
        this.tilejson = "2.2.0";
        this.scheme = "xyz";
        this.format = "pbf";
        this.name = name;
        this.description = description;
        this.tiles = tiles;
        this.minzoom = minzoom;
        this.maxzoom = maxzoom;
        this.bounds = bounds;
        this.vectorLayers = vectorLayers;
    }

    public String getTilejson() {
        return tilejson;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getScheme() {
        return scheme;
    }

    public String getFormat() {
        return format;
    }

    public List<String> getTiles() {
        return tiles;
    }

    public int getMinzoom() {
        return minzoom;
    }

    public int getMaxzoom() {
        return maxzoom;
    }

    public double[] getBounds() {
        return bounds;
    }

    public List<VectorLayerJSON> getVector_layers() {
        return vectorLayers;
    }

}
