package fi.nls.hakunapi.tiles.schemas;

import java.util.List;
import java.util.stream.Collectors;

import fi.nls.hakunapi.tiles.TileMatrixSet;

/**
 * JSON view of a {@link TileMatrixSet} definition as returned by
 * {@code /tileMatrixSets/{tileMatrixSetId}} (OGC 17-083r4).
 */
public class TileMatrixSetJSON {

    private final TileMatrixSet tms;

    public TileMatrixSetJSON(TileMatrixSet tms) {
        this.tms = tms;
    }

    public String getId() {
        return tms.getId();
    }

    public String getTitle() {
        return tms.getTitle();
    }

    public String getUri() {
        return "http://www.opengis.net/def/tilematrixset/OGC/1.0/" + tms.getId();
    }

    public String getCrs() {
        return tms.getCrs();
    }

    public List<TileMatrixJSON> getTileMatrices() {
        return tms.getTileMatrices().stream().map(TileMatrixJSON::new).collect(Collectors.toList());
    }

}
