# Minecraft 26.3 intake — 2026-09-15

The owner authorized adding 26.3 and publishing updated supported versions to
CurseForge, with minimal changes and conservative token use. No new release has
been uploaded. Existing 1.1 downloads remain the supported release.

## Inputs

Mojang's [official manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
lists 26.3 as a stable release, requiring Java 25.
`config/minecraft-version-matrix-26.3.json` pins its client/server SHA-1 hashes,
Fabric Loader 0.19.5, Fabric API 0.160.5+26.3 and Loom 1.17.21.
The dependency jars were downloaded from official Maven repositories and
SHA-256 hashed for the manifest.

The [NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
has no 26.3 runtime or installer. The manifest records this under
`pending_loaders`; add a real pinned NeoForge cell when available. The 26.2.0.69
NeoForge pin in the Fabric cell only configures the unused companion Gradle
project. It must never be launched or advertised as a 26.3 runtime.

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
  -Pneoforge_version=26.2.0.69 -Pmoddevgradle_version=2.0.144 \
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

## Remaining release work

1. Build/regression batch passes on `e880952`; preserve those exact-source
   results and repeat affected checks after further fixes.
2. Fix the 26.3 projectile-collision mixin failure below, then rerun the hidden,
   muted `:runAtlasUiClient` fixture. Audit custom Atlas pipeline compilation, input attribute
   locations, transparency/fog, curved clouds, upward views and the live/LOD seam.
   Check every runtime mixin target; menu success cannot prove packet handlers.
3. Run two-client seam/gameplay checks, copied-world upgrades and required
   release qualification. Preserve complete waypoint/timing data in new packets.
4. Pin the real NeoForge 26.3 runtime when upstream publishes it. The partial
   Fabric manifest intentionally does not satisfy the dual-loader contract.
5. Stage updated supported versions with immutable source, versions and
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
