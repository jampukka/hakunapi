#!/bin/bash
# Exact allocated bytes per tile: jdk.ThreadAllocationStatistics is a periodic
# read of getThreadAllocatedBytes, an exact per-thread counter, not a sample.
set -u
C=${1:?container}; URL=${2:?url}; N=${3:-4}
docker exec "$C" jcmd 1 JFR.start name=ea settings=profile \
  +jdk.ThreadAllocationStatistics#enabled=true +jdk.ThreadAllocationStatistics#period=100ms \
  filename=/tmp/ea.jfr >/dev/null 2>&1
for i in $(seq 1 "$N"); do curl -s -o /dev/null "$URL"; done
docker exec "$C" sh -c 'jcmd 1 JFR.dump name=ea filename=/tmp/ea.jfr; jcmd 1 JFR.stop name=ea' >/dev/null 2>&1
docker exec "$C" jfr print --events jdk.ThreadAllocationStatistics /tmp/ea.jfr 2>/dev/null \
| python3 -c '
import sys,re
txt=sys.stdin.read(); per={}
for b in txt.split("jdk.ThreadAllocationStatistics {")[1:]:
    m=re.search(r"allocated\s*=\s*([\d.]+)\s*(\w+)",b); t=re.search(r"thread\s*=\s*\"([^\"]+)\"",b)
    if not m or not t: continue
    mult={"bytes":1,"B":1,"kB":1e3,"KB":1e3,"MB":1e6,"GB":1e9}.get(m.group(2),1)
    v=float(m.group(1))*mult; n=t.group(1)
    lo,hi=per.get(n,(v,v)); per[n]=(min(lo,v),max(hi,v))
tot=sum(hi-lo for lo,hi in per.values())
import os
print("%.1f MB per tile" % (tot/1e6/int(os.environ["N"])))
' 
