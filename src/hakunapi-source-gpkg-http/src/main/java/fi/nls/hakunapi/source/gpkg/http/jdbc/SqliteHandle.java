package fi.nls.hakunapi.source.gpkg.http.jdbc;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.sql.SQLException;

import fi.nls.hakunapi.source.gpkg.http.vfs.Sqlite;

/**
 * An open sqlite3 handle and the statement primitives on it.
 *
 * Wraps the raw FFM calls so the JDBC classes above deal in exceptions and Java
 * types rather than return codes and pointers.
 *
 * Shared by every connection to one database, and usable from several threads at
 * once: libsqlite3 is asserted to be in serialized mode at registration, so
 * SQLite takes its own mutexes. The state here is shareable to match - the
 * {@code Arena} is {@code ofShared()} and {@code db} is an immutable pointer -
 * with the exception of {@code closed}, which is written only at shutdown.
 */
public final class SqliteHandle implements AutoCloseable {

    private final Arena arena;
    private final MemorySegment db;

    private boolean closed;

    private SqliteHandle(Arena arena, MemorySegment db) {
        this.arena = arena;
        this.db = db;
    }

    /**
     * Opens dbName read-only through the named VFS.
     */
    public static SqliteHandle open(String dbName, String vfsName) throws SQLException {
        Arena arena = Arena.ofShared();
        try {
            MemorySegment ppDb = arena.allocate(Sqlite.C_PTR);
            MemorySegment zName = arena.allocateFrom(dbName);
            MemorySegment zVfs = arena.allocateFrom(vfsName);
            int flags = Sqlite.SQLITE_OPEN_READONLY;
            int rc = (int) Sqlite.open_v2.invokeExact(zName, ppDb, flags, zVfs);
            MemorySegment db = ppDb.get(Sqlite.C_PTR, 0);
            if (rc != Sqlite.SQLITE_OK) {
                String message = MemorySegment.NULL.equals(db) ? Sqlite.errstr(rc) : Sqlite.errmsg(db);
                if (!MemorySegment.NULL.equals(db)) {
                    int ignore = (int) Sqlite.close_v2.invokeExact(db);
                }
                throw new SQLException("Failed to open " + dbName + ": " + message, null, rc);
            }
            return new SqliteHandle(arena, db);
        } catch (SQLException e) {
            arena.close();
            throw e;
        } catch (Throwable e) {
            arena.close();
            throw new SQLException("Failed to open " + dbName, e);
        }
    }

    MemorySegment db() {
        return db;
    }

    /**
     * Prepares sql, returning the statement pointer.
     */
    MemorySegment prepare(String sql) throws SQLException {
        try (Arena local = Arena.ofConfined()) {
            MemorySegment ppStmt = local.allocate(Sqlite.C_PTR);
            MemorySegment zSql = local.allocateFrom(sql);
            int rc = (int) Sqlite.prepare_v2.invokeExact(db, zSql, -1, ppStmt, MemorySegment.NULL);
            if (rc != Sqlite.SQLITE_OK) {
                throw new SQLException("Failed to prepare [" + sql + "]: " + Sqlite.errmsg(db), null, rc);
            }
            return ppStmt.get(Sqlite.C_PTR, 0);
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to prepare [" + sql + "]", e);
        }
    }

    /**
     * Runs a statement that returns no rows.
     */
    public void exec(String sql) throws SQLException {
        MemorySegment stmt = prepare(sql);
        try {
            int rc = (int) Sqlite.step.invokeExact(stmt);
            if (rc != Sqlite.SQLITE_DONE && rc != Sqlite.SQLITE_ROW) {
                throw new SQLException("Failed to run [" + sql + "]: " + Sqlite.errmsg(db), null, rc);
            }
        } catch (SQLException e) {
            throw e;
        } catch (Throwable e) {
            throw new SQLException("Failed to run [" + sql + "]", e);
        } finally {
            finalizeStatement(stmt);
        }
    }

    static void finalizeStatement(MemorySegment stmt) {
        try {
            int ignore = (int) Sqlite.finalize_.invokeExact(stmt);
        } catch (Throwable e) {
            // Ignore
        }
    }

    String errmsg() {
        return Sqlite.errmsg(db);
    }

    /**
     * Idempotent: Arena.close() throws on a second call, and this runs from
     * error paths where it may be reached twice.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            int ignore = (int) Sqlite.close_v2.invokeExact(db);
        } catch (Throwable e) {
            // Ignore
        } finally {
            arena.close();
        }
    }

}
