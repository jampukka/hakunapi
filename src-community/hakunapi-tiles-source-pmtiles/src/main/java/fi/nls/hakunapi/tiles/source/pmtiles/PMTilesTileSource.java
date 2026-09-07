package fi.nls.hakunapi.tiles.source.pmtiles;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.nls.hakunapi.core.config.HakunaConfigParser;
import fi.nls.hakunapi.tiles.BytesTile;
import fi.nls.hakunapi.tiles.Tile;
import fi.nls.hakunapi.tiles.MapTileLayer;
import fi.nls.hakunapi.tiles.TileMatrixSetRegistry;
import fi.nls.hakunapi.tiles.TileSource;
import fi.nls.hakunapi.tiles.config.TilesConfigParser;

/**
 * {@link TileSource} backed by a PMTiles v3 archive (https://github.com/protomaps/PMTiles).
 * A PMTiles archive is a single file holding pre-encoded tiles addressed by a
 * Hilbert-curve id; this source resolves the requested tile via the archive's
 * directories and passes the stored bytes through verbatim (no re-encoding),
 * advertising the archive's tile type as the layer's media type and the tile
 * compression via HTTP {@code Content-Encoding}.
 *
 * <p>The archive may live on the local filesystem or on a remote https endpoint
 * that honours HTTP range requests (object storage / CDN); only the small
 * header and directories are read up front, tiles are fetched on demand by byte
 * range.
 *
 * <p>Tile addresses are interpreted as XYZ (top-left origin, the PMTiles
 * convention): {@code tileMatrix} is the numeric zoom level, {@code tileCol} is
 * x and {@code tileRow} is y. The referenced tile matrix sets must therefore
 * use numeric zoom-level ids (e.g. the built-in {@code WebMercatorQuad}).
 *
 * <p>Per-layer configuration ({@code tiles.layers.<id>.pmtiles.*}):
 * <ul>
 *   <li>{@code path} - local archive file path (relative paths resolve against the config file)</li>
 *   <li>{@code url} - remote https archive URL (use instead of {@code path})</li>
 *   <li>{@code format} - override the advertised tile media type (default: derived from the archive's tile type)</li>
 * </ul>
 */
public class PMTilesTileSource implements TileSource {

    private static final Logger LOG = LoggerFactory.getLogger(PMTilesTileSource.class);

    public static final String TYPE = "pmtiles";

    private final List<PMTilesArchive> archives = new ArrayList<>();

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public MapTileLayer parse(HakunaConfigParser cfg, Path path, TileMatrixSetRegistry tms, String layerId)
            throws Exception {
        TilesConfigParser tiles = new TilesConfigParser(cfg);
        String p = "tiles.layers." + layerId + ".";
        String pm = p + "pmtiles.";

        String title = cfg.get(p + "title", layerId);
        String description = cfg.get(p + "description", title);
        List<String> tmsIds = tiles.readTileMatrixSetIds(layerId);

        String filePath = cfg.get(pm + "path");
        String url = cfg.get(pm + "url");
        if ((filePath == null) == (url == null)) {
            throw new IllegalArgumentException(
                    "tile layer " + layerId + ": exactly one of pmtiles.path or pmtiles.url is required");
        }

        RangeReader reader;
        String location;
        if (filePath != null) {
            Path archivePath = path != null ? path.resolveSibling(filePath) : Path.of(filePath);
            reader = new FileRangeReader(archivePath);
            location = archivePath.toString();
        } else {
            reader = new HttpRangeReader(URI.create(url));
            location = url;
        }

        PMTilesArchive archive;
        try {
            archive = PMTilesArchive.open(reader);
        } catch (Exception e) {
            reader.close();
            throw e;
        }
        archives.add(archive);

        String mediaType = cfg.get(pm + "format", archive.getHeader().tileMediaType());

        PMTilesConfig config = new PMTilesConfig(archive, mediaType, java.util.Set.copyOf(tmsIds));
        LOG.info("pmtiles tile layer {} -> {} type={}", layerId, location, mediaType);
        return new PMTilesTileLayer(layerId, title, description, this, tmsIds, List.of(mediaType), null, config);
    }

    @Override
    public Optional<Tile> getTile(MapTileLayer layer, String tileMatrixSetId, String tileMatrix,
            long tileRow, long tileCol, String mediaType, Map<String, String> requestParams) throws Exception {
        PMTilesConfig cfg = ((PMTilesTileLayer) layer).getPMTilesConfig();
        if (!cfg.tmsIds().contains(tileMatrixSetId)) {
            return Optional.empty();
        }

        int z;
        try {
            z = Integer.parseInt(tileMatrix);
        } catch (NumberFormatException e) {
            // PMTiles addresses tiles by numeric zoom; a non-numeric matrix id
            // cannot map to a tile id, so treat as "no such tile".
            return Optional.empty();
        }

        // PMTiles is XYZ: col is x, row is y.
        Optional<byte[]> bytes = cfg.archive().getTile(z, tileCol, tileRow);
        if (bytes.isEmpty()) {
            return Optional.empty();
        }
        String contentEncoding = Compression.contentEncoding(cfg.archive().getHeader().tileCompression());
        return Optional.of(new BytesTile(tileMatrixSetId, tileMatrix, tileRow, tileCol,
                cfg.mediaType(), contentEncoding, bytes.get()));
    }

    @Override
    public void close() throws Exception {
        Exception first = null;
        for (PMTilesArchive a : archives) {
            try {
                a.close();
            } catch (Exception e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        archives.clear();
        if (first != null) {
            throw first;
        }
    }

}
