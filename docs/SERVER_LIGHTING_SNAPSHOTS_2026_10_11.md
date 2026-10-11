# Large-server lighting snapshot allocation — 11 October 2026

Development follow-up on PR #279. The prior matched candidate (`cf3c5b6`)
reduced scheduled-save and movement bursts but introduced a 681.926 ms G1 full
compaction. It remains a historical failed overall performance gate.

## Evidence and cause

The original GC log shows allocation evacuation failures at the 4 GiB heap
limit. Full compaction reduces 4,091 MiB to 936 MiB; its concurrent marking
cycle aborts. This establishes allocation exhaustion and substantial reclaimable
old-generation garbage, rather than a requirement for 4 GiB of live state.
It does not by itself identify every retaining path or prove a permanent leak.

JFR allocation samples around 23:04:10–23:04:20 UTC attribute approximately
968 MiB to sky-light map clones and 841 MiB to block-light map clones. Palette
packing contributes approximately 697 MiB. These are sampled weights, not exact
per-allocation sizes or a retained-object census.

On each lighting publication, vanilla clones the entire section lookup map for
both sky and block light. Even an update to a small number of sections copies
all lookup keys and references. Incremental unloading increases opportunities
for these publications. The existing caches, section-value copy-on-write and
volatile snapshot publication are correctness mechanisms and must remain intact.

## Implementation

`RingLightSectionTable` partitions lookup keys into 256 independent fastutil
maps. A published copy clones only the small partition directory and marks
partitions shared. Before a subsequent mutation, the single lighting writer
clones that partition if it is shared. Section X/Z chooses the partition;
Y layers in the same column stay together. Published snapshots retain their own
directory and cannot observe later puts/removals. Values keep vanilla's existing
`DataLayer.copy()` ownership; this does not make light values mutable across
snapshots or defer light completion.

Three shared mixins integrate the table with `DataLayerStorageMap`, its two
concrete copy methods and storage construction. Construction opts in only when
the light chunk getter belongs to a server Overworld. Client, Nether and End
storage keeps the vanilla map. The redirects preserve vanilla's small read
cache, invalidation and `copyDataLayer` logic. The empty original lookup remains
the constructor argument for copied vanilla wrappers; sky top-section metadata
still uses vanilla copies. Nonempty constructor-supplied maps are migrated when
opting in. No heap flags, ticket propagation, light scheduling, world save
format, packet format or explicit flush semantics change.

All supported 26.x lines use these shared classes; bytecode audits confirm the
same four primitive map operation signatures on 26.1, 26.2 and 26.3.

## Validation status

A standalone allocation probe populates 98,304 section entries (4,096 columns ×
24 layers), then publishes 1,000 snapshots with one column updated per snapshot.
Thread-allocation counters report 1,572,976,000 bytes for whole-map cloning and
10,666,960 bytes for partitioned copies, about 99.3% less. This is a narrow
allocation experiment, not a server performance result or a worst-case claim;
updates touching every partition can still copy most of the lookup.

Deterministic snapshot branching and randomized operation tests pass. A temporary
native probe checks actual vanilla storage wrappers, cache invalidation,
`DataLayer` isolation, puts/removals and successive copies. It is excluded from
production along with tick/graph measurement probes.

Two pristine-snapshot repeats complete both movement cycles, ordinary autosave,
on-disk edit verification before explicit flush, explicit flush and restart.
Three real hidden/muted clients remain connected with 20 health in every sample.
The world fingerprint, server view/simulation distances, heap flags and workload
match the preceding candidate. Only the partitioned lookup fix and startup-only
ownership probes are added to its measurement instrumentation.

| Measurement | Prior candidate `cf3c5b6` | Repeat 1 | Repeat 2 |
| --- | ---: | ---: | ---: |
| Full G1 compactions in ordinary window | 1 | 0 | 0 |
| Largest individual GC pause | 681.90 ms | 233.58 ms | 224.60 ms |
| Worst whole tick, including GC | 796.01 ms | 277.40 ms | 265.59 ms |
| Autosave tick 6000 | 105.81 ms | 136.59 ms | 98.87 ms |
| Worst GC-free movement tick | 177.42 ms | 208.23 ms | 210.61 ms |
| Estimated allocation rate | 253.69 MiB/s | 180.93 MiB/s | 182.97 MiB/s |
| Leading sky/block whole-map clone sample weight | 28.3 GiB | 0.020 GiB | 0.018 GiB |
| Partitioned table allocation sample weight | — | 1.72 GiB | 1.81 GiB |
| Gold edit on disk before flush / after restart | PASS | PASS | PASS |

The clone comparison uses the preceding candidate's leading clone stacks, not
an exact total-allocation census. The new partition-table total includes its
small directories and modified partition copies. Vanilla maps remain active in
Nether/End, accounting for remaining concrete-wrapper clone samples. Overall
sampled allocation falls about 28–29%; GC-free movement maxima do not improve
further in these repeats. Young collection pauses, palette encoding and required
ticket operations remain visible. These results support removing the avoidable
whole-map allocation as the GC fix; they are not a promise of no future full
collections on any server or a controlled retained-heap proof.

Both repeats keep raw JFR/GC/controller/phase records under
`logs/chunk-stall-fix/light-partitions-{1,2}`. Compact evidence, runtime/JAR/world
identities, hashes, exact worst-tick boundaries and probe counts are banked in
[`evidence/server-lighting-snapshots-2026-10-11.json`](evidence/server-lighting-snapshots-2026-10-11.json).
The native ownership probe is banked as a separate patch; tick/graph probes use
the already recorded measurement patch. All probes are removed from production.
The public server is restored to its prior JAR and the private service, tunnel
and three test clients are stopped after each replay.

## Supported-version validation

Functional production source `292ab1765178b0df3af4add8510bd5457e446f86` passes:

| Source ABI | Fabric Java cases | NeoForge Java cases | Fresh / reopened runtime |
| --- | ---: | ---: | --- |
| 26.1 (shared 26.1–26.1.2 group) | 505 | 505 | Both loaders PASS |
| 26.2 | 505 | 505 | Both loaders PASS |
| 26.3 | 508 | 508 | Both loaders PASS |

All six source builds and 3,036 Java test executions pass. All nine GitHub CI
checks and 367 local static contracts pass. Twelve native phases complete a
524,288-cell Atlas or reopen it with zero generation time; independent format-11,
world-identity, decoded-length and present-cell audits match every report. Every
reopen is byte-identical to its immediately preceding completed fresh cache.
The six fresh runs also pass real rate-command, pause/drain/resume/cancel/restart
and synthetic FPS 4→2→1→2→4 checks. Interrupted/resumed disk phases from the prior
24-phase validation are retained; this lighting follow-up reruns fresh/reopen,
not a new 24-phase disk-interruption matrix.

A final build-only pass uses the required `0.0.0-qualification+mc<version>`
identity and `qualification` release label for all six disposable development
JARs. Their ZIP audits retain all three lighting mixins and 30 wall previews,
with no temporary ownership/tick/graph probe classes. Runtime validation uses
development classpaths; these artifacts are not frozen release candidates.

Compact counts, saved-file hashes, CI links and artifact audit hashes are in
[`evidence/server-lighting-validation-2026-10-11.json`](evidence/server-lighting-validation-2026-10-11.json).
The first local static invocation omitted `PYTHONPATH=scripts`, causing four
module import errors. Its raw failed invocation is retained; correcting only
the invocation environment passes all 367 contracts.

Full replacement-JAR release qualification remains required; old release PASS
evidence cannot qualify these changes. PR #279 remains draft, no public
candidate promotion or upload has occurred, and the public JAR hash remains
`9f6c522fd83d1c82231beb8f06e869a427efbc642c8d556b5c3e37c33530e5e1`.
