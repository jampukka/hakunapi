package fi.nls.hakunapi.tiles;

import java.io.OutputStream;

/**
 * The lazy body of a {@link Tile}: a function that writes the encoded tile bytes
 * to an {@link OutputStream} on demand. Returning a {@code TileBody} does not
 * materialise the tile; the bytes are produced only when {@link #writeTo} is
 * called, which lets a generating source stream straight to the (gzipped) response
 * with no intermediate buffer. A byte-backed body is simply
 * {@code out -> out.write(bytes)}; an MVT generator's body encodes its layers
 * into {@code out} at write time, one at a time.
 *
 * <p>Declares {@code throws Exception} rather than {@code IOException} because a
 * generating body does its feature queries here, at write time - the same wide
 * signature the rest of hakunapi's write path uses.
 */
@FunctionalInterface
public interface TileBody {

    void writeTo(OutputStream out) throws Exception;

}
