# RingWorld for Minecraft 1.21.1

This directory is the contribution home for the Minecraft Java 1.21.1
backport of RingWorld. Its local dual-loader functional parity matrix now
passes, but the port is **not yet integrated, packaged, published, or
supported**. Work is coordinated on the temporary integration branch
`port/mc-1.21.1`; incomplete port code must not be presented as a release.

The goal is full RingWorld behavior on Minecraft 1.21.1, with Fabric and
NeoForge support where the available loader versions make that practical.
The existing Minecraft 26.1 implementation remains authoritative for game
behavior. The backport adapts Minecraft and loader boundaries without
creating a divergent RingWorld design.

## Current dual-loader checkpoint

The backport is being validated on top of current `main`.
It pins Minecraft 1.21.1, Java 21, Gradle 8.10, Fabric Loom 1.8.13,
Fabric Loader 0.16.14, Fabric API 0.116.15+1.21.1, and NeoForge 21.1.239.
The artifact identity is deliberately `0.0.0-backport+mc1.21.1` with an
unsupported compiler-baseline label; it is not a release candidate.
The exact Windows-x64 Java/Mojang inputs and all 748 resolved Gradle artifacts
are now SHA-256-pinned by
[`dependency-inventory.json`](dependency-inventory.json) and Gradle's strict
verification metadata. Both loader checks fail if the reviewed versions,
wrapper, primary hashes, inventory counts, or dependency artifacts drift.

After the transient DNS failure cleared, the 2026-08-22 Windows work reached
a real dual-loader checkpoint. Fabric and NeoForge common/client sources
compile on Java 21, both builds pass all 338 tests and their contract checks,
and both isolated menu clients apply the required Mixins, load resources and
shaders, produce all thirteen creation/settings captures, and exit normally.
Both loaders also pass the integrated Atlas client gate: a fresh 2048x128
world acknowledges settings format 3 and terrain-noise mapping 4, completes
all 4,096 Atlas cells, renders the progressive and completed ring surface,
commits two live revisions, saves all dimensions, disconnects normally, and
clears client session state. The shared map/compass fixture also passes on
both loaders in both seam directions, including map pixels and markers,
scale/lock, banner removal/restoration, item-frame persistence, periodic
spawn/lodestone/recovery targets, normal teardown, and reopen. Both curved
object fixtures produce reviewed, materially matched far/near captures.
Both loaders pass the same-process `different-layout` regression using real
2,048×128 and 2,048×416 saves: dimension-owned storage exists in both,
disconnect clears raw client/GPU session state, the second settings/Atlas
identity replaces the first, partial Atlas data remains valid, and both worlds
save normally. The isolated fixture disables unrelated full-Atlas
pregeneration so its final save is bounded without changing either source.
Fabric now mirrors NeoForge's fail-closed login ordering: headless joins are
rejected at `PlayerList.placeNewPlayer` head, while permitted clients receive
immutable settings immediately after the play-login packet and before initial
position/chunk packets. Fabric's render-thread payload callbacks apply those
packets in arrival order rather than adding a second executor hop. Repeated
fresh and high-side-reopen joins now record zero rejected out-of-range chunks.
Both loaders also pass the reviewed safe-small 6/12/28-chunk visual matrix:
natural seam travel advances from presentation X=-4 to X=2 with a maximum
0.25-block step, preserves the requested camera pose, and renders both
textured rims. The 6- and 12-chunk cells record 840+ measured frames per
loader with no frame over 50 ms. The 28-chunk cells average 9.6-9.9 ms and
record one over-50-ms frame per loader. The subsequent 2026-08-23 matrix also
passes dedicated topology, persistence, aggregate worldgen/structures, the
complete two-client seam/gameplay fixture, the two-phase persisted-raid
regression, production 16,384x256 lifecycle, all noon/dusk/night/rain
projection views, natural seam travel, and both textured rims on Fabric and
NeoForge. Reviewed night and rain captures are materially loader-matched.
Packaging, clean public integration, broad compatibility, and owner release
approval remain. Exact inputs, commands, evidence boundaries, and the
fail-closed scope are recorded in
[`COMPILER_BASELINE.md`](COMPILER_BASELINE.md).

Use `-PringBackportCompilerScope=fabric` for the focused Fabric compiler
probe. Omitting the property retains the normal combined Fabric/NeoForge
project graph. The focused scope is diagnostic isolation only and does not
weaken the eventual dual-loader definition of done.

## What should stay shared

These rules and formats should remain loader- and Minecraft-version neutral:

- canonical X topology, nearest-image distance, and seam behavior;
- finite Z width and rim ownership;
- ring settings, validation, layout fingerprints, and Atlas identity;
- Atlas traversal, persistence, and revision semantics;
- gameplay expectations for blocks, entities, vehicles, portals, beds,
  maps, compasses, raids, and structures;
- network payload meaning, even where registration APIs differ; and
- pure geometry, topology, persistence, and policy tests.

Minecraft 1.21.1-specific code should be limited to the smallest practical
adapter surface: mixin targets and descriptors, renderer and shader hooks,
packet classes, world-generation entry points, mappings, loader lifecycle,
and build metadata.

## Planned layout

The directory may grow as the port establishes real compiler evidence:

```text
versions/mc1.21.1/
├── README.md             This guide and current status
├── common/               Minecraft 1.21.1 adapter code, if required
├── fabric/               Fabric-owned lifecycle and transport adapters
├── neoforge/             NeoForge-owned lifecycle and transport adapters
├── mixins/               Version-specific mixin configuration, if required
└── resources/            Version-specific shaders or metadata, if required
```

Do not copy shared RingWorld classes into this directory simply to make the
port compile. Propose a shared abstraction or a narrow version adapter first.

## Contribution workflow

1. Choose an open issue carrying the `mc:1.21.1` label. Comment before
   starting so two contributors do not unknowingly implement the same slice.
2. Fork the repository and branch from `port/mc-1.21.1`, not from an old
   release jar or generated/decompiled source.
3. Keep the pull request limited to one issue or adapter boundary.
4. Open the pull request against the upstream `port/mc-1.21.1` branch.
5. Include compiler output, focused tests, and any real runtime evidence the
   issue requests. A successful compile is not sufficient for topology,
   rendering, world generation, networking, or multiplayer work.
6. Update this README and other affected documentation with behavior or
   commands changed by the contribution.

Example:

```sh
git clone https://github.com/Delaser/RingWorld.git
cd RingWorld
git switch port/mc-1.21.1
git switch -c contributor/1.21.1-fabric-bootstrap
```

When opening the pull request, set the base branch to
`port/mc-1.21.1`. Maintainers will integrate completed slices there and merge
the backport into `main` only after its required qualification gates pass.

## Work areas

The backport is deliberately split so contributors can work independently:

1. pinned Minecraft 1.21.1, Java 21, mappings, and loader build inputs;
2. Fabric bootstrap and metadata;
3. NeoForge bootstrap and metadata;
4. shared/server mixin descriptor audit;
5. topology, storage, ticking, and seam interaction runtime checks;
6. world generation, structures, portals, and dimensions;
7. settings handshake and Atlas networking;
8. client chart, rendering, shaders, sky, clouds, and distant ring;
9. automated unit, server, multiplayer, and graphical fixtures; and
10. packaging, documentation, and release-host metadata.

The [backport epic](https://github.com/Delaser/RingWorld/issues/181) and
[Minecraft 1.21.1 milestone](https://github.com/Delaser/RingWorld/milestone/1)
are the authoritative task tracker:

- [build, mappings, and dependency baseline](https://github.com/Delaser/RingWorld/issues/182);
- [shared mixins and server topology](https://github.com/Delaser/RingWorld/issues/183);
- [Fabric adapter](https://github.com/Delaser/RingWorld/issues/184);
- [NeoForge adapter](https://github.com/Delaser/RingWorld/issues/185);
- [worldgen, structures, dimensions, and portals](https://github.com/Delaser/RingWorld/issues/186);
- [settings handshake, Atlas, and persistence](https://github.com/Delaser/RingWorld/issues/187);
- [client rendering, shaders, sky, and clouds](https://github.com/Delaser/RingWorld/issues/188);
- [gameplay and multiplayer qualification](https://github.com/Delaser/RingWorld/issues/189); and
- [automation, packaging, documentation, and release gates](https://github.com/Delaser/RingWorld/issues/190).

Issues state their dependencies, expected evidence, and whether Fabric,
NeoForge, or both are in scope.

## Compatibility and save policy

- Minecraft 1.21.1 uses Java 21; the current 26.1 line uses Java 25.
- A separate artifact is expected for 1.21.1. Do not widen current jar
  metadata until same-file evidence proves it is safe.
- RingWorld saved-data formats should remain readable where possible, but
  Minecraft worlds must not be moved backwards from 26.1 to 1.21.1.
- Existing RingWorld worlds must never be silently migrated to a different
  terrain-noise mapping or geometry.
- Both server and client require the matching RingWorld build.

## Definition of done

The port may be called supported only when both intended loaders have, or a
documented loader exception has:

- reproducible Java 21 builds from pinned inputs;
- matching core settings, protocol, topology, and Atlas contracts;
- dedicated-server creation, save, stop, and reload evidence;
- seam worldgen, structures, portals, and finite-rim evidence;
- two-client seam/gameplay/multiplayer evidence;
- real client resource, shader, rendering, lifecycle, and visual evidence;
- clean distributable jars containing the MPL-2.0 licence; and
- owner release approval after the automated matrix passes.

Until then, status should be described precisely as `not started`,
`compiles`, `runtime testing`, or `release candidate`—never `supported`.

## Contribution and licence requirements

All contributions are subject to the repository's
[contribution guide](../../CONTRIBUTING.md) and Mozilla Public License 2.0.
Do not submit decompiled Minecraft source, Mojang assets, credentials,
private runtime data, or code you are not entitled to license. Explain the
origin and licence of every new dependency or bundled asset.
