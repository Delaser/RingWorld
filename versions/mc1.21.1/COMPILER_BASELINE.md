# Minecraft 1.21.1 compiler baseline

Status: **FUNCTIONAL PARITY GATES COMPLETE; RELEASE INCOMPLETE** as of
2026-08-23. The exact Windows Java 21 dual-loader compiler/dependency baseline,
dedicated runtime matrix, two-client gameplay fixtures, and production-size
client gates pass. Clean public integration, distribution packaging, broader
compatibility, and owner release approval remain.

## Purpose and authority

This baseline implements issue #182's build-only first slice without changing
RingWorld behavior. Minecraft 26.1/26.1.2 `main` remains authoritative. The
backport changes Minecraft and loader ABI boundaries while retaining shared
topology, persistence, protocol meaning, worldgen policy, and rendering math.

The work branch starts from public main commit
`2312fc96bdc8a4a75b7fb7b84b84631b9f59e3fa`. The public
`port/mc-1.21.1` ref was still its older 26.1.2 ancestor
`25efbec21c2be319f9dd26768546fd8365eaf609` when this probe began.

PR #229 (`96c8a43070d83022fa382437ef9b461f812acee6`) was inspected but not
merged or cherry-picked. Its contributor commit is useful as an API-mapping
reference, but it is not a compiler baseline: it removes JUnit dependencies,
contains an artifact-name typo, leaves Fabric metadata on Java 25/newer
Loader requirements, comments out classes still referenced by entrypoints,
silently changes fixed-width protocol fields to VarLong under unchanged
channel identifiers, and disables major storage, gameplay, and renderer
contracts.

## Pinned primary inputs

| Input | Exact identity | SHA-256 / state |
| --- | --- | --- |
| Java | Eclipse Temurin 21.0.12.1+1 Windows x64 JDK ZIP | `f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e` |
| Gradle | 8.10 binary distribution | `5b9c5eb3f9fc2c94abaea57d90bd78747ca117ddbbf96c859d3741181a12bf2a` |
| Minecraft | `com.mojang:minecraft:1.21.1` with official Mojang mappings | immutable version manifest, client/server jars, mappings, asset index, and logging config pinned in `dependency-inventory.json` |
| Fabric Loom | `net.fabricmc:fabric-loom:1.8.13` | `27a4fb8206ba9806b46663cc762ddbbbf43154cea9566f68f79673e220496771` |
| Fabric Loader | `net.fabricmc:fabric-loader:0.16.14` | `9ed7b7f4197153ad97d71b4b9cd9148989e8f0c600934b035ce45f3ef003efc0` |
| Fabric API | `net.fabricmc.fabric-api:fabric-api:0.116.15+1.21.1` | `a61a10f730ab8aa45ff42486ee65699e6e51c28a0c168deb161e7d4029473aa3` |
| NeoForge | `net.neoforged:neoforge:21.1.239` through ModDevGradle 2.0.143 | universal/userdev/config, NeoForm, plugin, and full transitive graph SHA-256-pinned |

[`dependency-inventory.json`](dependency-inventory.json) binds the reviewed
toolchain and Mojang inputs. Gradle's native strict
[`verification-metadata.xml`](../../gradle/verification-metadata.xml) binds the
resolved Maven/plugin graph: 369 components, 748 artifacts, and 748 SHA-256
pins on this Windows baseline. `check` and `:neoforge:check` both run
`verifyBackportDependencyInventory`, which cross-checks those files against
`gradle.properties` and the wrapper. Only artifacts actually downloaded and
hashed are called checksum-verified.

The diagnostic artifact identity is:

```text
0.0.0-backport+mc1.21.1
1.21.1 compiler baseline (unsupported)
```

It is a qualification-only artifact. It must not be staged, published, or
described as supported or as a release candidate.

## Reproduction

Run Gradle itself under Java 21. The normal project graph includes both
loaders:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat compileJava --console=plain --no-daemon
.\gradlew.bat :neoforge:compileJava --console=plain --no-daemon
.\gradlew.bat clean build :neoforge:build --console=plain --no-daemon
.\gradlew.bat verifyBackportMojangDownloads --console=plain --no-daemon
```

For a focused Fabric configuration/compiler probe, exclude only the NeoForge
subproject:

```powershell
.\gradlew.bat compileJava -PringBackportCompilerScope=fabric `
  --console=plain --no-daemon
```

`ringBackportCompilerScope` accepts only `all` or `fabric`. Omitting it is
equivalent to `all`. The property is a compiler-cost isolation boundary, not
permission to claim single-loader completion.

Normal builds must not use `--write-verification-metadata`: that switch accepts
newly observed artifacts instead of enforcing the reviewed inventory. When a
deliberate dependency update is required, regenerate with the exact combined
build below, review every XML and primary-hash change, update the inventory
counts/required artifacts, and finish with the ordinary clean build above:

```powershell
.\gradlew.bat build :neoforge:build --write-verification-metadata sha256 `
  --console=plain --no-daemon
```

The current inventory is Windows-x64 evidence. A later Linux or macOS build
must add any platform-selected native artifacts through the same reviewed
process; it must not weaken dependency verification to make them resolve.
`verifyBackportMojangDownloads` is the explicit online audit: it re-downloads
the immutable version manifest, client/server jars, official mappings, asset
index, and client logging configuration, then independently checks their
declared byte sizes plus SHA-1 and SHA-256 values. It is intentionally not a
normal `check` dependency, so an ordinary verified build remains repeatable
from an already-populated cache without network access.

## 2026-08-22 Windows result

Verified before failure:

- the Gradle 8.10 wrapper download matched its pinned SHA-256;
- Gradle ran on Temurin 21.0.12.1+1;
- Fabric Loom 1.8.13 configured;
- Mojang's official 1.21.1 mappings merged;
- 51 Fabric API modules reached Loom's remapping path;
- the primary Loom, Loader, Fabric API, ModDevGradle, NeoForge, and NeoForm
  artifacts matched the checked-in SHA-256 pins;
- an ordinary clean combined build resolved under strict verification, rebuilt
  both loaders, ran all 338 cases, and passed the inventory verifier without
  metadata-generation mode.

The initial focused online `compileJava` did not reach `javac`. Gradle failed
resolving the Minecraft library configuration after transient DNS errors for:

```text
maven.fabricmc.net
libraries.minecraft.net
repo.maven.apache.org
```

Representative unresolved coordinates included `com.github.oshi:oshi-core:6.4.10`,
`com.mojang:authlib:6.0.54`, `com.mojang:datafixerupper:8.0.16`, Netty
4.1.97.Final modules, and LWJGL 3.3.3 modules. A later direct TCP probe to
Maven Central succeeded, so the failure is treated as transient host DNS/network
state rather than evidence that the pinned coordinates are invalid.

After network recovery, the pinned Fabric and NeoForge graphs resolved and
produced these real results under Java 21:

- `compileJava` and `compileClientJava` pass without a required-target or
  descriptor failure. Fabric's annotation processor reports the two expected
  alternate-loader method names used by the fail-closed shared
  `ServerWorldMixin` and `ServerEntityManagerMixin` hooks; at runtime exactly
  one named target must match on each loader;
- all 338 unit and parameterized tests pass;
- `build -PringBackportCompilerScope=fabric` passes, including the loader
  boundary, packet inventory, runtime-verifier contracts, remapped runtime jar,
  and sources jar;
- the isolated `runCreationUiClient` gate applies the required server/client
  Mixins, initializes OpenGL, loads RingWorld resources and shader programs,
  produces all thirteen expected captures across GUI scales 1-4 and the compact
  view, and exits normally without creating a world;
- `:neoforge:build` passes with the same 338 tests, packet inventory, and
  runtime-verifier contracts, and its isolated creation UI gate produces and
  verifies the same thirteen captures;
- Fabric and NeoForge `runAtlasUiClient` gates each create a disposable
  2048x128 world, acknowledge format 3/mapping 4 with fingerprint
  `adfcbcbaa1fc0b80`, complete all 4,096 Atlas cells, load the complete-ring
  texture and mesh, verify eleven UI/world captures and two ordered live
  revisions, save every dimension, disconnect normally, and prove client
  session teardown;
- both shared map/compass fixtures pass their two seam directions, persistent
  maps/banners/item frames, nearest-image compass targets, normal teardown,
  and reopen checks;
- both curved-object clients produce verified far/near captures, and manual
  review finds their projection, object placement, clouds, sky, and UI
  materially matched across loaders;
- both same-process `different-layout` clients pass a real 2048x128 to
  2048x416 switch. They verify dimension-owned storage in both saves, raw
  client/GPU session teardown between worlds, distinct settings/Atlas
  identities, partial-Atlas replacement, and normal final saving. The isolated
  layout fixture now disables unrelated full-Atlas pregeneration; an initial
  Fabric diagnostic run proved that leaving it enabled could spend minutes
  draining tickets from the deliberately partial second Atlas after the
  fixture itself had passed;
- Fabric and NeoForge both enforce pre-play headless admission and queue
  immutable settings directly after the play-login packet. A repeated Fabric
  map/compass fresh join plus high-side reopen records no initial chunks
  rejected outside the canonical client view range. Fabric's play payload
  handlers apply settings and Atlas packets directly on Fabric's documented
  render-thread callback so later vanilla packets cannot overtake them through
  an unnecessary second executor hop;
- the complete safe-small 6/12/28-chunk visual matrix passes, and the 6- and
  28-chunk captures were reviewed as materially loader-matched. Natural seam
  travel runs from presentation X=-4 to X=2 with a maximum 0.25-block step and
  unchanged camera pose. At 6 chunks Fabric/NeoForge average 8.495/8.494 ms
  with zero frames over 50 ms; at 12 chunks they average 8.481/8.485 ms with
  zero over 50 ms; at 28 chunks they average 9.865/9.608 ms with one over-50-ms
  frame each (55.595/67.999 ms maxima). No final 28-chunk log records an
  initial out-of-range chunk rejection.

After that bounded checkpoint, the 2026-08-23 runtime pass added:

- dedicated Fabric and NeoForge creation, stop, reload, immutable saved
  settings, dimension-owned storage, complete topology, finite-width, seam,
  portal, and aggregate world-generation/structure evidence;
- the complete dedicated two-client seam/gameplay matrix on both loaders,
  including combat, stateful blocks, bed/death recovery, boats, hostile
  navigation, physical portals, normalized multi-lap Nether returns,
  reconnect, destination water, and server-authoritative thunder/lightning;
- a real two-phase persisted raid on both loaders. Each arm phase saved a
  first vanilla wave with both seam-side players in the bossbar; each reload
  restored the POIs, raid, and raiders, observed a natural raider fold through
  the seam, completed vanilla victory, and awarded Hero of the Village;
- a complete 16,384x256 Atlas with all 65,536 cells. An immutable source save
  with matching `level.dat` and Atlas hashes was copied into both loader run
  roots, and each loader passed Overworld/Nether/End lifecycle, normal
  disconnect, raw client-session teardown, save, and same-process reopen;
- production 16,384x256 noon, dusk, night, and rain tangent, 16-chunk
  live/proxy handoff, and radial-up captures on both loaders. All automated
  capture verifiers pass. Direct review found loader-matched night and rain
  terrain, walls, horizon, precipitation, opposite-surface silhouette, and
  radial seam; the capture harness now gives asynchronous terrain/lightmap
  work 200 client ticks before measuring each view so its first frame is a
  steady-state comparison;
- production-size natural seam and both textured-rim visual gates on both
  loaders. The natural-crossing windows sampled 956 Fabric and 925 NeoForge
  frames, averaged 8.56/8.58 ms, recorded zero frames over 50 ms, and retained
  the same 0.25-block maximum step;
- the final ordinary `clean build :neoforge:build` passes from the strict
  369-component/748-artifact graph and reruns all 338 cases for both modules.
  The diagnostic Fabric runtime jar is 700,682 bytes with SHA-256
  `55dd91f62a8670d7fe8e8d11ab594dc81f5e26bacd8dd45e08fbcc6360c5c45d`;
  the NeoForge runtime jar is 668,957 bytes with SHA-256
  `d42b837258e1e1361065a6adbe4c4af89c70aa2606e034973f37e72e8ebbf81e`.
  Both declare MPL-2.0, target exactly Minecraft 1.21.1/Java 21, contain
  `LICENSE-RINGWORLD.txt`, and contain no stale MIT/evaluation identifier.

This is local Windows source/runtime evidence, not a published support claim.
It does not qualify third-party compatibility, installer/outer-package
assembly, authenticated production launchers, other operating systems, or a
public release artifact.

## Next gate

1. Integrate the reviewed working tree onto the public `port/mc-1.21.1` branch
   without weakening dependency verification or rewriting the shared
   protocol/save contracts.
2. Run the fail-closed staging/package procedure from that clean pushed
   revision, then obtain owner release approval. Do not publish from this
   diagnostic identity.

The compiler, dependency, functional runtime, and production visual baselines
are complete for the recorded Windows host. The remaining work is integration
and release qualification, so the port is not yet supported or a release
candidate.
