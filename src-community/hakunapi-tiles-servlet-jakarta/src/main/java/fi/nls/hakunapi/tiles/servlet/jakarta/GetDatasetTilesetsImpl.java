package fi.nls.hakunapi.tiles.servlet.jakarta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import fi.nls.hakunapi.core.FeatureServiceConfig;
import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.schemas.Link;
import fi.nls.hakunapi.simple.servlet.jakarta.ResponseUtil;
import fi.nls.hakunapi.simple.servlet.jakarta.operation.ResponseClass;
import fi.nls.hakunapi.tiles.TileMatrixSet;
import fi.nls.hakunapi.tiles.TilesLinkRel;
import fi.nls.hakunapi.tiles.TilesServiceConfig;
import fi.nls.hakunapi.tiles.VectorTileLayer;
import fi.nls.hakunapi.tiles.schemas.GeospatialData;
import fi.nls.hakunapi.tiles.schemas.TileJSON;
import fi.nls.hakunapi.tiles.schemas.TileSet;
import fi.nls.hakunapi.tiles.schemas.TileSetItem;
import fi.nls.hakunapi.tiles.schemas.TileSetsList;

/**
 * Dataset tilesets of OGC API - Tiles: {@code /tiles} and
 * {@code /tiles/{tileMatrixSetId}}. Where the geodata lane describes the tiles of
 * one collection, these describe the tiles of the dataset as a whole - a tile
 * carrying one layer per collection of every vector tile layer configured.
 *
 * <p>The tileset advertises those collections in {@code layers}, whose ids are
 * the layer names inside the encoded tile, so a client knows what a tile
 * contains without fetching one.
 */
@Path("/tiles")
public class GetDatasetTilesetsImpl {

    static final String TMS_URI_PREFIX = "http://www.opengis.net/def/tilematrixset/OGC/1.0/";

    @Inject
    private FeatureServiceConfig service;

    @Inject
    private TilesServiceConfig tiles;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseClass(TileSetsList.class)
    public Response list(@Context HttpHeaders headers) {
        String base = service.getCurrentServerURL(headers::getHeaderString);
        String tilesPath = base + "/tiles";

        List<Link> links = new ArrayList<>();
        links.add(new Link(tilesPath, TilesLinkRel.SELF, MediaType.APPLICATION_JSON, "This document"));

        List<TileSetItem> tilesets = new ArrayList<>();
        for (String tmsId : datasetTileMatrixSetIds()) {
            TileMatrixSet tms = tiles.getTileMatrixSets().get(tmsId).orElse(null);
            if (tms == null) {
                continue;
            }
            String tilesetPath = tilesPath + "/" + tmsId;
            List<Link> tsLinks = new ArrayList<>();
            tsLinks.add(new Link(tilesetPath, TilesLinkRel.SELF, MediaType.APPLICATION_JSON,
                    "Tileset " + tmsId));
            tsLinks.add(new Link(base + "/tileMatrixSets/" + tmsId, TilesLinkRel.TILING_SCHEME,
                    MediaType.APPLICATION_JSON, "Definition of " + tmsId));
            tilesets.add(new TileSetItem(service.getTitle(), "vector", tms.getCrs(), tmsId,
                    TMS_URI_PREFIX + tmsId, tsLinks));
        }
        return Response.ok(new TileSetsList(links, tilesets)).build();
    }

    @GET
    @Path("/{tileMatrixSetId}")
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseClass(TileSet.class)
    public Response tileset(@PathParam("tileMatrixSetId") String tileMatrixSetId,
            @Context HttpHeaders headers) {
        // A tileset exists only where a layer offers the scheme and the registry
        // defines it: a layer may name a scheme that was never configured.
        TileMatrixSet tms = datasetTileMatrixSetIds().contains(tileMatrixSetId)
                ? tiles.getTileMatrixSets().get(tileMatrixSetId).orElse(null) : null;
        if (tms == null) {
            return ResponseUtil.exception(Status.NOT_FOUND, "No such tileset");
        }
        String base = service.getCurrentServerURL(headers::getHeaderString);
        String tilesetPath = base + "/tiles/" + tileMatrixSetId;

        List<Link> links = new ArrayList<>();
        links.add(new Link(tilesetPath, TilesLinkRel.SELF, MediaType.APPLICATION_JSON, "This document"));
        links.add(new Link(base + "/tileMatrixSets/" + tileMatrixSetId, TilesLinkRel.TILING_SCHEME,
                MediaType.APPLICATION_JSON, "Definition of " + tileMatrixSetId));
        String tileTemplate = tilesetPath + "/{tileMatrix}/{tileRow}/{tileCol}";
        links.add(new Link(tileTemplate, TilesLinkRel.ITEM,
                GetCollectionTileOperation.VECTOR_TILE, "Tiles"));
        links.add(new Link(tilesetPath + "/tilejson.json", TilesLinkRel.ALTERNATE,
                MediaType.APPLICATION_JSON, "TileJSON description of this tileset"));

        TileSet tileSet = new TileSet(service.getTitle(), "vector", tms.getCrs(),
                TMS_URI_PREFIX + tileMatrixSetId, links, layers(tileMatrixSetId));
        return Response.ok(tileSet).build();
    }

    /**
     * TileJSON for the dataset tileset - what a MapLibre / Mapbox GL style reads
     * through {@code sources.<id>.url}. Served alongside the OGC tileset, not
     * instead of it.
     */
    @GET
    @Path("/{tileMatrixSetId}/tilejson.json")
    @Produces(MediaType.APPLICATION_JSON)
    @ResponseClass(TileJSON.class)
    public Response tilejson(@PathParam("tileMatrixSetId") String tileMatrixSetId,
            @Context HttpHeaders headers) {
        TileMatrixSet tms = datasetTileMatrixSetIds().contains(tileMatrixSetId)
                ? tiles.getTileMatrixSets().get(tileMatrixSetId).orElse(null) : null;
        if (tms == null) {
            return ResponseUtil.exception(Status.NOT_FOUND, "No such tileset");
        }
        String base = service.getCurrentServerURL(headers::getHeaderString);
        String tilesetPath = base + "/tiles/" + tileMatrixSetId;
        return Response.ok(TileJSONBuilder.build(service.getTitle(), service.getDescription(),
                tilesetPath, tms, datasetCollections(tileMatrixSetId))).build();
    }

    /**
     * Tile matrix sets the dataset offers: those supported by at least one vector
     * tile layer, in the order the layers declare them.
     */
    private Set<String> datasetTileMatrixSetIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (VectorTileLayer layer : tiles.getVectorLayers()) {
            ids.addAll(layer.getTileMatrixSetIds());
        }
        return ids;
    }

    /**
     * The collections carried by a dataset tile of this tile matrix set, in the
     * order they are rendered. Only the layers supporting the tile matrix set
     * contribute, matching what the tile resource renders, and a collection named
     * by more than one layer appears once - it becomes a single layer in the tile.
     */
    private List<FeatureType> datasetCollections(String tileMatrixSetId) {
        List<FeatureType> collections = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (VectorTileLayer layer : tiles.getVectorLayers()) {
            if (!layer.supportsTileMatrixSet(tileMatrixSetId)) {
                continue;
            }
            for (String id : layer.getCollectionIds()) {
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

    /** Those same collections as the tileset's {@code geospatialData} entries. */
    private List<GeospatialData> layers(String tileMatrixSetId) {
        List<FeatureType> collections = datasetCollections(tileMatrixSetId);
        List<GeospatialData> layers = new ArrayList<>(collections.size());
        for (int i = 0; i < collections.size(); i++) {
            FeatureType ft = collections.get(i);
            layers.add(new GeospatialData(ft.getName(), ft.getTitle(), "vector"));
        }
        return layers;
    }

}
