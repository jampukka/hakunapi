package fi.nls.hakunapi.simple.mosaic;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.simple.duckdb.DuckDBSimpleSource;

/**
 * {@link MosaicBackend} for GeoParquet tiles read through DuckDB. It reuses the full
 * {@link DuckDBSimpleSource} parser: for a tile at {@code path} it synthesizes a one-collection config
 * whose schema keys are the mosaic's declared backend template ({@code mosaic.backend.duckdb.*}) and
 * whose {@code table} is the tile path, then delegates to {@code parse}. The resulting
 * {@link FeatureType} is cached by path.
 *
 * <p>Because the schema template is identical across tiles, every tile publishes the mosaic's shared
 * schema; only the backing file differs. Trust model: if a file's columns don't match the template,
 * that tile's parse/probe fails and the tile errors — the mosaic does not validate up front.
 */
public class DuckDBMosaicBackend implements MosaicBackend {

    private static final String TILE_COLLECTION_ID = "tile";

    private final DuckDBSimpleSource delegate = new DuckDBSimpleSource();
    private final Properties template;
    private final int[] srids;
    private final Path configPath;
    private final ConcurrentHashMap<String, FeatureType> cache = new ConcurrentHashMap<>();

    /**
     * @param template  schema keys rooted at {@code collections.tile.} (already re-rooted from
     *                  {@code mosaic.backend.duckdb.*}); must not contain {@code table}
     * @param srids     the server's configured srid list
     * @param configPath config file path, forwarded to the delegate parser
     */
    public DuckDBMosaicBackend(Properties template, int[] srids, Path configPath) {
        this.template = template;
        this.srids = srids;
        this.configPath = configPath;
    }

    @Override
    public String getType() {
        return "duckdb";
    }

    @Override
    public FeatureType tileFeatureType(String path) throws Exception {
        FeatureType cached = cache.get(path);
        if (cached != null) {
            return cached;
        }
        FeatureType ft = parseTile(path);
        FeatureType prev = cache.putIfAbsent(path, ft);
        return prev != null ? prev : ft;
    }

    private FeatureType parseTile(String path) throws Exception {
        Properties props = new Properties();
        props.putAll(template);
        String p = "collections." + TILE_COLLECTION_ID + ".";
        props.setProperty(p + "table", path);
        // A stable relation alias keeps generated SQL independent of the varying file path.
        props.setProperty(p + "relation", TILE_COLLECTION_ID);
        HakunaConfigParser cfg = new HakunaConfigParser(props);
        return delegate.parse(cfg, configPath, TILE_COLLECTION_ID, srids);
    }

    @Override
    public void close() throws Exception {
        cache.clear();
        delegate.close();
    }

    /**
     * Re-root the mosaic's {@code collections.<id>.mosaic.backend.duckdb.*} keys under
     * {@code collections.tile.} so the DuckDB parser reads them as an ordinary collection config.
     */
    static Properties template(HakunaConfigParser cfg, String backendPrefix) {
        Properties out = new Properties();
        String dst = "collections." + TILE_COLLECTION_ID + ".";
        cfg.getAllStartingWith(backendPrefix).forEach((k, v) -> out.setProperty(dst + k, v));
        return out;
    }

}
