# Atlas pregeneration service plan

Status: Phases 1b and 2's player-facing Fabric workflow landed on 2026-08-01.
`RingAtlasPregenerationService` now owns one Overworld atlas writer, its
cursor/selected future/retry state, process-local controls, checkpointing, and
verified completion. Fabric commands, lifecycle hooks, and client tile streams
remain in `RingTerrainAtlasServer`. The pause-menu map, versioned status/control
payloads, server-side authority checks, and completion toast now reuse that
same service. Phase 3's Fabric-only dedicated-server adapter is implemented:
it owns launch gating, JSON evidence, world save, and stop after the service's
verified completion, without adding another scheduler or writer.

## Outcome

Provide one reusable, resumable function for generating the complete canonical
terrain atlas before players rely on the distant-ring proxy:

```java
AtlasPregenerationHandle pregenerate(
        ServerLevel overworld,
        AtlasPregenerationOptions options,
        AtlasPregenerationListener listener);
```

The function returns immediately on the server thread, advances through normal
server ticks and chunk futures, and completes only after every canonical atlas
cell has been captured and the dimension-owned atlas has been atomically
saved. It must never block the server thread waiting on a chunk future.

The primary product experience is a player-facing **Generate Entire Ring**
button. Pressing it generates every canonical terrain chunk needed by the
atlas; the player does not travel, teleport, or complete a lap. The same
service also supports:

- the existing low-impact background generation during ordinary play;
- a progress screen with pause, resume, and safe cancellation;
- `/ringworld atlas status|pause|resume`;
- an explicit operator-requested prewarm;
- a headless `prewarm-and-stop` server run for packaging, staging, and
  production-world preparation;
- deterministic integration and performance tests.

## Current implementation

The extracted `RingAtlasPregenerationService` preserves the proven baseline:

- it loads or resumes the atlas identified by immutable geometry, seed, layout
  fingerprint, format, and sample step;
- it scans only canonical chunks;
- it allows one `ChunkStatus.FULL` future in flight;
- it yields while the normal chunk queue has 64 or more pending tasks;
- player-loaded chunks populate the same atlas;
- dirty state saves atomically every 200 ticks and on world unload;
- new tiles stream incrementally to connected clients;
- pause/resume affects process state without changing saved layout.

The completed extraction does not create an independent worldgen
path and does not synthesize atlas colour from noise. Samples continue to come
from real `ChunkStatus.FULL` Overworld chunks using the existing heightmap,
biome tint, texture-luminance correction, and exposed-top-face height rules.

## Required interfaces

### Pure job model

Add loader-neutral records and state under `dev.ringworld.world`:

```java
record AtlasPregenerationOptions(
        AtlasPregenerationMode mode,
        int maxInFlightChunks,
        int pendingTaskSoftLimit,
        int checkpointIntervalChunks,
        int progressIntervalTicks,
        boolean stopServerWhenComplete) {}

record AtlasPregenerationProgress(
        AtlasPregenerationState state,
        long completedChunks,
        long totalChunks,
        int presentCells,
        int totalCells,
        double cellsPerSecond,
        Duration elapsed,
        Optional<Duration> eta,
        Optional<String> lastError) {}

record AtlasPregenerationResult(
        long worldHash,
        long completedChunks,
        int completedCells,
        Duration elapsed,
        Path atlasPath) {}
```

`Mode` initially has `BACKGROUND`, `INTERACTIVE`, and `HEADLESS_PREWARM`.
`State` distinguishes `IDLE`, `RUNNING`, `PAUSED`, `SAVING`, `COMPLETE`,
`CANCELLED`, and `FAILED`. Options validate conservative bounds. Background
and interactive modes default to one in-flight chunk and the current queue
threshold of 64. Headless concurrency may be configurable later, but the first
implementation also uses one in-flight chunk until measured evidence supports
more.

`RingAtlasPregenerationCursor` owns deterministic checked-`long` traversal and
resume logic:

- total chunks are `circumferenceChunks × widthChunks`;
- X remains canonical in `[0, circumferenceChunks)`;
- Z is derived from `minChunkZ + row`;
- `RingTerrainAtlas.isChunkPresent` skips complete chunks;
- restart resumes from `firstMissingChunkIndex`;
- no power-of-two bitmasking is permitted, even though the production default
  is 16,384 blocks.

The current X-major, finite-Z-row order remains initially. Changing ordering is
a performance experiment and requires before/after region-I/O and tick-time
evidence.

Phase 1a implements these records/interfaces, `AtlasPregenerationMode`,
`AtlasPregenerationState`, and the cursor without introducing a scheduler or
changing atlas bytes. State transitions are explicit so a future server-thread
owner can enforce pause, resume, save, cancellation, completion, and failure
consistently. The cursor begins from the atlas's first missing chunk, skips
newly present chunks while it advances, and derives finite Z from
`RingGeometry.minChunkZ()`; the present cells remain the resume journal. Rate
snapshots subtract the present-cell count captured at each start/resume, so a
partial restarted atlas reports no rate or ETA until that run adds cells.

### Server execution façade

Add `RingAtlasPregenerationService` under `dev.ringworld.server`. It owns one
job per Overworld and exposes:

```java
AtlasPregenerationHandle pregenerate(...);
Optional<AtlasPregenerationHandle> active(ServerLevel overworld);
```

The handle exposes:

```java
AtlasPregenerationProgress progress();
void pause();
void resume();
void cancel();
CompletionStage<AtlasPregenerationResult> completion();
```

All mutations occur on the Minecraft server thread. Chunk futures may complete
asynchronously, but their results are consumed and sampled on the server
thread. The service is the only atlas writer. Player-driven chunk capture,
network tile streaming, and periodic saving call into the same world-owned
state instead of competing with it.

`cancel` stops scheduling new chunks and checkpoints current progress; it does
not delete atlas cells or generated terrain. A later call resumes from the
persisted atlas.

When `pregenerateTerrainAtlas=false`, the service still creates the same
`BACKGROUND` handle in `IDLE` so status and legacy pause/resume commands stay
defined and player-loaded chunks use the authoritative writer. It does not
schedule chunks until a future explicit matching start; resume alone preserves
the disabled-background policy.

The initial server implementation accepts only the conservative execution
policy (one in-flight chunk and 64 pending-task limit). Non-default model
policy fields are rejected: `checkpointIntervalChunks=200` remains reserved
for a later policy implementation, while the active runtime preserves the
legacy 200-server-tick save cadence; the model's 20-tick progress setting is
likewise reserved while publication stays on its existing cadence.
`stopServerWhenComplete` is accepted only as `HEADLESS_PREWARM` intent; the
Fabric coordinator, rather than the service, owns the later world save, report,
and halt. `BACKGROUND` and `INTERACTIVE` are scheduling intent labels at this
stage and use the same conservative execution path.
`completedChunks` reports chunks completed by the current handle; durable
overall completion is represented by the atlas present-cell count and total.

### Loader adapters

Fabric command and lifecycle registration stay in a narrow platform adapter.
The job model, options, progress, completion result, traversal, and persistence
rules remain loader-neutral so a NeoForge adapter can register equivalent
hooks without changing behavior or formats.

Do not expose Fabric event types through the public service interface.

## Explicit prewarm workflows

### One-click player experience

Add a **RingWorld Map** button to the in-game pause menu while the player is in
a RingWorld Overworld. It opens a screen with:

- immutable world dimensions and atlas identity;
- generated canonical chunks and total chunks;
- present atlas cells, percentage, elapsed time, rate, and ETA;
- current state: idle, generating, paused, saving, complete, or failed;
- **Generate Entire Ring**, **Pause**, **Resume**, **Cancel**, and **Done**
  actions as appropriate.

The first press of **Generate Entire Ring** shows a confirmation containing the
exact canonical chunk count and a warning that real terrain and region files
will be generated and saved. Confirming sends one start request to the
integrated or dedicated server and switches the screen to live progress.

The progress screen is not modal: **Done** returns to play while generation
continues in the background. Reopening it shows the same authoritative server
job. Completion produces a small client toast and causes the existing
complete-atlas GPU ring to appear through the normal atlas metadata/tile path.
No movement or lap is involved.

If automatic `pregenerateTerrainAtlas=true` has already started the same job,
the initial button reads **View Generation Progress** instead of creating a
duplicate job. If the saved atlas is already complete, the screen reports
**Complete** and offers no destructive regeneration action.

Permissions:

- the owner of an integrated singleplayer world can control its job;
- dedicated-server gamemasters can start, pause, resume, or cancel;
- ordinary multiplayer clients receive read-only status;
- every serverbound request is permission-checked by the server.

Use new generation-specific, wire-versioned payload identifiers for start,
control, status, and progress. Do not append fields to existing settings or
atlas payload codecs. Progress messages are rate-limited and contain only the
loader-neutral progress model.

As a follow-up, the creation screen may offer **Create and generate complete
ring**. That action creates the world normally, joins it, and opens the same
progress screen; it must not introduce a second generator or block the render
thread during world creation.

### In-game/operator

Extend the existing command tree without changing current meanings:

```text
/ringworld atlas pregen start
/ringworld atlas pregen status
/ringworld atlas pregen pause
/ringworld atlas pregen resume
/ringworld atlas pregen cancel
```

The existing shorter `status|pause|resume` commands remain aliases. Status
prints geometry, canonical chunks, atlas cells, completion, rate, ETA,
in-flight count, queue pressure, checkpoint age, and last error.

### Headless prewarm-and-stop

The Fabric implementation provides the explicit server launch option and
development Gradle task. The launch:

1. opens or creates only the selected RingWorld copy;
2. validates immutable settings and atlas world hash;
3. starts `HEADLESS_PREWARM`;
4. writes machine-readable progress to an ignored run directory;
5. rejects or clearly warns off player joins while preparation is active;
6. waits through normal server ticks rather than blocking;
7. atomically saves the complete atlas and world;
8. verifies the saved atlas by reopening its header, identity, and completion;
9. stops the Minecraft server; the Gradle wrapper converts a non-`COMPLETE`
   report into the non-zero failure result.

If immutable settings reject a copied ordinary world while `ServerLevel` is
still under construction, before the lifecycle load event, the adapter writes
a `REJECTED` report with unavailable identity sentinels and rethrows the
original startup error. It does not synthesize bootstrap geometry or convert
the world in place.

The source world for a copied-world fixture is always read-only. Runtime
worlds, logs, screenshots, reports, account data, and atlas caches remain
ignored and must never enter a client package or private source history.

## Persistence and failure rules

- The dimension-owned path remains
  `dimensions/minecraft/overworld/data/ringworld/terrain-atlas.rwat.gz`.
- Format 5 and the current world-hash derivation remain unchanged unless atlas
  cell semantics change.
- Checkpoints use the existing temporary-file plus atomic-replace behavior.
- World unload performs a final checkpoint and resolves or cancels the handle
  deterministically.
- A chunk failure records its canonical index and error. Background mode may
  retry with bounded backoff; headless mode fails after a configured bounded
  retry count.
- Completion is not reported until the atlas is complete, the final dirty
  tiles are queued, the file is saved, and a validation reopen succeeds.
- Server restart needs no separate cursor file: present atlas cells are the
  authoritative resume journal.
- Atlas generation necessarily generates and saves real canonical terrain
  chunks. UI and operator output must state this disk/time side effect.

## Production-default envelope

The 16,384×256 default contains:

- 1,024 chunks around × 16 chunks across = 16,384 canonical chunks;
- 2,048 atlas columns × 32 rows = 65,536 cells;
- 128 × 2 transport tiles;
- approximately 0.44 MiB of raw atlas arrays/wire data before compression;
- exactly four intrinsic X blocks per capped 4,096-column proxy texel.

The safe-small 2,048×416 full-atlas run measured roughly 82 cells per second.
Linear extrapolation would be about 13 minutes for 65,536 cells, but this is
not a production promise: region writes, biome/worldgen cost, CPU, storage,
concurrency, and other server load require a real end-to-end benchmark.

## Implementation phases

### Phase 1: extract and preserve behavior

- Completed: introduced the pure cursor, options, progress, result, and state
  tests, then moved cursor, selected in-flight future, rates, pause/cancel
  state, saves, and completion into a world-owned job.
- Completed: one in-flight chunk, queue threshold 64, 200-tick saves, current
  sampling and X-major traversal are retained. Failed futures retain their
  selected canonical chunk for bounded retry; they cannot advance the cursor.
- Completed: existing commands and automatic background mode delegate to the
  job. Completion exposes final dirty tiles, atomically saves, reopens and
  verifies format-5 identity/completeness, then resolves success.

Exit gate: safe-small atlas bytes, colours, heights, completion order, client
streaming, pause/resume, restart resume, and runtime frame pacing match the
current implementation.

### Phase 2: one-click UI and explicit completion contract (implemented on Fabric)

- Publish the server façade and handle.
- Add idempotent start calls: a matching active job returns its handle;
  conflicting options fail clearly.
- Add cancellation, verified final save, completion/failure futures, and
  structured progress logging.
- Add the pause-menu entry, confirmation, progress screen, permission checks,
  versioned control/status payloads, and completion toast.
- Make an automatically running job appear as progress rather than starting a
  duplicate.
- Keep server lifecycle and loader registration behind adapters.

Exit gate: one click in a fresh safe-small singleplayer world completes the
whole atlas without player movement, the UI remains responsive and resumable,
the complete ring appears, and an interrupted/restarted dedicated server
proves exactly-once completion and no lost cells.

The Fabric adapter uses `atlas_pregen_status_request_v1`,
`atlas_pregen_control_v1`, and `atlas_pregen_status_v1`. Its snapshot contains
immutable atlas/world identity, geometry, exact durable completed canonical
chunks, cells, rate/ETA/error, and a server-computed `canControl`. It sends at
most once per 20 ticks per observer except immediate request/action/state
replies. Any RingWorld player may observe; only the integrated owner or a
dedicated-server gamemaster can control. A NeoForge adapter can register these
same payload layouts and call the loader-neutral model/service.

### Phase 3: headless prewarm (implemented on Fabric)

- `runHeadlessPrewarmServer` starts the explicit opt-in
  `-Dringworld.headlessPrewarm=true` mode after preparing only
  `run-headless-prewarm/world`; `-PringHeadlessPrewarmSource=<save-folder-id>`
  copies an ignored source from `run/saves` without opening it in place.
  `-PringHeadlessPrewarmResume=true` retains only the existing disposable
  runtime world, rejects a copy source, and resumes from its atlas cells.
- The adapter suppresses normal background autostart, safely replaces only the
  unstarted disabled-background `IDLE` handle, rejects accepted joins
  immediately, disables vanilla empty-server pausing in its disposable fixture,
  and uses the one-in-flight service. It saves the world only
  after the service atomically saves and reopens the complete atlas.
- `world/ringworld-prewarm/progress.json` is atomically refreshed every 20
  ticks with schema version, identity, exact durable `completedChunks`,
  separately named `generatedChunksThisRun`, rate, ETA, and error. A result
  filename of `progress.json` is rejected so progress and terminal paths cannot
  collide.
- Every launch deletes its selected terminal result and `progress.json` before
  the server starts, including direct JVM launches and `Resume`; the Gradle
  fixture repeats that cleanup as defense in depth. The verifier parses the new
  JSON schema rather than accepting a text substring. A stale `COMPLETE` report
  therefore cannot pass a crashed later launch.
  `result.json` records `COMPLETE`, `FAILED`, `INTERRUPTED`, or `REJECTED`,
  elapsed time, exact durable canonical chunks/cells, atlas path, and failure
  reason. Rejected startup declares `identityAvailable:false` and documented
  zero/null identity sentinels rather than inventing a layout.
- The JSON writer is Gson-backed, including explicit `null` fields and complete
  escaping for control characters in an error message; a report-write failure
  is a controlled checkpoint/failure/halt path rather than an uncaught server
  tick exception.
- SIGTERM/server stop first consumes any completed selected future, checkpoints
  the same service, then reports `INTERRUPTED`; restart resumes from atlas
  cells. Existing region worlds without RingWorld settings are rejected by the
  immutable-settings guard. The Gradle finalizer turns any non-`COMPLETE`
  terminal JSON into a nonzero command result.

Exit gate: fresh and copied safe-small worlds prewarm, reopen, serve the
complete atlas to a clean client, and stop cleanly.

### Phase 4: production benchmark and tuning

- Run the complete 16,384×256 job on representative hardware.
- Record chunks/cells per second, TPS/MSPT, peak heap, region growth, atlas
  file size, checkpoint cost, client transfer time, GPU build time, and total
  wall time.
- Test one-in-flight against carefully bounded higher concurrency only in
  headless mode.
- Keep ordinary-play defaults conservative unless measured frame/TPS evidence
  supports a change.

Exit gate: the production atlas completes, survives restart, matches live
terrain at sampled points and the seam, passes tangent/radial visual review,
and has documented capacity and rollback guidance.

## Test matrix

Pure tests:

- canonical enumeration contains every chunk exactly once;
- non-power-of-two custom circumference remains correct;
- completed chunks are skipped and partial chunks resume;
- checked-long totals reject overflow before allocation;
- pause, resume, cancel, retry, and completion transitions are legal;
- ETA and rate snapshots handle zero work and restart;
- repeated UI start requests return the same active job;
- no result completes before verified save.

Integration tests:

- button confirmation, progress, background close/reopen, pause/resume,
  cancel/resume, completion toast, and already-complete state;
- singleplayer control, multiplayer gamemaster control, and non-operator
  read-only/denied behavior;
- fresh, partial, complete, missing, corrupt, and wrong-world-hash atlases;
- server stop during an in-flight chunk and restart;
- player-loaded chunks racing the scheduler without duplicate writers;
- queue backpressure while normal chunk work is busy;
- final dirty tile delivery to connected clients;
- no canonical chunk request outside X `[0, C)` or finite Z;
- source-copy immutability and dimension-owned output paths;
- Nether and End unchanged;
- Fabric behavior parity with the future NeoForge adapter.

## Definition of done

The feature is complete only when a player can press **Generate Entire Ring**
and produce a complete, durable atlas without moving; one authoritative
function drives that UI, ordinary background generation, operator controls,
automated tests, and headless prewarm; every completion is resumable and
verified; real terrain remains authoritative; player activity receives
priority; custom dimensions remain supported; and the full 16,384×256
benchmark plus client visual review passes.
