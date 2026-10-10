# Remaining Large-server stalls — 10 October 2026

## Follow-up implementation: ordinary chunk work

The approved fix adds owner-thread work limits separately from Atlas persistence:

- Scheduled Overworld autosaves queue one array of visible chunk coordinates.
  Ordinary ticks revisit current holders, clear their save cooldown and use
  vanilla's dirty-chunk save path. The sweep examines at most 128 coordinates
  and attempts at most four saves per tick, sharing the cleanup time allowance with
  unloading/eager saving. A subsequent autosave requests another sweep rather
  than retaining another holder graph or an unbounded queue of snapshots.
- Ordinary unload/eager work normally shares a 256-task/scan and 16-save-attempt
  ceiling within a soft 2 ms allowance. Backlog above 2,000 or used/max heap at
  80% selects 8 ms / 4,096 / 64; backlog at least 8,192 or heap at 90% selects
  20 ms / 16,384 / 128. Vanilla's 128-active-write backpressure is retained.
  These are cleanup allowances, not changes to heap size or pregeneration rate.
  The mandatory
  `queueSize - 2000` bypass is disabled for this ordinary path. One indivisible
  operation can exceed the time allowance; one operation is allowed to ensure
  progress even when a tick's vanilla deadline has already expired.
- The optional location advancement check is deferred once after its observed
  player chunk changes, then retried on the next vanilla invocation even if
  movement continues. The maximum additional delay is one normal 20-tick
  interval; checks cannot starve during continuous travel. This avoids an
  immediate optional structure query forcing the whole ticket graph to settle.
- Ticket updates remain complete vanilla operations. Required chunk lookups,
  future admission, explicit saves, flushes and shutdown retain their normal
  progress and durability. Scheduled-save scopes restore their state with
  `finally`; ordinary maintenance counters reset each tick, including after an
  interrupted tick. No worker reads live chunks or level state.

The maintenance allowance does **not** bound ticket propagation, holder-future
publication, player/level metadata serialization, an individual snapshot, or
GC. Delayed unloading can temporarily retain more holders. These are explicit
tradeoffs to validate, not a guarantee of hitch-free play.
`-Dringworld.chunkWorkTimings=true` enables per-tick maintenance counters for
development measurements and is off by default.

### Failed development run retained

The first three-client test of `eeb6b33` failed during its first return transition.
A ticking vault's neighbour query found a holder whose ticket level was updated
but whose future was not published; vanilla's `chunkAbsent` shortcut skipped
propagation and returned an unloaded chunk. The server crashed. Its shutdown
drain also needed to bypass the tick allowance when the normal tick counter
stopped advancing. This is a **failed run**, excluded from performance claims.
The corrected required-lookup guard and save/stop escape are in `53d466f`.
The subsequent `53d466f` run survived both movement cycles, completed its
save sweep and preserved a newly edited gold block after an explicit flush and
restart (the on-disk baseline was a diamond block). Tick 6000 fell to 110.00 ms,
but four genuine full G1 compactions appeared, with pauses up to 1,003 ms;
the earlier baseline had no full compactions. This candidate is rejected as an
overall performance result. Splitting ticket processing across ticks was
removed rather than accepting its new consistency/retention risks. Attribution
of the additional allocation/retention pressure requires the safe-policy repeat;
the observation alone does not isolate its source from the unload changes.

That run's restart harness also expected the incorrect English response
`The time is`; Minecraft returned `The game time is … tick(s)` for the successful
conditional gold-block query. A separate recorded read-back verified the gold
block. The final harness checks the actual response, fails on any client
loss, and explicitly establishes the saved diamond baseline before changing it.

Raw records remain under `logs/chunk-stall-fix/`; rejected candidates are kept
separately. The public server was restored between runs. Temporary tick/graph
measurement mixins are not production changes or release JARs.

Removing the graph limiter alone (`acf7b54`) did not resolve the regression:
the fixed 2 ms unload policy also reproduced multi-second GC stalls. This run
was stopped during the post-movement waiting phase; normal shutdown/restart
preserved the newly edited gold block, but its interrupted controller is not
a completed performance/explicit-flush PASS. Its records are retained under
`logs/chunk-stall-fix/rejected-small-unload-budget/`.

Allocation samples over the first outside/return cycle estimate similar total
allocation rates (376.5 versus 382.8 MiB/s), but the two leading sky/block light
map clone stacks rise from about 13.0 GiB to 23.9 GiB over approximately two
minutes. Palette-encoding allocations fall. These are sample-weight estimates,
not measured retained heap or proof that an individual allocation caused a
particular collection. They show why total allocation rate alone hid the changed
allocation mix. The selected follow-up is a bounded catch-up allowance for
cleanup backlogs, rather than indefinitely retaining unload work to honour a
tiny deadline. It must be validated against the same workload before acceptance.

The first restored-snapshot run is also excluded from performance comparisons:
the private server forced Survival on login while the saved test players were
over the void. They died during settling but remained in the connected-player
list. Its quiet 75 ms autosave result does not represent the intended workload.
The corrected harness forces Spectator on login, checks all three players have
20 health in every sampling window, records their positions, and uses the same
pristine world fingerprint before each comparison. An independent region-file
reader checks the edited gold block before issuing an explicit save command.

### Matched Large-server result — 11 October

Functional baseline `6d40faa` and candidate `cf3c5b6` each start from the exact
same 456-file / 981,494,919-byte world snapshot (`90b4d5fe…`). Its Large ring is
32,768 × 512 blocks and its Atlas has all 16,777,216 cells. Both use NeoForge
26.3.0.37-beta, four logical server CPUs, the unchanged 4 GiB maximum heap,
view distance 28/simulation distance 8, and three real hidden/muted clients.
The health-checked workload is 60 seconds settling, two 45-second side/60-second
centre cycles, then 120 seconds waiting for ordinary saving. No local builds or
native tests compete with those clients during the measurement.

| Measurement | Before | Candidate |
| --- | ---: | ---: |
| Scheduled autosave tick 6000 | 386.61 ms | 105.81 ms |
| Worst GC-free movement tick | 1,028.93 ms | 177.42 ms |
| Worst tick in the ordinary measured window, including GC | 1,939.22 ms | 796.01 ms |
| First return, worst sampled 100-tick-window P99 | 1,939.2 ms | 203.3 ms |
| Second return, worst sampled 100-tick-window P99 | 1,051.7 ms | 204.2 ms |
| Largest individual GC pause | 270.27 ms | 681.90 ms |
| Full G1 compactions in ordinary window | 0 | 1 |
| Edit on disk before explicit flush; read back after restart | PASS | PASS |

The baseline's 1,029/663 ms GC-free movement ticks have 43/28 server samples in
the synchronous ticket graph beneath the location advancement's structure
query. Autosave has 13 server samples, including nine in chunk snapshot copying.
The candidate autosave has no chunk-copy sample, and its 6,553-coordinate sweep
finishes at tick 6,775 (774 maintenance ticks after it starts). Ordinary cleanup
observes the configured ceilings: normal 256 tasks/16 save attempts, catch-up
3,605/64, pressure 4,086/128. Task counts include scans and callbacks, not unique
chunks. The soft time ceiling can be exceeded by indivisible work or a GC pause.

**The targeted bursts improve, but this is not overall release acceptance.**
The candidate run contains one 682 ms full compaction in a pair whose baseline
has none; its worst tick contains about 755 ms of accumulated GC pause overlap.
Consequently this candidate stays in development and PR #279 remains open.
Matched-window allocation samples estimate 244.7 versus 253.7 MiB/s overall.
The two leading sky/block light-map clone stacks rise from 21.2 to 28.3 GiB of
sample weight, while palette packing falls slightly (14.8 to 14.3 GiB). These
estimates identify a changed allocation mix, not retained heap or proof of why
this particular full collection occurred. See
[`evidence/server-chunk-stall-allocations-2026-10-11.json`](evidence/server-chunk-stall-allocations-2026-10-11.json). Do not declare the GC regression fixed or promote the
candidate solely on its lower tick-window numbers. Required ticket operations
also remain complete and can still exceed a tick budget.

This is one paired development experiment, not a repeated statistical result
or frozen-JAR release qualification. Baseline diagnostic ticks have a 50 ms
threshold; candidate diagnostics record every tick plus graph spans. Do not
compare pooled tick percentiles or graph-span counts across those instruments.
Baseline phase samples omit absolute timestamps, so its travel-end boundary
uses the controller file modification time; precise tick/operation events and
the normal measurement window retain UTC timestamps. Explicit flush, client
shutdown and restart are outside the ordinary performance window.

Both runs establish a saved diamond block, change it to gold, inspect the exact
on-disk region position before issuing a new save command, complete explicit
flush/stop, and verify gold after restart. The public server is restored to its
prior JAR; the development candidate is not promoted or uploaded. Raw traces,
GC logs, phase results and hashes remain under `logs/chunk-stall-fix/`.
Compact measurements and identities are banked in
[`evidence/server-chunk-stall-fix-2026-10-11.json`](evidence/server-chunk-stall-fix-2026-10-11.json).
Temporary measurement mixins are excluded from production source; their patch is
[`evidence/server-chunk-stall-measurement-probes.patch`](evidence/server-chunk-stall-measurement-probes.patch).

All six builds/Java suites pass (503 cases per loader on 26.1/26.2, 506 on 26.3;
3,024 executions), together with the nine source CI checks. All 367 local static
checks also pass on the final cleanup-pressure tree. All 24 corrected native fresh/interrupted/resumed/reopened lifecycle phases
pass across the six source/loader runtimes. Independent format/count audits pass
for every saved file, every completed reopen matches its preceding resumed file
byte-for-byte with zero generation time, and all six fresh command/concurrency/
synthetic-FPS probes pass. See the [validation record](evidence/server-chunk-stall-validation-2026-10-11.json).
The old release files and full replacement qualification remain on hold.

## Confirmed causes from precise tick boundaries

A third three-client run identifies the remaining large hitches as **bursts of
normal chunk persistence and ticket processing**, rather than another synchronous
Atlas-file write. The clearest periodic cause is Minecraft's five-minute
**non-flushing autosave**: it still scans every visible chunk and snapshots dirty
chunk contents on the server thread. Asynchronous encoding/disk writes do not
make that preparation asynchronous or bounded.

The new measurement-only JAR (`fd00a979`, functional source `6d40faa`) adds JFR
boundaries around `tickServer`, `processUnloads` and `SerializableChunkData.copyOf`.
It also counts the actual unload queue, mandatory drainage, executed callbacks,
and snapshots. No scheduling or save behaviour changed. The temporary mixins
were removed after measurement; the [reproduction patch](evidence/server-stall-cause-probes.patch)
and [compact trace evidence](evidence/server-stall-causes-2026-10-10.json) are retained.
For reproduction, apply that patch to the recorded source and enable
`-Dringworld.stallCause=true` with startup JFR; keep heap-inspection events disabled.
Raw recording, decoder, controller, phase JSON and build log are retained locally
under `logs/stall-cause/`. The diagnostic build passes all 502 NeoForge 26.3 Java
cases. It is not a staged release JAR.

All three hidden/muted clients joined before a fixed 60-second settling period.
The same outside/in-band transitions followed, with no overlapping local builds
or lifecycle fixtures. The private Large-world copy, 4 GiB maximum heap and
NeoForge 26.3.0.37-beta were retained. This is diagnosis on an evolving world,
not a reset A/B performance comparison or replacement release qualification.

| Exact tick | Duration | Confirmed work |
|---|---:|---|
| 6000, 20:43:44.193 UTC | 1,018.73 ms | Five-minute autosave: 20 of 21 server execution samples in `autoSave`; 12 include `SerializableChunkData.copyOf`. No recorded unload span overlaps this tick. GC pauses overlap 221.58 ms. |
| 6140, 20:43:51.821 UTC | 619.90 ms | Immediately after the return transition: 21 execution/native samples in ticket/distance updates. An advancement structure lookup synchronously requests a chunk and pumps the ticket graph. Unload processing occupies only 7.03 ms; no GC pause overlaps. |
| 6141, 20:43:52.587 UTC | 218.72 ms | The next tick drains 12,600 callbacks from a queue of 14,600. `processUnloads` occupies 178.07 ms and prepares 796 chunk snapshots, taking 66.30 ms in aggregate. No GC pause overlaps. |

These durations come from JFR operation boundaries, not `tick query` window
percentiles or log timestamps. Execution samples remain sampled evidence, not a
complete CPU-time decomposition. GC time is already included in operation wall
time and must not be added to it again.

### 1. Periodic autosave performs an unbounded main-thread pass

The observed stack is:

```text
MinecraftServer.tickServer → autoSave → saveEverything
  → saveAllChunks → ServerLevel.save → ServerChunkCache.save
    → ChunkMap.saveAllChunks(false) → saveChunkIfNeeded → save
      → SerializableChunkData.copyOf
```

In the inspected 26.3.0.37-beta source, `computeNextAutosaveInterval` selects
300 seconds, or 6,000 ticks at 20 TPS. `autoSave` invokes
`saveEverything(true, false, false)`. The non-flushing branch clears
`nextChunkSaveTime` and calls `saveChunkIfNeeded` for **every** holder in
`visibleChunkMap`, without the ordinary tick deadline or a snapshot-count cap.
`save` copies the live chunk's sections, palettes, light arrays, heightmaps,
block-entity data and ticks before submitting NBT encoding to the background
executor. Level saved-data and world metadata preparation also occur in the
same save call; not all of its time is chunk copying.

Tick 6000 is the largest tick in this recording. Autosave appears in 20 of its
21 server execution samples, spanning 20:43:44.296–20:43:45.141 UTC. The 62
individual snapshot events exceeding the 1 ms recording threshold are all
outside `processUnloads`; their inclusive durations total 326.38 ms. That is a
thresholded subset, **not** the total snapshot count or total snapshot time.
This establishes the periodic autosave path as the large recurring hitch in
this reproduction. The Atlas capture queue cannot budget this vanilla pass.

### 2. A synchronous structure lookup pumps an unbounded ticket update

Tick 6140's sampled caller chain is:

```text
ServerPlayer.doTick → PlayerTrigger.trigger → LocationPredicate.matches
  → StructureManager.getStructureWithPieceAt → fillStartsForStructure
    → LevelReader.getChunk → ServerChunkCache.getChunk
      → managedBlock → MainThreadExecutor.pollTask
        → runDistanceManagerUpdates → DistanceManager.runAllUpdates
```

The active stack is loading-ticket graph propagation, including RingWorld's
periodic-neighbour context, after three large simultaneous position changes.
Vanilla calls `loadingChunkTracker.runDistanceUpdates(Integer.MAX_VALUE)` and
updates the affected holders/futures in the same call. Therefore a synchronous
chunk-dependent query can pull a substantial outstanding ticket transition into
the current tick. This is real server work, not a GC-only pause: there is no
GC pause in this 619.90 ms tick.

The recording does not establish that RingWorld's wrapping alone causes the
cost or that vanilla would take the same time on a non-ring world. The clear
trigger and path are the structure query and full ticket-graph update. Do not
blindly cap that graph call: a caller synchronously waiting for a chunk must
still be able to make progress.

### 3. Vanilla deliberately bypasses its unload deadline above 2,000 callbacks

The previously suspected queue condition is now observed directly. Vanilla uses:

```java
int minimum = Math.max(0, unloadQueue.size() - 2000);
while ((minimum > 0 || haveTime.getAsBoolean()) && (task = unloadQueue.poll()) != null) {
    minimum--;
    task.run();
}
```

At tick 6141, the queue contains 14,600 callbacks and the mandatory minimum is
12,600; exactly 12,600 run in that call. Only 796 chunk snapshots are prepared:
callbacks can be stale or reschedule around save futures, so callbacks must not
be reported as distinct chunks. The measured 178.07 ms pass exceeds a tick's
50 ms allowance even with no GC pause. `processUnloads` also includes ordinary
eager saves; the aggregate snapshot counter covers both paths.

### GC amplifies some bursts but does not explain all of them

Tick 5843 lasts 249.59 ms; an eager-save pass lasts 223.67 ms and overlaps
221.55 ms of GC, despite having zero unload callbacks and no forced drainage.
A slow snapshot wall time can therefore mostly be a collection pause, not an
intrinsically expensive copy. Conversely, ticks 6140/6141 demonstrate substantial
work without GC. Earlier allocation samples locate large whole-map light-data
clones in worker-side `LayerLightSectionStorage.swapSectionMap`, plus palette
re-encoding for chunk saves. Weighted allocation samples do not prove which
allocation caused a particular collection.

The largest unload pass in the entire recording (266.72 ms) occurs after client
disconnection and overlaps 232.96 ms of GC. It is retained in the evidence but
is not used as a normal-play transition result.

### Consequence for the next fix

Address the autosave's main-thread preparation separately from the already-fixed
Atlas checkpoint writer. A safe implementation would spread dirty-chunk snapshot
preparation over ticks while preserving durability, dirty-state acknowledgement,
explicit `save-all`/flush and stop semantics; live chunks still cannot be read by
workers. Ticket-transition churn and the forced-unload backlog need their own
bounded policy and correctness checks. Adding Atlas workers, increasing its
capture rate or merely raising the heap will not bound either vanilla path.

The public server was restored with its prior JAR and authentication settings;
all test clients and the SSH tunnel were stopped. No production behaviour change
or new release qualification is claimed. The first diagnostic attempt failed
before clients joined because of a stale local classes path; its logs are retained
separately and excluded. Packet loss remains unmeasured. Exact causes of every
older window cannot be reconstructed retrospectively: the earlier approximate
unload correlations must not be treated as exact attribution.

## Earlier recordings — historical context

The remaining stalls have more than one cause. A second three-client run reproduced
1,265 ms and 1,006 ms P99 windows without the heap-inspection collections caused by
the first profiler. It showed chunk unloading/snapshotting, ticket-graph updates
and real GC pauses, but lacked the precise tick boundaries used above.

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

## Native harness correction for this candidate

The first 26.3 Fabric reopen assertion compared the completed resumed cache
with a previous fresh rebuild, rather than with the file immediately before
reopen. The server reported COMPLETE with zero generation time; independent
read-back showed reopened and resumed files were byte-identical. Fresh and
resumed captures differ in 28,196 of 524,288 cells in this ticking terrain
fixture, including height/material/light fields; this is not evidence of a
reopen mutation. Exact fresh-versus-resumed feature parity is not claimed.
The corrected assertion compares reopen against resumed, while retaining the
independent present-cell and format audits for every phase. The failed assertion
and snapshots remain preserved; the affected reopen is rerun before continuing.
