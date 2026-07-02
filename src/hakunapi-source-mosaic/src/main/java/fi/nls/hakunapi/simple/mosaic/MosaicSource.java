package fi.nls.hakunapi.simple.mosaic;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import fi.nls.hakunapi.core.FeatureType;
import fi.nls.hakunapi.core.SimpleFeatureType;
import fi.nls.hakunapi.core.SimpleSource;
import fi.nls.hakunapi.core.config.HakunaConfigParser;

/**
 * A {@link SimpleSource} (type {@code mosaic}) publishing one logical collection backed by a dynamic
 * set of tile files. A PostGIS {@link MosaicIndex} is spatially queried on each request to pick the
 * tiles intersecting the request bbox; their rows are returned as a plain {@code UNION ALL} (no
 * dedup) — see {@link MosaicFeatureProducer}.
 *
 * <p>The mosaic declares one shared schema, forwarded to every tile (tiles are trusted to conform).
 * The schema is materialised once at startup by parsing a representative tile ({@code samplePath})
 * through the tile backend, and that prototype is what the mosaic publishes.
 *
 * <pre>
 * collections.mymosaic.type = mosaic
 *
 * # PostGIS catalog
 * collections.mymosaic.mosaic.index.db.jdbcUrl   = jdbc:postgresql://host/catalog
 * collections.mymosaic.mosaic.index.table        = mosaic_index
 * collections.mymosaic.mosaic.index.srid         = 3067
 *
 * # tile backend template (an ordinary DuckDB collection config, minus the file path)
 * collections.mymosaic.mosaic.backend.duckdb.id.mapping       = id
 * collections.mymosaic.mosaic.backend.duckdb.geometry.mapping = geom
 * collections.mymosaic.mosaic.backend.duckdb.geometry.type    = polygon
 * collections.mymosaic.mosaic.backend.duckdb.srid.storage     = 3067
 * collections.mymosaic.mosaic.backend.duckdb.properties       = name, class
 *
 * # representative tile used once to publish the shared schema
 * collections.mymosaic.mosaic.schema.samplePath = /data/tiles/sample.parquet
 * collections.mymosaic.mosaic.schema.backend    = duckdb
 * </pre>
 *
 * <p>Enable by listing this class in {@code db.classes} and set {@code type=mosaic}.
 */
public class MosaicSource implements SimpleSource {

    /** Tile backends currently shipped. MVP: DuckDB GeoParquet. */
    private static final String[] SUPPORTED_BACKENDS = { "duckdb" };

    private MosaicIndex index;
    private final Map<String, MosaicBackend> backends = new LinkedHashMap<>();

    public MosaicSource() {
        // Public no-arg constructor: instantiated reflectively from the db.classes list.
    }

    @Override
    public String getType() {
        return "mosaic";
    }

    @Override
    public SimpleFeatureType parse(HakunaConfigParser cfg, Path path, String collectionId, int[] srids)
            throws Exception {
        String base = "collections." + collectionId + ".mosaic.";

        this.index = new MosaicIndex(cfg, base + "index.");

        for (String type : SUPPORTED_BACKENDS) {
            String backendPrefix = base + "backend." + type + ".";
            if (cfg.getAllStartingWith(backendPrefix).isEmpty()) {
                continue;
            }
            backends.put(type, buildBackend(type, cfg, backendPrefix, srids, path));
        }
        if (backends.isEmpty()) {
            throw new IllegalArgumentException("Mosaic collection " + collectionId
                    + " declares no tile backend under " + base + "backend.<type>.*");
        }

        String samplePath = required(cfg, base + "schema.samplePath");
        String schemaBackend = cfg.get(base + "schema.backend", backends.keySet().iterator().next());
        MosaicBackend proto = backends.get(schemaBackend);
        if (proto == null) {
            throw new IllegalArgumentException("Mosaic schema.backend '" + schemaBackend
                    + "' has no matching backend template for collection " + collectionId);
        }
        FeatureType prototype = proto.tileFeatureType(samplePath);

        MosaicFeatureType ft = new MosaicFeatureType(prototype, index, backends);
        ft.setName(collectionId);
        // Presentation/query state that is not part of the delegated schema: read from the mosaic
        // collection config the same way base parsing would (the engine sets most of these afterwards
        // via HakunaConfigParser on the returned type; spatial extent is the one useful hint here).
        String[] extent = cfg.getMultiple("collections." + collectionId + ".spatialExtent");
        if (extent.length == 4) {
            double[] e = new double[4];
            for (int i = 0; i < 4; i++) {
                e[i] = Double.parseDouble(extent[i]);
            }
            ft.setSpatialExtent(e);
        }
        return ft;
    }

    private MosaicBackend buildBackend(String type, HakunaConfigParser cfg, String backendPrefix,
            int[] srids, Path configPath) {
        switch (type) {
        case "duckdb":
            Properties template = DuckDBMosaicBackend.template(cfg, backendPrefix);
            return new DuckDBMosaicBackend(template, srids, configPath);
        default:
            throw new IllegalArgumentException("Unsupported mosaic tile backend: " + type);
        }
    }

    private static String required(HakunaConfigParser cfg, String key) {
        String v = cfg.get(key);
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException("Missing required property " + key);
        }
        return v;
    }

    @Override
    public void close() throws Exception {
        Exception first = null;
        for (MosaicBackend b : backends.values()) {
            try {
                b.close();
            } catch (Exception e) {
                if (first == null) {
                    first = e;
                } else {
                    first.addSuppressed(e);
                }
            }
        }
        if (index != null) {
            index.close();
        }
        if (first != null) {
            throw first;
        }
    }

}
