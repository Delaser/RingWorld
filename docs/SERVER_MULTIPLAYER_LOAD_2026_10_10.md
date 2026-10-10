# Large ring multiplayer server trial — 10 October 2026

Three real Minecraft clients connected to an isolated copy of the pregenerated
Large world on the production host. The trial exposed a completed-Atlas status
scan that did not occur with no players. A small shared fix removed that scan;
tick latency improved, but chunk-loading/transition stalls remain.

## Setup and method

- Minecraft 26.3, NeoForge 26.3.0.37-beta; four logical CPUs, approximately 8 GiB
  host RAM, the same 4 GiB Java heap and G1 settings as production.
- Large: 32,768 circumference × 512 width, saved wall height 160. The canonical
  ring has 65,536 pregenerated chunks and a complete 16,777,216-cell Atlas.
- Public service stopped during testing. Each run started from a separate copy
  of the same stopped public world. Offline identities were confined to a
  loopback-bound test server reached through SSH; public authentication stayed on.
- Three actual NeoForge clients, separate processes, muted and hidden, 640×360,
  capped at 15 FPS, client view distance 12, 1.5 GiB heap each. Server view distance
  28 and simulation distance 8. These are server measurements, not client FPS results.
- Idle 60 seconds; joining/streaming 150 seconds; walking-pace travel 120 seconds;
  fast travel 120 seconds; stationary 120 seconds. The fixed run also has a
  60-second in-band creative-player/mob-simulation check.
- The scripted travel uses server teleports once a second to move three spectator
  players in widely separated areas. It stresses real chunk/Atlas transport but
  does not reproduce human movement, combat or a survival session.
- Travel/paired stationary cases are at Z=256, just outside the side wall. Those
  views include off-band chunks outside canonical pregeneration. The final extra
  check is inside the terrain band at Z=0, with creative players allowing normal
  spawning. It has no paired pre-fix baseline.
- RCON samples `tick query` every roughly five seconds. Average is the mean of
  reported 100-tick window means. P95 is the median of window P95 values; the P99
  column is the worst reported window P99. They are not pooled percentiles or
  individual maximum-tick measurements. Fixed-run game-time deltas also measure TPS.
- Runs are sequential, not randomized repetitions. OS cache, JIT, entity evolution
  and timing differ. Baseline clients were spread earlier during the join window;
  joining averages are therefore not a controlled paired benchmark.

## Results

| Three-client condition | Before avg tick, ms | After avg tick, ms | Median window P95, ms | Worst window P99, ms |
|---|---:|---:|---:|---:|
| Idle | 0.2 | 0.6 | 0.3 → 0.8 | 8.7 → 16.1 |
| Walking pace (5 blocks/s) | 39.0 | 23.2 | 296.6 → 41.7 | 455.5 → 268.7 |
| Fast travel (32 blocks/s) | 32.9 | 22.4 | 286.3 → 45.3 | 506.3 → 273.7 |
| Stationary after travel | 33.4 | 24.6 | 293.5 → 36.6 | 462.4 → 451.6 |

Fixed-run walking/fast/stationary phases measured approximately 20 TPS. Walking
average tick time fell by 40.4%, fast travel by
31.9%, and stationary by
26.3% in this trial. CPU use did not
uniformly decrease: walking averaged 237% →
281% (100% is one logical CPU). Do not claim a general CPU or throughput gain
from the latency improvement. Peak server RSS reached 4.63 GiB;
server process swap stayed at zero. This is a short trial, not a memory-leak or soak test.

The baseline logged four overload warnings. The fixed run logged two during the
shared phases and a third during the extra in-band transition. The extra check
averaged 32.8 ms, with 19.22 measured TPS over the
transition. Its first loading window reported **1,135.3 ms P99**; subsequent
windows were lower, but this still needs investigation. Do not describe the
server as hitch-free or fully qualified for launch.

All three clients stayed connected until planned shutdown, with no server ERROR
lines in either test log. All clients reached complete Atlas rendering. The fresh
Large transfer took about seven minutes at the existing eight-tiles/client/tick
limit; this was streaming an already generated world, not regenerating it.

## Diagnosis and fix

The pre-fix server-thread profile contains 1,307 of 2,929 execution samples
(44.6%) in:

`publishObservedStatus → sendPregenerationStatus → presentChunkCount → isChunkPresent → hasCell`

Each player status message rescanned the Large Atlas despite its maintained
present-cell count already proving completion. `presentChunkCount()` now returns
the exact geometry-derived total immediately when `isComplete()` is true.
Incomplete coverage keeps its exact scan. There is no new worker, storage field,
packet, tuning knob or disk-format change. The repeated scan is absent from the
fixed profile. Save/capture budgets are unchanged.

The new regression covers missing coverage, completion and clearing at steps
1, 2, 4 and 8. All 23 Atlas tests pass in each of six source/loader groups:
26.1 shared ABI, 26.2 and 26.3 × Fabric/NeoForge (138 test invocations). The server's
newer NeoForge 26.3.0.37-beta also passed that test class. Reused retained Minecraft
artifacts avoided rebuilding dependencies during the final parity run; the source
and tests were recompiled. The initial attempt using a different loader runtime
was deliberately stopped under local disk/memory pressure; its interrupted log
is retained, not counted as a pass.

## Remaining work and limits

The [follow-up stall investigation](SERVER_STALL_INVESTIGATION_2026_10_10.md)
reproduces long transitions without heap-inspection GC, identifies chunk
unload/ticket churn, real GC and unbudgeted player-load Atlas captures, and
records the profiler-induced pauses in the original recordings. The table
above remains the original trial's measurements, including that limitation.

- Profile the remaining transition/chunk-loading outliers, including the in-band
  1.1-second event. The status shortcut solves one demonstrated cause, not all stalls.
- Partial Atlas progress still uses an exact scan; this complete-world shortcut
  does not establish good multiplayer performance during a fresh Large rebuild.
- Initial full-Atlas streaming still takes minutes. Client logs show render-stall
  warnings during uploads/snapshots; three instances on one constrained laptop
  cannot establish a representative single-client FPS result.
- Three clients at view distance 12 do not qualify 20 players, larger client view
  distances, normal survival activity or long sessions. Network packet loss was
  not measured; SSH tunnelling also differs from ordinary public-port connections.
- The previous six frozen 1.4 release JARs remain held. These targeted tests and
  server trials do not replace fresh full release qualification.

## Recovery and evidence

Test clients and the SSH tunnel are stopped. The public service was restored
with the tested status-fix diagnostic JAR, online authentication still enabled,
normal auto policy, and its complete original Large world. The previous mod is
backed up outside `mods/`. No CurseForge/Modrinth upload occurred.

- Before JAR SHA256: `a13bcbee0b655bf805f6e0883a31e5e6a84e2ddf6933468e10114d33f64bfe2b`.
- After/public JAR SHA256: `9f6c522fd83d1c82231beb8f06e869a427efbc642c8d556b5c3e37c33530e5e1`.
- Source: parent `d793758` plus this PR's shared completion shortcut and regression.
  The diagnostic JAR was built from that working tree before the documenting commit.
- Public backup: `/opt/ringworld-server-archives/pre-1.4-checkpoint-20261010/pre-status-scan-fix.jar`.
- Machine-readable checked-in summary: [server multiplayer evidence](evidence/server-multiplayer-load-2026-10-10.json).
- Raw tick windows, server/client logs, both JFR recordings, controller scripts,
  compile/test logs and restoration confirmation: `logs/server-multiplayer-load/`.
  Disposable client cache directories were removed after shutdown; reproduction
  settings and launch scripts are retained. Superseded early fixture/build caches
  were also cleaned under disk pressure; final release artifacts and verified
  checkpoint/capture evidence remain intact.
