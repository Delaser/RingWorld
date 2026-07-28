# Minecraft 26.1.2 compiler baseline

Captured: 2026-07-28

Phase 1 base: `581a751`

Status: Phase 2 toolchain established; common source port in progress and
client compiler baseline captured

This checkpoint deliberately records an honest failing compile. It proves that
the project resolves the official unobfuscated Minecraft 26.1.2 and Fabric
artifacts under Java 25 without weakening any mixin. It is the stable base for
parallel source-port work, not a playable build.

## Toolchain

| Component | Version |
| --- | --- |
| Minecraft Java | 26.1.2 |
| Java | Microsoft OpenJDK 25.0.4+7 LTS |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.1.2 |
| Fabric Loom plugin | `net.fabricmc.fabric-loom` 1.17-SNAPSHOT |
| Gradle wrapper | 9.5.1 |
| RingWorld version | `0.2.0+mc26.1.2` |

The build has no mappings dependency, uses ordinary `implementation`
configurations, compiles for Java 25, declares exact Minecraft 26.1.2, and
keeps both mixin configs required with `defaultRequire: 1`.

## Reproduction

Run Gradle itself under Java 25:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean compileJava --console=plain
```

The first run downloads Gradle 9.5.1, Minecraft 26.1.2, and Fabric
dependencies. The expected checkpoint result is:

```text
95 errors
BUILD FAILED
```

The last fully passing suite remains the Mojang-mapped 1.21.11 Phase 1 commit,
with 73 cases and all runtime gates.

## Error inventory

| Source | Errors | Primary cause |
| --- | ---: | --- |
| `ServerChunkLoadingManagerMixin` | 23 | `ChunkPos` record access/packing plus later target redesign |
| `RingChunkFilter` | 17 | `ChunkPos` record access/packing |
| `ServerWorldMixin` | 15 | `ChunkPos` record access/packing; synthetic tick target required source redesign |
| `ServerEntityManagerMixin` | 15 | `ChunkPos` record access/packing |
| `RingWorldNetworking` | 6 | payload registry method renames |
| `ServerChunkManagerMixin` | 6 | `ChunkPos` record access/packing |
| `RingWorldServer` | 5 | Fabric level-event and chunk-load callback changes |
| `RingWorldSettings` | 3 | saved-data storage/API changes |
| `RingTerrainAtlasServer` | 2 | `ChunkPos` record access |
| `ChunkPosDistanceLevelPropagatorMixin` | 2 | `ChunkPos.pack` rename |
| `PlayerInteractionDistanceMixin` | 1 | attack-range API redesign |

The 95 diagnostics fall into four work streams:

1. mechanical `ChunkPos` conversion to `x()`, `z()`, `pack()`, and
   `unpack(long)`;
2. Fabric lifecycle and payload registration renames;
3. saved-data/dimension-storage migration owned by secondary task S2;
4. behavioral mixin/API redesign that must be checked against 26.1.2 source
   before changing injection targets.

The absence of a compile error for a mixin does not establish that its target
still exists or has the same semantics. All 35 mixins remain subject to the
source audit and runtime application gates in
[`PORTING_26_1_AUDIT.md`](PORTING_26_1_AUDIT.md).

## Primary follow-on progress

The first primary source-port pass reduced common compilation from 95 errors
to five without editing S2-owned storage code. It converted `ChunkPos` to its
26.1 record/packing API, renamed Fabric server level/chunk/tick callbacks,
renamed payload registries by wire direction, and preserved weapon-sensitive
attack reach.

The remaining five diagnostics are intentionally left to S2:

- three in `RingWorldSettings`;
- two `ChunkPos` accessor changes in `RingTerrainAtlasServer`.

This reduction does not prove that any of the affected mixin injection targets
apply at runtime.

The same pass source-audited the entity simulation call in 26.1.2
`ServerLevel.tick` and replaced the frozen baseline's synthetic
`method_31420` redirect. `ChunkTracker` neighbour packing was likewise updated
from `ChunkPos.asLong` to `ChunkPos.pack`.

## Client probe

A detached worktree with only temporary shims for the five S2-owned common
errors allowed `compileClientJava` to run without modifying the shared storage
lane. The first 26.1.2 client compile reported 21 source diagnostics:

| Area | Errors | Primary cause |
| --- | ---: | --- |
| sky render mixin | 3 | extracted sky state and level time API changes |
| entity render mixin | 2 | camera render-state ownership changed |
| world-creation screen | 4 | GUI graphics/render extraction redesign |
| terrain proxy renderer | 3 | render-pipeline, far-plane, and light-texture API changes |
| Fabric world-render callback | 2 | event package/phase redesign |
| client packet/chart mapping | 4 | `ChunkPos` record access |
| debug position display | 3 | removed `ChunkPos(BlockPos)` constructor and record access |

The primary branch removed the seven mechanical `ChunkPos` diagnostics, ported
the world-creation screen to `GuiGraphicsExtractor`, and moved the frame
sampling callback to `LevelRenderEvents.END_MAIN`. A second detached probe
reported eight diagnostics in the sky/entity mixins and complete-ring
renderer. Source inspection established the new level render-state packages,
Overworld clock accessor, `ColorTargetState`/`DepthStencilState` pipeline
model, exact camera far-plane formula, and `GameRenderer.levelLightmap()`.
After applying those changes, the detached `compileClientJava` probe passes.

This is a source-compile checkpoint, not runtime rendering evidence. Mixin
application, shader ABI, sky order, depth behavior, and live/proxy alignment
still require the runtime gates after S2 makes the main build whole.

## Next ownership split

Primary work proceeds with common Fabric API/name updates, canonical topology,
world generation, networking, rendering, and mixin target audits.

The secondary agent may now begin S2 from this exact Phase 2 checkpoint:
world storage, saved RingWorld settings, terrain-atlas storage migration, and
copied-world tests. S2 must not change geometry fields, topology mixins, or
wire layouts.
