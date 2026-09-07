#!/usr/bin/env python3
"""Add GeoPackage rtree indexes to a subset, reading envelopes from the GPKG
binary header (magic 'GP', flags bit 1-3 = envelope kind)."""
import sqlite3, struct, sys

ENV_DOUBLES = {0: 0, 1: 4, 2: 6, 3: 6, 4: 8}

def envelope(blob):
    """minx,maxx,miny,maxy for a GPKG geometry blob.

    A GeoPackage may store no envelope (envelope indicator 0), which MTK does
    for point geometries. Then the envelope has to come from the WKB itself; for
    a point that is just its coordinate, and that is the only such case here.
    """
    if blob is None or len(blob) < 8 or blob[0:2] != b'GP':
        return None
    flags = blob[3]
    ek = (flags >> 1) & 0x07
    little = flags & 0x01
    hdr = 8 + 8 * ENV_DOUBLES[ek]
    if ek != 0:
        fmt = ('<' if little else '>') + 'd' * ENV_DOUBLES[ek]
        v = struct.unpack_from(fmt, blob, 8)
        return v[0], v[1], v[2], v[3]
    return wkb_envelope(blob, hdr)

def wkb_envelope(blob, off):
    """Envelope of the WKB at off. Handles the geometry types MTK uses; returns
    None for anything else so the caller can count it rather than guess."""
    if len(blob) < off + 5:
        return None
    bo = blob[off]
    e = '<' if bo == 1 else '>'
    gtype = struct.unpack_from(e + 'I', blob, off + 1)[0]
    base = gtype % 1000
    has_z = 1000 <= gtype < 2000 or gtype >= 3000
    has_m = 2000 <= gtype < 3000 or gtype >= 3000
    dims = 2 + (1 if has_z else 0) + (1 if has_m else 0)
    xs, ys = [], []

    def points(o, n):
        nonlocal xs, ys
        for i in range(n):
            x, y = struct.unpack_from(e + 'dd', blob, o + i * 8 * dims)
            xs.append(x); ys.append(y)
        return o + n * 8 * dims

    o = off + 5
    if base == 1:                                  # Point
        points(o, 1)
    elif base in (2,):                             # LineString
        n = struct.unpack_from(e + 'I', blob, o)[0]
        points(o + 4, n)
    elif base == 3:                                # Polygon
        nring = struct.unpack_from(e + 'I', blob, o)[0]
        o += 4
        for _ in range(nring):
            n = struct.unpack_from(e + 'I', blob, o)[0]
            o = points(o + 4, n)
    elif base in (4, 5, 6, 7):                     # Multi* / GeometryCollection
        n = struct.unpack_from(e + 'I', blob, o)[0]
        o += 4
        for _ in range(n):
            sub = wkb_envelope(blob, o)
            if sub is None:
                return None
            xs += [sub[0], sub[1]]; ys += [sub[2], sub[3]]
            o = wkb_end(blob, o)
    else:
        return None
    if not xs:
        return None
    return min(xs), max(xs), min(ys), max(ys)

def wkb_end(blob, off):
    """Offset just past the WKB geometry at off."""
    bo = blob[off]
    e = '<' if bo == 1 else '>'
    gtype = struct.unpack_from(e + 'I', blob, off + 1)[0]
    base = gtype % 1000
    has_z = 1000 <= gtype < 2000 or gtype >= 3000
    has_m = 2000 <= gtype < 3000 or gtype >= 3000
    dims = 2 + (1 if has_z else 0) + (1 if has_m else 0)
    o = off + 5
    if base == 1:
        return o + 8 * dims
    if base == 2:
        n = struct.unpack_from(e + 'I', blob, o)[0]
        return o + 4 + n * 8 * dims
    if base == 3:
        nring = struct.unpack_from(e + 'I', blob, o)[0]
        o += 4
        for _ in range(nring):
            n = struct.unpack_from(e + 'I', blob, o)[0]
            o += 4 + n * 8 * dims
        return o
    n = struct.unpack_from(e + 'I', blob, o)[0]
    o += 4
    for _ in range(n):
        o = wkb_end(blob, o)
    return o

def main(path):
    db = sqlite3.connect(path)
    db.execute("PRAGMA journal_mode=OFF")
    db.execute("PRAGMA synchronous=OFF")
    rows = db.execute("SELECT table_name, column_name FROM gpkg_geometry_columns").fetchall()
    for t, col in rows:
        idx = f"rtree_{t}_{col}"
        db.execute(f'DROP TABLE IF EXISTS "{idx}"')
        db.execute(f'CREATE VIRTUAL TABLE "{idx}" USING rtree(id, minx, maxx, miny, maxy)')
        batch, n, skipped = [], 0, 0
        for fid, blob in db.execute(f'SELECT fid, "{col}" FROM "{t}"'):
            e = envelope(blob)
            if e is None:
                skipped += 1
                continue
            batch.append((fid, e[0], e[1], e[2], e[3]))
            if len(batch) >= 20000:
                db.executemany(f'INSERT INTO "{idx}" VALUES (?,?,?,?,?)', batch)
                n += len(batch); batch = []
        if batch:
            db.executemany(f'INSERT INTO "{idx}" VALUES (?,?,?,?,?)', batch)
            n += len(batch)
        db.commit()
        print(f"{idx}: {n} rows" + (f" ({skipped} without envelope)" if skipped else ""))
    # the extension rows hakunapi's isSpatialIndex() looks for
    db.execute("""CREATE TABLE IF NOT EXISTS gpkg_extensions (
        table_name TEXT, column_name TEXT, extension_name TEXT NOT NULL,
        definition TEXT NOT NULL, scope TEXT NOT NULL)""")
    for t, col in rows:
        db.execute("""INSERT INTO gpkg_extensions
            SELECT ?,?,'gpkg_rtree_index','http://www.geopackage.org/spec120/#extension_rtree','write-only'
            WHERE NOT EXISTS (SELECT 1 FROM gpkg_extensions
                WHERE table_name=? AND column_name=? AND extension_name='gpkg_rtree_index')""",
            (t, col, t, col))
    db.commit()
    db.close()

main(sys.argv[1])
