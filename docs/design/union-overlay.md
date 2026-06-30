# Design: query-param-driven multi-source UNION overlay

Status: draft (design only, no implementation)
Branch: `design/union-overlay`

## 1. Problem and semantics

A single registered collection (one "master" `FeatureType`) should be able to fold an
**extra** datasource into its `/items` response, selected at request time by a query
parameter:

```
GET /collections/{id}/items?branch=<key>
```

The `branch` source is overlaid onto the master source with **UNION (DISTINCT)**
semantics — not `UNION ALL`. Deduplication is by **feature id**.

**Branch has priority.** On an id collision, the branch row wins and the master row is
dropped. Conceptually:

- master = base dataset
- branch = a set of edits + inserts layered on top

So a branch row with id `X` overrides the master row `X`; a branch row with a new id is
an insert; a master row whose id is absent from the branch passes through unchanged.

Constraints that shape the design:

- **Mixed backends.** Master may be PostGIS, branch may be Parquet/DuckDB (or
  FlatGeobuf). There is no shared SQL engine, so an engine-side `UNION` is impossible.
  The merge must happen in Java.
- **Branch may be large.** It cannot be loaded fully into memory. The merge must
  stream both sources.

### Chosen approach: sorted k-way merge by id

Both sources emit rows **sorted ascending by id** under a shared total order and an
identical schema. A streaming 2-way merge consumes the two ordered streams with O(k)
memory (k = 2 = number of sources). On equal id, emit the branch row and advance both
heads (dedup + priority in one step). This streams an arbitrarily large branch.

## 2. Architecture

Three new pieces, all in terms of existing `hakunapi-core` interfaces:

### 2.1 `BranchParam` — a `GetFeatureParam`

Registered as a **collection-specific** parameter via `FeatureType.getParameters()`.
This is the real extension point: `ParamUtil.getParameters()`
(`hakunapi-simple-servlet-jakarta/.../operation/ParamUtil.java:66-88`) appends
`ft.getParameters()` to the conformance params and sorts by `priority()`. A
collection-specific param therefore becomes a **known** parameter and passes
`checkUnknownParameters` (which otherwise rejects any unlisted query param).

`GetFeatureParam` contract:

```java
void modify(FeatureServiceConfig service, GetFeatureRequest request, String value);
int priority();          // ordering among params for one request
String getParamName();   // "branch"
```

`BranchParam.modify(...)`:

1. **Allowlist-resolve** `value` → a configured branch source descriptor (see §6
   security). Reject any value not in the allowlist with `IllegalArgumentException`.
2. Stash the resolved branch source on the request so the producer can pick it up. The
   request already carries a catch-all `Map<String,String> queryParams`
   (`GetFeatureRequest.addQueryParam`/`getQueryParam`); the resolved key (not the raw
   value) is the natural thing to record, or a typed slot if we extend the request.

`priority()` should run `BranchParam` late enough that `sortby`, `filter`, and
pagination are already applied to the `GetFeatureCollection`, so the union producer sees
the final filter/order state.

### 2.2 `UnionFeatureProducer` — a `FeatureProducer` wrapper

`FeatureProducer` is `{ getNumberMatched, getFeatures }`. The master `FeatureType` is
wired so that, **when a branch is active on the request**, its producer is the
`UnionFeatureProducer`; otherwise the plain master producer runs (zero overhead for
non-branch requests).

`UnionFeatureProducer` holds:

- `master` — the original `FeatureProducer`
- a factory that builds a `FeatureProducer` for the resolved branch source

`getFeatures(request, col)`:

1. Clone/derive a per-source `GetFeatureCollection` for master and for branch, each with
   **forced** `ORDER BY id ASC` plus the id-range predicate (§4, §5).
2. Open both child `FeatureStream`s.
3. Return a `MergeByIdFeatureStream` over the two.

`getNumberMatched(...)` — see §6 (recommend OMIT).

### 2.3 `MergeByIdFeatureStream` — a `FeatureStream`

`FeatureStream` is `AutoCloseable + Iterator<ValueProvider>`. The merge holds two child
streams and one lookahead `ValueProvider` (head) per child.

Sources are indexed by **priority**: index 0 = branch (wins ties), index 1 = master.

`next()` rule:

- Both heads present: compare ids.
  - `branch.id <  master.id` → emit branch head, advance branch.
  - `branch.id >  master.id` → emit master head, advance master.
  - `branch.id == master.id` → emit **branch** head, advance **both** (dedup +
    branch-priority override).
- One head exhausted: drain the other.

`close()` closes both child streams (suppress-and-rethrow so one failure does not leak
the other).

Id is read positionally: the id column position comes from `ft.getId()` /
`col.getProperties()`, and `ValueProvider.getObject(i)` / `getLong(i)` / `getString(i)`
yields the comparable id value.

```
                 ?branch=<key>
                       │
                 BranchParam.modify        (allowlist-resolve, stash on request)
                       │
       ┌───────────────┴───────────────┐
       ▼                               ▼
  master FeatureProducer        UnionFeatureProducer
                                       │
                         ┌─────────────┴─────────────┐
                         ▼                           ▼
                  master stream               branch stream
                  (ORDER BY id ASC,           (ORDER BY id ASC,
                   id-range pushdown)          id-range pushdown)
                         └─────────────┬─────────────┘
                                       ▼
                            MergeByIdFeatureStream
                          (idx0=branch wins tie, idx1=master)
                                       ▼
                                  items response
```

## 3. The id-sorted-merge invariant + merge rule

**Invariant (locked):** every participating source emits rows sorted ascending by id, under one
shared total order, with an identical projected schema. The merge depends entirely on
this; if any source violates it, output is wrong (silently). See §4 for how each backend
must guarantee it.

**Merge rule (restated):** lower id emitted first; on equal id, branch (priority idx 0)
is emitted and **both** heads advance. This makes the output a UNION-distinct stream in
which branch overrides master, while never buffering more than one row per source.

## 4. Per-backend requirement: forced id ASC + id-range pushdown

Each child `FeatureProducer` must honor:

1. **Forced ordering** `ORDER BY id ASC` (FeatureType default order, forced to id).
2. **Id-range predicate** pushdown for paging resume: `id >= cursor` (§5).

**No fixed assumption about which backends participate.** Master and branch sources are
both chosen via **configuration** (§2.1 allowlist, §7 strategy/config). Any source that
can satisfy the two requirements above may serve as master or branch. The requirements
are a contract the configured source must meet, not a fixed master=postgis / branch=X
pairing.

A source qualifies as a union participant iff it can:

- emit rows ordered by id ASC under pushdown (or sort internally **without** full-dataset
  buffering — external/streamed sort — to keep the large-branch guarantee), and
- push down an `id >= cursor` range predicate.

Known status of existing sources (verify per source when configured, not assumed):

| Source  | id ASC order | id-range filter | Notes |
|---------|--------------|-----------------|-------|
| postgis | SQL `ORDER BY` | SQL `WHERE`   | reference impl; meets contract |
| duckdb  | **verify**   | **verify**      | JDBC SimpleSource; bbox-covering pruning exists — confirm `ORDER BY id` + `id >= ?` translate to SQL / row-group pruning |
| fgb     | **verify**   | **verify**      | FlatGeobuf; spatial index, not id-ordered — may need an id index or streamed sort; quantify cost for large branch |

Sources that cannot meet the contract are simply not eligible as union participants;
this is enforced/validated at configuration time.

## 5. Cursor paging design

Reuse `PaginationStrategyCursor` (`hakunapi-core/.../PaginationStrategyCursor.java`).

- The forced single `orderBy` is **id**, so the cursor collapses to a single value:
  `getNextCursor` returns `next.getObject(0).toString()` = the last emitted id.
- On resume, `apply()` adds `Filter.greaterThanOrEqualTo(idProp, cursor)` to the
  collection, which the `UnionFeatureProducer` pushes into **both** child collections.
  Each source restarts at `id >= cursor`, the merge re-runs, and the same dedup/priority
  rule reproduces the exact continuation. **Stateless** — no seen-set, no server-side
  cursor table. Works for a large branch.

**Note — `>=` boundary.** `PaginationStrategyCursor.apply()` uses `greaterThanOrEqualTo`
(`>=`), so resume restarts *at* the cursor id, not after it. This is **by design and not
a union-specific problem**: the engine already requests `limit + 1` rows (the read-one-
past in `writeResponseBody` / `SimpleFeatureWriter` drives the next-link decision), and
sources simply honor the requested limit. The boundary row is the row whose cursor is
emitted as `next`; the page-edge bookkeeping is the same as any single-source cursor
scan. The union path inherits it unchanged — each child source resumes at `id >= cursor`,
the merge re-runs, and the engine's limit+1 handling closes the page. No special-casing
in `UnionFeatureProducer`.

## 6. Caveats / limitations

- **`sortby` — not an issue.** User-driven `sortby` is **not supported** today:
  `SortbyParam.modify()` ignores the request value and just applies
  `FeatureType.getDefaultOrderBy()`, and the param is hidden ("waiting for Sorting
  extension"). Output order is governed by the FeatureType's configured order (sortables
  are a FeatureType-level config concern), which the union path forces to **id ASC**. If
  arbitrary user sortby ever lands, the rule is: **reject** non-id sortby when `branch`
  is active (a re-sort buffer would break the large-branch streaming guarantee). No
  per-request sortby handling needed now.
- **`numberMatched` — out of scope.** Not computed for union responses. Dedup-counting
  across mixed backends needs a cross-source distinct count and is not needed now. OGC
  API Features allows omitting `numberMatched`; the union response omits it. (Revisit
  only if an exact count is later required, via opt-in full scan.)
- **Allowlist security (path traversal).** The `branch` value selects a data source,
  potentially a file path (Parquet/FGB). Using the raw value to locate a file is an
  arbitrary-file-read / path-traversal vector. `BranchParam` **must** map the value
  through a configured allowlist (key → pre-registered source); never derive a filesystem
  path from request input. Unknown key → reject.
- **Id must be globally unique and comparable across sources** — **required, locked.**
  The merge assumes one id space shared by master and branch with a consistent comparison
  (same type, same collation/ordering). This is a hard precondition: master and branch
  must share id type + ordering, and the projected schema must match. Validated at
  configuration/wiring time; a source pairing that violates it is rejected.

### Resolved (decided in design review)

- **sortby** — no per-request handling; not supported today, output order is the
  FeatureType default forced to id ASC. If user sortby ever lands: reject non-id when
  branch active. (§6)
- **numberMatched** — out of scope; omitted from union responses. (§6)
- **Cursor `>=`** — non-issue; engine already reads `limit + 1`, sources just honor the
  limit. No union-specific page-edge handling. (§5)
- **id comparability** — required and locked; validated at config time. (§6)

### Open questions for implementation

1. **Branch source configuration (strategy + config)**: a strategy-pattern source
   resolver keyed by the allowlist entry. Define the config shape (how a union/branch
   pairing is declared), and where the id/schema-compatibility + pushdown-contract check
   runs (startup vs first use). This is the main unresolved design surface.
2. **duckdb / fgb pushdown verification** (§4): confirm id-order + `id >= cursor`
   translate to real engine pushdown; measure large-branch behavior; define streamed-sort
   fallback for non-id-ordered sources (fgb).
3. **Where to force `ORDER BY id ASC`**: rewrite each child
   `GetFeatureCollection.orderBy` inside `UnionFeatureProducer` (preferred — backends
   stay generic) vs a per-backend flag.
4. **Wiring seam**: `writeResponseBody` selects the producer via
   `c.getFt().getFeatureProducer()`
   (`GetCollectionItemsOperation.java:225`). The master FeatureType must yield
   `UnionFeatureProducer` only when a branch is active on the request — decide whether
   `BranchParam.modify` sets a typed request slot read at this seam, or the FeatureType
   wraps its producer conditionally.
