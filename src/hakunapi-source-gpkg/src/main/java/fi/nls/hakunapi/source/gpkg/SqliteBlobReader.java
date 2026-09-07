package fi.nls.hakunapi.source.gpkg;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.sqlite.core.CoreStatement;
import org.sqlite.core.DB;
import org.sqlite.core.SafeStmtPtr;

/**
 * Reads a BLOB column without copying it out of SQLite.
 *
 * <p>{@code ResultSet.getBytes} goes to {@code NativeDB.column_blob}, a native
 * method that allocates a fresh {@code byte[]} per call and hands over ownership;
 * the driver offers no read-into-buffer entry point. It does however have one
 * accessor that does not copy - {@code column_text_utf8}, used internally behind
 * {@code column_text} - which returns a direct {@link ByteBuffer} over SQLite's
 * own memory. On a BLOB column that buffer holds exactly the blob's bytes, and
 * SQLite reuses the same allocation from row to row, so the whole scan reads
 * through one buffer instead of allocating per feature.
 *
 * <p>Two properties of that route the caller must respect:
 * <ul>
 *   <li><b>The buffer dies on the next {@code step()}.</b> Reading it after the
 *       cursor advances reads freed memory, and does so silently. Only a consumer
 *       that decodes the row before advancing may use this.</li>
 *   <li><b>Reading a BLOB as text converts the value in place</b>, so the
 *       column's {@code sqlite3_column_type} becomes TEXT. Nothing here reads the
 *       type back, but a second read of the same column sees the converted value.</li>
 * </ul>
 *
 * <p>Both the statement's pointer field and the accessor are internal to
 * sqlite-jdbc, so this is bound to that driver's internals: if they move, the
 * reader reports itself unavailable and the caller falls back to
 * {@code getBytes}.
 */
public final class SqliteBlobReader {

    private static final Logger LOG = LoggerFactory.getLogger(SqliteBlobReader.class);

    private static final Field POINTER_FIELD;
    private static final Method COLUMN_TEXT_UTF8;

    static {
        Field pointer = null;
        Method columnText = null;
        try {
            pointer = CoreStatement.class.getDeclaredField("pointer");
            pointer.setAccessible(true);
            columnText = Class.forName("org.sqlite.core.NativeDB")
                    .getDeclaredMethod("column_text_utf8", long.class, int.class);
            columnText.setAccessible(true);
        } catch (Exception e) {
            LOG.info("Zero-copy blob reads unavailable, falling back to ResultSet#getBytes: {}",
                    e.toString());
            pointer = null;
            columnText = null;
        }
        POINTER_FIELD = pointer;
        COLUMN_TEXT_UTF8 = columnText;
    }

    private final SafeStmtPtr pointer;

    private SqliteBlobReader(SafeStmtPtr pointer) {
        this.pointer = pointer;
    }

    /**
     * A reader for {@code ps}, or null if this driver does not expose the route.
     * The statement's pointer is stable for the life of the statement, so this is
     * resolved once per query rather than per row.
     */
    public static SqliteBlobReader of(PreparedStatement ps) {
        if (POINTER_FIELD == null) {
            return null;
        }
        try {
            PreparedStatement raw = ps.isWrapperFor(PreparedStatement.class)
                    ? ps.unwrap(PreparedStatement.class) : ps;
            if (!(raw instanceof CoreStatement)) {
                return null;
            }
            CoreStatement cs = (CoreStatement) raw;
            SafeStmtPtr pointer = (SafeStmtPtr) POINTER_FIELD.get(cs);
            // safeRun hands the DB back to the accessor, so it has to be the
            // NativeDB the method was resolved against.
            DB db = cs.getDatabase();
            if (pointer == null || !COLUMN_TEXT_UTF8.getDeclaringClass().isInstance(db)) {
                return null;
            }
            return new SqliteBlobReader(pointer);
        } catch (Exception e) {
            LOG.info("Zero-copy blob reads unavailable for this statement: {}", e.toString());
            return null;
        }
    }

    /**
     * The current row's blob in column {@code i} (0-based), as a buffer over
     * SQLite's memory, or null if the value is NULL. Valid only until the cursor
     * advances.
     */
    public ByteBuffer read(int i) {
        try {
            return pointer.safeRun((d, ptr) -> (ByteBuffer) COLUMN_TEXT_UTF8.invoke(d, ptr, i));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
