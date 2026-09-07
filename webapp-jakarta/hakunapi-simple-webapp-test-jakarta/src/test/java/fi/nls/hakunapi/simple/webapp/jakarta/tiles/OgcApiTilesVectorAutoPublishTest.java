package fi.nls.hakunapi.simple.webapp.jakarta.tiles;

import static com.jayway.jsonassert.JsonAssert.with;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import java.io.File;

import jakarta.servlet.ServletContextEvent;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

import fi.nls.hakunapi.tiles.servlet.jakarta.TilesContextListener;
import fi.nls.hakunapi.simple.webapp.jakarta.HakunaTestServletContext;
import fi.nls.hakunapi.simple.webapp.jakarta.SimpleFeaturesApplication;

/**
 * Verifies the {@code tiles.vector=true} toggle: every feature collection with a
 * geometry is auto-published as a vector tile layer with no per-collection
 * configuration, and collections without a geometry are skipped.
 */
public class OgcApiTilesVectorAutoPublishTest extends JerseyTest {

    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("tiles-toggle/hakuna.properties").getFile());
        System.setProperty("hakuna.config.path", file.getParentFile().getAbsolutePath() + "/");

        final HakunaTestServletContext sc = new HakunaTestServletContext();
        final ServletContextEvent sce = new ServletContextEvent(sc);
        new TilesContextListener().contextInitialized(sce);

        return new SimpleFeaturesApplication(sc);
    }

    @Test
    public void testGeomCollectionAutoPublished() {
        final String response = target("/collections/geom_collection/tiles").request().get(String.class);
        with(response).assertThat("$.tilesets[?(@.tileMatrixSetId == 'WebMercatorQuad')]",
                org.hamcrest.Matchers.hasSize(1));
    }

    @Test
    public void testGeomCollectionTileServed() {
        final Response response = target("/collections/geom_collection/tiles/WebMercatorQuad/0/0/0")
                .request().get();
        assertEquals(200, response.getStatus());
        assertEquals("application/vnd.mapbox-vector-tile", response.getMediaType().toString());
        org.junit.Assert.assertNotNull(response.readEntity(byte[].class));
    }

    @Test
    public void testNonGeomCollectionNotPublished() {
        final Response response = target("/collections/nongeom_collection/tiles").request().get();
        assertEquals(404, response.getStatus());
    }

    @Test
    public void testGeomCollectionMetadataHasTilesetsLink() {
        final String response = target("/collections/geom_collection").request().get(String.class);
        with(response)
                .assertThat("$.itemType", equalTo("feature"))
                .assertThat("$.links[?(@.rel == 'http://www.opengis.net/def/rel/ogc/1.0/tilesets-vector')]",
                        org.hamcrest.Matchers.hasSize(1));
    }
}
