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
