# RingWorld for Minecraft 1.21.1

This directory is the contribution home for the planned Minecraft Java
1.21.1 backport of RingWorld. The port is **not currently playable or
supported**. Work is coordinated on the temporary integration branch
`port/mc-1.21.1`; incomplete port code must not be presented as a release.

The goal is full RingWorld behavior on Minecraft 1.21.1, with Fabric and
NeoForge support where the available loader versions make that practical.
The existing Minecraft 26.1 implementation remains authoritative for game
behavior. The backport adapts Minecraft and loader boundaries without
creating a divergent RingWorld design.

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

The GitHub milestone and epic are the authoritative task tracker. Issues
should state their dependencies, accepted files, expected evidence, and
whether Fabric, NeoForge, or both are in scope.

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
