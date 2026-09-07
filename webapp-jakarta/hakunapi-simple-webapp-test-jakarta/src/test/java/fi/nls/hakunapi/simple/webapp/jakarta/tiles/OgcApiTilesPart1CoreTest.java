package fi.nls.hakunapi.simple.webapp.jakarta.tiles;

import static com.jayway.jsonassert.JsonAssert.with;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.File;

import jakarta.servlet.ServletContextEvent;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.tiles.servlet.jakarta.TilesContextListener;
import fi.nls.hakunapi.simple.webapp.jakarta.HakunaTestServletContext;
import fi.nls.hakunapi.simple.webapp.jakarta.SimpleFeaturesApplication;
import fi.nls.hakunapi.source.TestTileSource;

public class OgcApiTilesPart1CoreTest extends JerseyTest {

    private static final Logger LOG = LoggerFactory.getLogger(OgcApiTilesPart1CoreTest.class);

    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("tiles/hakuna.properties").getFile());
        System.setProperty("hakuna.config.path", file.getParentFile().getAbsolutePath() + "/");

        final HakunaTestServletContext sc = new HakunaTestServletContext();
        final ServletContextEvent sce = new ServletContextEvent(sc);
        new TilesContextListener().contextInitialized(sce);

        return new SimpleFeaturesApplication(sc);
    }

    @Test
    public void testConformanceAdvertisesTiles() {
        final String response = target("/conformance").request().get(String.class);
        LOG.info(response);
        with(response).assertThat("$.conformsTo", hasItems(
                equalTo("http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/core"),
                equalTo("http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/tileset"),
                equalTo("http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/tilesets-list"),
                equalTo("http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/geodata-tilesets"),
                // the vector tile layers serve application/vnd.mapbox-vector-tile
                equalTo("http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/mvt")));
    }

    @Test
    public void testLandingPageAdvertisesTileMatrixSets() {
        final String response = target("/").request().get(String.class);
        LOG.info(response);
        with(response).assertThat("$.links[?(@.rel == 'http://www.opengis.net/def/rel/ogc/1.0/tiling-schemes')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testTileMatrixSetsList() {
        final String response = target("/tileMatrixSets").request().get(String.class);
        LOG.info(response);
        with(response).assertThat("$.tileMatrixSets[?(@.id == 'WebMercatorQuad')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testWebMercatorQuadDefinition() {
        final String response = target("/tileMatrixSets/WebMercatorQuad").request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.id", equalTo("WebMercatorQuad"))
                .assertThat("$.crs", equalTo("http://www.opengis.net/def/crs/EPSG/0/3857"))
                .assertThat("$.tileMatrices[0].id", equalTo("0"))
                .assertThat("$.tileMatrices[0].matrixWidth", equalTo(1));
    }

    @Test
    public void testCollectionTilesetsList() {
        final String response = target("/collections/test_collection/tiles").request().get(String.class);
        LOG.info(response);
        with(response).assertThat("$.tilesets[?(@.tileMatrixSetId == 'WebMercatorQuad')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testCollectionTileset() {
        final String response = target("/collections/test_collection/tiles/WebMercatorQuad")
                .request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.dataType", equalTo("vector"))
                .assertThat("$.links[?(@.rel == 'item')]", org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testGetTile() {
        final Response response = target("/collections/test_collection/tiles/WebMercatorQuad/0/0/0")
                .request().get();
        assertEquals(200, response.getStatus());
        assertEquals("application/vnd.mapbox-vector-tile", response.getMediaType().toString());
        byte[] body = response.readEntity(byte[].class);
        org.junit.Assert.assertNotNull(body);
    }

    @Test
    public void testGetTileWithFParam() {
        // f=mvt has to select the tile media type the same way f=json selects
        // JSON elsewhere: the extension declares the mapping, GlobalFQueryParamFilter
        // turns it into an Accept header.
        final Response response = target("/collections/test_collection/tiles/WebMercatorQuad/0/0/0")
                .queryParam("f", "mvt").request().get();
        assertEquals(200, response.getStatus());
        assertEquals("application/vnd.mapbox-vector-tile", response.getMediaType().toString());
        org.junit.Assert.assertNotNull(response.readEntity(byte[].class));
    }

    @Test
    public void testInvalidFParamListsExtensionFormats() {
        final Response response = target("/collections/test_collection/tiles/WebMercatorQuad/0/0/0")
                .queryParam("f", "no-such-format").request().get();
        assertEquals(400, response.getStatus());
        // The message enumerates what is actually accepted, extensions included.
        with(response.readEntity(String.class))
                .assertThat("$.description", org.hamcrest.Matchers.containsString("mvt"));
    }

    @Test
    public void testMapCollectionTilesetsList() {
        final String response = target("/collections/test_map/map/tiles").request().get(String.class);
        LOG.info(response);
        with(response).assertThat("$.tilesets[?(@.tileMatrixSetId == 'WebMercatorQuad')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testMapCollectionTileset() {
        final String response = target("/collections/test_map/map/tiles/WebMercatorQuad")
                .request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.dataType", equalTo("map"))
                .assertThat("$.links[?(@.rel == 'item')]", org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testGetMapTile() {
        final Response response = target("/collections/test_map/map/tiles/WebMercatorQuad/0/0/0")
                .request().get();
        assertEquals(200, response.getStatus());
        byte[] body = response.readEntity(byte[].class);
        assertArrayEquals(TestTileSource.TILE_BYTES, body);
    }

    @Test
    public void testCollectionMetadataHasTilesetsLink() {
        final String response = target("/collections/test_collection").request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.itemType", equalTo("feature"))
                .assertThat("$.links[?(@.rel == 'http://www.opengis.net/def/rel/ogc/1.0/tilesets-vector')]",
                        org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testMapLayerListedAsCollection() {
        final String response = target("/collections").request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.collections[?(@.id == 'test_map')]", org.hamcrest.Matchers.hasSize(1))
                .assertThat("$.collections[?(@.id == 'test_collection')]", org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testMapCollectionMetadataNoItemsHasMapTilesets() {
        final String response = target("/collections/test_map").request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.id", equalTo("test_map"))
                .assertThat("$.itemType", equalTo("map"))
                .assertThat("$.links[?(@.rel == 'http://www.opengis.net/def/rel/ogc/1.0/tilesets-map')]",
                        org.hamcrest.Matchers.hasSize(1))
                .assertThat("$.links[?(@.rel == 'items')]", org.hamcrest.Matchers.hasSize(0));
    }

    @Test
    public void testApiIncludesTilePaths() {
        final String response = target("/api").request().get(String.class);
        with(response).assertThat(
                "$.paths['/collections/test_collection/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}']",
                org.hamcrest.Matchers.notNullValue());
        with(response).assertThat(
                "$.paths['/tiles/{tileMatrixSetId}/{tileMatrix}/{tileRow}/{tileCol}']",
                org.hamcrest.Matchers.notNullValue());
    }

    @Test
    public void testLandingPageAdvertisesDatasetTilesets() {
        final String response = target("/").request().get(String.class);
        with(response).assertThat(
                "$.links[?(@.rel == 'http://www.opengis.net/def/rel/ogc/1.0/tilesets-vector')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testDatasetTilesetsList() {
        final String response = target("/tiles").request().get(String.class);
        LOG.info(response);
        with(response).assertThat("$.tilesets[?(@.tileMatrixSetId == 'WebMercatorQuad')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testDatasetTileset() {
        final String response = target("/tiles/WebMercatorQuad").request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.dataType", equalTo("vector"))
                .assertThat("$.links[?(@.rel == 'item')]", org.hamcrest.Matchers.hasSize(1));
    }

    /**
     * The dataset tileset advertises every collection a dataset tile carries, one
     * geospatialData each - the ids are the layer names inside the tile.
     * test_collection is named by two tile layers but appears once.
     */
    @Test
    public void testDatasetTilesetListsCollectionsAsLayers() {
        final String response = target("/tiles/WebMercatorQuad").request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.layers[?(@.id == 'test_collection')]", org.hamcrest.Matchers.hasSize(1))
                .assertThat("$.layers[?(@.id == 'other_collection')]", org.hamcrest.Matchers.hasSize(1))
                .assertThat("$.layers[?(@.dataType == 'vector')]", org.hamcrest.Matchers.hasSize(2));
    }

    @Test
    public void testDatasetTilesetUnknownTileMatrixSet() {
        final Response response = target("/tiles/NoSuchTileMatrixSet").request().get();
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGetDatasetTile() {
        final Response response = target("/tiles/WebMercatorQuad/0/0/0").request().get();
        assertEquals(200, response.getStatus());
        assertEquals("application/vnd.mapbox-vector-tile", response.getMediaType().toString());
        org.junit.Assert.assertNotNull(response.readEntity(byte[].class));
    }

    @Test
    public void testGetDatasetTileUnknownTileMatrixSet() {
        final Response response = target("/tiles/NoSuchTileMatrixSet/0/0/0").request().get();
        assertEquals(404, response.getStatus());
    }

    /**
     * A tile layer combining several collections is not a tileset of any one
     * collection, so it must not appear in the collections lane - it is reachable
     * only as a dataset tileset.
     */
    @Test
    public void testMultiCollectionLayerNotPublishedAsCollection() {
        final String collections = target("/collections").request().get(String.class);
        with(collections).assertThat("$.collections[?(@.id == 'combined')]",
                org.hamcrest.Matchers.hasSize(0));
        assertEquals(404, target("/collections/combined/tiles").request().get().getStatus());
        assertEquals(404, target("/collections/combined/tiles/WebMercatorQuad").request().get().getStatus());
        assertEquals(404,
                target("/collections/combined/tiles/WebMercatorQuad/0/0/0").request().get().getStatus());
    }

    @Test
    public void testConformanceAdvertisesDatasetTilesets() {
        final String response = target("/conformance").request().get(String.class);
        with(response).assertThat("$.conformsTo", hasItems(
                equalTo("http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/dataset-tilesets")));
    }

    /**
     * TileJSON is what a MapLibre / Mapbox GL style actually reads: the tile
     * template, the zoom range and the bounds. Served alongside the OGC tileset.
     */
    @Test
    public void testDatasetTileJSON() {
        final String response = target("/tiles/WebMercatorQuad/tilejson.json")
                .request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.tilejson", equalTo("2.2.0"))
                .assertThat("$.scheme", equalTo("xyz"))
                .assertThat("$.format", equalTo("pbf"))
                .assertThat("$.minzoom", equalTo(0))
                .assertThat("$.maxzoom", equalTo(24))
                // Row before column, matching the OGC tile template.
                .assertThat("$.tiles[0]",
                        org.hamcrest.Matchers.endsWith("/tiles/WebMercatorQuad/{z}/{y}/{x}"))
                .assertThat("$.vector_layers[?(@.id == 'test_collection')]",
                        org.hamcrest.Matchers.hasSize(1))
                .assertThat("$.vector_layers[?(@.id == 'other_collection')]",
                        org.hamcrest.Matchers.hasSize(1));
    }

    /**
     * fields carries the attribute schema NLS's own service leaves empty. The
     * test source gives my_prop no type, so it is a String; the geometry and the
     * id are not tile attributes and must not appear.
     */
    @Test
    public void testDatasetTileJSONFields() {
        final String response = target("/tiles/WebMercatorQuad/tilejson.json")
                .request().get(String.class);
        with(response)
                .assertThat("$.vector_layers[?(@.id == 'test_collection')].fields.my_prop",
                        hasItems(equalTo("String")))
                .assertThat("$.vector_layers[?(@.id == 'test_collection')].fields.geom",
                        org.hamcrest.Matchers.empty())
                .assertThat("$.vector_layers[?(@.id == 'test_collection')].fields.fid",
                        org.hamcrest.Matchers.empty());
    }

    @Test
    public void testCollectionTileJSON() {
        final String response = target("/collections/test_collection/tiles/WebMercatorQuad/tilejson.json")
                .request().get(String.class);
        LOG.info(response);
        with(response)
                .assertThat("$.tilejson", equalTo("2.2.0"))
                .assertThat("$.vector_layers", org.hamcrest.Matchers.hasSize(1))
                .assertThat("$.vector_layers[0].id", equalTo("test_collection"));
    }

    @Test
    public void testTilesetLinksToTileJSON() {
        final String response = target("/tiles/WebMercatorQuad").request().get(String.class);
        with(response).assertThat("$.links[?(@.rel == 'alternate')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testTileJSONUnknownTileMatrixSet() {
        assertEquals(404, target("/tiles/NoSuchTileMatrixSet/tilejson.json").request().get().getStatus());
    }

    /** A multi-collection layer has no collection-level tilejson either. */
    @Test
    public void testMultiCollectionLayerHasNoCollectionTileJSON() {
        assertEquals(404, target("/collections/combined/tiles/WebMercatorQuad/tilejson.json")
                .request().get().getStatus());
    }

}
