package fi.nls.hakunapi.tiles.schemas;

/**
 * One entry of a {@link TileSet}'s {@code layers} array (OGC API - Tiles
 * {@code geospatialData}): the geospatial data resource a tile carries a layer
 * for. The {@code id} is the collection id, which is also the name of the layer
 * inside the encoded tile - what a vector tile style's {@code source-layer}
 * has to match.
 */
public class GeospatialData {

    private final String id;
    private final String title;
    private final String dataType;

    public GeospatialData(String id, String title, String dataType) {
        this.id = id;
        this.title = title;
        this.dataType = dataType;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDataType() {
        return dataType;
    }

}
