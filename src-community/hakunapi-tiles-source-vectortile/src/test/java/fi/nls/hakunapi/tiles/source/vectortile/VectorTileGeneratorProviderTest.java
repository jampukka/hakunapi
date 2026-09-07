package fi.nls.hakunapi.tiles.source.vectortile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.tiles.TileBody;

/**
 * Tests {@link VectorTileGeneratorProvider}: ServiceLoader-based lookup of
 * generators by type id, and the fail-fast guard against two implementations
 * claiming the same type id.
 */
public class VectorTileGeneratorProviderTest {

    /** Minimal generator with a configurable type id, for indexing tests. */
    private static class FakeGenerator implements VectorTileGenerator {
        private final String type;

        FakeGenerator(String type) {
            this.type = type;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public Optional<TileBody> generate(TileContext ctx, List<FeatureType> collections,
                String mediaType, Map<String, String> requestParams) {
            return Optional.empty();
        }
    }

    @Test
    public void looksUpRegisteredGeneratorByType() {
        // The empty stub is registered via META-INF/services on the classpath.
        VectorTileGenerator g = VectorTileGeneratorProvider.get(EmptyVectorTileGenerator.TYPE);
        assertNotNull(g);
        assertEquals(EmptyVectorTileGenerator.TYPE, g.getType());
    }

    @Test
    public void unknownTypeReturnsNull() {
        assertNull(VectorTileGeneratorProvider.get("no-such-generator"));
    }

    @Test
    public void distinctTypesIndexedByType() {
        FakeGenerator a = new FakeGenerator("a");
        FakeGenerator b = new FakeGenerator("b");
        Map<String, VectorTileGenerator> byType =
                VectorTileGeneratorProvider.byType(List.of(a, b));
        assertSame(a, byType.get("a"));
        assertSame(b, byType.get("b"));
    }

    @Test(expected = IllegalStateException.class)
    public void duplicateTypeIdFailsFast() {
        VectorTileGeneratorProvider.byType(List.of(
                new FakeGenerator("dup"), new FakeGenerator("dup")));
    }

}
