#!/bin/bash
# Fetch a pyramid of tiles to /dev/null and report server throughput.
#
#   pyramid.sh <tileMatrixSet> <zMin> <zMax> [concurrency]
#
# Covers the bench's Helsinki view level by level, so tile counts grow 4x per
# level the way a real client's does. Bodies are discarded: this measures the
# server, not a renderer.
#
# Numbers from a run of this script are in
# docs/plans/ogc-api-tiles-benchmark.md.
#
# Pointing it at another tile server, to compare like with like on the same
# PostGIS database:
#
#   TEMPLATE='http://localhost:3000/addresses/{z}/{x}/{y}' ./pyramid.sh ...
#
# TEMPLATE takes {z} {x} {y}, so a server using XYZ order needs no edit here.
# The hakunapi default spells out the OGC path order, {tileMatrix}/{row}/{col}.
set -u
TEMPLATE=${TEMPLATE:-'http://localhost:8080/features/collections/addresses/tiles/{tms}/{z}/{y}/{x}?f=mvt'}
TMS=${1:-ETRS-TM35FIN}
ZMIN=${2:-5}
ZMAX=${3:-12}
CONC=${4:-8}

case "$TMS" in
  ETRS-TM35FIN)
    OX=-548576; OY=8388608; R0=8192
    MINX=370000; MINY=6660000; MAXX=400000; MAXY=6685000 ;;
  WebMercatorQuad)
    OX=-20037508.3427892; OY=20037508.3427892; R0=156543.03392804097
    MINX=2740000; MINY=8420000; MAXX=2830000; MAXY=8480000 ;;
  *)
    echo "unknown tile matrix set: $TMS" >&2; exit 2 ;;
esac

URLS=$(mktemp)
OUT=$(mktemp)
trap 'rm -f "$URLS" "$OUT"' EXIT

python3 -c '
import sys
ox, oy, r0, minx, miny, maxx, maxy = map(float, sys.argv[1:8])
zmin, zmax = int(sys.argv[8]), int(sys.argv[9])
template, tms = sys.argv[10], sys.argv[11]
for z in range(zmin, zmax + 1):
    span = (r0 / 2 ** z) * 256
    c0, c1 = int((minx - ox) // span), int((maxx - ox) // span)
    r0_, r1 = int((oy - maxy) // span), int((oy - miny) // span)
    n = 2 ** z
    for row in range(max(0, r0_), min(n - 1, r1) + 1):
        for col in range(max(0, c0), min(n - 1, c1) + 1):
            print(template.replace("{tms}", tms).replace("{z}", str(z))
                  .replace("{x}", str(col)).replace("{y}", str(row)))
' "$OX" "$OY" "$R0" "$MINX" "$MINY" "$MAXX" "$MAXY" "$ZMIN" "$ZMAX" "$TEMPLATE" "$TMS" > "$URLS"

TOTAL=$(wc -l < "$URLS")
echo "$TMS  z$ZMIN..z$ZMAX  $TOTAL tiles  concurrency $CONC"

START=$(date +%s.%N)
xargs -a "$URLS" -P "$CONC" -n 1 \
    curl -s -o /dev/null -w '%{http_code} %{size_download} %{time_total}\n' > "$OUT"
END=$(date +%s.%N)

python3 -c '
import sys
start, end, path = float(sys.argv[1]), float(sys.argv[2]), sys.argv[3]
codes, total, times = {}, 0, []
for line in open(path):
    f = line.split()
    if len(f) != 3:
        continue
    codes[f[0]] = codes.get(f[0], 0) + 1
    total += int(f[1])
    times.append(float(f[2]))
if not times:
    print("no responses")
    raise SystemExit(1)
times.sort()
n, wall = len(times), end - start
pct = lambda q: times[min(n - 1, int(q * n))] * 1000
print("codes      " + ", ".join("%s:%d" % kv for kv in sorted(codes.items())))
print("wall       %.2f s   %.1f tiles/s" % (wall, n / wall))
print("bytes      %.1f MB  %.1f MB/s" % (total / 1e6, total / 1e6 / wall))
print("latency    p50 %.0f  p90 %.0f  p99 %.0f  max %.0f ms"
      % (pct(.5), pct(.9), pct(.99), times[-1] * 1000))
' "$START" "$END" "$OUT"
