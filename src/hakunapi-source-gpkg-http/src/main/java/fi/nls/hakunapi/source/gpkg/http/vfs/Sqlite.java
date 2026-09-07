package fi.nls.hakunapi.source.gpkg.http.vfs;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Downcalls into the platform's libsqlite3, and the constants we need from it.
 *
 * The library is loaded once per JVM. We deliberately link against the system
 * SQLite rather than shipping one: the whole point of the FFM route is that no
 * native artifact of ours has to be built or maintained.
 */
public final class Sqlite {

    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;
    public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;
    public static final java.lang.foreign.AddressLayout C_PTR = ValueLayout.ADDRESS;

    public static final int SQLITE_OK = 0;
    public static final int SQLITE_ERROR = 1;
    public static final int SQLITE_IOERR = 10;
    public static final int SQLITE_READONLY = 8;
    public static final int SQLITE_NOTFOUND = 12;
    public static final int SQLITE_ROW = 100;
    public static final int SQLITE_DONE = 101;
    public static final int SQLITE_IOERR_SHORT_READ = 522;

    public static final int SQLITE_OPEN_READONLY = 0x00000001;
    public static final int SQLITE_OPEN_URI = 0x00000040;

    public static final int SQLITE_IOCAP_IMMUTABLE = 0x00002000;

    public static final int SQLITE_INTEGER = 1;
    public static final int SQLITE_FLOAT = 2;
    public static final int SQLITE_TEXT = 3;
    public static final int SQLITE_BLOB = 4;
    public static final int SQLITE_NULL = 5;

    /** Passed as the destructor argument to bind_* to make SQLite copy the value. */
    public static final long SQLITE_TRANSIENT = -1L;

    /**
     * Candidates tried in order when hakunapi.sqlite.library is not set. The
     * bare soname is what a -dev package installs; the versioned ones are what a
     * runtime-only image has.
     */
    private static final String[] LIBRARY_NAMES = {
            "libsqlite3.so", "libsqlite3.so.0", "libsqlite3.dylib", "sqlite3.dll"
    };

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = lookup();

    private static SymbolLookup lookup() {
        // The arena is global on purpose: the library stays loaded for the JVM
        Arena arena = Arena.global();
        String explicit = System.getProperty("hakunapi.sqlite.library");
        if (explicit != null) {
            return SymbolLookup.libraryLookup(java.nio.file.Path.of(explicit), arena);
        }
        RuntimeException failure = null;
        for (String name : LIBRARY_NAMES) {
            try {
                return SymbolLookup.libraryLookup(name, arena);
            } catch (IllegalArgumentException e) {
                failure = e;
            }
        }
        throw new IllegalStateException("Could not load libsqlite3."
                + " Install it, or point hakunapi.sqlite.library at the library file", failure);
    }

    public static final MethodHandle vfs_register = fn("sqlite3_vfs_register", C_INT, C_PTR, C_INT);
    public static final MethodHandle vfs_find = fn("sqlite3_vfs_find", C_PTR, C_PTR);
    public static final MethodHandle open_v2 = fn("sqlite3_open_v2", C_INT, C_PTR, C_PTR, C_INT, C_PTR);
    public static final MethodHandle close_v2 = fn("sqlite3_close_v2", C_INT, C_PTR);
    public static final MethodHandle prepare_v2 = fn("sqlite3_prepare_v2", C_INT, C_PTR, C_PTR, C_INT, C_PTR, C_PTR);
    public static final MethodHandle step = fn("sqlite3_step", C_INT, C_PTR);
    public static final MethodHandle reset = fn("sqlite3_reset", C_INT, C_PTR);
    public static final MethodHandle finalize_ = fn("sqlite3_finalize", C_INT, C_PTR);
    public static final MethodHandle errmsg = fn("sqlite3_errmsg", C_PTR, C_PTR);
    public static final MethodHandle errstr = fn("sqlite3_errstr", C_PTR, C_INT);
    public static final MethodHandle libversion = fn("sqlite3_libversion", C_PTR);
    public static final MethodHandle threadsafe = fn("sqlite3_threadsafe", C_INT);

    public static final MethodHandle column_count = fn("sqlite3_column_count", C_INT, C_PTR);
    public static final MethodHandle column_name = fn("sqlite3_column_name", C_PTR, C_PTR, C_INT);
    public static final MethodHandle column_type = fn("sqlite3_column_type", C_INT, C_PTR, C_INT);
    public static final MethodHandle column_int = fn("sqlite3_column_int", C_INT, C_PTR, C_INT);
    public static final MethodHandle column_int64 = fn("sqlite3_column_int64", C_LONG, C_PTR, C_INT);
    public static final MethodHandle column_double = fn("sqlite3_column_double", C_DOUBLE, C_PTR, C_INT);
    public static final MethodHandle column_text = fn("sqlite3_column_text", C_PTR, C_PTR, C_INT);
    public static final MethodHandle column_blob = fn("sqlite3_column_blob", C_PTR, C_PTR, C_INT);
    public static final MethodHandle column_bytes = fn("sqlite3_column_bytes", C_INT, C_PTR, C_INT);
    public static final MethodHandle column_decltype = fn("sqlite3_column_decltype", C_PTR, C_PTR, C_INT);

    public static final MethodHandle bind_null = fn("sqlite3_bind_null", C_INT, C_PTR, C_INT);
    public static final MethodHandle bind_int = fn("sqlite3_bind_int", C_INT, C_PTR, C_INT, C_INT);
    public static final MethodHandle bind_int64 = fn("sqlite3_bind_int64", C_INT, C_PTR, C_INT, C_LONG);
    public static final MethodHandle bind_double = fn("sqlite3_bind_double", C_INT, C_PTR, C_INT, C_DOUBLE);
    public static final MethodHandle bind_text = fn("sqlite3_bind_text", C_INT, C_PTR, C_INT, C_PTR, C_INT, C_PTR);
    public static final MethodHandle bind_blob = fn("sqlite3_bind_blob", C_INT, C_PTR, C_INT, C_PTR, C_INT, C_PTR);
    public static final MethodHandle bind_parameter_count = fn("sqlite3_bind_parameter_count", C_INT, C_PTR);

    private Sqlite() {
    }

    private static MethodHandle fn(String name, MemoryLayout ret, MemoryLayout... args) {
        MemorySegment sym = LOOKUP.find(name)
                .orElseThrow(() -> new IllegalStateException("libsqlite3 is missing symbol " + name));
        return LINKER.downcallHandle(sym, FunctionDescriptor.of(ret, args));
    }

    public static Linker linker() {
        return LINKER;
    }

    /** Reads a NUL-terminated C string. Returns null for a NULL pointer. */
    public static String cstring(MemorySegment p) {
        if (p == null || MemorySegment.NULL.equals(p)) {
            return null;
        }
        return p.reinterpret(Long.MAX_VALUE).getString(0);
    }

    public static String libversionString() {
        try {
            return cstring((MemorySegment) libversion.invokeExact());
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to read sqlite3_libversion", e);
        }
    }

    public static String errstr(int rc) {
        try {
            return cstring((MemorySegment) errstr.invokeExact(rc));
        } catch (Throwable e) {
            return "rc " + rc;
        }
    }

    public static String errmsg(MemorySegment db) {
        try {
            return cstring((MemorySegment) errmsg.invokeExact(db));
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Allocates a NUL-terminated copy of s. The caller owns the arena.
     */
    public static MemorySegment cstring(Arena arena, String s) {
        return arena.allocateFrom(s);
    }

}
