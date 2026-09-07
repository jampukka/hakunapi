#!/bin/bash
# Build a Helsinki-area subset of mtkmaasto.gpkg at a given page_size.
#
#   mtk-subset.sh <out.gpkg> <page_size>
#   ./mtk-subset-rtree.py <out.gpkg>          # then add the spatial indexes
#
# Why this exists: the full .gpkg is 49 GB, written at SQLite's default
# page_size of 4096, and quantifying what a larger page size would buy meant
# comparing two files. A ~250 MB subset covering the four tiles the bench
# measures reproduces the full file's Range request counts exactly, so the
# comparison holds without rewriting 49 GB:
#
#   ./mtk-subset.sh hki-4096.gpkg 4096 && ./mtk-subset-rtree.py hki-4096.gpkg
#   sqlite3 hki-4096.gpkg "PRAGMA page_size=65536; VACUUM INTO 'hki-65536.gpkg';"
#
# The area covers WebMercatorQuad 10/296/582 and 12/1185/2331 and
# ETRS-TM35FIN 7/104/56 and 9/419/227 - all four are Helsinki.
set -eu
SRC=$HOME/data/mtkmaasto.gpkg
DST=$1
PAGE=$2
MINX=360000; MAXX=400000; MINY=6655000; MAXY=6695000
TABLES="tieviiva rautatie lentokenttaalue vesikulkuvayla autoliikennealue jarvi meri virtavesialue virtavesikapea allas vesikivi suo kallioalue hietikko kivikko soistuma niitty muuavoinalue metsamaankasvillisuus jyrkanne kivi maastokuvionreuna maatalousmaa puisto hautausmaa urheilujavirkistysalue taajaanrakennettualue maaaineksenottoalue rakennus paikannimi kunnanhallintoraja"

rm -f "$DST"
{
echo "PRAGMA page_size = $PAGE;"
echo "PRAGMA journal_mode = OFF;"
echo "PRAGMA synchronous = OFF;"
# create an empty db with the right page size before anything else
echo "ATTACH DATABASE '$SRC' AS src;"
# gpkg metadata tables
for t in gpkg_spatial_ref_sys gpkg_contents gpkg_geometry_columns; do
  echo "CREATE TABLE $t AS SELECT * FROM src.$t;"
done
echo "CREATE TABLE gpkg_extensions AS SELECT * FROM src.gpkg_extensions;"
# CREATE TABLE AS SELECT drops the primary key, and hakunapi needs "fid" to be
# one, so take the real DDL from the source and copy rows into it.
for t in $TABLES; do
  sqlite3 -readonly "file:$SRC?immutable=1" "SELECT sql || ';' FROM sqlite_master WHERE type='table' AND name='$t';"
  echo "INSERT INTO \"$t\" SELECT * FROM src.\"$t\" WHERE fid IN (SELECT id FROM src.rtree_${t}_geom WHERE maxx>=$MINX AND minx<=$MAXX AND maxy>=$MINY AND miny<=$MAXY);"
done
echo "DELETE FROM gpkg_contents WHERE table_name NOT IN ($(echo $TABLES | tr ' ' '\n' | sed "s/.*/'&'/" | paste -sd,));"
echo "DELETE FROM gpkg_geometry_columns WHERE table_name NOT IN ($(echo $TABLES | tr ' ' '\n' | sed "s/.*/'&'/" | paste -sd,));"
echo "DETACH DATABASE src;"
} | sqlite3 "$DST"
echo "built $DST page_size=$(sqlite3 "$DST" 'pragma page_size;') size=$(du -h "$DST" | cut -f1)"
