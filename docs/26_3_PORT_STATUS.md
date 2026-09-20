# Minecraft 26.3 intake — 2026-09-15

The owner authorized adding 26.3 and publishing updated supported versions to
CurseForge, with minimal changes and conservative token use. No new release has
been uploaded. Existing 1.1 downloads remain the supported release.

## September 20 development and tooling checkpoint

The owner requested Fabric fixes followed by NeoForge and a usage pause at
**70% remaining** for this session (revised from 80% by the owner), superseding the normal 5% threshold.
The owner requests continuing until the release is polished enough for full launch.
NeoForge has now published **26.3.0.7-beta**; its universal and installer jars
and ModDevGradle **2.0.147** were retrieved from official Maven and SHA-256
hashed. This is an upstream beta, not RingWorld release qualification.

The required 26.3 projectile hook now accepts the new surface-hit flag and
preserves its hit-position semantics while projecting seam-adjacent hitboxes.
The shared 26.1/26.2 collision hook is unchanged. Both loaders' disposable
multiplayer and raid servers explicitly set `white-list=false`. The source-ABI
test now selects the reviewed 26.3 source directory.

Real client testing exposed a second port issue: SkyRenderer now passes one
RenderPass through its drawing methods, and DynamicUniforms is now
DynamicGpuData. The version-owned sky adapter uses those required targets,
keeps the lower atmosphere in the sky pass, then draws the Atlas and centered
sun after vanilla closes that pass. The sun scale now has one constant site.

Fabric builds with **440 passing Java cases**. The hidden, muted Atlas UI
fixture passes all eleven captures, completed generation, ordered live revision
and normal disconnect (`logs/26.3-port/fabric-fixes-atlas-ui-4.log`). Its progressive
world screenshot was visually inspected. Map/compass (including save/reopen), curved objects and same-process
layout switching also pass. Full multiplayer now passes the verifier with both clients and server exiting 0.
Raid arm/save and reload/victory markers pass, with normal server shutdown and
intentional test-client cleanup. These are development checks, not frozen release qualification.

The final Python sweep passes 433 cases: 431 passes and two expected platform
skips (`fixes-2026-09-20/python-background-final.log`). The earlier
429-case sweep also passed before the current-format qualification checks. Earlier failures caused by PATH
selecting an incompatible macOS system Python remain in the retained logs.

Teleport acknowledgements now carry coordinates and call vanilla's shared
`handlePlayerPositionChange` helper. Periodic player projection and folding now
hook that helper so acknowledgements and normal movement use the same canonical
chart. The 26.3 movement fixtures let vanilla send the changed pose on its next
tick instead of sending a forbidden second position packet.

The first two production lifecycle attempts timed out before their baseline:
one source Atlas was incomplete and the second used an obsolete one-block
sampling cache. Neither counts as a lifecycle pass. The retained 26.2 industrial
world was copied and prewarmed under current 26.3 settings, completing all
1,048,576 two-block samples and 16,384 chunks on the 16384×256 ring. Only the
owned preparation copy used accelerated ticks; this is generation evidence,
not a generation-time benchmark. The original save is unchanged.

Fabric now passes the complete production lifecycle, natural seam/both-rim
visual fixture, and noon/dusk/night/rain projection verifiers. NeoForge also
passes map/compass persistence, curved objects, production lifecycle, natural
seam/both-rim checks, full multiplayer with all three processes exiting 0, and
raid persistence/victory. All four NeoForge projection environments, layout switching, the 19-capture
creation fixture and fresh 2048×416 worldgen pass. That worldgen sample checks
208 chunks, 21 loot containers and three seam-crossing structure starts. Its
monument search ends with the expected typed SEARCH_BUDGET_EXHAUSTED outcome,
not a claim of a generated monument. Evidence is below `logs/26.3-port/fixes-2026-09-20/`.

The original projection pose was directly below a retained wall-study panel
at X=4096, Y=128–175. A test-only `ringworld.projectionCameraX` override permits
unobstructed captures at X=3072 without editing the source world or weakening
any completion, settling, seam, or image verification. Initial Fabric captures
retain the obstructed pose; the follow-up captures use the clear pose.

Four fresh NeoForge clients logged a nonfatal `NoSuchFileException` for
`config/neoforge-common.new.tmp.toml` in NightConfig's file watcher. All
completed their assertions and exited successfully. A repeat of the curved-object
fixture with the existing config passed without the exception. The stack points to the
macOS Java polling watcher inspecting a temporary config file during startup;
that race is an inference from the stack, not a confirmed upstream diagnosis.
Retain it as an upstream-runtime follow-up; do not call these logs error-free.
Settled rendering still includes occasional frames above 50 ms and measured
wall-texture GPU uploads above 16 ms; these checks do not prove hitch-free play.

## Full-launch qualification follow-up — September 20

The first nightly attempt on `993e4d1` was stopped before any fixture PASS.
Its fresh hidden Fabric window hung in `SDL_GL_SwapWindow` during startup;
two thread snapshots showed unchanged render-thread CPU time. Disabling VSync
in disposable automated-client options resolves this: both loaders then pass
all 19 creation captures and exit normally. Ordinary client settings and the
frozen mod jars are unchanged. Evidence: `fixes-2026-09-20/nightly-1-operator-stop.txt`,
`nightly-creation-threads*.txt`, and `background-no-vsync-fabric.log` (the latter
contains both loader runs). This interrupted attempt is not a download retry
or a passing nightly aggregate.

The copied-world checker now explicitly records the runtime's supported
old-format-to-format-5 migration while requiring every semantic settings field
to remain equal. Unknown, backward and intermediate format transitions fail.
Full Python discovery passes 435 cases (433 passed, two expected skips), logged
in `fixes-2026-09-20/python-migration-final.log`.

RingWorld 1.2 descriptors and change-only changelogs for all three version lines
are prepared under `deploy/qualified/1.2/`. They pass metadata validation but
are not staged or published. GitHub briefly timed out; the connection recovered
and `993e4d1` was pushed. The existing Archipelago smooth-join failure and
whole-ring structure-density acceptance gap remain explicit launch-review items.

## Inputs

Mojang's [official manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
lists 26.3 as a stable release, requiring Java 25.
`config/minecraft-version-matrix-26.3.json` pins its client/server SHA-1 hashes,
Fabric Loader 0.19.5, Fabric API 0.160.5+26.3 and Loom 1.17.21.
The dependency jars were downloaded from official Maven repositories and
SHA-256 hashed for the manifest.

The [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
had no 26.3 runtime at intake. The September 20 follow-up pins the published
26.3.0.7-beta universal/installer and ModDevGradle 2.0.147 in both manifest cells.
Both cells remain pending qualification. NeoForge builds with 440 passing cases and its full Atlas UI runtime fixture passes. Historical Fabric logs used a 26.2
companion dependency solely to configure the unused NeoForge Gradle project.

Loom 1.18.1 requires Gradle 9.7.0 and cannot run with the existing 9.5.1 wrapper.
Loom 1.17.21 resolves the game and reaches compilation, so no wrapper upgrade
was needed for this intake.

## Implementation checkpoint — 2026-09-15

The owner authorized execution of the port plan. A version-owned `src/versions/26.3`
adapter now compiles both main and client code. Build source selection supports
Java and resource overrides without changing the 26.1/26.2 implementations.
Version-specific test sources and the outbound packet inventory follow that
same selected ABI. Shared server fixtures use a small per-version API adapter.

The 26.3 implementation rewrites density functions before RandomState compiles
and caches their samplers. This covers terrain, climate, aquifers, ores and
material functions while keeping other dimensions unconfigured. Surface noise,
biome resolvers, spawn, structure lookup and sleep APIs are adapted. Mapped
world-generation caches retain periodicity and use nearby coordinates when
selecting dependency status; 26.3's new cache-copy step otherwise loses the seam
alias metadata. Existing optional river-biome behavior is retained.

Client adapters cover RenderPearl GPU types, SDL hidden test windows, moved
packet accessors and complete stepped movement paths. The global shader buffer
uses 26.3's field order. Version-owned shaders use the new include syntax,
explicit interface locations, multidraw terrain and transparency phases;
cloud shaders use their new resource names. These changes need further real-world
visual and multiplayer qualification before support can be advertised.

### Retained development evidence

- `logs/26.3-port/build-4.log`: Fabric `:test :build` passes **440 tests**,
  including three new compiled-noise/path tests. This precedes the final
  shader-buffer/resource adjustments; those compile in the later client run.
- `server-4.log`: fresh 2048×128 dedicated stronghold fixture passes, including
  periodic cardinal terrain, rims, underside and clean server shutdown.
- `worldgen-1.log`: fresh 2048×416 fixture passes with the worldgen matrix enabled.
  It inspects 208 chunks, cave air, ores, logs, 22 loot containers and three
  crossing structure starts. Seam terrain passes (largest height delta 11,
  no cliff columns). Monument search exhausts its bounded budget; this is not
  evidence of a successfully generated monument.
- `client-4.log`: hidden, muted SDL client passes 19 creation-screen captures,
  including two seed previews, with no shader pipeline compilation errors.
  This is a menu/preview test, **not an in-world rendering pass**.
- Earlier failed attempts remain under the same log directory. Startup first
  failed on a noise-interface mixin target, then the mapped cache seam lookup.
  The aborted failed server was terminated before the passing run. One attempt
  failed to bind its port while that process remained alive. Earlier client
  attempts exposed the global-buffer signature and shader compiler changes.

Reproduce the build with Java 25:

```sh
./gradlew :test :build \
  -Pminecraft_version=26.3 -Ploader_version=0.19.5 \
  -Ploom_version=1.17.21 -Pfabric_api_version=0.160.5+26.3 \
  -Pneoforge_version=26.3.0.7-beta -Pmoddevgradle_version=2.0.147 \
  -Pmod_version=0.0.0-qualification+mc26.3 -Prelease_label=qualification \
  -PringQualificationRoot=dist/qualification/26.3-intake \
  -PringQualificationCell=26.3-fabric --console=plain
```

Local helper `logs/26.3-port/build-fabric.sh` holds these pins. The ignored
`background.gradle` init script hides and mutes the creation fixture. Its
screenshots are below the isolated cell's `run/run-creation-ui/screenshots`.
Decompiled vanilla reference files under `logs/26.3-port/vanilla` are local
inspection inputs only and must never be committed or distributed.

## Existing-version regression

Before the implementation checkpoint, both Fabric and NeoForge built successfully against 26.1 (the 26.1.x source
floor) and 26.2, with 437 tests passing per build: 1,748 cases total. These are
diagnostic `0.0.0-qualification` jars, not publishable release files. This run
does not requalify 26.1.1/26.1.2 runtimes or establish fresh release evidence.
Logs: `logs/26.3-port/regression-26.1.log` and `regression-26.2.log`.
The batch run below repeats these builds after the new source/resource selection and fixture adapters. The new manifest passes structural validation; 24 existing matrix/support
contract tests pass. No new framework, wrapper change or compatibility bypass
was introduced.

## Qualification tooling and remaining release work

The existing readers now understand format-5 settings, Atlas-9 twelve-byte
cells (including bounded block light), and layout fingerprint version 4 with
persisted wall and generation settings. Their computed production identity
matches the independently emitted Java prewarm report. Reload/forward-upgrade
comparisons retain all wall/generation fields; missing old-world option fields
use the same legacy defaults as Minecraft's codec. Atlas/map client checks now
require and report the current format-5 acknowledgement. Historical retained
reports and published support ranges have not been rewritten.

The final source sweep passes **440 cases per 26.3 loader** and **437 cases per
loader on both 26.1 and 26.2**, totalling 2,628 Java cases. Logs are under
`fixes-2026-09-20/final-batch/`. Both loaders' clear-pose projection screenshots
are retained with their per-environment logs. Quick qualification **20260920T093330Z-c3dd96e9330d** passes both loaders on
clean pushed source `39216e8`, including frozen builds, per-cell source builds,
artifact/range checks, and separately installed dedicated servers with normal
stops. Each server uses its exact frozen jar. This is quick qualification,
not the remaining nightly/upgrade/package gates.

The subsequent Gradle-only fixture change makes qualification clients hidden
and muted by default, without changing mod code or the retained frozen jars.
`gradle/automated-clients.gradle` also disables onboarding and NeoForge's early
splash; visible operator checks can explicitly opt out of the hidden window.
The nightly run must record its current execution source separately from the
retained `39216e8` candidate source.

1. Run clean, pushed frozen-candidate qualification, copied-world upgrades,
   required package checks and release equivalence. Keep development Gradle
   runtime evidence distinct from installed-loader/frozen-jar results.
2. Stage updated supported versions with immutable source, versions and
   changelogs; upload to CurseForge and verify hosted file hashes. Diagnostic
   `0.0.0-qualification` jars are never upload candidates.

## Owner-authorized test batches — 2026-09-15

The owner explicitly authorized continuing below the 5% weekly allowance with
“run in batches,” following the proposal to stop at the first failure. These
runs use source commit `e880952`; no implementation changes were made during
the batches.

| Batch | Check | Result |
| --- | --- | --- |
| 1 | 26.3 Fabric final build | PASS, 440 tests |
| 1 | 26.1 Fabric / NeoForge source regression | PASS, 437 tests each |
| 1 | 26.2 Fabric / NeoForge source regression | PASS, 437 tests each |
| 1 | Jar/source-jar entry and shader selection checks | PASS, no duplicate paths; correct version's Globals shader |
| 2 | 26.3 hidden, muted in-world Atlas UI client | FAIL during world entry |

Batch 1 totals **2,188 passing tests**. All builds use diagnostic qualification
identities; this does not establish same-jar patch-version runtime coverage or
release qualification. Logs: `logs/26.3-port/batch-1-fabric-26.3.log`,
`batch-1-regression-26.1.log`, and `batch-1-regression-26.2.log`.

Batch 2 creates its isolated 2048×128 world but exits with a required mixin
failure. `ProjectileUtilMixin.ringworld$periodicPiercingCollisions` cannot find
the previous `ProjectileUtil.getManyEntityHitResult` descriptor (the overload
ending in `Predicate, float, ClipContext.Block, boolean` and returning
`Collection`). Adapt this against the actual 26.3 collision API while retaining
periodic piercing/projectile behavior. Do not make the injection optional.
Evidence: `logs/26.3-port/batch-2-atlas-26.3.log`; Gradle exits 1. The in-world
rendering gate did not pass. Multiplayer and copied-world batches were not
started; testing stopped at the first failure as agreed. No test game is left
running and no new release has been uploaded.

The session has no `CURSEFORGE_API_TOKEN` configured and no browser control tool;
publication needs an authenticated upload route. Owner publication authorization
is already provided; this is an access limitation, not a request to reapprove it.

## Subsequent all-test sweep

The owner then requested running all tests and listing errors, overriding the
stop-at-first-failure policy. See [the complete sweep report](26_3_TEST_ERRORS_2026-09-15.md).
The 429-test Python suite has one stale ABI expectation and two platform skips.
Fresh Atlas generation, corrected resume, and one copied 26.1.2 → 26.3 server
worldgen/structure smoke pass. All attempted in-world client groups, multiplayer
and raid clients reproduce the required ProjectileUtil mixin failure. Multiplayer
fixtures also need an explicit whitelist policy. Full dual-loader/frozen-candidate
qualification and dependent gameplay assertions remain blocked. No code was
changed or release uploaded during this diagnostic sweep.


September 20 multiplayer evidence is under `logs/26.3-port/fixes-2026-09-20/`.
`multiplayer-server.log`, `multiplayer-client-a.log`, and `multiplayer-client-b.log`
contain the full passing seam/combat/placement/vehicle/reconnect/bed/death/
portal/weather matrix. `network-prep/verify-multiplayer.log` passes.
Attempt 1 exposed teleport-acknowledgement mapping and duplicate fixture movement;
attempt 2 passed gameplay but the diagnostic wrapper stopped the server before
client completion. The final attempt waits for both normal client exits first.
Raid logs `raid-arm-*` and `raid-reload-*` prove saved seam raiders, restored
bossbars, canonical navigation and victory/Hero of the Village. Their clients
were intentionally stopped after server markers (exit 143), not clean-client
exit evidence.


NeoForge `neoforge-build-1.log` passes all 440 Java cases. The hidden, muted
`neoforge-atlas-ui-1.log` passes all eleven Atlas UI captures, generation and
revision proofs, settings handshake and normal disconnect/session clear.
The progressive-world screenshot was visually inspected. No NeoForge-specific
source fork was needed beyond the shared 26.3 adapters. Its dedicated multiplayer and raid checks now pass; final release gates remain.


Both loaders pass the 19-capture creation fixture with the new default hidden/
muted qualification policy (`background-default-fabric.log` and
`background-default-neoforge.log`). The production-render operator and nightly
coordinator now accept `--projection-camera-x 3072` so the retained frozen jar
uses the same unobstructed pose. The selected coordinate is recorded in its
command/evidence; invalid or noncanonical coordinates are rejected.

Frozen quick candidates from `39216e8`:

- Fabric: `e16c2942c4b5108f377c995d868ded953acd9b11e63cbc50a206e19749403c54`
- NeoForge: `ab76c07dbf31d28a526fdf07e03b73f5a4a18990fe6c1a7e1bea615e361d1259`
