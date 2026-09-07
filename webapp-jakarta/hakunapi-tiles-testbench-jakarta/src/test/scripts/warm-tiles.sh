#!/bin/bash
# Warm steady-state latency per tile: warmup rounds first, then measured rounds.
#
#   warm-tiles.sh [warmup] [measured] [label]
#
# The state being optimised is a server that has been up for weeks, so cold
# start, first hit, JIT warmup and an empty SQLite page cache are all noise
# here - unlike tile-latency.sh, which reports a cold column on purpose. Every
# tile is fetched [warmup] times with the result discarded, and only then
# measured, reported as median/p90/p99 over [measured] rounds. A single best run
# is not a result and neither is a first one.
#
# TILES_FILE selects the set, one "<tms>/<z>/<y>/<x>" per line:
#
#   TILES_FILE=tiles-light.txt ./warm-tiles.sh 10 30 baseline   # ~14 s, the iteration loop
#   TILES_FILE=tiles-heavy.txt ./warm-tiles.sh 6 15 baseline    # minutes, the worst case
#
# light is the sub-300ms tiles: fast enough to measure between two edits.
# heavy is the multi-second dataset tiles (17.6 MB and 10.4 MB), run rarely to
# check the worst case has not regressed.
set -u
BASE=${BASE:-http://localhost:8080/features/tiles}
TILES_FILE=${TILES_FILE:-$(dirname "$0")/tiles-light.txt}
WARM=${1:-6}
REPS=${2:-15}
LABEL=${3:-run}
OUT=$(mktemp); trap 'rm -f "$OUT"' EXIT

# Skip blank lines and comments so a tile list can document itself
mapfile -t TILES < <(grep -vE '^\s*(#|$)' "$TILES_FILE")

# Warmup: every tile, every round, results discarded
for _ in $(seq 1 "$WARM"); do
  for t in "${TILES[@]}"; do curl -s -o /dev/null "$BASE/$t?f=mvt"; done
done

for _ in $(seq 1 "$REPS"); do
  for t in "${TILES[@]}"; do
    curl -s -o /dev/null -w "$t %{time_total} %{size_download} %{http_code}\n" "$BASE/$t?f=mvt"
  done
done > "$OUT"

echo "== $LABEL  warmup=$WARM measured=$REPS =="
python3 - "$OUT" <<'PY'
import sys, collections
d = collections.defaultdict(list); size = {}; codes = collections.defaultdict(set)
for line in open(sys.argv[1]):
    f = line.split()
    if len(f) != 4: continue
    d[f[0]].append(float(f[1])*1000); size[f[0]] = int(f[2]); codes[f[0]].add(f[3])
def q(v, p):
    v = sorted(v); return v[min(len(v)-1, int(p*len(v)))]
print("%-26s %9s %9s %9s %9s %10s %6s" % ("tile","med_ms","p90_ms","p99_ms","max_ms","size_kb","code"))
for t in d:
    v = d[t]
    print("%-26s %9.1f %9.1f %9.1f %9.1f %10.1f %6s"
          % (t, q(v,.5), q(v,.9), q(v,.99), max(v), size[t]/1024, ",".join(sorted(codes[t]))))
PY
