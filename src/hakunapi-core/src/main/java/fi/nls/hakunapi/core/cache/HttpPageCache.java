package fi.nls.hakunapi.core.cache;

import java.nio.ByteBuffer;
import java.util.Properties;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;

/**
 * Shared page cache for HTTP-based file sources (e.g., FlatGeoBuf over HTTP).
 *
 * The cache is opt-in and disabled by default. To enable, configure:
 * - http.pageCache.maxSizeMB: Maximum cache size in megabytes (must be > 0 to enable)
 *
 * When disabled (default), getInstance() returns null and callers must handle this
 * by loading pages directly without caching.
 */
public class HttpPageCache {

    private static final Logger LOG = LoggerFactory.getLogger(HttpPageCache.class);

    public static final String PROP_MAX_SIZE_MB = "http.pageCache.maxSizeMB";

    private static volatile HttpPageCache instance;
    private static volatile boolean initialized = false;

    private final Cache<PageKey, ByteBuffer> cache;
    private final int maxSizeMB;

    /**
     * Key for cached pages - combines URL and page index.
     */
    public record PageKey(String url, int pageIndex) {}

    private HttpPageCache(int maxSizeMB) {
        this.maxSizeMB = maxSizeMB;
        long maxBytes = (long) maxSizeMB * 1024 * 1024;

        this.cache = Caffeine.newBuilder()
                .maximumWeight(maxBytes)
                .weigher((Weigher<PageKey, ByteBuffer>) (key, buffer) -> buffer.capacity())
                .build();

        LOG.info("Initialized HTTP page cache with max size {} MB", maxSizeMB);
    }

    /**
     * Get the shared cache instance, or null if caching is disabled.
     *
     * @return the cache instance, or null if not configured
     */
    public static HttpPageCache getInstance() {
        return instance;
    }

    /**
     * Initialize the shared cache from configuration properties.
     * Should be called once during application startup, before any sources are parsed.
     *
     * If http.pageCache.maxSizeMB is not set or is <= 0, caching is disabled.
     */
    public static synchronized void init(Properties cfg) {
        if (initialized) {
            LOG.warn("HttpPageCache already initialized, ignoring re-initialization");
            return;
        }
        initialized = true;

        String maxSizeStr = cfg.getProperty(PROP_MAX_SIZE_MB);
        if (maxSizeStr == null) {
            LOG.debug("HTTP page cache disabled ({}  not configured)", PROP_MAX_SIZE_MB);
            return;
        }

        int maxSizeMB;
        try {
            maxSizeMB = Integer.parseInt(maxSizeStr.trim());
        } catch (NumberFormatException e) {
            LOG.warn("Invalid value for {}: {}, cache disabled", PROP_MAX_SIZE_MB, maxSizeStr);
            return;
        }

        if (maxSizeMB <= 0) {
            LOG.debug("HTTP page cache disabled ({} = {})", PROP_MAX_SIZE_MB, maxSizeMB);
            return;
        }

        instance = new HttpPageCache(maxSizeMB);
    }

    /**
     * Check if caching is enabled.
     */
    public static boolean isEnabled() {
        return instance != null;
    }

    /**
     * Get a page from cache, or load it using the provided loader.
     * If caching is disabled, just calls the loader directly.
     *
     * @param url the URL of the file
     * @param pageIndex the page index
     * @param loader function to load the page if not cached
     * @return the page buffer
     */
    public static ByteBuffer getOrLoad(String url, int pageIndex, Function<Integer, ByteBuffer> loader) {
        HttpPageCache cache = instance;
        if (cache == null) {
            return loader.apply(pageIndex);
        }
        return cache.get(url, pageIndex, loader);
    }

    /**
     * Get a page from cache, loading it if not present.
     */
    public ByteBuffer get(String url, int pageIndex, Function<Integer, ByteBuffer> loader) {
        return cache.get(new PageKey(url, pageIndex), key -> loader.apply(key.pageIndex()));
    }

    /**
     * Get a page from cache, or null if not present.
     */
    public ByteBuffer getIfPresent(String url, int pageIndex) {
        return cache.getIfPresent(new PageKey(url, pageIndex));
    }

    /**
     * Put a page into the cache.
     */
    public void put(String url, int pageIndex, ByteBuffer buffer) {
        cache.put(new PageKey(url, pageIndex), buffer);
    }

    /**
     * Invalidate all cached pages for a specific URL.
     */
    public void invalidate(String url) {
        cache.asMap().keySet().removeIf(key -> key.url().equals(url));
    }

    /**
     * Invalidate all cached pages.
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * Get approximate number of cached entries.
     */
    public long estimatedSize() {
        return cache.estimatedSize();
    }

    public int getMaxSizeMB() {
        return maxSizeMB;
    }
}
