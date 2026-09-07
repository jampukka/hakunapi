package fi.nls.hakunapi.tiles.schemas;

import java.util.Map;

/**
 * One entry of a {@link TileJSON}'s {@code vector_layers}: a layer present in the
 * tiles, its {@code id} being the layer name inside the encoded tile - what a
 * vector tile style's {@code source-layer} must match.
 *
 * <p>{@code fields} maps each attribute name to its TileJSON type name
 * ({@code "Number"}, {@code "String"}, {@code "Boolean"}), so a client can see
 * what a layer carries without decoding a tile.
 */
public class VectorLayerJSON {

    private final String id;
    private final String description;
    private final Map<String, String> fields;

    public VectorLayerJSON(String id, String description, Map<String, String> fields) {
        this.id = id;
        this.description = description;
        this.fields = fields;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getFields() {
        return fields;
    }

}
