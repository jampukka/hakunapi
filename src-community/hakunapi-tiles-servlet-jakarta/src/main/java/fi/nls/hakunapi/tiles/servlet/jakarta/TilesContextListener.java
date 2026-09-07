package fi.nls.hakunapi.tiles.servlet.jakarta;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.core.extension.ApiExtension;
import fi.nls.hakunapi.simple.webapp.jakarta.HakunaContextListener;
import fi.nls.hakunapi.tiles.CollectionTileSource;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.TileSource;
import fi.nls.hakunapi.tiles.TilesServiceConfig;
import fi.nls.hakunapi.tiles.VectorTileLayer;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

/**
 * Bootstrap for a webapp that serves OGC API - Tiles: parses the tile
 * configuration and hands the servlet layer a {@link TilesApiExtension}.
 *
 * <p>Deploy this in place of {@link HakunaContextListener}, with the tile source
 * modules the deployment needs on the classpath. Tiles stays entirely out of the
 * reference webapp this way - the only tiles-aware part is this listener.
 */
public class TilesContextListener extends HakunaContextListener {

    private static final Logger LOG = LoggerFactory.getLogger(TilesContextListener.class);

    @Override
    protected List<ApiExtension> createApiExtensions(HakunaConfigParser config, Path configPath,
            Map<String, FeatureType> collections) throws Exception {
        TilesConfigParser parser = new TilesConfigParser(config);

        // Sources split into map (precomputed raster, TileSource) and vector
        // (rendered from feature collections, CollectionTileSource); a layer is
        // routed to one kind by its configured type.
        Map<String, TileSource> mapSourcesByType = new HashMap<>();
        Map<String, CollectionTileSource> vectorSourcesByType = new HashMap<>();
        for (TileSource source : ServiceLoader.load(TileSource.class)) {
            LOG.info("TileSource (map): {} -> {}", source.getType(), source);
            mapSourcesByType.put(source.getType(), source);
        }
        for (CollectionTileSource source : ServiceLoader.load(CollectionTileSource.class)) {
            LOG.info("TileSource (vector): {} -> {}", source.getType(), source);
            vectorSourcesByType.put(source.getType(), source);
        }

        TileMatrixSetRegistry tileMatrixSets = parser.readTileMatrixSets();
        List<VectorTileLayer> vectorLayers = new ArrayList<>();
        List<MapTileLayer> mapLayers = new ArrayList<>();
        for (String layerId : parser.readTileLayerIds()) {
            String type = parser.readTileLayerType(layerId);
            boolean isVector = vectorSourcesByType.size() == 1 && mapSourcesByType.isEmpty()
                    || (type != null && vectorSourcesByType.containsKey(type));
            boolean isMap = mapSourcesByType.size() == 1 && vectorSourcesByType.isEmpty()
                    || (type != null && mapSourcesByType.containsKey(type));
            if (isVector) {
                vectorLayers.add(parser.readVectorTileLayer(configPath, vectorSourcesByType,
                        tileMatrixSets, layerId));
            } else if (isMap) {
                mapLayers.add(parser.readMapTileLayer(configPath, mapSourcesByType,
                        tileMatrixSets, layerId));
            } else {
                throw new IllegalArgumentException("Unknown or missing tile source type for layer: "
                        + layerId + " (type=" + type + ")");
            }
        }

        if (parser.isVectorAutoPublishEnabled()) {
            addAutoPublishedLayers(parser, configPath, vectorSourcesByType, tileMatrixSets,
                    collections, vectorLayers);
        }

        if (vectorLayers.isEmpty() && mapLayers.isEmpty()) {
            LOG.info("No tile layers configured, OGC API - Tiles not published");
            return List.of();
        }
        LOG.info("OGC API - Tiles: {} vector layer(s), {} map layer(s)",
                vectorLayers.size(), mapLayers.size());
        return List.of(new TilesApiExtension(
                new TilesServiceConfig(vectorLayers, mapLayers, tileMatrixSets)));
    }

    /**
     * {@code tiles.vector=true}: publish a vector tile layer for every feature
     * collection that has a geometry, sharing the collection id, with the
     * service-default generator and no per-collection config. Explicit
     * {@code tiles.layers.<id>} definitions win over the toggle.
     */
    private void addAutoPublishedLayers(TilesConfigParser parser, Path configPath,
            Map<String, CollectionTileSource> vectorSourcesByType, TileMatrixSetRegistry tileMatrixSets,
            Map<String, FeatureType> collections, List<VectorTileLayer> vectorLayers) throws Exception {
        if (vectorSourcesByType.size() != 1) {
            throw new IllegalArgumentException("tiles.vector=true requires exactly one vector"
                    + " tile source (CollectionTileSource) on the classpath; found "
                    + vectorSourcesByType.size());
        }
        Set<String> explicit = new HashSet<>();
        for (VectorTileLayer layer : vectorLayers) {
            explicit.add(layer.getId());
        }
        for (FeatureType ft : collections.values()) {
            if (ft.getGeom() == null || explicit.contains(ft.getName())) {
                continue;
            }
            vectorLayers.add(parser.readVectorTileLayer(configPath, vectorSourcesByType,
                    tileMatrixSets, ft.getName()));
        }
    }

}
