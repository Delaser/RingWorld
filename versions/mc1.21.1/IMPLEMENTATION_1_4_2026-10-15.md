# 1.4 implementation ledger

The owner authorized implementation on 15 October 2026: finish 1.21.1 first,
then 1.20.1 Fabric/Forge, diagnosing and fixing failures until the complete
[acceptance plan](../../docs/BACKPORT_1_4_DUAL_VERSION_PLAN_2026-10-15.md) is met.
This authorizes development/testing, not publication or public-server changes.

Pinned upstream runtime source: `1533878fe3a6eab7562830b3f39cbd7caae6d46a`.
Baseline product: `8f1fe43`; approved plan checkpoint: `695d02c`.
Work branch: `codex/backport-1.4`. Existing saves and prior evidence are preserved.

## P1: owner-thread chunk admission

The service now invokes the audited private `getChunkFutureMainThread` admission
through a required shared Mixin invoker. Its canonical cursor and non-expiring
region ticket remain unchanged. Vanilla ticket propagation and dependency
scheduling remain intact; only the public wrapper's future-completion
`managedBlock` is bypassed. Captures still happen on later owner-thread ticks
after readiness, identity and coverage checks. Cancellation/unload keeps its
existing lease ownership and never resolves a discarded result.

ASM regressions inspect the pinned Minecraft implementation and forbid the
public blocking acquisition from the service. The opt-in
`ringworld.atlasRequestTimings` logs completion-at-return and owner span for a
real cold-runtime proof. It is off by default. This is not a hard tick-time
guarantee: ticket propagation itself remains an indivisible vanilla operation.

P1 validation passes on both loaders: Fabric 533 and NeoForge 556 cases,
including the actual pinned-bytecode admission checks. Fresh official-loader
packaged dedicated runtimes each returned all 982 admitted generation requests
before completion, captured all 262,144 cells, saved all dimensions, then reopened
with no new requests and byte-identical complete Atlas files. Independent disk
parsers validated immutable settings, format 10, counts, revision and hashes.
Evidence: `.codex-tmp/b14p1/results.json`, per-loader fresh/reopen logs and disk
receipts, plus `.codex-tmp/backport-1.4-p1-build.log`. These are local diagnostic
jars with the baseline 1.3 label, not frozen 1.4 release evidence. Admission spans
are functional traces, not the final paired performance/soak matrix.

No 1.4 candidate or full acceptance PASS is claimed. The development identity
advances explicitly with the integrated feature port.

## Remaining stages

P2 Atlas 11/sampler/coherent persistence; P3 adaptive capture/concurrency and
chunk/light work; P4 exterior gameplay; P5 rendering/UI; full 1.21.1 acceptance;
then the independent Java-17 1.20.1 Fabric/Forge graphs and their complete gates.

## P2/P3: integrated Atlas engine (in progress)

The native Java-21 implementation now carries Atlas format 11, the bounded
manufactured-column selector, deferred loaded-chunk capture, eight retained
request slots, adaptive 4/2/1 backoff, fixed 1/2/4/8 operator controls, and local
integrated-client frame feedback on both loaders. The format bump deliberately
rebuilds visual caches; saved dimensions, topology and terrain remain unchanged.
The P1 private admission path is retained for every slot. Unloaded recapture
cells remain droppable and exact/overflow fairness remains the backport policy.

Checkpoint allocation, compression, replacement and verification run on a
single worker. Owner-thread copies advance in bounded slices and repair slices
edited during copying. Newer mutations remain dirty after an older write, and
normal completion/cancellation waits for its durable checkpoint without blocking
normal gameplay ticks. Blocking drain is confined to teardown. The foliage
settlement observation now includes queued loaded-chunk captures.

The development identity is `1.4.0-dev.1+mc1.21.1`. The historical format-10
reader and tests are preserved; a separate strict format-11 reader binds the new
semantic identity. Final native runtime, upgrade/recovery and production-scale
performance evidence for this engine is still pending. No full acceptance PASS
is claimed.

### Interrupted-run defect found and repaired

The first Atlas-11 packaged Fabric fresh generation and complete reopen pass,
but the eight-request interrupted run failed. Cancelling a private vanilla
admission future poisoned vanilla chunk-save dependencies, logged `Failed to
save chunk` and stalled shutdown. The attempt remains under
`.codex-tmp/b14a2/fabric/recovery-interrupt/` with an explicit `FAILED.json`;
the owned JVM required forced termination after shutdown stopped progressing.
It is not acceptance evidence.

Atlas requests now retain a dependent completion future that they exclusively
own. Cancelling it releases the Atlas ticket without cancelling vanilla's
future. A regression models a vanilla save dependent on the original future
and requires successful completion after Atlas cancellation. Native repeated
interruption/resume and both-loader validation are required before crediting
this repair. The earlier completed-world-only P1 proof did not exercise this
pending-future cancellation case.

## P3: scheduled maintenance and lighting (in progress)

The required native hooks now scope scheduled autosaves in 1.21.1 `tickServer`,
queue coordinate-only save sweeps, and apply normal/catch-up/heap-pressure
budgets to ordinary unload/eager work. Explicit/flush saves and shutdown retain
vanilla drains. The native `chunkSaveCooldowns` and one-argument
`saveChunkIfNeeded` are used. A backport-owned asynchronous-write counter
preserves original futures and exceptions because 1.21.1 lacks mainline's
counter. Location advancements defer once after movement and cannot starve
under continuous travel.

Server Overworld light maps use mainline's partitioned lookup snapshots. The
native covariant copy methods have separate required Sky/Block mixins; the
client, Nether and End remain vanilla. An opt-in `ringworld.lightStorageProbe`
checks real transformed cache invalidation, DataLayer identity/isolation,
branch/removal and concurrent published reads on detached copies only. Normal
play does not run it. All new injections remain required.

Build/unit checks pass (Fabric 579 / NeoForge 602; zero failures, errors or
skips); native packaged, real scheduled-durability, allocation/GC and controlled
Large-world acceptance remain pending. These counts should be reconciled with
the XML receipts if more cases are added before final qualification.

## P4: exterior building and visibility (in progress)

Player block/bucket placement now uses a separately persisted default-on policy,
with both bed halves checked before mutation. The native SavedData factory keeps
terrain settings and layout hashes unchanged. Both loaders register the identical
building-settings/control wire records, reject control before acknowledgement,
validate the current fingerprint and owner/operator authority, broadcast policy,
and refresh permission snapshots with observed Atlas status. Client prediction
uses the matching layout's policy and resets raw policy/permission state on
teardown. The map gains a World page and the operator command is
`/ringworld building outside on|off|show`.

Periodic chunk/entity watching now includes nearby exterior builds within the
ordinary distance window. Terrain generation and Atlas coverage retain their
finite-Z guards. The legacy traversal seed no longer clamps an exterior camera
to the rim because its actual chunk is streamed. The private legacy underlay
coverage proof still excludes the exterior; it must not gain exterior Atlas LOD.
Build, UI and real multiplayer placement/visibility acceptance are pending.

### P2/P3 packaged functional checkpoint

All fourteen official-loader dedicated phases pass at source `44cf94a`:
fresh generation/reopen, fixed-eight-request clean interruption/resume/reopen,
and copied format-10 visual-cache rebuild/reopen, on Fabric and NeoForge.
Each complete phase has all 262,144 durable cells; interrupted phases have a
strictly partial durable checkpoint and normal all-dimension shutdown. Complete
reopens admit no requests and preserve cache bytes. Upgrade checks preserve the
saved-settings values and layout fingerprint while rebuilding Atlas identity.
These checks do not assert byte-identical region files or exhaustive block-state
migration coverage. The complete gameplay/upgrade suite remains required.

Every launch also passes the real light-storage probe: Overworld block/sky maps
use partitioned snapshots and Nether/End block maps remain vanilla. Logs have
no chunk-save failures. Evidence is `.codex-tmp/b14a4/results.json`, fourteen
hash-bound process logs, retained Atlas/settings files and independent readers.
The compiled source archive was compared against `44cf94a` (normalizing text
line endings); jars are retained alongside their hashes. This is diagnostic
functional evidence, not the final clean/pushed frozen acceptance matrix.

The earlier `.codex-tmp/b14a3` first launch completed normally but its harness
expected six light stores rather than vanilla's four; `FAILED.json` records
that harness error. The corrected fourteen-phase run is fresh, not a relabelled
PASS over the failed cancellation attempt.

P4 build/unit checks pass on both loaders (Fabric 585 / NeoForge 608). The
expanded real-client fixture adds World On/Off/restored captures using real
widgets and policy payloads, waits for a fresh authority snapshot after a stale
fingerprint request, and retains all older generation/handoff/revision/teardown
assertions. Its revision marker is now stone: Atlas 11 intentionally omits a
qualifying floating manufactured gold block. Both Gradle verifiers require the
new policy marker and three decodable captures. Real-client execution is next.

### P4 real-client checkpoint

Both source-ABI integrated Atlas UI runs pass at `6443b3b`, with sixteen
captures each. New World-page widgets perform on/off/on payload round trips
and stale-fingerprint rejection; old progressive/complete handoff, cancellation,
retry, ordered stone placement/removal revisions and normal disconnect/raw
session teardown remain. Independent server-disk readers find complete format-11
Atlases and the restored saved building policy. Evidence and 32 PNG hashes:
`.codex-tmp/b14-building-ui/results.json`; an exact source archive is retained.
The Fabric World-Off view was visually inspected. This is bounded integrated
client evidence; dedicated non-operator, bed/bucket, both-rim exterior gameplay,
production rendering and full performance acceptance remain outstanding.

## P5: projection, wall closure and atmosphere (in progress)

The port carries the shared height-dependent nearby projection, default-on
three-chunk session settings, immutable visibility views, matched terrain/object/
cloud/proxy projection, and vertical coverage with horizontal material detail
(visual policy 7). Stationary setting changes invalidate the native visibility
graph. Native per-program RingWorld uniforms preserve stock Globals and skip
unrelated programs; mainline's GPU-buffer API is not copied into the legacy ABI.

Closed seeded crest/cap geometry, both finite terrain-edge closures, saved-height
exterior depth and the positive `maxZ+1` outer face are ported. Wall colour mips
remain opaque, retaining the backport's legacy material coordinate hash. The
CPU/GPU cloud deck shares one saved-wall-derived height. Flat sky discs are
removed and ordinary-air backdrop matching is no longer altitude-dependent;
water, special-effect and other-dimension guards remain. The actual render camera
being underwater skips Atlas drawing without discarding retained resources.

A private backport adaptation removes the ambiguous inverse from physical mesh
positions: the GPU stream receives intrinsic coordinates through optional mesh
consumer metadata. Physical mesh/API consumers remain unchanged. This preserves
Y=319 terrain even when a small original cylinder has negative physical radius.
A pure regression checks metadata for every terrain/cap/closure vertex, and an
opt-in transform-feedback driver probe compares the shipped projection function
with CPU output at 1024/2048/16384/32768 circumference, distances 1/3/8, four
camera heights, both correction states and multi-lap/seam vertices. Atlas UI
verifiers now require that numerical driver PASS. Full build/native validation
and production visual review for P5 remain pending.

The legacy proxy remains immediately after section compilation, with the existing
opaque streaming floor, finite-Z exclusion, exact chunk identity, adjacent-fringe
and distinct-tick readiness checks, and live/Atlas overlap. No final acceptance
or release is claimed at this implementation checkpoint.

The 1.21.1 shader-import ABI prefixes `shaders/include/` before parsing a
ResourceLocation, so its shared projection is an added
`minecraft:shaders/include/ringworld_projection.glsl` resource rather than a
modern namespaced import. It replaces no stock include or Globals file. The
GPU probe consumes that exact shipped include. Both Atlas UI verifiers also
require actual correction-widget On/Off, 1/3/8, restored defaults and five new
captures. Compact Display details use a separate layout to avoid footer overlap.

All thirty upstream preview PNGs are 384x213 downsampled versions of the older
640x355 images (same aspect ratio), totalling 2,370,843 bytes instead of
12,684,720. They are not byte/pixel-identical lossless replacements. The
Industrial engineered pair was inspected. Editor and standalone wall selector
now share one normalized-UV drawing helper; draft/apply behavior is unchanged.
Native creation-screen review remains required. Client teardown now also resets
the integrated-frame-feedback mailbox and correction visibility cache directly.

The P5 dual build and unit/contracts pass (Fabric 598 / NeoForge 621). Initial
source guards correctly rejected the old altitude-gated horizon/two-disc
assumptions and were updated to require the new continuous-backdrop behavior,
retaining the exact native method ABI, special-fog guards and ring-before-sun
ordering. Real-client execution is the next diagnostic checkpoint; no GPU,
production visual or performance PASS is inferred from compilation.

### P5 bounded graphical checkpoint

All four fresh source-ABI UI runs pass at `7b811be`: 26 menu-only creation
captures and 21 expanded Atlas captures per loader (94 total). Each numerical
GPU probe passes 9,504 samples on the NVIDIA GTX 1080 Ti / 560.94 driver, with
identical maximum CPU/GPU component error `0.0011974855266316808` blocks.
Both integrated servers persist complete format-11 Atlases after ordered
stone revisions and normal disconnect; raw correction/feedback/session teardown
passes. The Fabric compact Display/default/details and progressive handoff
captures were inspected. Evidence, PNG hashes, independent disk receipts and
source archive are under `.codex-tmp/b14-render-ui/`.

These are bounded functional graphical diagnostics. Production-size weather/
lifecycle, real dedicated multiplayer/worldgen/raid, exterior placement and
visibility, underwater/reload, repeated memory/performance tests and the frozen
acceptance runner are not yet qualified. 1.20.1 implementation has not started.

## Extended native acceptance fixtures (in progress)

The 1.4 headless concurrency/pause/drain/cancel/restart and synthetic-FPS
backoff/recovery probes are being ported before production input preparation.
They remain opt-in and explicitly do not replace healthy real-client FPS tests.
The optional headless metrics retain raw previous-tick nanoseconds as well as
summaries. An opt-in owner-span log measures partial/complete progress counting,
so Large-world status cost is measured rather than inferred from the complete
Atlas shortcut. These new fixtures require build and native execution before
credit. Production-world inputs will be copied; original 1.3/Beta saves stay
untouched.

### Extended native and production-input checkpoint

The four opt-in native runs pass at `a4658f9`: pause/drain/cancel/restart and
fixed-rate persistence on Fabric/NeoForge, plus synthetic-FPS 4→2→1→2→4 on
each. Each independent disk receipt finds all 262,144 cells. Raw tick samples
independently reproduce reported percentiles and slow-tick counts; these are
headless functional diagnostics, not ordinary-gameplay performance results.
Evidence is `.codex-tmp/b14-native-probes/results.json`.

Production preparation has completed a copied 16,384×256 Atlas-11 master with
all 4,194,304 cells and unchanged original-save inventory. A fresh 32,768×512
native world is generating under a four-GiB server heap. Producer source,
jar, raw logs/GC data and stopped-master inventories are retained under
`.codex-tmp/b14-worlds/`; this input preparation is not acceptance.

The next native exterior fixtures port the upstream two-client placement,
policy/save/reopen, render-pass visibility and vanilla void-damage scenarios.
The placement fixture adds survival inventory preservation for rejected stone
and beds, both possible exterior bed halves, and stale operator fingerprints.
The 1.21.1 render path requires a dedicated exception for the native
`LevelRenderer.isSectionCompiled` entity gate outside finite Z. Interior and
non-Overworld behavior remain vanilla. Real model-render entry is recorded
only under the opt-in visibility flag. Dual build/contracts pass with these
fixtures; actual packaged execution remains required.

The graphical runtime verifiers now read ordered date/sequence rollover archives
plus `latest.log`, bounded to 64 MiB per attempt. A real gzip fixture retains
an earlier failure across midnight and rejects corrupt archives. Runs must own
fresh log directories; archives from a prior attempt are not valid evidence.

### Partial Large progress cost

The old diagnostic Large producer exposes increasing partial-status cost:
674 observations through 3,540,224/16,777,216 cells include a 27.1799 ms maximum.
This is one headless query, not a three-player gameplay/performance verdict.
A stable atomic checkpoint with 3,583,488 present cells was independently
validated and retained at `.codex-tmp/b14-worlds/large-partial-checkpoint/`.

The candidate replaces partial `presentChunkCount()` scans with a derived short
cell count per canonical chunk and an exact complete-chunk counter. First
presence, repeated edits/tiles, loading, snapshot copying, reusable checkpoint
repairs (including removals) and clearing all maintain that index. The wire/disk
format remains 11. Large source-step-1 memory adds 128 KiB per Atlas; the
instance memory estimate includes this derived storage. Both chunk-presence and
progress queries are constant time; first-missing iteration checks chunks.

Ten new parameterized cases pass on each loader, using an independent full cell
oracle at all five legal sample steps and non-power-of-two dimensions. Full
unit totals are Fabric 608 / NeoForge 631. Real partial Large status/three-client
measurements still need the newly frozen candidate; the running producer and
queued exterior diagnostics retain their earlier exact source/jar identities.

### Final mainline fixture-stability adaptations

The native GoalSelector regression now proves the tagged fixture navigator owns
MOVE without disabling LOOK, retrying a path or changing arrival/timeout checks.
Registered lifecycle players enter actual Creative mode before Atlas waiting,
and the server reapplies abilities to their replacement after reopen without
healing or reviving them. Unexpected death fails the client. Layout switching
uses a fresh server-owned Spectator acknowledgement for each copied session.

Production captures require a coherent complete published scene, including the
previous texture during a crossfade, while allowing harmless newer revisions to
build. Rim/seam captures independently require the arrived camera/rim chunks.
The immutable foliage fixture retains its stronger drain checks and now binds
exact Atlas-11 identity `99266ba30dab5309`, decoded independently from the new
production master; a Java identity assertion matches that reader. No wildcard
or older-format guard is used.

Both full builds pass (Fabric 610 / NeoForge 633, zero failures/errors/skips).
The first build failed on a missing `DeathScreen` import; the repaired build
passes with no assertion changes. Native execution of the affected lifecycle,
world switch, navigation and production-capture cases is still outstanding.

### Native visual feature fixtures and raw measurements

Mainline's underwater, horizon, floating-Atlas/platform, wall/altitude and cloud
fixtures are adapted to native 1.21.1 creation, camera, teleport, gamerule and
screenshot APIs. Only native-version source copies are opened; the 26.x
file-fix upgrade helper is not applicable. Native 1.21.1 has no separate
vignette/inactivity options used by the newer fixture. Normal controls remain
unchanged outside opt-in flags.

The underwater fixture keeps all six mainline states and adds a rear camera
below the open pool surface while the player's eyes remain dry. It independently
asserts actual camera fluid, suppressed proxy drawing and above-water recovery.
Floating-Atlas floor edits wait for normal recapture to drain even when the
input cache was already complete, then wait for the client to match the baseline.
Natural/short-gap/thick-layer controls, stacked builds, seam, colour/light/water,
support edits, initial capture and normal save/reopen assertions remain intact.

Opt-in `ringworld.rawFrameEvidence` retains every measured projection, natural
seam and foliage duration in bounded, exclusive RWFR files. Ordinary play
allocates no sample buffers and writes no measurement files. An independent
reader validates header/length/positive samples and computes percentiles and
slow-frame counts; it deliberately produces no performance PASS verdict.

`scripts/run_backport_client_diagnostics.py` defines 24 serial packaged cases
per loader (at least 143 captures and 24 raw frame windows), using inventory-
verified official class/module paths and the actual copied runtime jar. Existing
Gradle preparation/verifiers run in fresh contained qualification directories;
new native controls require every named PNG. Saved Atlas data is independently
decoded, original inputs are inventoried and preserved, and optional successful
world retirement retains file inventories plus settings/maps/player/Atlas data.
Failed attempts retain their worlds. Source drift, missing/corrupt samples or
captures, explicit failures and abnormal exits cannot pass. These tools label
every result diagnostic-only and cannot substitute for the 68-family final
acceptance/performance/platform/review verdict. Both full builds pass at this
checkpoint; the new native graphical cases have not run yet.

Ten Python diagnostic/measurement/workflow checks pass and are wired into the
static workflow. Their result is tooling validation, not Minecraft execution.

### Production inputs and packaged mapping failure

Both stopped production masters pass independent disk verification at producer
`a4658f9`: Medium has 4,194,304 cells (233.61 seconds); Large has all
16,777,216 cells (3,175.86 seconds), with four-GiB heap, clean all-dimension
shutdown and no save/OOM error. Their inventories, hashes, GC logs and source
archive are under `.codex-tmp/b14-worlds/`. The original save inventory remains
unchanged. Large's old partial-status path has 3,121 observations: 1,170 exceed
50 ms and two exceed 100 ms, maximum 107.7832 ms. Those direct owner spans justify
the incremental count fix. Vanilla tick history excludes some end-hook work;
its headless summaries are not inclusive gameplay performance qualification.

The first packaged exterior attempt at `b62bce9` fails client startup. Both
refmaps exist, but `ringworld.client.mixins.json` selects the common table, so
required sleeping/disconnect targets and nested client classes cannot resolve.
The attempt and watchdog/normal server shutdown remain at
`.codex-tmp/b14-exterior-fabric/placement/FAILED.json`. Waiting follower runs were
canceled without launching Minecraft and have explicit NOT_RUN receipts.

Moving the configuration into `src/client/resources/` lets Loom select
`client-ringworld-refmap.json`; NeoForge still consumes its native named
configuration. No injector requirement or target was weakened. The new jar
validator reproduces the old candidate failure and verifies every generated
mapping selected by each referenced config, plus required native client entries.
It is mandatory for Fabric `check`. Both builds and its three pure regressions
pass. A fresh packaged rerun remains required; source-development UI successes
could not establish this distribution linkage.

### Exterior runtime and inclusive performance measurements

The corrected Fabric packaged runtime passes placement, persistence/reopen and
all seven exterior visibility stages, including real player/cow/boat rendering,
both rims, range rejection, falling and vanilla void damage. Its 32 captures,
logs and source identity are retained at `.codex-tmp/b14-exterior-fabric-v2/`.
This is bounded diagnostic evidence at `4a8a599`, not final acceptance.

The NeoForge rerun fails before either client joins: its inherited launcher
puts the parent `1.21.1.jar` on the module path under a basename absent from
the official child profile's ignore list. The installed patched game then
collides with that raw game module. The failed attempt is retained and
classified as a harness-input failure; no gameplay assertion ran. A separate
seed uses the official child-version basename with byte-identical, Mojang-hash-
verified game content and unchanged official JVM arguments. Its native rerun
remains required.

Opt-in inclusive server timing now surrounds `tickServer`, including loader
tail hooks, and accounts for scheduled owner work while excluding only normal
scheduler parking. Managed blocking during a tick or queued task stays counted.
Per-instance bounded primitive buffers export raw RWTK samples at shutdown;
ordinary play allocates no evidence buffers. Records retain game/scheduled/idle
durations, tick/player/phase identity and available owner CPU time. An independent
reader rejects malformed, overlapping, incomplete or hash-mismatched records,
then computes work percentiles and loop intervals without issuing a verdict.
Four shared accounting cases and three reader regressions pass. Both full
builds pass 614 Fabric / 637 NeoForge cases. A deliberately delayed late-hook
native probe is still required before using this meter for performance gates.

Fresh exterior diagnostics suppress the vanilla first-run tutorial overlay and
detect abnormal client startup exits promptly. Earlier captures remain retained;
their overlay is not evidence of a RingWorld layout defect.
