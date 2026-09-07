package fi.nls.hakunapi.tiles.servlet.jakarta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.property.HakunaProperty;
import fi.nls.hakunapi.core.property.HakunaPropertyType;
import fi.nls.hakunapi.tiles.TileMatrix;
import fi.nls.hakunapi.tiles.TileMatrixSet;
import fi.nls.hakunapi.tiles.schemas.TileJSON;
import fi.nls.hakunapi.tiles.schemas.VectorLayerJSON;

/**
 * Builds a {@link TileJSON} from the tile models, shared by the dataset and
 * collection tilejson resources. Everything it needs is already known: the tile
 * matrix set gives the zoom range, the collections give the layers, their
 * attributes and the bounds.
 */
public final class TileJSONBuilder {

    private static final String NUMBER = "Number";
    private static final String STRING = "String";
    private static final String BOOLEAN = "Boolean";

    private TileJSONBuilder() {}

    /**
     * @param name        tileset name
     * @param description tileset description, may be null
     * @param tilesetPath absolute URL of the tileset, the tile template's parent
     * @param tms         the tile matrix set, source of the zoom range
     * @param collections the collections carried by a tile, one layer each
     */
    public static TileJSON build(String name, String description, String tilesetPath,
            TileMatrixSet tms, List<FeatureType> collections) {
        List<TileMatrix> matrices = tms.getTileMatrices();
        int minzoom = 0;
        int maxzoom = 0;
        if (!matrices.isEmpty()) {
            minzoom = zoomOf(matrices.get(0));
            maxzoom = zoomOf(matrices.get(matrices.size() - 1));
        }
        // Row before column, matching the OGC tile template
        // {tileMatrix}/{tileRow}/{tileCol} these tiles are served from.
        List<String> tiles = List.of(tilesetPath + "/{z}/{y}/{x}");

        List<VectorLayerJSON> layers = new ArrayList<>(collections.size());
        for (int i = 0; i < collections.size(); i++) {
            FeatureType ft = collections.get(i);
            layers.add(new VectorLayerJSON(ft.getName(), ft.getDescription(), fields(ft)));
        }
        return new TileJSON(name, description, tiles, minzoom, maxzoom,
                bounds(collections), layers);
    }

    /**
     * A tile matrix id is the zoom level in every scheme hakunapi builds, but it
     * is a free-form identifier in the model, so a non-numeric id falls back to 0
     * rather than failing the whole document.
     */
    private static int zoomOf(TileMatrix matrix) {
        try {
            return Integer.parseInt(matrix.getId());
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    /**
     * The union of the collections' WGS84 extents, or the whole world when none
     * declares one: a TileJSON client uses bounds to decide where to request
     * tiles at all, so "unknown" and "everywhere" must not be confused.
     */
    private static double[] bounds(List<FeatureType> collections) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < collections.size(); i++) {
            double[] e = collections.get(i).getSpatialExtent();
            if (e == null || e.length < 4) {
                continue;
            }
            minX = Math.min(minX, e[0]);
            minY = Math.min(minY, e[1]);
            maxX = Math.max(maxX, e[2]);
            maxY = Math.max(maxY, e[3]);
        }
        if (minX > maxX || minY > maxY) {
            return TileJSON.WORLD;
        }
        return new double[] { minX, minY, maxX, maxY };
    }

    /**
     * The layer's attributes as TileJSON field types. The geometry and the id are
     * left out: neither is an MVT attribute (the id is the feature's own id), so
     * neither appears as a tag in the encoded tile.
     */
    private static Map<String, String> fields(FeatureType ft) {
        Map<String, String> fields = new LinkedHashMap<>();
        HakunaProperty geom = ft.getGeom();
        HakunaProperty id = ft.getId();
        List<HakunaProperty> props = ft.getProperties();
        for (int i = 0; i < props.size(); i++) {
            HakunaProperty p = props.get(i);
            if (p == geom || p == id) {
                continue;
            }
            fields.put(p.getName(), fieldType(p.getType()));
        }
        return fields;
    }

    /**
     * The TileJSON type name for a property type, mirroring how the MVT encoder
     * actually tags a value: numbers and booleans keep their type, and everything
     * else - dates, uuids, arrays, json - is stringified.
     */
    private static String fieldType(HakunaPropertyType type) {
        switch (type) {
        case INT:
        case LONG:
        case FLOAT:
        case DOUBLE:
            return NUMBER;
        case BOOLEAN:
            return BOOLEAN;
        default:
            return STRING;
        }
    }

}
