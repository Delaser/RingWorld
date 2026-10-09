# Bounded Atlas pregeneration concurrency (#253)

This development trial feeds several independent FULL chunk requests into
Minecraft's existing generation pipeline. It does not create a replacement
worldgen executor, assign cores, or read live world data on worker threads.
The user authorized implementation on 9 October 2026.

## Policy and controls

`-Dringworld.atlasInFlightChunks=4` opts into four outstanding requests. The
accepted trial range is 1–8; 1 retains the serial baseline. The normal default
remains one until runtime evidence and ordinary-play impact are reviewed.
This is an operational JVM property, not a saved world-generation option.
Read it when creating the world-owned job. Background, interactive and headless
callers use the same execution policy, so UI/commands do not start competing
writers. Changing it requires restarting with no retained old job.

The existing 64-pending-task threshold is checked before every new request,
including between starts in one tick. Capacity is an upper bound, not a
promise of that many active requests or cores. Player work and the vanilla
chunk dependency graph can reduce it. No vanilla worker-thread limit changes.

One shared X-major canonical cursor feeds bounded slots. Each slot retains its
selected chunk, future, processed marker, retry count/backoff and loading ticket
until safely captured/released. Completion order can differ from selection
order. A rotating ready-slot search prevents both head-of-line blocking and
starvation. The explicit pregeneration consumer captures **at most one ready
chunk per tick**, preserving its serial capture budget. Existing normal
player-loaded chunk callbacks and bounded dirty-cell recapture remain separate.
Atlas/world reads, capture, tile/revision changes and saves stay server-thread
owned. The existing 200-tick checkpoint cadence is unchanged.

## Lifecycle and correctness

Pause prevents new requests while already issued work can drain. Resume fills
free slots under the same queue limit. Cancellation and terminal failure never
resolve discarded chunk results; all leases are attempted even if a release
fails. A failed release remains reachable for tick/unload retry and blocks job
replacement. Teardown retries every retained lease up to the existing three
attempts and fails closed if any cannot be released.

A failed chunk keeps its own selection and retry/backoff; other slots may
progress. The Atlas remains the durable journal. On restart, earlier holes are
retried while out-of-order completed chunks are skipped. Cursor exhaustion
cannot publish a partial Atlas as COMPLETE. Complete Atlas data alone is not
enough: every outstanding ticket must drain, then the saved Atlas must reopen
with complete coverage and matching revision before the completion future is
published.

No cache-format, seed, geometry, generation-settings, topology or network-schema
change is introduced. The current shared cache format remains 11 from #257.
The published 1.3 large server is unchanged; it is not an experiment target.

## Development evidence

Pure tests cover distinct shared selections, independent retries, out-of-order
readiness, fair draining, cancellation without resolving results, multiple
release failures retained while other tickets close, bounded admission and
resume with holes beneath later completed chunks. Existing ticket/selection,
replacement, lifecycle and persistence contracts remain in the suite.

The opt-in headless fixture `-Dringworld.testAtlasConcurrency=true` waits for
multiple live requests, pauses and drains them, observes a no-request pause
barrier, resumes, cancels with multiple requests, verifies cancellation and no
retained leases, then restarts the same journal to verified completion. It is
inactive during normal play. A dedicated run interrupted through normal RCON
`stop` must record INTERRUPTED; the completion-only Gradle finalizer is expected
to reject that partial run. Resume and another complete-cache reopen must pass.
These expected interruptions are not mislabeled complete runs.

`-Dringworld.measureAtlasConcurrency=true` adds optional headless result metrics:
process CPU time divided by wall time (1.0 means one busy logical core), peak
used Java heap, peak active requests/pending tasks and observed server tick
mean/p95/p99/max/over-50ms counts. Tick durations come from the previous completed
Minecraft tick. It excludes zero startup samples and is not a client FPS
measurement; heap is not process RSS. CPU measurement includes initial world
startup and final saves. The headless result's generation elapsed time is a
separate interval. Native benchmarks use identical seed 25320261009,
2048×128×160 geometry, no players, view/simulation distance 2 and isolated
loopback-only servers. Fresh runs compare 1/2/4/8 requests twice in reversed
order. Results cannot establish multiplayer latency or long-session behaviour.

Build, native, independent gzip/region coverage checks and performance results
are retained under ignored `logs/atlas-concurrency-253/`. Final tables are
pending the six-cell native matrix and matched benchmarks. Development checks
are not full frozen-candidate release qualification. Do not merge or publish
this trial until its measured recommendation and remaining limits are reviewed.
