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

No production fix from this investigation has been implemented or qualified.
Any resulting shared change must pass all supported 26.x versions and both
loaders; the held release JARs still require fresh full qualification.

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
