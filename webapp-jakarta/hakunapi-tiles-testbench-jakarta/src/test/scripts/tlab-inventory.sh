#!/bin/bash
# Complete allocation inventory: every TLAB refill with a stack trace, not a
# sample. jdk.ObjectAllocationSample is an extrapolated sample and ranks small
# frequently-allocated objects far too high; these two events cover all of it.
#   tlab-inventory.sh <container> <url>
set -u
C=${1:?container}; U=${2:?url}
docker exec "$C" jcmd 1 JFR.start name=tl settings=profile \
  +jdk.ObjectAllocationInNewTLAB#enabled=true  +jdk.ObjectAllocationInNewTLAB#stackTrace=true \
  +jdk.ObjectAllocationOutsideTLAB#enabled=true +jdk.ObjectAllocationOutsideTLAB#stackTrace=true \
  filename=/tmp/tl.jfr >/dev/null 2>&1
curl -s -o /dev/null "$U"
docker exec "$C" sh -c 'jcmd 1 JFR.dump name=tl filename=/tmp/tl.jfr; jcmd 1 JFR.stop name=tl' >/dev/null 2>&1
docker exec "$C" jfr print --events jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB \
  --stack-depth 12 /tmp/tl.jfr 2>/dev/null | python3 -c '
import sys,re,collections
txt=sys.stdin.read()
recs=re.split(r"\njdk\.ObjectAllocation(?:InNewTLAB|OutsideTLAB) \{", txt)
U={"bytes":1,"B":1,"kB":1e3,"KB":1e3,"MB":1e6,"GB":1e9}
site=collections.Counter(); cls=collections.Counter()
for r in recs[1:]:
    m=re.search(r"objectClass = ([^\s(]+)",r)
    w=re.search(r"(?:tlabSize|allocationSize)\s*=\s*([\d.]+)\s*(\w+)",r)
    if not m or not w: continue
    sz=float(w.group(1))*U.get(w.group(2),1)
    fr=re.findall(r"^\s{4}([\w.$]+\.[\w$<>]+)\(",r,re.M)
    ours=[f for f in fr if "hakunapi" in f]
    site[ours[0] if ours else (fr[0] if fr else "?")]+=sz; cls[m.group(1)]+=sz
tot=sum(site.values()) or 1
print("total %.1f MB\n"%(tot/1e6))
for s,v in site.most_common(20): print("%7.1f MB %5.1f%%  %s"%(v/1e6,100*v/tot,s))
print()
for c,v in cls.most_common(10): print("%7.1f MB %5.1f%%  %s"%(v/1e6,100*v/tot,c))
'
