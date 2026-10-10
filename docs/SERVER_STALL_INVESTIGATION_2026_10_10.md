# Remaining Large-server stalls — 10 October 2026

The remaining stalls have more than one cause. A second three-client run reproduced
1,265 ms and 1,006 ms P99 windows without the heap-inspection collections caused by
the first profiler. The transition recording shows chunk unloading/snapshotting,
ticket-graph updates and real GC pauses. Player-loaded Atlas sampling adds work,
but the evidence does not attribute the whole stall to the Atlas.

## Method and results

Reused the isolated pregenerated Large-world copy on the production host, with
the same `9f6c522f` diagnostic JAR/source `adca86c`, NeoForge 26.3.0.37-beta,
four logical CPUs and unchanged 1–4 GiB G1 heap. Three real clients were hidden,
muted, capped at 15 FPS and used view distance 12. The public server was empty,
stopped during testing, and restored afterward with authentication unchanged.

A direct startup JFR recording used `profile.jfc` with `jdk.ObjectCount` and
`jdk.OldObjectSample` disabled, and 20 ms execution sampling. GC/safepoint logs,
Atlas timing logs, host `vmstat` and timestamped RCON windows were retained.
No production code or heap tuning changed during this run.

After a 120-second joining period, three spectators moved to widely separated
outside-wall positions, held for 45 seconds, then became creative players at
three in-band positions for 60 seconds. Both transitions were repeated. Each
transition moves the players about 3,808 blocks along X and across the wall;
this is a deliberate simultaneous chunk-load/unload stress, not ordinary walking.
The existing test world was reused, not reset to the earlier baseline.

| Phase | Mean reported tick, ms | Median window P95, ms | Worst window P99, ms |
|---|---:|---:|---:|
| Joining, including client startup | 18.9 | 17.3 | 434.2 |
| Outside wall, first hold | 25.9 | 41.1 | 163.6 |
| In-band, first transition/hold | 32.5 | 49.9 | 550.6 |
| Outside wall, repeated transition/hold | 26.3 | 32.2 | 1,264.7 |
| In-band, repeated transition/hold | 33.0 | 45.6 | 1,005.9 |

These are summaries of `tick query`'s 100-tick windows, not pooled percentiles,
exact maximum ticks or an A/B improvement measurement. Joining includes time
before all clients connect. There were three overload warnings and no server
thread ERROR lines. Packet loss and representative single-client FPS were not measured.

## Findings

**The first profiler introduced real pauses.** Its before recording forced two
`Heap Inspection Initiated GC` full collections totalling 1,047 ms; its fixed
recording forced two totalling 1,333 ms (719 and 613 ms). Those recordings cannot
establish hitch-free behaviour. The aggregate Minecraft JFR `gcTotalDurationMs`
also includes concurrent collection duration; use `sumOfPauses`/`GCPhasePause`
for stop-the-world time. The new recording contains zero heap-inspection GCs.

**The new long transition window contains a burst of normal chunk lifecycle work.**
At 18:40:36.9–37.3 UTC, nine server-thread samples are in
`ChunkMap.processUnloads → save → SerializableChunkData.copyOf`. At
18:40:37.3–38.14 UTC, 26 samples are in `runDistanceManagerUpdates`, including
loading-ticket graph propagation and holder/future updates. GC then pauses for
269 ms and 120 ms near 18:40:38. These events fall in the measured 1,265 ms
transition window. There are no checkpoint-writer execution samples in that
window. Sampling/window summaries do not reconstruct exact tick boundaries or
prove which one operation accounts for the entire reported P99.

**GC is a genuine remaining contributor.** The largest recorded collection
pause is 391 ms (about 395 ms including safepoint coordination). In the
18:38:40–18:42:32 UTC travel/hold interval, collections account for 10.67 seconds
of stop-the-world time. Large sampled allocation sites are vanilla chunk palette
re-encoding for saves and copying the skylight/block-light section maps. They
dominate the sampled allocation ranking; Atlas tile encoding is a smaller
contributor. Allocation samples are weighted estimates, not exact byte counters.

**Player-loaded Atlas captures bypass the pregeneration budget.**
`captureLoadedChunk` samples the entire chunk immediately in the load callback;
it does not use `RingAtlasCaptureBudget`. In the same travel interval it appears
in 497 of 7,221 server-thread execution/native samples (6.9%). This confirms a
contributor, not the dominant explanation for the longest transition. Its
`surfaceColor` also obtains `world.getBiome` before checking whether the material
needs a biome tint, so stone and other untinted side samples pay that lookup too.
The recorded call path reaches `Level.getChunk`; this run does not prove those
particular lookups blocked on newly generated neighbours.

**The asynchronous checkpoint remains bounded work, with soft-budget outliers.**
It appears in 188 of 7,221 travel samples (2.6%). Disk serialization is on its
worker, not restored to the tick. However, the largest logged copy-step wall
time is 106 ms under load. The 2 ms budget is soft and includes GC/preemption;
the earlier roughly 8 ms observation was an early result, not the full-run maximum.
Do not claim this cost has disappeared or attribute the entire 106 ms to copying.

**Initial tile streaming has a small, concrete allocation inefficiency.**
`encodeTile` reserves 3,072 bytes for a full tile's cells, then also writes a
two-byte header. Every full tile therefore grows that buffer before creating the
outgoing byte array. Reserving the header too preserves the exact wire format
and avoids one growth allocation/copy per full tile. Tile encoding appears in
251 of 7,221 travel samples (3.5%); it is not the main second-long stall.

Normal spawning and entity ticks also occupy substantial server-thread time
(1,363 and 1,160 inclusive samples respectively). These categories overlap with
other stacks and exclude time stopped by GC; they are not additive CPU percentages.

## Recommended implementation order

1. Put player-load Atlas captures through a coalescing owner-thread queue and
   the existing small capture/time budget. Do not hold unloaded chunk references
   or fetch neighbours to drain it. Preserve current edits, light updates,
   pregeneration completion and unload/stop checkpoint semantics.
2. Request biomes only for tinted materials; include the tile header in its
   buffer reservation. Keep colours and packet bytes identical. These are small
   shared changes, not a new renderer, protocol or thread pool.
3. Run matched travel/teleport tests with chunk/ticket/unload and allocation
   telemetry. Evaluate a bounded chunk-retention grace period against its memory
   cost before changing unload policy. Do not move live chunk/world access to a
   worker or disable chunk persistence to hide the spikes.
4. Recheck GC after reducing churn, then compare heap configuration separately
   if necessary. Do not change heap, retention and Atlas scheduling together and
   call the combined result proof of one fix.

The owner approved this route. Shared queued player captures, tint-only biome
lookups and the tile-header reservation are implemented on PR #279. Development
validation and the repeat three-client test are complete below. The held release JARs
still require fresh full qualification; chunk-retention and heap policy have not
changed. The findings above describe the pre-fix recording.

## Approved queue fix — follow-up results

Player-load callbacks now enqueue coalesced canonical coordinates. The owner
thread samples still-loaded chunks with the shared 4/2/1 adaptive capture policy
and soft 2 ms budget, reserving the first slot for player work. Ready pregen
results already sampled by that queue validate their identity/coverage and
release their tickets without a duplicate sample. Stop/unload freezes admission,
drains still-resident queued chunks and flushes the checkpoint. No chunk
references, extra thread pool, neighbour loads or disk-format changes were added.
Untinted materials skip the biome lookup, and tile encoding reserves its header.

The repeat used diagnostic JAR `e564df76`, the same NeoForge .37 host and Java
heap, the existing Large-world copy and the same three-client script. A failed
client launch referenced stale compiled output; it never reached multiplayer
and is excluded. The corrected clients use the tested isolated output. Profiler
heap-inspection events remain disabled. Results are mixed:

| Phase | Before mean / worst P99, ms | After mean / worst P99, ms |
|---|---:|---:|
| Outside, first | 25.9 / 163.6 | 27.7 / 220.8 |
| In-band, first | 32.5 / 550.6 | 36.3 / 1,183.1 |
| Outside, repeat | 26.3 / 1,264.7 | 28.3 / 395.5 |
| In-band, repeat | 33.0 / 1,005.9 | 35.3 / 1,228.1 |

These are sequential same-workload trials on a reused world, not a reset-world
A/B. Entity state, cache state and workload timing vary. All three clients joined
at 90.5 seconds in this run versus 65.3 seconds before; the fixed 120-second
joining phase therefore gave them about 30 versus 55 seconds to settle. Local
lifecycle fixtures were also running during this run's client startup. A controlled
future comparison must use a client-ready barrier plus the same post-join settle
interval and separate local workloads. They establish **no
overall tick-time improvement** and do not qualify a release.

The four-chunk ceiling is observed; automatic targets of 4, 2 and 1 are logged.
The queue reaches 4,226 coordinates; the last timing window before shutdown
still contains 1,446. Atlas refreshes can lag behind delivered terrain during
bursts. Queued coordinates retain no live chunks and shutdown drains only those
still resident. Maximum measured capture-step wall time is 15.6 ms; the budget
is soft. Capture occupies 104 of 7,093 travel execution samples (1.5%), versus
497 of 7,221 (6.9%) before. This lower share includes delayed work and the tint
lookup change; it is not a pure CPU-time speedup measurement.

The largest observed completed tick is 1,228 ms. The approximate surrounding
recording window contains 19 unload samples, including 15 chunk-save snapshot
samples, and three GC pauses totalling about 265 ms. The other 1,183 ms spike
also surrounds chunk unloading/snapshotting and GC. Neither window samples
Atlas capture or the checkpoint writer. Slow-tick logs observe the previous
completed tick from a later world hook; these are approximate correlations, not
exact operation/tick boundaries. Travel GC pause time is 10.85 seconds, close to
the earlier 10.67 seconds; the largest individual pause is 304 ms. There are two
overload warnings, zero server-thread errors and zero heap-inspection GCs.

Reviewing the actual .37 Minecraft sources confirms `ChunkMap.processUnloads`
forces its unload queue down toward 2,000 entries even when its normal time
supplier expires. Its save path still snapshots mutable chunks on the server
thread before worker encoding/writes. The recording lacks unload-queue sizes,
so it cannot establish whether the forced-drain branch fired. Allocation samples remain dominated by
chunk/light-map cloning and palette re-encoding. A retention grace period could
increase this four-GiB server's memory pressure, so it was **not enabled**.
The next experiment should instrument unload-queue lengths and per-tick
snapshot counts, then isolate the unload/save burst with bounded memory and
persistence safeguards. A short retention grace period cannot reuse chunks
across this test's widely separated teleports and 45–60-second holds; retaining
both regions that long would raise memory cost. Heap tuning and changing
vanilla unload scheduling are separate experiments, not included in this fix.

All six source/loader builds and Java suites pass: 499 cases per loader on
26.1/26.2 and 502 on 26.3, totalling 3,000 executions. Local static qualification
runs 367 Python tests successfully, and all nine CI checks pass on source
`6d40faa`. All 24 native fresh/interrupted/resumed/reopened phases pass, including
pause/drain/cancel/restart, live rate override and local FPS 4→2→1→2→4 probes
in every fresh runtime. Independently decoded format-11 saved files match
reported partial/complete cell counts and hashes. Completed reopens take zero
generation time and preserve saved bytes. These are development source runtimes
on the three ABIs, not frozen-JAR qualification across all ten runtime cells.
Fresh full qualification of replacements remains held while these transition
stalls are unresolved. The public server is verified active again with zero
players, 0.2 ms mean ticks and its complete 16,777,216-cell Atlas; all test clients,
tunnel, isolated service and restoration timer are stopped. Its previous
diagnostic build remains installed; the queue trial was isolated.
See [checked-in follow-up evidence](evidence/atlas-player-capture-2026-10-10.json).
Raw captures, logs, reproduction scripts and decoded events are retained under
`logs/atlas-player-capture/`; the earlier recording is unchanged.

## Evidence and recovery

- [Checked-in summary](evidence/server-stalls-2026-10-10.json).
- Raw recording, compact decoded events, GC log, phase JSON, host counters,
  client/server logs and controller: `logs/server-stall-investigation/`.
- The reproduction scripts/JFC are retained there; diagnostic flags were applied
  only to the isolated service. The prior multiplayer recordings are unchanged.
- All three clients and the SSH tunnel stopped. The public service is active
  with its original world and previous diagnostic build; the test service and
  restoration timer are stopped. The final empty-server check averaged 0.9 ms
  per tick. No public release upload occurred.
