package fi.nls.hakunapi.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.SimpleSource;

/**
 * Builds the {@link SimpleSource} instances declared by the {@code db.classes} config property
 * (comma-separated fully-qualified class names, each with a public no-arg constructor). Falls back to
 * a single built-in PostGIS source when unset.
 *
 * <p>Factored out of the servlet bootstrap so it can be reused by composite sources (e.g. the union
 * source) that need to resolve child sources by {@code type} but only receive the config, not the
 * already-built source map.
 */
public final class SourceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SourceLoader.class);

    public static final String[] DEFAULT_SOURCE_CLASSES =
            new String[] { "fi.nls.hakunapi.simple.postgis.PostGISSimpleSource" };

    private SourceLoader() {
    }

    /**
     * Instantiate the sources listed in {@code db.classes} (or the default). Class-not-found and
     * instantiation failures are logged and skipped, matching the servlet bootstrap's behaviour.
     */
    public static List<SimpleSource> load(HakunaConfigParser config) {
        String[] sourceClasses = config.getMultiple("db.classes", DEFAULT_SOURCE_CLASSES);
        return Stream.of(sourceClasses).map(clsName -> {
            try {
                return Class.forName(clsName);
            } catch (ClassNotFoundException e) {
                LOG.error("Source: class not found " + clsName);
                return null;
            }
        }).filter(Objects::nonNull).map(cls -> {
            try {
                SimpleSource source = (SimpleSource) cls.getDeclaredConstructor().newInstance();
                LOG.info("Source: " + source.getType() + " instance " + source);
                return source;
            } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException e) {
                LOG.error("Source: instantiation exception for " + cls);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Build a {@code type -> source} map from {@link #load(HakunaConfigParser)}. On duplicate types
     * the last one wins (same as populating a map in list order).
     */
    public static Map<String, SimpleSource> byType(HakunaConfigParser config) {
        Map<String, SimpleSource> byType = new LinkedHashMap<>();
        for (SimpleSource source : load(config)) {
            byType.put(source.getType(), source);
        }
        return byType;
    }

}
