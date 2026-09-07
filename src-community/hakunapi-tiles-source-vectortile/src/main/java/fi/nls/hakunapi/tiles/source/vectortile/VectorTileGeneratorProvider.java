package fi.nls.hakunapi.tiles.source.vectortile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers {@link VectorTileGenerator} implementations on the classpath via the
 * Java {@link ServiceLoader} mechanism and looks them up by their
 * {@link VectorTileGenerator#getType() type} id. This follows the same SPI style
 * as the rest of hakunapi's pluggable backends (output formats, CRS registries,
 * telemetry): an implementation ships a {@code META-INF/services/} entry and is
 * selected from configuration by its short type id, with no class names in the
 * config and no reflection in this module.
 */
public class VectorTileGeneratorProvider {

    private static final ServiceLoader<VectorTileGenerator> LOADER =
            ServiceLoader.load(VectorTileGenerator.class);

    /**
     * @param type the {@link VectorTileGenerator#getType() type} id to bind
     * @return the generator registered under {@code type}, or {@code null} if no
     *         implementation on the classpath declares it
     */
    public static VectorTileGenerator get(String type) {
        LOADER.reload();
        return byType(LOADER).get(type);
    }

    /**
     * Index the given generators by their type id. Fails fast if two
     * implementations claim the same type id, since the binding would otherwise
     * be ambiguous (a classpath-hygiene bug worth catching at startup rather than
     * silently picking one).
     */
    static Map<String, VectorTileGenerator> byType(Iterable<VectorTileGenerator> generators) {
        Map<String, VectorTileGenerator> byType = new LinkedHashMap<>();
        for (VectorTileGenerator g : generators) {
            VectorTileGenerator prev = byType.put(g.getType(), g);
            if (prev != null) {
                throw new IllegalStateException("Duplicate vector tile generator type '" + g.getType()
                        + "' on the classpath: " + prev.getClass().getName()
                        + " and " + g.getClass().getName());
            }
        }
        return byType;
    }

}
