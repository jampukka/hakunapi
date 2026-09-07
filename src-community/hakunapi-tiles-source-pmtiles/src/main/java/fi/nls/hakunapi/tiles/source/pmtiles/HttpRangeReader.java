package fi.nls.hakunapi.tiles.source.pmtiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link RangeReader} over a remote PMTiles archive served via HTTP(S),
 * fetching ranges with the {@code Range: bytes=from-to} header. The server must
 * honour range requests (respond {@code 206 Partial Content}); this is the
 * standard cloud-native PMTiles deployment (object storage / CDN).
 */
public class HttpRangeReader implements RangeReader {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final URI uri;

    public HttpRangeReader(URI uri) {
        this.uri = uri;
    }

    @Override
    public byte[] readRange(long offset, int length) throws IOException, InterruptedException {
        long end = offset + length - 1;
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Range", "bytes=" + offset + "-" + end)
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        int status = resp.statusCode();
        // 206 = ranged as requested; 200 = server ignored Range and sent the whole body.
        if (status != 206 && status != 200) {
            throw new IOException("PMTiles range request failed: HTTP " + status + " for " + uri);
        }
        byte[] body = resp.body();
        if (status == 200) {
            if (offset + length > body.length) {
                throw new IOException("PMTiles range past end of body: offset=" + offset + " length=" + length);
            }
            byte[] out = new byte[length];
            System.arraycopy(body, (int) offset, out, 0, length);
            return out;
        }
        if (body.length < length) {
            throw new IOException("PMTiles short range read: wanted " + length + " got " + body.length);
        }
        return body;
    }

}
