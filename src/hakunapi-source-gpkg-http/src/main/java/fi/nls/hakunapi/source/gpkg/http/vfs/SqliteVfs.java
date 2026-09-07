package fi.nls.hakunapi.source.gpkg.http.vfs;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A read-only sqlite3_vfs whose file primitives are implemented in Java.
 *
 * SQLite calls xRead through an upcall stub; we look up the {@link RangeReader}
 * registered for that file and hand back the bytes. Everything above the VFS —
 * the query planner, the rtree module, the page cache — is the real engine.
 *
 * Registered once per JVM. The structs and upcall stubs live in a shared arena
 * for the lifetime of the process: SQLite keeps the pointers, so they must not
 * be scoped to a request.
 */
public final class SqliteVfs {

    private static final Logger LOG = LoggerFactory.getLogger(SqliteVfs.class);

    public static final String VFS_NAME = "hakunapi-range";

    /** Offsets into struct sqlite3_vfs, LP64. */
    private static final int VFS_IVERSION = 0;
    private static final int VFS_SZOSFILE = 4;
    private static final int VFS_MXPATHNAME = 8;
    private static final int VFS_ZNAME = 24;
    private static final int VFS_XOPEN = 40;
    private static final int VFS_XDELETE = 48;
    private static final int VFS_XACCESS = 56;
    private static final int VFS_XFULLPATHNAME = 64;
    private static final int VFS_XRANDOMNESS = 104;
    private static final int VFS_XSLEEP = 112;
    private static final int VFS_XCURRENTTIME = 120;
    private static final int VFS_XGETLASTERROR = 128;
    private static final int VFS_SIZE = 136;

    /** struct sqlite3_file: pMethods, then our own file id in the tail. */
    private static final int FILE_PMETHODS = 0;
    private static final int FILE_ID = 8;
    private static final int FILE_SIZE = 16;

    /** struct sqlite3_io_methods, version 1: iVersion + 12 function pointers. */
    private static final int IOM_IVERSION = 0;
    private static final int IOM_XCLOSE = 8;
    private static final int IOM_XREAD = 16;
    private static final int IOM_XWRITE = 24;
    private static final int IOM_XTRUNCATE = 32;
    private static final int IOM_XSYNC = 40;
    private static final int IOM_XFILESIZE = 48;
    private static final int IOM_XLOCK = 56;
    private static final int IOM_XUNLOCK = 64;
    private static final int IOM_XCHECKRESERVEDLOCK = 72;
    private static final int IOM_XFILECONTROL = 80;
    private static final int IOM_XSECTORSIZE = 88;
    private static final int IOM_XDEVICECHARACTERISTICS = 96;
    private static final int IOM_SIZE = 104;

    /**
     * Reported to SQLite as the device sector size. Only a hint - SQLite reads a
     * database page at a time regardless, and the reader fetches exactly what it
     * is asked for.
     */
    private static final int SECTOR_SIZE = 4096;

    private static final Arena ARENA = Arena.ofShared();
    private static final Map<Long, RangeReader> READERS = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private static MemorySegment ioMethods;
    private static boolean registered;

    private SqliteVfs() {
    }

    /**
     * Registers the VFS with SQLite. Idempotent.
     *
     * @throws IllegalStateException if libsqlite3 rejects the registration
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        try {
            requireThreadsafe();
            ioMethods = buildIoMethods();
            MemorySegment vfs = buildVfs();
            int rc = (int) Sqlite.vfs_register.invokeExact(vfs, 0);
            if (rc != Sqlite.SQLITE_OK) {
                throw new IllegalStateException("sqlite3_vfs_register failed: " + Sqlite.errstr(rc));
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to register " + VFS_NAME + " VFS", e);
        }
        registered = true;
        LOG.info("Registered SQLite VFS {} against libsqlite3 {}", VFS_NAME, Sqlite.libversionString());
    }

    /**
     * Checks that libsqlite3 was built in serialized mode. SqliteVfsDataSource
     * shares one sqlite3 handle across request threads and relies on SQLite to
     * lock its own page cache; a build without those mutexes would corrupt it
     * silently rather than complain, so this is asserted once rather than
     * trusted.
     *
     * Only SQLITE_THREADSAFE=1 (serialized) permits sharing a handle. 2
     * (multi-thread) still reports itself as thread safe but requires a handle
     * per thread, so it is rejected too rather than accepted as "safe enough".
     */
    private static void requireThreadsafe() throws Throwable {
        int mode = (int) Sqlite.threadsafe.invokeExact();
        if (mode != 1) {
            throw new IllegalStateException("libsqlite3 " + Sqlite.libversionString()
                    + " was built with SQLITE_THREADSAFE=" + mode
                    + ", but sharing one connection across request threads needs 1 (serialized)");
        }
    }

    /**
     * Registers a reader and returns the database name to open it by. The name
     * carries the id, so xOpen needs no other state.
     */
    public static String attach(RangeReader reader) {
        long id = NEXT_ID.getAndIncrement();
        READERS.put(id, reader);
        return Long.toString(id);
    }

    public static void detach(String name) {
        READERS.remove(Long.parseLong(name));
    }

    private static RangeReader reader(MemorySegment file) {
        long id = file.reinterpret(FILE_SIZE).get(Sqlite.C_LONG, FILE_ID);
        return READERS.get(id);
    }

    // ---- sqlite3_io_methods ------------------------------------------------

    private static int xClose(MemorySegment file) {
        return Sqlite.SQLITE_OK;
    }

    private static int xRead(MemorySegment file, MemorySegment buf, int iAmt, long iOfst) {
        RangeReader reader = reader(file);
        if (reader == null) {
            return Sqlite.SQLITE_IOERR;
        }
        try {
            MemorySegment dst = buf.reinterpret(iAmt);
            int n = reader.read(iOfst, dst, iAmt);
            if (n < iAmt) {
                // SQLite requires the tail to be zeroed on a short read
                dst.asSlice(n, iAmt - n).fill((byte) 0);
                return Sqlite.SQLITE_IOERR_SHORT_READ;
            }
            return Sqlite.SQLITE_OK;
        } catch (Exception e) {
            LOG.warn("xRead failed at offset {} for {} bytes", iOfst, iAmt, e);
            return Sqlite.SQLITE_IOERR;
        }
    }

    private static int xWrite(MemorySegment file, MemorySegment buf, int iAmt, long iOfst) {
        return Sqlite.SQLITE_READONLY;
    }

    private static int xTruncate(MemorySegment file, long size) {
        return Sqlite.SQLITE_READONLY;
    }

    private static int xSync(MemorySegment file, int flags) {
        return Sqlite.SQLITE_OK;
    }

    private static int xFileSize(MemorySegment file, MemorySegment pSize) {
        RangeReader reader = reader(file);
        if (reader == null) {
            return Sqlite.SQLITE_IOERR;
        }
        try {
            pSize.reinterpret(8).set(Sqlite.C_LONG, 0, reader.size());
            return Sqlite.SQLITE_OK;
        } catch (Exception e) {
            LOG.warn("xFileSize failed", e);
            return Sqlite.SQLITE_IOERR;
        }
    }

    private static int xLock(MemorySegment file, int level) {
        return Sqlite.SQLITE_OK;
    }

    private static int xUnlock(MemorySegment file, int level) {
        return Sqlite.SQLITE_OK;
    }

    private static int xCheckReservedLock(MemorySegment file, MemorySegment pResOut) {
        pResOut.reinterpret(4).set(Sqlite.C_INT, 0, 0);
        return Sqlite.SQLITE_OK;
    }

    private static int xFileControl(MemorySegment file, int op, MemorySegment pArg) {
        return Sqlite.SQLITE_NOTFOUND;
    }

    private static int xSectorSize(MemorySegment file) {
        return SECTOR_SIZE;
    }

    private static int xDeviceCharacteristics(MemorySegment file) {
        return Sqlite.SQLITE_IOCAP_IMMUTABLE;
    }

    // ---- sqlite3_vfs -------------------------------------------------------

    private static int xOpen(MemorySegment vfs, MemorySegment zName, MemorySegment file, int flags,
            MemorySegment pOutFlags) {
        String name = Sqlite.cstring(zName);
        if (name == null) {
            return Sqlite.SQLITE_IOERR;
        }
        long id;
        try {
            id = Long.parseLong(name);
        } catch (NumberFormatException e) {
            LOG.warn("xOpen for unknown database name {}", name);
            return Sqlite.SQLITE_IOERR;
        }
        if (!READERS.containsKey(id)) {
            LOG.warn("xOpen for detached database {}", name);
            return Sqlite.SQLITE_IOERR;
        }
        MemorySegment f = file.reinterpret(FILE_SIZE);
        f.set(Sqlite.C_PTR, FILE_PMETHODS, ioMethods);
        f.set(Sqlite.C_LONG, FILE_ID, id);
        if (!MemorySegment.NULL.equals(pOutFlags)) {
            pOutFlags.reinterpret(4).set(Sqlite.C_INT, 0, Sqlite.SQLITE_OPEN_READONLY);
        }
        return Sqlite.SQLITE_OK;
    }

    private static int xDelete(MemorySegment vfs, MemorySegment zName, int syncDir) {
        return Sqlite.SQLITE_READONLY;
    }

    private static int xAccess(MemorySegment vfs, MemorySegment zName, int flags, MemorySegment pResOut) {
        // Journals and WAL files never exist for us
        pResOut.reinterpret(4).set(Sqlite.C_INT, 0, 0);
        return Sqlite.SQLITE_OK;
    }

    private static int xFullPathname(MemorySegment vfs, MemorySegment zName, int nOut, MemorySegment zOut) {
        String name = Sqlite.cstring(zName);
        byte[] bytes = name == null ? new byte[0] : name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MemorySegment dst = zOut.reinterpret(nOut);
        int n = Math.min(bytes.length, nOut - 1);
        MemorySegment.copy(bytes, 0, dst, Sqlite.C_CHAR, 0, n);
        dst.set(Sqlite.C_CHAR, n, (byte) 0);
        return Sqlite.SQLITE_OK;
    }

    private static int xRandomness(MemorySegment vfs, int nByte, MemorySegment zOut) {
        byte[] bytes = new byte[nByte];
        RANDOM.nextBytes(bytes);
        MemorySegment.copy(bytes, 0, zOut.reinterpret(nByte), Sqlite.C_CHAR, 0, nByte);
        return nByte;
    }

    private static final java.util.Random RANDOM = new java.util.Random();

    private static int xSleep(MemorySegment vfs, int micros) {
        return 0;
    }

    private static int xCurrentTime(MemorySegment vfs, MemorySegment pNow) {
        // Julian day number
        pNow.reinterpret(8).set(Sqlite.C_DOUBLE, 0, System.currentTimeMillis() / 86400000.0 + 2440587.5);
        return Sqlite.SQLITE_OK;
    }

    private static int xGetLastError(MemorySegment vfs, int nBuf, MemorySegment zBuf) {
        return Sqlite.SQLITE_OK;
    }

    // ---- struct building ---------------------------------------------------

    private static MemorySegment buildIoMethods() throws Throwable {
        MemorySegment iom = ARENA.allocate(IOM_SIZE);
        iom.fill((byte) 0);
        iom.set(Sqlite.C_INT, IOM_IVERSION, 1);
        iom.set(Sqlite.C_PTR, IOM_XCLOSE, upcall("xClose", int.class, MemorySegment.class));
        iom.set(Sqlite.C_PTR, IOM_XREAD,
                upcall("xRead", int.class, MemorySegment.class, MemorySegment.class, int.class, long.class));
        iom.set(Sqlite.C_PTR, IOM_XWRITE,
                upcall("xWrite", int.class, MemorySegment.class, MemorySegment.class, int.class, long.class));
        iom.set(Sqlite.C_PTR, IOM_XTRUNCATE, upcall("xTruncate", int.class, MemorySegment.class, long.class));
        iom.set(Sqlite.C_PTR, IOM_XSYNC, upcall("xSync", int.class, MemorySegment.class, int.class));
        iom.set(Sqlite.C_PTR, IOM_XFILESIZE,
                upcall("xFileSize", int.class, MemorySegment.class, MemorySegment.class));
        iom.set(Sqlite.C_PTR, IOM_XLOCK, upcall("xLock", int.class, MemorySegment.class, int.class));
        iom.set(Sqlite.C_PTR, IOM_XUNLOCK, upcall("xUnlock", int.class, MemorySegment.class, int.class));
        iom.set(Sqlite.C_PTR, IOM_XCHECKRESERVEDLOCK,
                upcall("xCheckReservedLock", int.class, MemorySegment.class, MemorySegment.class));
        iom.set(Sqlite.C_PTR, IOM_XFILECONTROL,
                upcall("xFileControl", int.class, MemorySegment.class, int.class, MemorySegment.class));
        iom.set(Sqlite.C_PTR, IOM_XSECTORSIZE, upcall("xSectorSize", int.class, MemorySegment.class));
        iom.set(Sqlite.C_PTR, IOM_XDEVICECHARACTERISTICS,
                upcall("xDeviceCharacteristics", int.class, MemorySegment.class));
        return iom;
    }

    private static MemorySegment buildVfs() throws Throwable {
        MemorySegment vfs = ARENA.allocate(VFS_SIZE);
        vfs.fill((byte) 0);
        vfs.set(Sqlite.C_INT, VFS_IVERSION, 1);
        vfs.set(Sqlite.C_INT, VFS_SZOSFILE, FILE_SIZE);
        vfs.set(Sqlite.C_INT, VFS_MXPATHNAME, 512);
        vfs.set(Sqlite.C_PTR, VFS_ZNAME, ARENA.allocateFrom(VFS_NAME));
        vfs.set(Sqlite.C_PTR, VFS_XOPEN, upcall("xOpen", int.class, MemorySegment.class, MemorySegment.class,
                MemorySegment.class, int.class, MemorySegment.class));
        vfs.set(Sqlite.C_PTR, VFS_XDELETE,
                upcall("xDelete", int.class, MemorySegment.class, MemorySegment.class, int.class));
        vfs.set(Sqlite.C_PTR, VFS_XACCESS, upcall("xAccess", int.class, MemorySegment.class, MemorySegment.class,
                int.class, MemorySegment.class));
        vfs.set(Sqlite.C_PTR, VFS_XFULLPATHNAME, upcall("xFullPathname", int.class, MemorySegment.class,
                MemorySegment.class, int.class, MemorySegment.class));
        vfs.set(Sqlite.C_PTR, VFS_XRANDOMNESS,
                upcall("xRandomness", int.class, MemorySegment.class, int.class, MemorySegment.class));
        vfs.set(Sqlite.C_PTR, VFS_XSLEEP, upcall("xSleep", int.class, MemorySegment.class, int.class));
        vfs.set(Sqlite.C_PTR, VFS_XCURRENTTIME,
                upcall("xCurrentTime", int.class, MemorySegment.class, MemorySegment.class));
        vfs.set(Sqlite.C_PTR, VFS_XGETLASTERROR,
                upcall("xGetLastError", int.class, MemorySegment.class, int.class, MemorySegment.class));
        return vfs;
    }

    private static MemorySegment upcall(String name, Class<?> ret, Class<?>... params) throws Throwable {
        java.lang.invoke.MethodType mt = java.lang.invoke.MethodType.methodType(ret, params);
        MethodHandle mh = MethodHandles.lookup().findStatic(SqliteVfs.class, name, mt);
        FunctionDescriptor fd = descriptor(ret, params);
        return Sqlite.linker().upcallStub(mh, fd, ARENA);
    }

    private static FunctionDescriptor descriptor(Class<?> ret, Class<?>... params) {
        java.lang.foreign.MemoryLayout[] argLayouts = new java.lang.foreign.MemoryLayout[params.length];
        for (int i = 0; i < params.length; i++) {
            argLayouts[i] = layout(params[i]);
        }
        return FunctionDescriptor.of(layout(ret), argLayouts);
    }

    private static java.lang.foreign.MemoryLayout layout(Class<?> type) {
        if (type == int.class) {
            return Sqlite.C_INT;
        }
        if (type == long.class) {
            return Sqlite.C_LONG;
        }
        if (type == double.class) {
            return Sqlite.C_DOUBLE;
        }
        if (type == MemorySegment.class) {
            return Sqlite.C_PTR;
        }
        throw new IllegalArgumentException("Unsupported layout for " + type);
    }

}
