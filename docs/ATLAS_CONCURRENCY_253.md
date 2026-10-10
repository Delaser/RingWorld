# Bounded Atlas pregeneration concurrency (#253)

This implementation feeds several independent FULL chunk requests into
Minecraft's existing generation pipeline. It does not create a replacement
worldgen executor, assign cores, or read live world data on worker threads.
The user authorized implementation on 9 October 2026.

## Policy and controls

The owner authorized automatic scaling after the fixed-request benchmark.
The upcoming-release implementation defaults to **auto**, starting with four requests.
Absent `ringworld.atlasInFlightChunks`, or `-Dringworld.atlasInFlightChunks=auto`,
selects auto. Numeric values 1–8 select a **fixed** limit, including the serial
baseline (`=1`) and fixed four-request comparison (`=4`). This process setting
is read when creating a job. `/ringworld chunk_gen_rate 1|2|4|8` changes the
fixed limit live; `/ringworld chunk_gen_rate auto` restores automatic scaling
starting at four. The bare command shows the current mode/target. It requires
the existing gamemaster permission and always controls the server's Overworld,
including when issued from Nether/End or console. The override applies to
current and subsequent jobs until world unload, then the JVM default returns.
It is not a saved world setting. Background, interactive and headless calls share the same world-owned
writer and capacity policy.

Auto observes performance about once per second and selects 4 / 2 / 1.
Two consecutive poor observations halve the target; severe pressure selects
one immediately. Ten consecutive healthy observations allow one recovery
step (1→2 or 2→4). Borderline measurements reset recovery, preventing oscillation.
No request is cancelled to reduce the target: already issued work drains,
while admission stops at the lower target. Rotating admission also prevents
retained retries from starving when the target is smaller than the slot count.
Eight bounded slots are reserved once so live increases preserve the same
cursor, retries and ticket leases; admission follows the selected target.
Ready capture now shares this adaptive target: up to four chunks per tick,
checking a soft two-millisecond budget between complete chunks. It always allows
one ready chunk to make progress. A single chunk, chunk-load callbacks, GC or
scheduling can exceed the target; this is not a hard tick-time guarantee.
Numeric limits 1/2/4 bound both stages, while eight concurrent requests still
allow at most four explicit captures per tick. Fixed modes retain the time
budget but disable automatic pressure backoff, as before.

Integrated single-player uses both server tick pressure and focused, unpaused
owner FPS. The client reports roughly once per second through the integrated
server's task queue; there are no new packets and remote clients cannot throttle
a dedicated server with FPS reports. The target is min(60, configured FPS cap).
FPS below 80% of target is poor, below 40% severe, and at least 95% healthy.
Thus a deliberate 30 FPS cap is judged against 30, not 60. Zero/uninitialised
FPS is ignored. Paused/unfocused feedback stops and expires after three seconds.

Dedicated/headless servers have no FPS; they use the mean of the last twenty
completed tick samples. Mean above 40 ms is poor, at least 100 ms severe, below
25 ms healthy. Recovery requires healthy tick pressure and, when available,
healthy FPS. These are initial policy thresholds, not a guarantee of minimum FPS
or multiplayer latency. Disk stalls, GPU load and other mods may not improve
when request count falls. Client feedback and policy changes never read or
mutate a live world off its server thread. `/ringworld atlas status` reports the
current target and auto/fixed policy. Selecting a numeric command disables
automatic backoff until `auto` is selected again. Mode changes discard previous
FPS samples and automatic observation history, so stale pressure cannot leak
into the restored automatic policy. Commands do not start a stopped job or
change its paused/running state.

The existing 64-pending-task threshold is checked before every new request,
including between starts in one tick. This is a soft submission gate;
generation dependencies can produce a larger queue after admission. Capacity is an upper bound, not a
promise of that many active requests or cores. Player work and the vanilla
chunk dependency graph can reduce it. No vanilla worker-thread limit changes.

One shared X-major canonical cursor feeds bounded slots. Each slot retains its
selected chunk, future, processed marker, retry count/backoff and loading ticket
until safely captured/released. Completion order can differ from selection
order. A rotating ready-slot search prevents both head-of-line blocking and
starvation. The explicit pregeneration consumer captures **up to four ready
chunks per tick within its soft time budget**, scaling to two/one with the shared
policy. Observe performance before capture, so the new target applies that tick.
Existing normal
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
progress. Even a recoverable start failure after installing a ticket returns
a retained failed future: its lease remains owned until release succeeds, so
a simultaneous start/release failure cannot orphan a ticket. An empty batch
waiting on queue backpressure must not be treated as an exhausted cursor.
The Atlas remains the durable journal. On restart, earlier holes are retried while out-of-order completed chunks are skipped. Cursor exhaustion
cannot publish a partial Atlas as COMPLETE. Complete Atlas data alone is not
enough: every outstanding ticket must drain, then the saved Atlas must reopen
with complete coverage and matching revision before the completion future is
published. Server stop and world unload freeze ordinary chunk-load capture
before checkpointing, so late save-drain callbacks cannot change the durable
cell count after an interruption report. User pause/cancel does not apply this
shutdown-only capture barrier.

No cache-format, seed, geometry, generation-settings, topology or network-schema
change is introduced. The current shared cache format remains 11 from #257.
The owner authorized the Large server's 1.4 update on 10 October. Its original
world and installation are backed up; see [checkpoint follow-up](SERVER_ATLAS_CHECKPOINTS.md).

## Adaptive capture follow-up — 10 October 2026

The owner authorized extending auto scaling to capture after the one-per-tick
limit was identified as a roughly 55-minute floor for a Large Atlas rebuild.
This reuses the existing controller, commands and feedback mailbox; it adds no
threads, packets, saved settings or cache-format changes. Capture remains on the
server thread. Rotating ready slots are marked processed after each attempt;
every processed lease is still released even if another attempt fails. Pause
drains already issued work at the bounded rate; cancel never resolves discarded
results. Checkpoint copying and dirty-cell recapture retain their separate budgets.

`-Dringworld.atlasCaptureTimings=true` is optional diagnostics. Every 200 ticks it
logs explicit capture attempts, total/maximum owner duration, maximum observed
completed server tick and request target. The duration includes result handling
and processed-lease cleanup, but not normal chunk-load callback captures. This
must not be presented as total Atlas CPU cost. Final validation follows below.

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

## Matched benchmark results

Mac14,2: eight logical cores, 16 GiB RAM, Java 25.0.4+7, Minecraft 26.1.2
Fabric. Each cell generated 1,024 canonical chunks and 262,144 one-block Atlas
samples from a fresh world. Two runs per request count, serial local execution;
the second pass reverses the first order. No client or players were connected.

| Requests | Pass 1 / pass 2 generation seconds | Mean seconds | Speedup vs 1 | Mean busy CPU cores | Peak used heap MiB, pass 1 / 2 | p99 tick ms, pass 1 / 2 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 195.61 / 153.61 | 174.61 | 1.00× | 0.98 | 542 / 514 | 34.63 / 14.46 |
| 2 | 90.97 / 87.00 | 88.98 | 1.96× | 1.62 | 596 / 523 | 31.73 / 17.65 |
| 4 | 65.43 / 62.90 | 64.16 | 2.72× | 2.25 | 825 / 660 | 38.62 / 23.08 |
| 8 | 59.36 / 59.43 | 59.40 | 2.94× | 2.14 | 597 / 519 | 18.83 / 22.00 |

Four is the measured starting ceiling for the new **adaptive trial**. Eight saved only another
7.4% of elapsed time on these means. Processing 1,024 explicit captures at
one per tick and 20 ticks/second takes 51.2 seconds, before other costs. Normal
chunk-load callbacks also capture surfaces through their unchanged path, so
this is a capture-budget reference rather than a strict wall-clock floor.
Increasing requests does not raise the explicit consumer budget.
The serial baseline varied substantially between passes, so these are observed
local results rather than a promised speed multiplier.

Slow ticks still occurred. Over-50 ms tick counts for the two passes were
16/1, 7/2, 9/4 and 5/3 for 1/2/4/8 requests respectively. Maxima were
644/75, 103/62, 75/88 and 87/75 ms. Startup/save/GC and generation are included;
this does not demonstrate hitch-free play or multiplayer latency. Pending
queues peaked at 492–703 tasks despite the 64-task submission gate because
Minecraft expands generation dependencies after admission.

The benchmark passes preceded the final empty-batch/backpressure correction.
Pass 1 also preceded the failed-start lease retention correction; pass 2
included that correction. Both preceded the shutdown capture barrier. Those fixes target failed starts and cancellation
restart/backpressure; healthy capture/scheduling is unchanged. Final native
lifecycle checks and source builds below use the corrected candidate. Do not
call these timings frozen-JAR release qualification.

## Sample-content investigation

Comparing initial serial/four-request Atlas heights found differences, so the
benchmark was paused for a saved-world audit. Separate diagnostic 1/4 worlds
had 1,131 changed top heights in 24 chunks. Removing vegetation and snow for
comparison left **zero underlying-ground height differences** in those columns.
A repeated serial run also changed 199 top heights. This is consistent with
feature-placement order/timing affecting vegetation; exact feature or structure
parity across concurrency counts has not been established.

In both diagnostic saved worlds, every natural Atlas height matched its own
canonical `WORLD_SURFACE` heightmap. The only 212 exclusions were deepslate
bricks/tiles in the manufactured wall, expected under #257. This is evidence
against premature natural-surface capture in those worlds, not proof of every
seed or generation feature. The diagnostic serial run overlapped analysis and
is excluded from the matched timing table. Preserve this variation as a limit
when reviewing adaptive mode for integration.

## Validation status and reproduction

The final native matrix passes on source
`9c801171af51dd22e9b2a45ad2401b2bc780da7f`: six version/loader cells,
three launches each. Each fresh run passes the multiple-request
pause/drain/resume/cancel/restart probe, then stops through normal RCON with
several live requests. Its INTERRUPTED report matches saved presence bytes.
Each resumed and reopened run independently has every Atlas cell and all
1,024 canonical region chunk records. No Minecraft ERROR/FATAL entries occurred
in these passing runs. Complete-cache reopen reports zero generation elapsed
time and issues no chunk requests. Resumed timings are not fresh benchmarks.

| Minecraft | Loader | Lifecycle probe | Interrupted cells, report = disk | Resumed coverage | Complete-cache reopen |
| --- | --- | --- | --- | --- | --- |
| 26.1.2 | Fabric | PASS | 13,056 | 262,144 / 262,144 | PASS, 0 ms |
| 26.1.2 | NeoForge | PASS | 10,752 | 262,144 / 262,144 | PASS, 0 ms |
| 26.2 | Fabric | PASS | 9,984 | 262,144 / 262,144 | PASS, 0 ms |
| 26.2 | NeoForge | PASS | 10,496 | 262,144 / 262,144 | PASS, 0 ms |
| 26.3 | Fabric | PASS | 11,520 | 262,144 / 262,144 | PASS, 0 ms |
| 26.3 | NeoForge | PASS | 13,312 | 262,144 / 262,144 | PASS, 0 ms |

Native pins: 26.1.2 Fabric Loader 0.19.3/API 0.155.2+26.1.2 and NeoForge
26.1.2.87; 26.2 Loader 0.19.3/API 0.158.0+26.2 and NeoForge 26.2.0.69;
26.3 Loader 0.19.5/API 0.160.5+26.3 and NeoForge 26.3.0.7-beta. Java
25.0.4+7 throughout; Gradle 9.5.1, one Gradle worker, sequential servers.
All six local source builds/tests pass on the same source (2,856 test-case
executions, zero failures/errors/skips). GitHub's six source cells (26.1 / 26.2 /
26.3, both loaders), two static guards and packaging also pass; source workflow
run 37960719459, static 37960719438, packaging 37960719565. The broader local
Python suite passed 440 tests with two expected platform skips before these
Java-only lifecycle corrections; the final static CI guards pass afterward.

| Minecraft | Loader | Build | Java test cases | Failures / errors / skips |
| --- | --- | --- | --- | --- |
| 26.1.2 | Fabric | PASS | 475 | 0 / 0 / 0 |
| 26.1.2 | NeoForge | PASS | 475 | 0 / 0 / 0 |
| 26.2 | Fabric | PASS | 475 | 0 / 0 / 0 |
| 26.2 | NeoForge | PASS | 475 | 0 / 0 / 0 |
| 26.3 | Fabric | PASS | 478 | 0 / 0 / 0 |
| 26.3 | NeoForge | PASS | 478 | 0 / 0 / 0 |

The earlier `1021e5f` matrix had five completed cells; its final 26.3 NeoForge
interruption report recorded 10,496 cells but later unload saved 11,008. The
shutdown capture barrier fixes that discrepancy. All previous logs/worlds,
including the failing run, remain under `before-shutdown-barrier/`.

Build/native logs, reports, independent gzip/region coverage checks and sample
content audits are retained under ignored `logs/atlas-concurrency-253/`.
The cancelled benchmark log and failed backpressure lifecycle log remain there;
neither is a passing run. A test-only missing assertion import was repaired
after the first failed-start cleanup CI run. The first 26.3 shutdown was
rejected by a legacy save-summary log assertion: 26.3 omits that message. Its
log/world are retained, and the corrected check requires normal shutdown, final
save entries for all three dimensions and independent disk verification, then
resume/reopen. No Minecraft ERROR/FATAL entries are accepted in passing runs.
Older versions still require their original save-summary marker.

For a fresh disposable Fabric fixture, use the existing headless task with
accepted local EULA and a loopback-only `server.properties` (seed 25320261009,
view/simulation distance 2):

```sh
JAVA_TOOL_OPTIONS="-Dringworld.atlasInFlightChunks=4 -Dringworld.measureAtlasConcurrency=true -Dringworld.testAtlasConcurrency=true" \
  ./gradlew :runHeadlessPrewarmServer \
  -PringHeadlessPrewarmCircumference=2048 -PringHeadlessPrewarmWidth=128 \
  --max-workers=1 --console=plain
```

Use `:neoforge:runHeadlessPrewarmServer` with the
`ringNeoForgeHeadlessPrewarm` property prefix for NeoForge. Normal RCON `stop`
before completion is expected to produce INTERRUPTED and a failed
completion-only Gradle finalizer; preserve that report. Repeat with
`-PringHeadlessPrewarmResume=true` (or the NeoForge prefix) and without the
lifecycle probe, first to complete and then to reopen the complete cache.
Fresh fixture preparation replaces only its dedicated disposable world; use
resume for the interruption/reopen legs. Independently verify the saved gzip's
world hash, format 11, all cell presence bytes and every canonical MCA chunk
record. Do not run these tasks against a valuable world or change the live
large-server generation.

Development checks are not full frozen-candidate release qualification.
The owner approved integration through PR #273 on 9 October after reviewing
the measurements and automatic/live controls. Player-active priority/latency,
actual client FPS and wider feature/structure-content testing remain release
qualification work, tracked in #253. Integration does not authorize publication
or deployment. The published 1.3 server still uses its original policy.

## Adaptive validation follow-up

The fixed-policy tables above record source 9c80117, before adaptive scaling;
they do not qualify the new default. `RingAtlasAdaptiveConcurrencyTest` covers
sustained FPS/tick backoff, severe pressure, capped FPS, isolated dips,
measurement cadence, missing measurements and gradual recovery. A controlled
headless probe (`ringworld.testAtlasAutoScale=true`) uses the same integrated
FPS mailbox with synthetic 40/60 FPS and requires 4→2→1→2→4. It does not claim
that actual GPU/render performance was measured. The live client reads Minecraft
FPS in the shared helper, registered through both loader tick hooks.

Adaptive source **fbe08ba59d6d31ae006620676bf35c469298ba07** passes all six
source builds/tests and all six native lifecycle/stop/resume/reopen cells
(18 launches). The fixture geometry, seed and independent disk checks are the
same as the fixed-policy lifecycle matrix above. Each initial launch exercises
pause/drain/resume/cancel/restart and controlled FPS pressure/recovery before
normal interruption. Each subsequent resume reaches all 262,144 cells and all
1,024 canonical region records; reopening preserves the completed height hash
and admits no new requests.

| Minecraft | Loader | Unit cases | Adaptive 4→2→1→2→4 and lifecycle | Interrupted report = disk cells | Resume / complete-cache reopen |
|---|---|---:|---|---:|---|
| 26.1.2 | Fabric | 482 | PASS | 70,656 | PASS / PASS |
| 26.1.2 | NeoForge | 482 | PASS | 69,632 | PASS / PASS |
| 26.2 | Fabric | 482 | PASS | 66,560 | PASS / PASS |
| 26.2 | NeoForge | 482 | PASS | 74,752 | PASS / PASS |
| 26.3 | Fabric | 485 | PASS | 69,888 | PASS / PASS |
| 26.3 | NeoForge | 485 | PASS | 77,824 | PASS / PASS |

All 2,898 local test executions pass with zero failures, errors or skips.
All nine CI checks pass on the adaptive source: source workflow run
37969029077, static run 37969029064 and packaging run 37969029139.
The first exploratory compile failed because the NeoForge client hook lacked
its shared-helper import; that failed log remains preserved. No Minecraft
ERROR/FATAL entries occur in the passing adaptive native runs. Expected
interruption-only Gradle finalizer failures remain recorded as interruptions,
not completed generation.

To exercise the adaptive probe on a fresh disposable fixture, replace the JVM
options in the command above with:

```sh
JAVA_TOOL_OPTIONS="-Dringworld.atlasInFlightChunks=auto -Dringworld.measureAtlasConcurrency=true -Dringworld.testAtlasConcurrency=true -Dringworld.testAtlasAutoScale=true"
```

Keep `auto` for resume/reopen and remove both synthetic probe flags. New
evidence is retained separately in `logs/atlas-auto-scaling-253/`; earlier
fixed-policy and failed evidence remains intact. Normal production does not
enable the synthetic probes or metrics. Actual integrated-client FPS,
player-active priority/latency and wider feature/structure parity remain
unqualified; these are development checks, not release qualification.

## Live chunk generation rate command follow-up

The owner requested `/ringworld chunk_gen_rate 1|2|4|8` after the adaptive
trial. `auto` restores automatic scaling, and the bare command shows the
current setting. Source **7505fb38184dbd1d20f2238cb048ec1e2a7ca4f9** passes
all six local builds/tests (2,916 executions), all nine CI checks, and all six
real-dispatcher native cells (18 passing launches):

| Minecraft | Loader | Unit cases | Command / adaptive / lifecycle | Observed live high → drained low | Resume / cache reopen |
|---|---|---:|---|---|---|
| 26.1.2 | Fabric | 485 | PASS | 8 → 1 | PASS / PASS |
| 26.1.2 | NeoForge | 485 | PASS | 7 → 1 | PASS / PASS |
| 26.2 | Fabric | 485 | PASS | 8 → 1 | PASS / PASS |
| 26.2 | NeoForge | 485 | PASS | 7 → 1 | PASS / PASS |
| 26.3 | Fabric | 488 | PASS | 8 → 1 | PASS / PASS |
| 26.3 | NeoForge | 488 | PASS | 8 → 1 | PASS / PASS |

Each initial native run uses actual authenticated loopback RCON commands to
set and query 1/2/4/8, reject 0/3/9 without altering the setting, retain pause
while changing all four values, resume with a target of eight, observe more
than four outstanding requests, lower to one and verify drainage, then
restore auto at four. The opt-in `ringworld.testAtlasRateCommand=true`
extends the existing concurrency probe to verify that a fixed session
override survives cancel/replacement and can return to auto. It is disabled
in normal play; use it alongside the two existing probes on a disposable
headless fixture. The ordinary adaptive pressure/recovery probe also passes.

All interrupted reports match saved presence counts; each resume completes
all 262,144 cells and all 1,024 canonical region records. Complete-cache
reopen preserves the height hash and issues no requests. Evidence and 19-entry
command transcripts per cell are retained under ignored
`logs/atlas-rate-command-253/`. The first 26.2 NeoForge run passed its command
checks but was rejected for a Mojang Yggdrasil public-key fetch ERROR; its log,
transcript and world remain under `failed-yggdrasil-26.2-neoforge/`. The unchanged
retry passes cleanly; the failed run is not counted as passing evidence.

These checks do not measure actual graphical FPS or qualify a release.
The owner approved integration through PR #273 on 9 October. The live published
server is unchanged; full release qualification remains separate.

## Server checkpoint follow-up (10 October 2026)

The concurrency benchmarks above measure generation admission. They do not
remove the synchronous full-Atlas save stall. The checkpoint implementation now
uses bounded worker persistence; see `SERVER_ATLAS_CHECKPOINTS.md` for current
semantics and separate same-world performance evidence. Cancellation tests wait
for durable asynchronous cancellation before asserting termination/replacement.
