# Server Atlas checkpoint persistence

## Problem and release baseline

The qualified 1.4 source `6ee5bd56c1de8044c658d80d5a0c0f4a9d6c4085`
rewrites the entire dirty Atlas on the server thread every 200 ticks. Each cell
requires twelve uncompressed bytes: Medium processes 48 MiB and Large 192 MiB
per checkpoint, including absent cells. Encoding, GZIP and filesystem work all
block the tick. Adaptive chunk admission does not change this path.

The owner's 10 October instruction authorizes implementation, comparison using
the pregenerated Large server world, and a 1.4 server update. It does not authorize
public upload. The six previous qualified files remain intact; their full-suite
PASS does not qualify this changed source.

## Implementation

`RingAtlasCheckpointWriter` owns one in-flight checkpoint per Overworld. The
worker allocates a private Atlas buffer, then the server copies 16,384-cell slices
with a two-millisecond per-tick target, checked between slices. A slice is bounded
to 192 KiB. Record the actual maximum copy step: scheduling and GC can exceed a
soft time target. Never describe it as a hard real-time guarantee.

The first direct-snapshot trial on the real server measured 202–673 ms copies
and roughly four-second background writes. This evidence justified incremental
copying rather than retaining one 192 MiB main-thread allocation/copy.

Every changed cell in the authoritative capture and recapture paths marks an
already copied slice for repair. Uncopied slices observe changes on their first
pass. Once the initial pass and all repairs finish on the owner thread, copy the
current revision and validate present-cell accounting. The resulting buffer is
coherent at that point. Only then hand it back to the worker for serialization,
compression, replacement and optional complete-file verification. Workers never
read the live Atlas or mutate service/job state.

Mutation generations are separate from network revisions. The captured generation
is acknowledged only after successful persistence; edits during disk work remain
dirty. Allocation, copying and writing share one outstanding slot. There is no
queue of full snapshots. Sustained edits can delay preparation; repair storage is
bounded by the number of slices. Ordinary write failures retain dirty state and
retry at the next 200-tick checkpoint opportunity.

The disk format remains 11, with the existing GZIP codec and temporary-file
replacement (atomic where supported). No sampling, transport or client rendering
change is made. One extra 192 MiB Atlas buffer is needed on Large; final verification
also temporarily loads a second independent copy. Worker allocation and JVM-wide
GC still require measurement. The first bounded-copy trial saw a 134 ms tick
outlier while repeatedly allocating Large buffers; reuse removes those recurring
192 MiB allocations. Preserve that diagnostic separately from final measurements.

## Lifecycle

Normal pregeneration completion remains SAVING until worker verification succeeds.
Ordinary cancellation releases tickets first and waits asynchronously for its
checkpoint; the concurrency fixture now waits for durable cancellation before
asserting terminal state and replacement. Paused/idle cancellation can retain its
existing state until cancellation finishes. Later mutations remain pending.

Server stop/world unload freezes captures using the existing policy, drains any
active snapshot, and flushes remaining dirty state. Only teardown may block and
finish copying without a tick budget. Worker waits have a 60-second timeout and
do not require server-queue callbacks. Failure prevents successful state removal;
same-path replacement is rejected while an old state remains owned.

## Diagnostics and comparison

The later [three-client Large-server trial](SERVER_MULTIPLAYER_LOAD_2026_10_10.md)
found a separate multiplayer-only hot path: completed-chunk status reports
rescanned all cells for each player. Complete Atlases now return the exact total
from their maintained present-cell count without scanning. Partial coverage
keeps its existing exact scan; this shortcut does not change save or capture work.

`-Dringworld.atlasSaveTimings=true` logs copied-cell count, total owner copy work,
maximum copy step, worker write duration and verification duration.

`-Dringworld.atlasCheckpointProbe=true` enables an explicit test only. It compares
the unchanged pre-fix synchronous `RingTerrainAtlas.save` call against the bounded
writer in the same running server, using a separate disposable cache file. Warm
each path once; then run six pairs in alternating AB/BA order, with 200 ticks
between completed operations. Log owner work, copy/write phases, observed tick
maximum and used heap. The async owner figure is the greater of request time and
maximum copy step, not total elapsed time to finish a checkpoint.

An optional `ringworld.atlasCheckpointProbeSource` points to an immutable complete
Atlas backup. The test reader accepts format 10 or 11 solely for performance input;
it does not migrate/install an authoritative cache or change the game's format
validation. This permits comparing both paths against the server's complete
pregenerated terrain while the normal format-11 Atlas rebuild is paused. Preserve
the source hash and distinguish this controlled codec comparison from gameplay
FPS, packet-loss testing and full release qualification.

Retained deployment, build, benchmark and native lifecycle evidence is under
ignored `logs/server-atlas-checkpoint/`. Final results are appended after testing.

## Large server comparison — 10 October 2026

Four logical CPUs, 7,941 MiB host RAM, 4 GiB Java heap, Minecraft 26.3 /
NeoForge 26.3.0.37-beta. No players connected. The normal Atlas rebuild was
paused. Both paths used the complete 32,768 × 512 Atlas from the owner's
pregenerated Large-world backup, with identical data and codec. Warm each path
once, then alternate six AB/BA pairs. The intentional synchronous test reproduced
seven-to-eight-second overload warnings.

| Measurement | Original synchronous path | Bounded worker with buffer reuse |
| --- | ---: | ---: |
| Measured checkpoints | 6 | 6 |
| Mean largest owner-thread step per checkpoint | 7,559.62 ms | 6.14 ms |
| Worst owner-thread checkpoint step | 7,998.10 ms | 12.63 ms |
| Worst observed server tick during checkpoint | 7,999.10 ms | 13.49 ms |
| Mean serialization/compression/write time | 7,559.57 ms, on server thread | 7,699.32 ms, on worker |

The worst observed checkpoint tick was 99.83% shorter. This is removal of a
server-thread stall, not a claim of faster disk writes or improved client FPS.
The two-millisecond slice target is soft: the observed worst copy step exceeded
it, but remained below the normal 50 ms tick budget in this test.

The source input SHA-256 is
`743b8f062d0d33bef3ca472395b67d77975c6e7ae95da6e529d17bb2dbb4ffd5`.
The measured trial JAR SHA-256 is
`1e19417b7c655d7a0289700e4815d597a28c4fb78af75833f0456f1f2be618eb`.
The final code also catches fatal worker failures to terminate the checkpoint
future instead of leaving it pending; that does not change the successful
measured path. See [machine-readable measurements](evidence/server-atlas-checkpoint-2026-10-10.json)
for every raw warmup/measured operation. Raw server logs are retained locally.

The original 1.3 installation and complete world/config are preserved under
`/opt/ringworld-server-archives/pre-1.4-checkpoint-20261010/`. The test reader never
installs the old format-10 samples as a new format-11 authoritative cache.
Real world chunks remain pregenerated; the normal 1.4 Atlas needs its separate
format-11 recapture/rebuild. Background generation is restored and the
comparison probe is disabled after measurement.

## Final development validation and server state

All six source targets build and pass Java tests: Fabric and NeoForge on 26.1
(oldest shared 26.1.x ABI), 26.2 and 26.3. The oldest pair each runs 492 Java tests
with no failures; the shared Python suite runs 452 tests, with 450 passing and two
Windows-only skips on macOS. Seven new checkpoint tests cover coherent repaired
copies, byte-equivalence, one outstanding save, changes during writing, buffer
reuse, failure/retry, verification and teardown without server-queue callbacks.

Each of the six targets passes four dedicated native phases: fresh generation
with real pause/drain/resume/cancel/restart and rate-command probes; normal stop
partway through generation; resume to completion; and reopen of the complete
cache. All 24 retained files were independently GZIP-decoded, presence-counted
against their reports and SHA-256 audited. Interrupted files contain partial
coverage; all resumed files contain complete coverage. Complete reopens have
zero elapsed generation time and byte-identical caches. The interruption runner
expects a nonzero completion-only Gradle finalizer exit with an `INTERRUPTED`
report; it is not a runtime failure. One earlier readiness race in the test's RCON
stop request is retained separately; the final runner waits for listener readiness.

The checkpoint-only remote JAR SHA-256 was
`f98f421baca28dcc4b3815f2b85c0130604e67f35e8325d5da647ee578470693`.
That deployment ran with only `atlasSaveTimings` enabled,
not the comparison probe. A normal restart durably retained 2,922,496 format-11
cells; recapture subsequently passed 31% and continued at roughly 5,100 cells/s.
A separate read-only region-header audit finds all 65,536 canonical chunk records
present in 128 region files. This proves record coverage, not every terrain value.
World chunks remain intact; rebuilding the Atlas is separate from regenerating them.

The retained post-restart window contains 32 successful periodic checkpoints,
zero overload warnings, zero save errors and zero flush errors. Its worst recorded
owner copy step is 63.89 ms shortly after restart. Preserve this outlier alongside
the controlled 13.49 ms maximum: the two-millisecond copy target is soft and the
fix does not promise every gameplay tick stays below 50 ms. No active-player or
packet-loss comparison was performed.

The later [adaptive capture follow-up](ATLAS_CONCURRENCY_253.md#adaptive-capture-follow-up--10-october-2026)
supersedes that server JAR, reaches full Atlas coverage, and reopens cleanly with
all timing/probe flags disabled. Its separate evidence records the final JAR and
cache hashes; the paired save measurements above remain historical and unchanged.

This is development validation of the shared persistence fix, not replacement
release qualification. Keep the previous six qualified JARs and evidence intact;
rebuild and run the full release suite on all supported runtime cells before
publishing the changed 1.4 files. No public upload was performed.
