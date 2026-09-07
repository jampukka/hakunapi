#!/bin/bash
# Time individual dataset tiles, cold and warm, and report the Range traffic
# each one caused.
#
#   tile-latency.sh [repeats]
#
# The four tiles are the ones the MTK bench has local-file numbers for, so a run
# against docker-compose-tiles-mtk-http.yml is directly comparable with a run
# against docker-compose-tiles-mtk.yml.
#
# Cold means the hakunapi container is restarted before the request: that clears
# SQLite's page cache, the connection pool and the JIT together. It is not
# attributable to one of the three, but it is the honest number for a first
# request against a fresh server. Warm is the same tile repeated against the
# running container.
#
# COMPOSE selects which bench is being measured. The HTTP one additionally has
# an nginx access log, and where it exists the script reports Range requests and
# bytes per tile - the numbers that explain the latency:
#
#   COMPOSE=docker-compose-tiles-mtk-http.yml ./tile-latency.sh
#   COMPOSE=docker-compose-tiles-mtk.yml      ./tile-latency.sh
set -u

COMPOSE=${COMPOSE:-docker-compose-tiles-mtk-http.yml}
BASE=${BASE:-http://localhost:8080/features/tiles}
REPEATS=${1:-5}
# Where the compose file lives, so the script runs from anywhere
DOCKER_DIR=${DOCKER_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../docker" && pwd)}

TILES=(
  "WebMercatorQuad/10/296/582"
  "WebMercatorQuad/12/1185/2331"
  "ETRS-TM35FIN/7/104/56"
  "ETRS-TM35FIN/9/419/227"
)

dc() { (cd "$DOCKER_DIR" && docker compose -f "$COMPOSE" "$@"); }

# The nginx log only exists in the HTTP bench, and only with the logging config
has_storage() { dc ps --services 2>/dev/null | grep -qx storage; }

# Truncating rather than rotating: one file, read by line count between requests
reset_log() {
  has_storage || return 0
  dc exec -T storage sh -c ': > /var/log/nginx/range.log' 2>/dev/null
}

# Range requests and total bytes since the last reset. The log format is
# "<range header> <status> <bytes sent> <request time>" - see nginx-range-log.conf
read_log() {
  has_storage || { echo "- -"; return 0; }
  dc exec -T storage sh -c 'cat /var/log/nginx/range.log' 2>/dev/null \
    | awk '{ n++; b += $3 } END { printf "%d %d\n", n, b }'
}

# Tomcat answers before the config is parsed, so poll a real collections request
wait_up() {
  local i
  for i in $(seq 1 120); do
    if curl -sf -o /dev/null "$BASE/../collections?f=json"; then
      return 0
    fi
    sleep 1
  done
  echo "server did not come up" >&2
  return 1
}

# time_total in ms, and the body size, so a truncated response is visible
fetch() {
  curl -s -o /dev/null -w '%{time_total} %{size_download} %{http_code}' "$1"
}

printf '%-28s %10s %10s %10s %12s %10s %10s\n' \
  tile cold_ms warm_ms size_kb cold_ranges cold_kb code
for t in "${TILES[@]}"; do
  url="$BASE/$t?f=mvt"

  dc restart hakunapi >/dev/null 2>&1
  wait_up || exit 1
  reset_log
  read -r cold_s cold_size cold_code <<<"$(fetch "$url")"
  read -r ranges rbytes <<<"$(read_log)"

  # Warm: the best of REPEATS, which is the steady state a client sees
  warm_best=""
  for _ in $(seq 1 "$REPEATS"); do
    read -r w _ _ <<<"$(fetch "$url")"
    if [ -z "$warm_best" ] || awk "BEGIN{exit !($w < $warm_best)}"; then
      warm_best=$w
    fi
  done

  awk -v t="$t" -v c="$cold_s" -v w="$warm_best" -v s="$cold_size" \
      -v r="$ranges" -v rb="$rbytes" -v code="$cold_code" \
      'BEGIN { printf "%-28s %10.0f %10.0f %10.1f %12s %10s %10s\n",
               t, c*1000, w*1000, s/1024,
               r, (rb=="-" ? "-" : sprintf("%.1f", rb/1024)), code }'
done
