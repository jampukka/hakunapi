package fi.nls.hakunapi.tiles.servlet.jakarta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.FeatureServiceConfig;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.operation.DynamicResponseOperation;
import fi.nls.hakunapi.simple.servlet.jakarta.ResponseUtil;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.TilesServiceConfig;
import fi.nls.hakunapi.tiles.VectorTileLayer;

/**
 * The OGC API - Tiles dataset tile resource:
 * {@code /tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}}.
 *
 * <p>Where the geodata lane renders the collections of a single tile layer, this
 * renders the dataset: the collections of every vector tile layer supporting the
 * requested tile matrix set, unioned, as one layer each in a single tile. The
 * tile is produced by one {@code CollectionTileSource} - the first participating
 * layer's - because a tile is one encoded body; a deployment mixing sources gets
 * all its collections rendered through that source rather than losing the
 * dataset lane.
 */
@Path("/tiles")
public class GetDatasetTileImpl implements DynamicResponseOperation {

    private static final Logger LOG = LoggerFactory.getLogger(GetDatasetTileImpl.class);

    @Inject
    private FeatureServiceConfig service;

    @Inject
    private TilesServiceConfig tiles;

    @Override
    public Map<String, Class<?>> getResponsesByContentType(FeatureServiceConfig service) {
        Map<String, Class<?>> map = new HashMap<>();
        map.put(GetCollectionTileOperation.VECTOR_TILE, byte[].class);
        return map;
    }

    @GET
    @Path("/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}")
    public Response handle(
            @PathParam("tileMatrixSetId") String tileMatrixSetId,
            @PathParam("tileMatrix") String tileMatrix,
            @PathParam("tileRow") long tileRow,
            @PathParam("tileCol") long tileCol,
            @Context UriInfo uriInfo) {
        List<VectorTileLayer> layers = participatingLayers(tileMatrixSetId);
        if (layers.isEmpty()) {
            return ResponseUtil.exception(Status.NOT_FOUND, "Unsupported tileMatrixSetId");
        }
        // A tile is one encoded body, so one source renders it; see the class javadoc.
        VectorTileLayer layer = layers.get(0);
        String mediaType = layer.getMediaTypes().isEmpty()
                ? GetCollectionTileOperation.VECTOR_TILE : layer.getMediaTypes().get(0);
        try {
            List<FeatureType> collections = resolveCollections(layers);
            Map<String, String> requestParams = whitelistedParams(layer, uriInfo);
            Tile tile = layer.getSource()
                    .getTile(layer, tileMatrixSetId, tileMatrix, tileRow, tileCol, collections,
                            mediaType, requestParams)
                    .orElse(null);
            if (tile == null) {
                return ResponseUtil.exception(Status.NOT_FOUND, "Tile not found");
            }
            return TileResponse.ok(tile, mediaType);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            return ResponseUtil.exception(Status.INTERNAL_SERVER_ERROR,
                    "Error occured, message: " + e.getMessage());
        }
    }

    /** Vector tile layers offering the requested tile matrix set, in configuration order. */
    private List<VectorTileLayer> participatingLayers(String tileMatrixSetId) {
        List<VectorTileLayer> layers = new ArrayList<>();
        for (VectorTileLayer layer : tiles.getVectorLayers()) {
            if (layer.supportsTileMatrixSet(tileMatrixSetId)) {
                layers.add(layer);
            }
        }
        return layers;
    }

    /**
     * The collections of every participating layer, one MVT layer each. A
     * collection named by more than one layer is rendered once: two layers of the
     * same name in one tile is not something a client can use.
     */
    private List<FeatureType> resolveCollections(List<VectorTileLayer> layers) {
        List<FeatureType> collections = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < layers.size(); i++) {
            List<String> ids = layers.get(i).getCollectionIds();
            for (int j = 0; j < ids.size(); j++) {
                String id = ids.get(j);
                if (!seen.add(id)) {
                    continue;
                }
                FeatureType ft = service.getCollection(id);
                if (ft != null) {
                    collections.add(ft);
                }
            }
        }
        return collections;
    }

    /**
     * Inbound query params intersected with the rendering source's passthrough
     * whitelist. Multi-valued params collapse to their first value.
     */
    private static Map<String, String> whitelistedParams(VectorTileLayer layer, UriInfo uriInfo) {
        Set<String> allowed = layer.getSource().getPassthroughParams(layer);
        if (allowed.isEmpty()) {
            return Map.of();
        }
        MultivaluedMap<String, String> query = uriInfo.getQueryParameters();
        Map<String, String> out = new LinkedHashMap<>();
        for (String name : allowed) {
            String value = query.getFirst(name);
            if (value != null) {
                out.put(name, value);
            }
        }
        return out;
    }

}
