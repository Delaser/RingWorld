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

The inclusive meter's native late-hook proof passes on both official dedicated
runtimes at `ef5e517`. Fabric's delayed hook is 100.959 ms, its vanilla stored
sample 1.040 ms and its enclosing measured game work 102.316 ms. NeoForge's
corresponding values are 100.871 / 5.100 / 106.332 ms. Raw samples, independent
summaries and normal shutdown evidence are under
`.codex-tmp/b14-inclusive-tick-probe/`. These deliberately stressed runs prove
the measurement boundary only, not ordinary-play performance.

NeoForge's corrected packaged profile now launches both real clients and passes
placement/reopen. Its visibility run reaches stages 0–3, then times out waiting
for client B's stage-4 render proof after returning from beyond tracking range;
client A passes stage 4. The failure and clean save remain at
`.codex-tmp/b14-exterior-neoforge-v3/visibility/`. Added dormant fixture logs
retain the pending entity/render inventory and a screenshot without weakening
the ten-frame assertions. A visibility-only rerun is diagnostic-only.

The native saved-data reader catches decode errors and returns null. RingWorld
now distinguishes that from confirmed file absence before using defaults for
geometry, building, sky or structure policy. Unreadable files, directories at
file paths and incomplete saves with orphan policy files reject without
replacing the original bytes. Building and sky state are checked before
publishing Overworld geometry or admitting players. Missing options in genuine
older worlds keep their documented defaults. File-preservation and malformed-
codec regressions are added; native corruption/rejection checks remain pending.

The pending NeoForge visibility trace establishes a fixture ownership bug:
the stage-3 cow unloads with `UNLOADED_TO_CHUNK`; the next stage discards that
stale reference while the same UUID reloads as a different native entity.
Independent inspection of the failed world's entity region confirms the extra
saved cow. Current player/cow/boat extraction succeeds. Failed runs v3/v4/v5
remain retained. The fixture now holds region tickets for its actual object
chunks until explicit removal, removes objects before observer teleports, and
asserts both native identity and removal from the index. Exact entity-count,
ten-render-frame, range, seam and void checks remain unchanged; reruns on both
loaders are required. Both builds still pass 617 / 640 cases.

Fabric's seven native saved-state controls pass at `c2b5ca6`: healthy complete
reopen; corrupt geometry/building/sky/structure; orphan policy; and a genuine
unmodded 1.21.1 world. Negative cases reject before `Done` and retain exact
invalid policy bytes. Their startup failure also exposes vanilla's secondary
shutdown exception when no Overworld was constructed; this is retained as
expected negative-case output, not described as a normal gameplay shutdown.
NeoForge's corresponding native slice is in progress. Evidence is retained
under `.codex-tmp/b14-saved-rejection/` and remains diagnostic-only.

All fourteen native saved-state controls complete on Fabric and NeoForge.
The held-entity visibility reruns also pass on both at `ff78c1d`, including all
seven stages, explicit entity identity/removal, natural seam and vanilla void
damage. Evidence is under `.codex-tmp/b14-exterior-neoforge-v6/` and
`.codex-tmp/b14-exterior-fabric-v3/`.

The first packaged graphical matrix passes Fabric's 26 creation captures and
its GPU projection probe, then fails Atlas UI's build identity assertion: the
UTF-8 argfile gives the Windows launcher `Â·` instead of `·`. The actual rendered
identity is correct. A separate native Java probe reproduces the argfile mismatch
and proves byte-correct UTF-8 property content when the label is passed as a
native process argument. The driver now uses that boundary; the identity
assertion remains unchanged. The failed attempt stays at
`dist/qualification/b14-packaged-clients-fabric-v2/atlas-ui/`. Subsequent cases
and the NeoForge graphical matrix did not run in that attempt. This harness
correction requires a fresh run; it does not change either frozen mod jar.

At runner checkpoint `0520074`, Fabric's packaged creation, Atlas UI, map/compass
and curved-object cases pass. Atlas UI retains all 21 captures, the native GPU
probe, ordered live revisions and normal session teardown. Map/compass retains
its eight captures and persistent-state reload checks. This is still bounded
diagnostic evidence, using the unchanged `ff78c1d` product jars.

The next underwater case is blocked at vanilla's first-run narrator/accessibility
welcome screen, confirmed by computer-use inspection. It is closed normally
before any world is created and classified as a harness-input failure. The
driver now normalizes onboarding/tutorial/focus/autojump options for every fresh
profile while preserving fixture graphics, frame limits and GUI scale. Its
normalization regression proves preservation and idempotence. The failed output
is retained at `dist/qualification/b14-packaged-clients-fabric-v3/underwater/`;
the water assertions still need native execution.

The future 1.20.1 portable Java 17 archive is verified and extracted locally.
No 1.20.1 implementation, build or acceptance result is claimed. Approximately
726 MiB of byte-identical disposable installed-library copies from successful
diagnostics are retired with complete hash inventories and retained immutable
seed counterparts. Failed worlds/logs, production masters, native raw samples
and original runtime seeds remain preserved.

### Reproduced cache-history collision and content-bound reuse

At `90a5674`, the isolated real `ClientRingState` regression fails: two complete
Atlases with identical saved settings/world hash and revision 42 but different
edited heights certify the foreign disk cache as current. Its source, Gradle
init recipe, failed XML and raw log are retained under the cache-collision audit
paths. This is a product correctness failure, independent of the earlier
launcher/profile issues.

Atlas content now has an incremental 64-bit non-cryptographic fingerprint over
present cell index, signed height, top/side RGB and packed light/water. Changes,
wire merges, disk reads, snapshots, clear and repaired reusable checkpoint copies
maintain it; getters do not scan. Checkpoint completion and verification also
bind the copied content. Disk format 11, world/layout hash and revision ordering
remain unchanged. This fingerprint is a cache consistency check, not a security
or cryptographic-integrity claim.

Both sides require revision **and** content equality for complete-cache reuse.
Mismatched cached content is discarded before provisional rendering. Metadata
advances to `terrain_atlas_metadata_v5`, request to `terrain_atlas_request_v3`,
each adding one fixed-width long; tile v4 / revision v1 remain unchanged. Required
capability negotiation rejects older layouts. This deliberate correction is
recorded in the dual-version plan and must be carried into the 1.20.1 adapters.

Both builds pass 626 Fabric / 649 NeoForge cases. Tests retain the exact
previously failing collision, positive reuse, all rendered fields/write order,
five sample steps, wire/disk/snapshot/checkpoint repair and independent packet
byte vectors. An independent Python disk reader computes content identity on
request and agrees with the fixed signed-height/packed-light vector. Its streamed
boundary/presence checks pass. Initial compilation failed only on an undeclared
test IOException; repaired full builds pass without weakening assertions.

The new packaged native cache fixture performs three normal opens/disconnects:
fresh authoritative content; a deliberately foreign valid same-revision local
cache that must be rejected before streaming and repaired; then exact positive
cache reuse. It preserves original/foreign cache files and raw metadata decisions
for independent decoding, plus three captures. It has compiled on both loaders
but has not run yet at this checkpoint. The graphical recipe now has 25 cases.

Before the source change, Fabric's underwater seven-state, horizon sixteen-state,
floating-Atlas/platform/support-edit/save/reopen, production lifecycle and
different-layout switch cases pass. Floating captures have 2,416 settled frames
across five windows and zero over 50 ms; these summarized windows are diagnostic,
not final raw-measurement performance acceptance. The queue is deliberately
stopped after normal layout-switch completion with an explicit cancellation
receipt. Remaining captures and the NeoForge graphical matrix were not launched.
All those earlier successes remain historical diagnostics and must be refreshed
on the changed candidate.

The first native cache fixture at `53cd0af` proves rejection before streaming,
full repair and positive reuse, then correctly fails its unchanged-world control.
Independent cell comparison finds one changed top RGB at X=86/Z=32, with height,
side, light/water and presence unchanged; saved entity inspection finds sheep
beside that cell. Random-tick and spawning controls do not prevent existing sheep
from modifying grass. The fixture now disables mob griefing in its disposable
copy; every content/revision, metadata and teardown assertion remains intact.
The failed world/cache files and logs are retained at
`dist/qualification/b14-cache-regression-fabric/`. NeoForge did not launch after
that failure. Fresh native runs on both loaders remain required.

At `0f2e1b0`, Fabric's native fixture passes all three stages and normal
disconnects: foreign revision-80 content is rejected with zero provisional cells,
the full server stream repairs it, and the third open reuses all 262,144 cells
with identical revision/content. The Python wrapper then fails because it
incorrectly requires the initial cold-login revision/content (revision 11) to
equal the subsequently settled authoritative baseline (revision 80). That cold
phase legitimately includes normal loaded-chunk recaptures. The verifier now
permits only that initial observation to precede the baseline; both actual
rejection/reuse controls still require exact baseline revision/content and all
three retain strict empty/complete and disconnect checks. Regression coverage
includes the cold transition and rejects a mismatched rejection-stage revision.
The failed wrapper attempt is preserved at
`dist/qualification/b14-cache-regression-fabric-v2/`. Both fresh native wrapper
runs remain required; NeoForge was not dispatched after the wrapper failure.

### Cache regression completion and continuous performance evidence

Fresh packaged Fabric and NeoForge cache regression v3 runs both pass all three
normal opens/disconnects at runner `5cf0258` with the unchanged `0f2e1b0` product
jars. Independent decoding verifies foreign same-revision content rejection
before provisional rendering, complete authoritative repair, and subsequent
exact cache reuse. Both settle to content fingerprint `6faaad226ea8d420`;
Fabric revision is 91 and NeoForge revision is 142. The earlier product and
harness failures remain preserved separately.

The independent Large Atlas checkpoint stress collection also completes on both
loaders from `5cf0258`. Each retains two warmup operations and six ordered AB/BA
pairs. Every one of the fourteen gzip outputs per loader is byte-identical to
the immutable source `f51d867ae68969c8dd42ee37e864b44259d6d490d8c397b01cb6dd78a3454b04`.
Measured asynchronous owner steps are at most 2.8668 ms on Fabric and 2.1181 ms
on NeoForge; synchronous controls take roughly 3.4 seconds. Inclusive raw tick
streams and normal all-dimension shutdowns are retained. This is deliberately
checkpoint stress evidence, not ordinary gameplay performance qualification.

An opt-in continuous native frame recorder now uses a bounded primitive buffer
pool and one disk worker. Exhaustion, failed writes, rejected work and missing
tail windows cannot silently produce success. Shutdown receipts bind the entire
sequence and sample count. Monotonic/UTC clock anchors permit joining independent
server/client windows; start/end client anchors expose clock drift. Uninstrumented
gameplay allocates no frame buffers. The shared server sampler also records its
clock anchor without changing the raw tick format.

Both strict builds pass 630 Fabric / 653 NeoForge cases. The new tests exercise
delayed worker ownership, buffer exhaustion, disk/rejection failure, contiguous
windows and shutdown. Independent Python readers check hashes, exact duration
sums, clock bounds, ordered windows and a mandatory completion receipt. The
serial packaged performance collector uses three real creative/flying clients,
a 4 GiB server, 2 GiB per client, 28-chunk view, 8-chunk simulation, raw GC logs,
inclusive ticks, continuous frames and repeated native health/pose observations.
Its first short instrumentation run is still pending. It cannot award release
acceptance; paired baseline comparisons, production traversal, scheduled-save
persistence checks and the 60-minute soak remain required.

The first three-client Fabric run completes native joins, warmup, a measured
minute, all three healthy players, complete continuous frame streams and normal
client/server shutdown. Its collector then fails its original absolute 5 ms
clock-drift guard: client A drifts -5.094 ms across 187 seconds, B -3.624 ms
across 151 seconds, C -2.882 ms across 120 seconds. This proportional drift is
consistent with gradual UTC clock adjustment, rather than missing frames.
The attempt and raw streams remain retained as a failed diagnostic.

Clock joining now calibrates an affine monotonic-to-UTC mapping from native
start/end anchors on both server and clients. Raw performance durations remain
unscaled monotonic differences. The correlation rejects excessive bracket
uncertainty, unbounded clock steps and rates outside 500 ppm plus a fixed 5 ms
allowance; this is an instrumentation bound, not a gameplay threshold. The
collector retains its exact measured window and health observations before
postprocessing. Unit coverage includes the observed gradual adjustment and a
rejected one-second clock step. Fresh native collection remains required.

### Completed bounded collections and a real partial-generation shutdown failure

At `076b312`, fresh three-client native collections pass on both loaders. The
60-second measured stationary Small windows each contain 1,199 inclusive server
ticks: Fabric mean/P95 5.282/6.381 ms, NeoForge 5.626/6.856 ms, zero over 50 ms.
All six measured client streams average 16.756–16.772 ms at a 60 FPS cap, with
zero frames over 50 ms. Exact complete streams, health/pose observations, normal
stops and independent saved-Atlas checks are retained. Z=64 is immediately
outside this Small world's maxZ=63. These are instrumentation diagnostics,
not interior/production traversal, paired-baseline or soak qualification.

Both loaders also pass all four native worldgen phases and the aggregate matrix:
production fresh/reload, deliberate seam crossing and scarce-structure terminal
policy. Current Fabric additionally passes creation UI, Atlas UI, cache identity,
maps/compasses, curved objects and underwater. All preceding attempts remain
separate; final replacement qualification is still required.

The following Fabric horizon run captures all sixteen controls, then stalls at
normal logout while background Atlas generation is 88.9% complete. Two native
thread dumps show an unbounded chunk-unload retry loop; an allocation histogram
contains approximately 2.89 million completion callbacks. Shutdown exceeds
120 seconds. The process is stopped after retaining diagnostics; its disposable
world remains intact as failed evidence. Capture completion does not earn PASS.
Both serial queues stop and no later graphical/gameplay test is dispatched.

The native retry can observe a completed save future while generation references
still need queued chunk-owner work. Unbounded saving must service that owner
between callbacks rather than endlessly rescheduling the retry. The Overworld
save drain now does so without discarding callbacks or narrowing the native
flush. Ordinary tick ceilings and Nether/End behavior remain unchanged. A
regression with real completed futures reproduces repeated retries and proves
owner progress permits exactly one durable write without cancelling the future.

The audit also finds early cancellation registered only for headless runs.
Both loader stop hooks now freeze every Overworld Atlas and release background
leases before native chunk draining; final coherent checkpoint/worker shutdown
remains owned by level unload. Pending-release counts are logged. Both strict
builds pass 632 Fabric / 655 NeoForge cases. The first compile attempt failed on
a test method declaration typo; the corrected full builds pass.

The graphical runner now independently enforces a 120-second normal shutdown
deadline after native completion and captures a thread dump before retaining a
failed attempt. Its tests reject capture-only success with a still-running
process and preserve nonzero native exit codes. Fresh horizon retests on both
loaders and an ordinary dedicated interruption/resume proof are now complete.
Both horizon runs capture all sixteen views and stop normally; Fabric releases
four pending Atlas loads and completes all-dimension saving within four seconds.
Both loaders also pass ordinary interruption, paused reopen, headless completion,
and byte-identical complete reopen on fresh 4096x256 disposable worlds. Exact
records are retained under `dist/qualification/b14-horizon-shutdown-*` and
`dist/qualification/b14-ordinary-stop`; these remain diagnostic evidence.

### Wall curvature investigation

The owner reports that wall curvature appears inconsistent with terrain. The
shared mainline nearby correction deliberately makes angular spacing depend on
vertex height. Both live terrain and the private Atlas shader use that function;
this is not evidence of separate circumference settings. A numerical diagnostic
for the 2048-block ring/default three-chunk correction finds a 0.803-degree
sea/crest angular difference at delta X=48. Straight interpolation between the
inner proxy wall's Y62/96 endpoints deviates by at most 0.049 blocks in that
model, versus 0.673 blocks for the full outer Y-64/96 face. A build-height crest
can produce substantially larger errors. These calculations do not identify
the sole cause of the reported appearance or establish native visual acceptance.

A new opt-in horizon projection control waits for complete Atlas/render readiness
and captures identical ground/elevated poses with correction on and off. It
retains normal reload/dimension/stop controls. Default horizon behavior is
unchanged. Fresh native comparison is required before selecting a correction.

The fresh complete-Atlas comparison passes on both loaders at `1761485` with
eight captures each, resource reload, dimension controls and normal stopping.
The broad wall arc remains with correction disabled. No separate circumference
or radius path was found: live terrain/water and the private mesh share the
same projection. Crest elevation and correction alter its apparent curve;
the transition from detailed live water to plain Atlas water appears to
exaggerate separation. This is a static visual observation, not a pixel-ownership
or motion proof. The water transition remains an open visual-quality concern.

Fabric's complete two-client gameplay matrix also passes at `1761485`, including
the strict portal routing, bed reconnect, death and weather controls. The
following raid arm server saves normally, but a client fails with a native
GLFW access violation around automated window closure. The JVM crash report
identifies the render thread in `RenderSystem.limitDisplayFPS`; it does not
establish a RingWorld cause or a vanilla/environment cause. The attempt remains
FAIL, with both server save and client crash evidence retained. Its closure
receipt was written prematurely after the input API timed out; process exit
validation correctly rejected the run. Future closure receipts must follow
verified normal client exit.

An opt-in raid client now stops through Minecraft's native loop after a joined
session disconnects, asserting raw session teardown first. The coordinator must
still independently require the raid's durable-save/victory marker and normal
server/client exits, so an unexpected disconnect cannot earn PASS. Fresh raid
arm/reload runs and separate investigation of the native window-close failure
remain required.

At `e6ee066`, Fabric again passes the full multiplayer matrix and both raid
stages, including vanilla victory/Hero of the Village and normal native client
exits with raw session teardown. The NeoForge multiplayer run then suffers an
ambient creeper death immediately after its successful End return, while the
survival observer waits for the weather phase's stability gate. The other client
finishes locally and leaves; the weather fixture returns early for the missing
peer before reaching its timeout. This attempt remains FAIL. A PID/path-bound
temporary attach agent queues a normal halt on the owned NeoForge server without
transforming classes; its source, archive, cleanup reason and all-dimension save
remain with the failed evidence. No injected run is qualification evidence.

The disposable multiplayer fixture now disables ambient spawning at its first
world tick. Explicit hostile-navigation, explosion and combat subjects and all
their assertions remain. No player is healed or revived after an unexpected
death. Portal/weather phases immediately reject a dead observer, and missing
peers cannot bypass the phase timeout. Fresh dual-loader multiplayer/raid
coverage is required. Both graphical queues have stopped before dispatching
their remaining cases.

The no-RingWorld Fabric/API title-menu window-close control exits normally for
both clients. The loaded GLFW DLL exactly matches its pinned native-archive
bytes (`affa7e54eb0dedce4a5721c327c1a16035edbbd039cd402e08107d6d2d55eb1a`).
This is not an identical disconnected-server control and does not classify the
earlier access violation; that investigation remains open.

### Current-candidate gameplay and foliage isolation

At `898781c`, both loaders pass the full two-client multiplayer matrix, raid
arm/save, and raid reload through vanilla victory/Hero of the Village, including
normal client exits and raw session teardown. Records are retained under
`dist/qualification/b14-packaged-gameplay-isolated`.

The subsequent Fabric graphical rerun passes floating Atlas, production
lifecycle, different-layout switching, walls, altitude, clouds, all four
production lighting environments, safe-small 6/12/28-chunk seam/rims, and the
production seam/rims gate. It then fails foliage: the Atlas changes from
revision 436 to 437 immediately after a 200-tick quiescent observation. The
attempt saves all dimensions normally and remains FAIL under
`dist/qualification/b14-packaged-clients-fabric-isolated/foliage`. NeoForge and
the queued completion/report operators stop before dispatching dependent work.

A separate, explicitly unqualified private overlay inserts only two conditional
JDK stack traces into the Atlas service. Its fresh native reproduction identifies
`EatBlockGoal`/sheep AI changing `(4194,63,46)` just after post-fixture quiescence,
followed by revision 575 to 576 and the unchanged assertion failing. Pinned Tiny
mappings identify Fabric `class_1345` as `EatBlockGoal`. An earlier bounded trace
also sees underground scheduled lava changes; those are not asserted to cause
the post-quiescence failure. Overlay source, class before/after bytes, hashes,
control receipts, raw logs and failed world remain under
`dist/qualification/b14-foliage-mutation-trace`. No overlay is a shipping or
qualification artifact.

The foliage fixture now disables mob griefing in its disposable copied world,
alongside its existing zero random-tick speed. This prevents sheep AI from
invalidating the fixed visual snapshot. Explicit foliage edits, live recapture,
revision stability, streaming coverage and motion assertions remain intact.
Fresh dual-loader foliage and exact-candidate regression evidence are required.

The owner requested a screenshot report upon completion of 1.21.1 testing.
The prepared PDF/HTML report generator requires current-candidate terminal/image
hashes and complete dual-loader graphical/gameplay records. It does not generate
a completed report from the interrupted queue. Performance/soak, the native
manual-close investigation and moving wall/water review remain acceptance gates.

### One-block foliage isolation contract

At `a7553fb`, the native Fabric foliage client completes all nine captures and
144 motion steps, with 1,370 measured frames, zero frames over 50 ms and zero
unsafe coverage frames, then saves normally. Its verifier still fails, so the
attempt under `dist/qualification/b14-foliage-fixed-fabric` remains FAIL.
The legacy verifier requires the pre-placement, post-placement and post-clear
Atlas revisions to be equal. Its leaf planes were deliberately placed on an
eight-block sample boundary. The one-block Atlas correctly samples those
authoritative leaves: the motion phase retains revision 407, and clearing
advances to 527. Loosening the revision assertion alone would also invalidate
the old pixel mask's claim to isolate live foliage from the proxy.

The revised fixture retains server-authored leaves and all motion, compile,
coverage, blocked-sky and same-pose repeatability checks. It records the native
one-block leaf sample and changed content fingerprint, then creates two
explicit Atlas-only controls at the same post-fixture revision/content by
temporarily restoring the original block states only in the client's diagnostic
volume. A server-thread proof requires all 7,508 authoritative leaves and the
unchanged Atlas to remain present. The existing pixel algorithm uses these
same-Atlas controls to isolate live geometry. No production sampler, proxy,
protocol, or recapture behavior is disabled or overridden.

The authoritative cleanup restores all 15,015 original block states, including
positions that the checkerboard assigned air. Volumes containing block entities
are rejected before any placement, so a block-state-only snapshot cannot discard
their data. The fixture requires client agreement, drained native work and exact
Atlas content-fingerprint restoration; revisions must advance in the correct
order. Separate restored-world captures remain. The shared verifier validates
all controls, and its pure contract includes twelve negative cases for stale
Atlas, missing native leaves, incomplete authority/restoration and malformed
content evidence. These changes require fresh builds and real-client validation
on both loaders; no acceptance PASS is claimed from the design alone.

### Early Creative-mode capture setup

At `1e64438`, both exact packaged foliage runs pass all eleven native captures,
same-Atlas ablation authority and exact original-block/content restoration.
Both report zero unsafe frames and zero history residual. Fabric records 1,373
motion frames, P95 18.495 ms and one 50.200 ms frame; NeoForge records 1,370,
P95 18.528 ms and maximum 33.331 ms. These are diagnostic frame windows, not
paired performance acceptance. Both loaders also pass all six multiplayer and
raid arm/reload phases, including vanilla victory/Hero, normal native exits and
raw session teardown. Current records are under the `*-foliage-oneblock` roots.

The following Fabric graphical queue passes fourteen cases before production
noon. During that fixture's complete-Atlas wait, its copied Survival-world
player is killed by a zombie. Setup changed the game mode only after the Atlas
barrier, and the death screen bypassed further fixture ticks. The failed world,
logs and earlier passes remain retained. The operator exits the failed world
through the death screen's Title Screen action, then Quit Game: all dimensions
save normally, but missing native completion correctly fails the attempt.
Dependent graphical/report/performance operators stop; no report is generated.

The owner explicitly requested Creative by default unless a test requires
another mode. Opt-in integrated visual fixtures now prepare only their local
owner in Creative flight at login, before ordinary server ticks and Atlas waits.
The shared capture bridge also uses Creative, and the copied-world projection,
seam/rims and foliage clients await server-owned Creative/flight acknowledgement
before their readiness barriers. Death still fails; no player is healed or
respawned. Dedicated multiplayer and deliberate Survival phases are excluded
from this login policy. Horizon captures now retain Creative. Explicit observer
or collision controls that require another mode remain separate.

The packaged graphical driver requires the initial server Creative proof and
rejects an explicit native failure immediately rather than waiting thirty
minutes on a death screen. Its nine Python regression cases pass, including
the new immediate-death refusal. Fresh strict builds and full exact-candidate
native reruns are required; this repair is not yet natively validated.

The owner cancelled the twenty-minute scheduled follow-up. It is deleted;
work continues in this chat under the instruction to finish 1.21.1 and prepare
it for publishing. That instruction does not authorize uploading a release.

### Resumed graphics and cloud-camera review

The owner requested resuming at check 15. Fourteen completed Fabric cases and
six dedicated gameplay/raid phases remain bound to their original `1e64438`
source/jar hashes. Both `cf5fe1c` focused Creative suites pass noon, seam/rims-6
and foliage; the remaining graphical cases then complete on both loaders.
One streaming attempt launches Fancy while its verifier requires Fabulous.
That failed attempt remains retained. An explicit matching Fabulous launch and
unchanged verifier pass; the packaged recipe now binds both properties.

All 26 graphical verifiers per loader pass, with a composite evidence index.
The generated, undelivered 48-page PDF and 326-capture HTML review expose a
cloud-fixture problem: its fixed X=1192 camera is inside stone below the cloud
deck and dirt inside it. Independent original-source Anvil decoding confirms
those blocks at Y=88/106. The native PASS records are retained but do not earn
cloud visual acceptance. Other completed cases are preserved.

The repair changes only the opt-in cloud setup: select a clear column using
the server-owned Atlas and actual heightmap, verify all three native player
volumes, and keep that column for all six Fancy/Fast captures. Each capture
also requires actual client air/collision/position/flight proof. Terrain and
cloud projection are not edited. Native dual-loader reruns and fresh image
review remain pending. The screenshot packet will be refreshed after them;
performance, soak, upgrade, native-close and formal acceptance remain separate.

### 16 October: visual completion and closed-world retention repair

The owner requested continuation from check 15. All 26 graphical native
verifiers per loader now pass as explicitly composite evidence. Only clouds
were repeated at `13f6e78`: all six unobstructed-camera proofs and Fancy/Fast
below/inside/above captures pass on both loaders. The 51-page PDF and
326-capture HTML packet was rendered, visually inspected and delivered with
release acceptance pending. The rejected 48-page packet is preserved.

Both exact `13f6e78` jars also pass ten ordinary native disconnect/reopen cycles,
37-diamond inventory and UUID disk checks after each disconnect, raw session/GPU
teardown and eleven normal all-dimension saves. The first failed attempt is
retained: Windows wrote CRLF where the disk acknowledgement expected LF.
Writing exact binary LF repaired that handoff without changing the game.

Resource acceptance did not pass. A separate diagnostic waits for ordinary
texture/cache workers to finish, then requests full GC after normal disconnect.
Fabric's post-full-GC heap grows from 347 to 945 MiB over ten cycles; tracked
native Other grows by about 8 MiB per cycle. NeoForge reproduces similar growth.
Fabric's retained diagnostic histogram contains eight client levels, eight
integrated servers and 24 server levels, while only two Atlas instances and one
active Atlas-service state remain. The histogram's additional collection is
disclosed. These forced-collection windows are not ordinary performance data.

The client chunk registry used weak manager keys but strong storage values.
Pinned mappings confirm storage's `field_16254` refers to its manager; loaded
chunks provide another path through their client level. This defeats the weak
key and retains the closed world graph. The actual-source regression reproduces
the cycle before the fix. Weak values break that path while vanilla's live
manager continues owning its current storage. All three ownership cases pass;
the existing driver/workflow checks also pass, for sixteen focused cases total.
The public registry API and chart behavior are unchanged.

The repair is implemented; native validation is pending. Freeze new jars and
repeat the affected memory/reopen, multiplayer/whole-chart teleport, map/compass,
layout-switch, cache-identity and production lifecycle gates. Retain unaffected
visual results with reviewed source/archive provenance. Do not restart the full
visual suite or infer resource stability from the old functional passes.
Large paired performance, real FPS policy, soak, upgrade/closure, moving wall/water
review and formal acceptance remain outstanding. No release upload or 1.20.1
implementation is claimed.

### Second retained-world root: final server cleanup

The `10404cb` repair builds on both loaders with all 1,287 Java cases. Archive
comparison confirms `RingClientChunkMaps.class` is its only runtime change.
A stronger prototype assertion stopped its first production reopen test because
one client cache remained after collection; that failed attempt is preserved.
Do not assume one registry entry proves unbounded retention or erase the failure.

An explicitly observation-only Small diagnostic records live registry referents,
identities and vanilla camera/renderer fields over ten reopens. Only the latest
client world remains in that weak registry after each full GC. The registry
therefore no longer accumulates old client worlds, but heap and tracked native
memory still grow. Its histogram has one client level yet eleven integrated
servers and 33 server levels. No resource acceptance is awarded.

JFR's retained-object paths identify the second root directly:
`RingWorldServer.WORLD_GEOMETRY -> ServerLevel -> IntegratedServer`.
Closed servers retain their registry/structure data through this static
identity map. Existing level-unload callbacks are insufficient. The shared
`onServerStopped` method now removes geometry and pending legacy-rim entries
belonging to the stopped server; both loader final-stop events call it.
Earlier unload cleanup and the Atlas save/flush order remain unchanged.

Four actual-method ownership cases pass alongside the three client ownership
cases and thirteen existing driver/workflow cases. Fresh strict builds and
native resource/lifecycle validation of the combined repair remain pending.
The latest diagnostic is retained as `COLLECTED_OWNERSHIP_DIAGNOSTIC`, never a
release or production-world acceptance result. Its root-path dump and additional
histogram safepoint are disclosed and excluded from ordinary performance.

### Rain visibility correction, 16 October

The owner requested a visible but weather-faded Atlas with a continuous live
terrain connection in rain. The 1.21.1 sky adapter passed the celestial
`1 - rain` alpha into terrain reveal. At full rain that value became zero,
replacing the Atlas entirely with fog colour. The same zero also reached the
live-terrain tone convergence, so an earlier rain capture could complete without
proving a visible distant ring.

`RingSurfaceWeather` now retains 55% of lit terrain at full rain and interpolates
continuously from the exact clear-weather value of one. Both loaders share this
policy. The existing renderer publishes the same value to the Atlas draw and
live-terrain handoff. Celestial alpha, opaque coverage/depth, readiness, generation
fog, projection, underwater exclusion, saved formats and dependency pins retain
their existing behavior. The helper is Java-17-compatible for the planned 1.20.1
port; it does not establish implemented support for that version.

The opt-in rain projection fixture now requires actual full rain, a drawn proxy
and the expected nonzero native reveal before each screenshot. The strict
dual-loader build passes with four new weather-policy cases per loader. Native
rain/Fabulous-rain and clear-weather transition controls still require fresh
validation and visual review. Existing saves, failed attempts, the 52-case
graphical composite, upgrade/persistence and other diagnostic records remain
retained under their original source and jar identities. This focused change
does not relabel those records as final exact-artifact release acceptance.

The first Fabric native rain attempt at `84d1580` produces all three captures
with full rain, reveal 0.55 and a drawn proxy, then saves and closes normally.
Its Gradle verifier rejects that result because the historical rain assertion
explicitly required reveal at most 0.05. The failed record and original verifier
log remain retained. The shared Fabric/NeoForge assertion now requires the 0.55
envelope and three ordered full-rain/drawn-proxy observations. Its positive
contract and seven negative controls pass, including rejection of zero reveal,
unfaded terrain, missing/duplicate stages, partial rain and a missing proxy.
Clear-weather, image-detail and handoff-continuity assertions are unchanged.

Fresh packaged validation at `e4ab935` now passes on both loaders. The strict
dual build passes 636 Fabric/common and 659 NeoForge Java cases (1,295 total),
with no failures, errors or skipped cases. Frozen runtime jars are retained in
the local `b14-rain-visibility-candidates-v3` identity:

- Fabric: `d3c2a43ef11c0ce08b106d4003d40b060ce1f597cf0f5f7203aee9701a8a2efc`.
- NeoForge: `a42c9fea11e4850469c52a2cd7aa0f459d1ce79a0a3667daf48ccfedb35dbfde`.

All four complete-Atlas rain cells pass: Fancy at 16 chunks and Fabulous at
28 chunks on each loader. Each produces three ordered native observations of
full rain, reveal 0.55 and a drawn proxy, passes the unchanged image-detail and
handoff-continuity checks, saves all dimensions normally, and passes the
independent persisted Atlas-11 check. All twelve rain screenshots were reviewed.

Eight clear-weather controls also pass: Fancy noon, Fabulous noon, deliberately
early streaming and the foliage/rebuild fixture on each loader. Both foliage
runs retain all 144 motion steps, zero unsafe coverage frames, zero history
residual, and exact authoritative block/Atlas-content restoration. Early
streaming retains the opaque safety floor and proves a complete subsequent
drain. All required images were reviewed; the four derived foliage mask/residual
images remain explicitly labelled as derived evidence. The local
`.codex-tmp/b14-rain-focused-evidence.json` independently rehashes the installed
jars, native logs, images, actual presentation options, raw frame files and
persisted Atlas/settings inputs for these twelve phases.

The existing sixteen-capture horizon fixture additionally passes on each
loader, including ground/elevated rain and thunder, resource reload, and
Nether/End/Overworld controls. All 32 images were reviewed, with the weather
and reload views also inspected full-size. These fresh worlds finish with
partial Atlas caches (639,488 and 652,032 of 851,968 cells respectively).
Their bounded weather/reload evidence must not be described as complete-Atlas
reveal or performance proof; that reveal is established by the four production
rain cells above. The separate local `b14-rain-horizon-evidence.json` binds
their native weather sequence, installed jars, images and independent disk
inputs.

The six matched Fabulous clear captures retain downsampled luma correlations
of 0.999939 to 1.0 against the prior `2e6ecd6` captures, with mean absolute luma
differences below 0.098 on the 0–255 scale. This descriptive comparison supports
the reviewed unchanged clear appearance; it is not paired performance evidence
or an attribution of the remaining pixel variation.

Independent archive comparison against `2e6ecd6` confirms that all server
classes and resources are byte-identical. The only semantic changes are the
weather helper, sky adapter and opt-in projection capture fixture; two nested
fixture classes differ only in source-line numbers. Prior unrelated diagnostic
records, original saves and failed attempts remain intact with their original
identities. Old rain images are historical evidence of the hidden-Atlas defect,
not current visibility proof. These fourteen focused runs do not restart the
whole visual suite or qualify the release: paired Large performance, soaks,
remaining closure controls, moving wall/water review, formal execution and
final release staging remain pending. No publication or 1.20.1 support is claimed.

The public packaged-client adapter now pins 60 FPS with VSync off and records
that presentation profile. Its normalization function is structurally identical
to all four retained operator snapshots from the twelve focused production and
transition phases. A direct inherited-options check also verifies duplicate
removal, preservation of render distance/graphics options and idempotence.
This promotes the already exercised diagnostic policy; it changes no runtime
class, shader, world input or performance acceptance threshold.
The existing options regression now covers conflicting/duplicate inherited
FPS and VSync values while retaining GUI scale, render distance, graphics mode
and idempotence. All sixteen client/performance/exterior driver cases pass.

The unmeasured Large comparison-cache preparation also completes from the
unchanged private 1.3 sampler/scheduler. Independent parsing verifies a genuine
format-10 cache with all 16,777,216 cells, normal all-dimension saving and
unchanged original Large input. Only that native cache is carried into an
otherwise original-world benchmark clone. The clone links original immutable
files to avoid unnecessary duplication: always copy it before a game launch.
Preparation uses a tick sprint and is explicitly excluded from benchmark data.
The retained terminal is
`dist/qualification/b14-native-v10-reference-large/terminal.json`; it does not
qualify current Atlas-11 behavior or 1.4 performance.

### Large comparison review and texture allocation repair, 16 October

Three complete Fabric BA/AB/BA Large comparison pairs are retained. All median
relative P95/P99 limits pass, but the current clients have higher frequencies
of rare frames above 250/500 ms. Performance acceptance remains blocked. The
first attempt at the second current leg failed in the private evidence recorder
after measurement; it remains failed. A fresh retry and the third current leg
complete ordinary saving, native restart and an unmeasured loaded-capture drain.
The recorder now exclusive-creates its final drain aggregate once, with eight
regression cases; it changes no production code or measurement window.

Bounded JFR/GC review connects several client outliers to full GC, while other
outliers have no matching local GC pause. Sparse render-thread samples do not
establish a complete cause. Weighted allocation samples identify immutable
`SurfaceSample` results on the surface-texture builder as a large avoidable
allocation source. Those weights are estimates, not exact allocation totals
or proof that eliminating them will resolve every stall.

The focused repair adds caller-owned primitive sample storage and reuses one
instance per texture build. Both complete and partial texture loops use it.
The immutable sampling API, missing-result singleton and interpolation
arithmetic remain unchanged. The scratch instance contains no Atlas/world
reference and belongs only to its worker invocation. Projection, shaders,
coverage readiness, rain policy, persistence and server acquisition remain
unchanged.

An independent oracle was generated by loading the frozen pre-repair Fabric
jar `d3c2a43ef11c0ce08b106d4003d40b060ce1f597cf0f5f7203aee9701a8a2efc`,
not the new implementation. Its 4,096 vectors cover dense/partial sampling at
steps 1/2/4/8, seam/band edges and seeded random coordinates. The checked-in
CSV SHA-256 is
`6ac74ce3cece6629f41c0bbfab02beb77603c584c4227d7d1dfb0075745912cf`.
New tests require bit-exact immutable and reusable results, independent scratch
ownership, complete missing-result reset and a measured allocation budget after
warmup. The development strict dual build passes 647 Fabric/common and 670
NeoForge cases, with no failures, errors or skips. The intended stable metadata
is now selected for subsequent internal acceptance candidates; neither the
label nor compilation establishes release acceptance. The stable-labelled
strict rebuild also passes all 1,317 cases and the existing dependency, shader,
Mixin, loader-boundary and runtime-verifier contracts. Both allocation tests
report 616 measured bytes for 200,000 warmed reusable samples, within the
262,144-byte bound and without a result object for every point.
Affected native texture/performance validation remains pending.
Existing records retain their original identities;
this repair does not invalidate unrelated completed tests or qualify a release.

### Projection fixture stability diagnosis, 16 October

The stable-labelled allocation candidate at `c884edf` passes all four focused
Fabric controls: noon, foliage, early streaming and Fabulous rain. Foliage
retains all 144 motion steps, zero unsafe coverage frames and exact restoration.
The three projection controls retain their ordinary random-tick/mob rules in
these original runs; their individual frame summaries remain diagnostic.

The first NeoForge noon attempt repeatedly loses the required ten-second
settled window. Its exact streaming window is complete, but vanilla's global
section-ready predicate flickers while Atlas revisions continue. After about
15 minutes, the client was closed through its native window and saved all
dimensions normally. The incomplete attempt, original copied world, partial
capture, raw frames and short JFR trace remain retained as a failed diagnostic;
it does not pass the noon gate or count toward the closure matrix.

Source review identifies an independent fixture defect: the local wait counter
resets on every briefly ready tick, so oscillation can evade its deadline until
the outer 30-minute process limit. A monotonic two-minute deadline now applies
after each settled projection stage is armed. The original 200 consecutive
ready ticks, full-radius/compiled-window predicates, image checks and raw frame
recording remain intact. The separate early-streaming capture/drain contract is
unchanged.

Only the opt-in projection fixture's disposable world now sets random block
ticks to zero and disables mob block griefing, as the existing foliage fixture
already does. Native logs disclose this visual quiescence. Ordinary gameplay,
Large comparisons, default profiles and soaks keep random ticks and mobs on.
The failed world's disk state confirms those rules had remained at 3/true and
that terrain/Atlas changes occurred, but does not by itself attribute every
readiness reset. Bounded JFR samples show section compilation, coverage checks
and ordinary spawning; they are not a complete causal proof. Fresh native
validation must establish whether the controlled visual fixture settles.

The strict dual rebuild passes 656 Fabric/common and 679 NeoForge cases (1,335
total), with no failures, errors or skips. Nine new deadline cases per loader
cover the boundary, repeated polling, separate stages, `nanoTime` rollover,
backward-clock rejection and invalid budgets. The sampling oracle and allocation
budget still pass. Native retries are pending; no previous failed attempt is
promoted and the release remains unqualified.

### Large allocation follow-up, 16 October

All seven fresh projection controls now pass on both loaders, completing the
eight-case composite with the unchanged Fabric foliage run. The new NeoForge
noon run settles in about 191 seconds. Both foliage runs retain all 144 motion
steps, zero unsafe frames, zero history residual and exact block/Atlas
restoration. All eight contact sheets and twelve full-size native images were
reviewed; the complete evidence retains 36 native and four derived images.
Early streaming still shows a deliberately coarse opaque safety floor. These
controlled visual worlds do not provide ordinary-play performance evidence.

The first exact `043aa09` Large collection completes all three clients, both
movement cycles, ordinary disk persistence without explicit flush, queue drain,
normal shutdown and native block verification after reopening. Optional copied
client caches are correctly rejected because the live server revision advanced
before login; ordinary streaming completes. The cache-profile queue stops at
its acceptance guard. This is retained diagnostic evidence, not a product
failure or a matched performance pair. No cache header or identity check was
changed.

Client outliers of roughly 250–600 ms remain, with several overlapping full
G1 compactions. Bounded JFR allocation samples now identify large renderer
snapshot and pixel arrays as the dominant remaining avoidable allocation
source. Remaining immutable mesh samples also allocate. JFR weights are
estimates, and overlap alone does not attribute every stall.

The focused follow-up gives the renderer a session-owned reusable snapshot and
complete-texture scratch arrays. An exclusive lease prevents recapture while
the worker or unpublished result still owns the buffers. Success, worker
failure and submission failure release the lease; teardown drops the workspace
so an abandoned worker cannot overwrite a later session. The existing public
independent `snapshot()` operation is unchanged. A separate exclusive-copy
operation copies all cells, counters, revision and content identity. Mesh
construction uses the already verified reusable sampler without changing
projection or interpolation arithmetic. Weather, shaders, coverage readiness,
server acquisition and saved/network formats remain unchanged.

The strict dual build passes 698 Fabric/common and 721 NeoForge cases (1,419
total), with zero failures, errors or skips. Ten workspace cases per loader
cover complete-to-partial replacement, all sampling steps, invalid ownership,
resolution/layout/world changes and an actual asynchronous abandoned-worker
handoff. Warm capture/scratch reuse allocates 12,000 bytes for 500 captures.
Thirty-two full vertex/material/intrinsic streams match SHA-256 oracles generated
independently from the frozen pre-repair jar. The checked-in oracle SHA-256 is
`2c4bf6de059f8607cf78f50a3969fc8cd0080167cf1ecd8b1f5a9cdddb8bad97`.
Native affected rendering/lifecycle controls and Large measurements remain
required. Completed unrelated evidence remains preserved with its original
source and artifact identity; the release is not yet qualified.

### Atlas UI capture acknowledgement, 16 October

The exclusive-workspace candidates at `abe6eb6` pass all sixteen focused
native controls: noon, Atlas UI, cache identity, production lifecycle/reopen,
different-layout switching, foliage, early streaming and Fabulous rain on both
loaders. Both foliage runs preserve 144 motion steps, zero unsafe coverage
frames, exact restoration and zero history residual. The image set contains
84 native captures and four derived verifier images.

Manual review of all sixteen contact sheets catches an evidence defect:
NeoForge's capture labelled resumed still shows the preceding paused screen.
The fixture checks the actual RUNNING acknowledgement before capture, but its
three-frame wait starts when the request is sent. A late acknowledgement can
therefore capture the preceding framebuffer. The original successful functional
terminal and stale screenshot remain retained; that image is not accepted as
visual proof of the resumed screen.

The opt-in fixture now waits three actual rendered frames after observing the
expected server state, resetting on state loss or a new request. A pure barrier
rejects tick-only advancement and backward/invalid counters. Native logs bind
running, paused, resumed and cancelled captures to acknowledgement/frame
boundaries, and the packaged driver validates their ordered states and delays.
Six negative host-contract cases reject absent, stale, duplicate, reordered or
insufficiently rendered boundaries. The existing eleven driver tests also pass.

The review additionally finds a stale completion estimate on a cancelled job.
Only a running generation job now displays its projected ETA; complete shows
zero and inactive/saving states show Unknown. Transported progress and server
work are unchanged. Seven state cases per loader cover this display policy.
The strict dual rebuild passes 712 Fabric/common and 735 NeoForge cases (1,447
total), with no failures, errors or skips. Only the two Atlas UI runs need fresh
captures; renderer, Atlas, server, persistence and weather implementations are
unchanged from the workspace candidates. Their fourteen other focused controls
remain retained with original identities, subject to the narrow archive impact
check. Large performance acceptance and the final review report remain pending.

### Remaining client storage allocation, 16 October

Both acknowledged-frame Atlas UI repeats pass on `469da6c`; full-size review
confirms Generating/Pause after resume and Cancelled/Retry with Unknown ETA
after cancellation. The reviewed focused composite retains fourteen unchanged
workspace-candidate controls and these two new UI runs: 84 native images, four
derived images, sixteen contact sheets and sixteen full-size image inspections.
Original stale UI evidence remains retained. Early streaming/partial generation
still has coarse, tiled or faceted loading appearances; those images do not
certify settled quality. Moving wall/water review remains pending.

The first ordinary, unseeded Large run with the UI candidate saves its edit
without explicit flush, drains loaded captures, closes normally and verifies
the edited block through a native restart. Performance remains blocked. Its
measured clients have 5/7/5 frames over 250 ms and 1/3/0 over 500 ms. Several
outliers overlap full G1 compactions of approximately 258–393 ms. Bounded JFR
samples confirm that renderer snapshot/pixel and immutable mesh-sample hot
allocations have disappeared from the leading groups; client-cache snapshots
and reduced-display copies are now the leading avoidable Atlas allocations.
This is a diagnostic collection, not a completed matched pair or acceptance.

The next repair reuses only exclusively owned client cache snapshots, complete
reduced-display buffers and per-level mip scratch. Active cache encoding never
lends its snapshot to a new capture; pending writes coalesce under the owner
lock. Session generation changes discard idle buffers and prevent older writes
from restoring them, while preserving all queued final saves and their paths.
Raw client teardown also checks that no idle cache snapshot remains. Worker
fatal errors/executor rejection complete pending failures and release drain
state instead of stranding shutdown. The public independent snapshot, display
copy and mip APIs retain their original algorithms; new destination APIs are
validated separately. Server implementation, file/wire formats, projection,
coverage readiness, weather and shader resources are unchanged.

The strict dual build passes 734 Fabric/common and 757 NeoForge cases (1,491
total), with no failures, errors or skips. New cases cover actual concurrent
active/pending snapshot isolation with final file verification, session discard,
world/layout changes, failure/retry, byte-equivalent reduced-display and mip
replacement, and allocation after warmup. Cache capture measures 121,600 bytes
for 200 writes with a test sink excluding disk allocation; reduced-display/mip
reuse measures 480 bytes for twenty complete builds. These small synthetic
allocation contracts do not prove Large performance. Native cache/noon smoke
and the failed Large workload must pass before continuing paired acceptance.

### Exact GPU mesh reuse and corrected diagnostic clocks, 17 October

The storage candidates at `e02f6bc` pass both loaders' native cache/noon controls.
Their three Fabric Large candidate legs also complete normal saving, loaded-work
drain and native edit verification after restart. The third historical 1.3 leg
throws a snapshot-allocation heap error during cold transfer. That original
failure remains retained; one fresh retry with identical arguments succeeds.
No completed leg or original save is overwritten.

The independently rehashed three-pair review remains blocked. Median server
P95/P99 ratios are 0.4823/0.5774, but client B's median P95 ratio is 1.1081,
above the 1.10 limit. Candidate client frames over 250 ms total 6/2/5 across
the three legs, versus 1/0/0 historically. Candidate clients have no measured
frames over 500 ms. One 345 ms frame overlaps a 329.983 ms full collection;
the remaining long frames do not overlap native GC pauses. These results are
diagnostic evidence, not release acceptance.

JFR event timestamps on this host run about 0.25% slower than native frame and
GC-log clocks. Uncorrected sampled-stack selections can therefore be nearly
two seconds away from the actual frame; retain those earlier analyses but do
not use them for attribution. Matching major GC identities and durations gives
an affine calibration with residuals below 3 ms on all nine candidate clients.
Six bounded clock-contract tests reject insufficient spans, duplicate identities,
duration mismatches, clock steps and excessive extrapolation. The corrected
windows retain the existing five-millisecond pad. Raw frame/tick statistics
and acceptance limits are unchanged. Eleven of thirteen long frames now contain
native samples in `RingSurfaceGpu.uploadMesh`; one contains the full collection
and one remains unattributed. Sample overlap is evidence for a focused repair,
not a claim that every stall has a single cause.

The repair hashes the actual packed vertex-byte range on the existing surface
worker. A full-Atlas height/material key can change at cells outside the mesh's
sampled lattice even when every emitted vertex remains identical. Only the
same world/layout with a valid GPU mesh and matching vertex count, stride and
SHA-256 identity may reuse that buffer. Changed positions, UVs and material
colours still upload normally. Publication advances the matching height key
and detailed-state metadata after the coherent texture update. Teardown clears
the identity; an abandoned build cannot acquire ownership in a new session.
Mesh generation, shaders, rain visibility, readiness, server code and saved/wire
formats remain unchanged.

The strict dual build passes 743 Fabric/common and 766 NeoForge cases (1,509
total), with no failures, errors or skips. Nine new cases cover all packed-byte
fields, raw floating-point bits, direct/read-only/sliced buffers, ownership,
draw-state validation and a real unsampled Atlas mutation that changes the
old key while preserving the emitted mesh. A sampled-neighbour mutation must
change the identity. The first test attempt used an adjacent unsampled row
for that negative control; its failed record is retained and the coordinate
was corrected to the actual sampled row. Native affected rendering/lifecycle
checks and the failed Large workload remain required; no acceptance is granted
from these unit cases.

### Separate mesh preparation from surface publication, 17 October

The exact-byte candidates at `e49d928` pass both loaders' cache-identity and
noon projection smokes. Twelve native images were reviewed through two contact
sheets and four full-size inspections. The first repaired Fabric Large run
also saves its gold-block edit ordinarily, drains loaded work, exits normally
and verifies the block through a native restart. This is one diagnostic run,
not three matched pairs. Its measured client P95 values are 24.53/24.31/26.94 ms;
frames over 250 ms are 2/1/0, with no frames over 500 ms. One A frame combines
a 163.74 ms mesh upload with texture upload and release; another A frame has
an OpenAL device-query sample but remains unattributed. B's 386.76 ms frame
overlaps a 349.249 ms full collection. These residual failures remain open.

The next renderer repair prepares a changed GPU mesh on one render call while
retaining the preceding matched mesh and texture. A private staged owner holds
the replacement, immutable build and exclusive workspace lease. The following
render call rechecks the existing generation/world/revision guard before
publishing the texture and mesh together. An identical packed mesh still uses
the existing buffer. Resource reload/disconnect closes both staged owners;
current-revision readiness and raw teardown include this pending state.
The ordinary complete-scene capture predicate, shaders, rain policy, coverage
proof and all vertex algorithms are unchanged.

Strict dual build/check passes, retaining all 1,509 passing unit cases.
Three additional bounded cases compile the actual private staged-owner close
implementation with minimal resource stubs: normal close, GPU-close failure
and build-close failure each retire both owners. They do not qualify native
GPU staging or lifecycle. Fresh affected native controls and the failed Large
workload remain required. This repair does not claim to resolve full collections
or the unattributed audio-query frame.

### Cache encoding storage outside the client heap, 17 October

The staged-mesh candidates at `1534f03` pass both loaders' cache/noon smokes
and the twelve-image visual review. Their first ordinary Fabric Large run has
no frames over 250 ms on A or B. C still has a 343.324 ms frame overlapping
a 329.567 ms full collection at the 4 GiB heap limit. A separate same-artifact
four-processor diagnostic does not resolve this: C's full collection lasts
525.468 ms and its frame reaches 535.894 ms. Retain that failed diagnostic;
subsequent acceptance uses the original uncapped processor profile.

Cache encoding previously retained a 192 MiB Java snapshot for a complete
Large Atlas, with further isolated images needed while encoding. The client
now supplies explicitly owned LWJGL native buffers to the shared cache writer.
Bulk capture keeps the same exclusive active/pending ownership and revision;
the worker emits the original format-11 bytes and atomic file replacement.
Idle storage is reusable only within the session. Disconnect releases idle
storage immediately and lets queued final saves finish before releasing their
active storage. Allocation failure preserves an earlier queued save. Common
code keeps a heap allocator and has no LWJGL dependency. Public Atlas snapshot,
sampling, persistence and rendering algorithms are unchanged.

The strict dual build passes 782 Fabric/common and 805 NeoForge cases (1,587
total), with no failures, errors or skips. Thirty-two encoding comparisons use
the existing independent Atlas encoder across dense/partial layouts, water,
lighting, four steps and heap/direct storage. Seven further cases cover full
overwrite, ownership failure paths, active save/discard, queued-save survival
and capture allocation below 512 KiB for a four-million-cell Atlas. Direct
buffer test doubles prove ownership contracts, not actual native freeing.
The packaged cache fixture additionally waits for the actual native byte count
to reach zero after each of its three normal disconnects, within its existing
watchdog. Both native loaders and the failed ordinary Large workload must
validate this candidate before any performance or release credit.

### Bounded GPU vertex transfer, 17 October

The native-cache candidates at `de39ab6` pass both loaders' cache/noon gates
and twelve affected rendering/lifecycle controls. All three Fabric comparison
pairs complete ordinary durability, drain, normal exits and native restart.
Tick/frame percentile limits pass and none of the eighteen measured client
legs has a full collection. The severe-frame screen remains blocked: current
client B has one frame over 250 ms, as does historical B, but its shorter
measured duration yields a higher event rate. Current A additionally has a
337.030 ms frame without its own GC or safepoint; its one late vanilla block
read sample is insufficient attribution. Retain this unresolved evidence.
An additional raw boundary audit finds no over-250-ms frames excluded at the
measurement-window boundaries in any of the six runs.

Current B's 255.768 ms frame contains three calibrated native samples in
vertex `glBufferData`, matching its logged 249.82 ms ring-mesh upload.
Historical B's 293.259 ms frame also contains three samples in that operation.
These are actual large vertex transfers, not an introduced checkpoint stall.
The earlier strict JFR inspector rejected one young-GC duration mismatch;
its failure is retained. A separate inspector accepts the differently scoped
young-GC timing only when the unique same-ID native start/end interval and
all five native phase components independently corroborate the JFR duration.
All anchors and the original 3 ms clock-fit tolerance remain required; raw
pause/frame statistics are unchanged. Seventeen positive/negative clock
checks pass. This analysis does not resolve the unattributed A frame.

The renderer now allocates an unpublished vertex buffer and transfers at most
8 MiB of the original immutable packed bytes per later render call. The pinned
1.21.1 vanilla adapter initializes VAO/draw/index state with a storage-free
MeshData header; the original MeshData and allocator remain owned by the build.
No original vertex data is submitted to the monolithic upload. The old matched
mesh/texture continues drawing throughout transfer. Every transfer rechecks
the existing generation/world/revision guard; publication waits for all bytes
and occurs with the matching texture on a subsequent call. Disconnect/reload
retires the incomplete GPU owner before releasing the build's CPU storage.
This limits transferred bytes, not driver latency, and still needs native proof.
There is no change to mesh algorithms, draw counts, shaders, rain visibility,
coverage/readiness rules, server work, wire/save formats or policy defaults.

The strict dual build retains all 1,587 passing Java cases with no failures,
errors or skips. Eight bounded tests compile the actual GPU adapter and staged
close implementation with recording GL/type stubs. They cover exact complete
byte reconstruction and offsets, early/double publication rejection, original
buffer-view preservation, abandonment, failed transfer retry, allocation
failure, invalid indexed input, and both staged close failure paths. These
checks do not certify native GL behavior or latency. A fresh exact candidate
must pass affected native rendering/lifecycle controls and the failed Large
workload before proceeding to the remaining acceptance cells.

### Native GPU controls and cache encoder CPU review, 17 October

The bounded-transfer candidates at `a7101b7` pass both loaders' cache identity,
production noon, underwater/reload, production lifecycle/reopen, layout switch,
foliage, deliberately early streaming and Fabulous rain controls. All sixteen
native cases save and exit normally. Fifty-six native images and four derived
verifier images are retained; sixteen contact sheets and sixteen full-size
native images were inspected. Rain remains visibly faded and connected, and
submerged cameras have no distant-ring ghost. The existing near-water detail
versus opaque distant-water contrast still needs the moving-view review.

The first ordinary Large Fabric run completes independent ordinary disk-edit
proof, loaded-capture queue retirement, normal exits and native gold-block
restart. Raw client P95 values are 25.526/26.363/28.539 ms and maxima are
106.246/108.604/111.485 ms, with zero frames over 250/500 ms and zero measured
full collections. Minimum observed physical/commit headroom is 2.818/1.276 GB.
This is one current leg, not paired performance acceptance. The largest server
tick is 2.888 seconds in the first exterior transition; its calibrated samples
include vanilla chunk loading, ticket/filter work and decoding. The selected
park events are vanilla chunk I/O, not cache encoder calls. Sampled overlap
does not exhaustively attribute the complete tick.

Centre-movement frame percentiles remain slower than the historical reference,
while the quiet view is faster. One bounded current/historical client-A review
finds more current vanilla terrain drawing and section-check samples and less
FPS-limit waiting. These are sampled stacks, not exact CPU time or visible
chunk counts; a higher-work-throughput explanation remains unproven. Keep the
phase results visible alongside the original whole-workload acceptance limits.

The same review finds substantial cache-worker samples in per-value output
stream locking. Cache records are fixed twelve-byte format-11 values. The
worker now interleaves 4,096 records into a reusable 49,152-byte local heap
buffer and writes each batch through the original standard JDK stream chain.
All byte-order conversion remains on the worker. Capture, native storage,
ownership, atomic replacement, geometry, GPU code, shaders and UI are unchanged.
The strict dual build retains 782 Fabric/common and 805 NeoForge passing cases;
all thirty-two independent full-GZIP byte comparisons pass, including partial
final batches, sparse cells, water, lighting, both storage types and four steps.

A synthetic four-million-cell encoder diagnostic uses warmup and three
alternating pairs. Every output has the same compressed SHA-256; median CPU
ratio is approximately 0.913. This is isolated encoder evidence, not gameplay
acceptance or proof of the centre-phase cause. An earlier custom-buffer
prototype, its 1,599-case build and six stream failure tests are retained as
an unadopted diagnostic; batching keeps the standard stream implementation and
adds no production class. Fresh native cache identity checks and full paired,
default, partial and soak measurements must qualify the final candidate.


### Final local release preparation and reduced acceptance scope, 17 October

The owner explicitly stopped the extended testing and replaced the remaining
benchmark matrices, hour-long soaks and repeated closure diagnostics with
essential build, functionality, visual and save/reopen checks. Preserve all
completed evidence; do not restart the abandoned queues. The interrupted fresh
Fabric historical leg was cancelled by the owner and saved all dimensions
normally. It is not a new diagnosed product failure.

The final code checkpoint is `0e886b2`. Its strict dual build passes 782
Fabric/common and 805 NeoForge cases without failures, errors or skips.
Both exact candidates pass native cache identity controls. GPU, renderer,
server, UI and resource entries remain byte-identical to the preceding
candidate's affected dual-loader visual/lifecycle controls; only cache-worker
record batching changed. Four ordinary default-policy Small/Medium cells
(Fabric and NeoForge) complete scheduled-save, independent disk-edit,
normal shutdown and native reopen checks. Mean server ticks range from
12.3 to 21.6 ms; P95 ranges from 15.3 to 29.3 ms. Those twelve client streams
have no measured frames over 250 ms.

Three completed Fabric Large comparisons contain two client frames over
250 ms in 55.94 combined client-minutes, versus three in 70.95 historical
client-minutes. Both current events occurred on client A (302 and 339 ms);
their full cause remains unresolved. No measured client frame exceeds 500 ms.
Whole-workload P95/P99 comparisons stay within the original relative limits.
Current server ticks over 500 ms number eleven in 18.65 minutes, versus
117 in 23.65 historical minutes. Terrain-transition maxima reach approximately
three seconds. These are bounded stress observations, not proof that every
machine or ordinary gameplay session behaves the same way. The original
strict rare-event screen remains failed; the owner accepted these limitations
and removed the remaining exhaustive qualification requirement. Do not
relabel that screen as passed.

The explicit 1.4 staging profile performs one clean Java 21 dual-loader build,
checks runtime identity, MPL licensing and shared named classes/resources,
and stages only the runtime jars with checksums and exact corresponding-source
links. Final rebuilt bytes must match the frozen candidates before carrying
forward their native evidence. Publication is not authorized by local staging.
Minecraft 1.20.1 remains separate, unimplemented follow-up work.
