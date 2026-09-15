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

## Compilation blocker

The Fabric source compile reaches javac and stops at its 100-error limit.
These are source incompatibilities, not a missing Minecraft download:

- Density functions moved into `levelgen.densityfunction` and changed their
  interfaces. `RingNoiseRouter`, `RingClimateSampler`, and periodic density
  mixins need a 26.3 adapter.
- Surface-generation types changed; existing surface mixins no longer compile.
- `RandomState.sampler()` / `router()` and the previous biome-fill signature
  are gone. Spawn-target types changed too.
- Entity iteration, movement handling and structure lookup APIs also changed.

No production Java or shader source was changed. Client compilation and runtime
tests have not run because main compilation failed. Full dual-loader qualification
correctly remains unavailable with a missing loader cell; source compilation is
not release qualification.

Reproduce the Fabric failure with Java 25:

```sh
./gradlew :compileJava :compileClientJava \
  -Pminecraft_version=26.3 -Ploader_version=0.19.5 \
  -Ploom_version=1.17.21 -Pfabric_api_version=0.160.5+26.3 \
  -Pneoforge_version=26.2.0.69 -Pmoddevgradle_version=2.0.144 \
  -Pmod_version=0.0.0-qualification+mc26.3 -Prelease_label=qualification \
  -PringQualificationRoot=dist/qualification/26.3-intake \
  -PringQualificationCell=26.3-fabric --console=plain
```

Local evidence: `logs/26.3-port/compile.log`.

## Existing-version regression

Both Fabric and NeoForge build successfully against 26.1 (the 26.1.x source
floor) and 26.2, with 437 tests passing per build: 1,748 cases total. These are
diagnostic `0.0.0-qualification` jars, not publishable release files. This run
does not requalify 26.1.1/26.1.2 runtimes or establish fresh release evidence.
Logs: `logs/26.3-port/regression-26.1.log` and `regression-26.2.log`.
The new manifest passes structural validation; 24 existing matrix/support
contract tests pass. No new framework, wrapper change or compatibility bypass
was introduced.

## Remaining release work

Port the affected APIs while preserving older source adapters; pin the real
26.3 NeoForge runtime when published; pass relevant worldgen, rendering and
multiplayer checks on release candidates; stage updated supported versions
with immutable source and changelogs; upload to CurseForge and verify hashes.
The current session has no `CURSEFORGE_API_TOKEN` configured and no browser
control tool, so publication will also need an authenticated upload route.
Owner publication authorization is already provided.
