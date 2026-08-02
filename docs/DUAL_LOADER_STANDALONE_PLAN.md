# Dual-loader standalone release plan

Status: approved 2026-08-02. GitHub epic: [#34](https://github.com/Delaser/RingWorld/issues/34).

## Objective

Finish a solid standalone RingWorld build for Fabric and NeoForge before
spending project time on broad third-party mod compatibility. Both loader
artifacts must preserve one saved-data format, one wire format, one topology,
one shader contract, and the same gameplay behavior.

## Phase 4 — NeoForge

1. [#90](https://github.com/Delaser/RingWorld/issues/90): split loader-neutral
   sources from Fabric-owned entrypoints, events, transport, paths, and launch
   code. Keep the Fabric candidate green at every step.
2. [#91](https://github.com/Delaser/RingWorld/issues/91): add the NeoForge
   metadata, entrypoints, lifecycle adapters, networking transport, commands,
   and development launches. **Checkpoint reached:** the Java 25 NeoForge
   26.1.2.87 / ModDevGradle 2.0.143 module shares the core sources, passes all
   233 unit/parameterized cases, and its dedicated server reaches `Done` with
   atlas startup/progress. Graphical-client integration and testing completed
   under #92.
3. [#92](https://github.com/Delaser/RingWorld/issues/92): **complete.** The NeoForge
   client pass the curved-renderer, atlas, shader, sky, world-switch, and frame
   pacing gates. **Initial checkpoint reached:** shared client payload/session
   state plus NeoForge lifecycle, payload, and render-pipeline adapters load
   the shared client mixins/shaders/resources; a copied 16,384×256 integrated
   world acknowledged format 2, streamed an incomplete atlas, and rendered
   progressive tangent/radial diagnostics. The replacement
   `:neoforge:runProductionProjectionClient` gate uses a disposable save copy,
   waits for completion, captures tangent/handoff/radial views, verifies them,
   and exits. Production noon/dusk/night/rain, seam/both-rim, layout-switch,
   and Overworld/Nether/End/save/reopen lifecycle gates pass. Gameplay and
   multiplayer parity continue under #93.
4. [#93](https://github.com/Delaser/RingWorld/issues/93): **complete.** The
   NeoForge server passes topology, worldgen, storage, atlas, structure,
   headless-prewarm, and dedicated two-client gameplay gates. The shared
   multiplayer matrix covers natural seam travel, visibility, combat, blocks,
   explosions, beds, death/respawn, physical Nether/End portals, boats,
   teleports, and reconnect; the loader-selectable worldgen matrix covers
   fresh/reload production and safe-small policy cases.
5. [#94](https://github.com/Delaser/RingWorld/issues/94): **complete.** Fabric
   and NeoForge produce loader-labelled, reproducible client/server packages
   and local Modrinth stages from one clean pushed commit. The gate validates
   each loader's metadata and licence, compares shared mixins, API, protocol,
   settings, geometry, and shaders before writing either stage. Loader-specific
   Prism instances preserve user data and prevent third-party mods from being
   carried across loaders while refreshing their own managed jars. Focused tests
   and a real packaged macOS NeoForge client launch pass. The Windows launcher
   executes in CI; the final graphical Windows Minecraft run remains #12.

The renderer and server ports may proceed independently after the shared build
and platform contracts are stable. Cross-loader client/server connections are
not an initial release requirement.

## Phase 5 — gameplay and visual polish

- [#95](https://github.com/Delaser/RingWorld/issues/95): ordinary survival and
  creative playability, including sleeping, portals, maps, raids, vehicles,
  structures, combat, building, redstone, weather, and reconnects. The fresh
  strengthened two-client and raid fixtures now pass on both loaders; direct
  maps/compasses now pass on both loaders, while ordinary play sampling,
  weather/portal delays, and candidate evidence remain.
- [#96](https://github.com/Delaser/RingWorld/issues/96): final live/atlas
  transition, colour, fog, ring alignment, sky, sun, clouds, walls, curved
  entities, supported-size captures, and performance budgets.

These passes may run together once both loader clients and servers function.
No known blocker or high-severity standalone defect may remain at sign-off.

## Phase 6 — release

1. Complete the remaining clean Windows/package evidence in
   [#12](https://github.com/Delaser/RingWorld/issues/12).
2. Run the independent exact-candidate review in
   [#13](https://github.com/Delaser/RingWorld/issues/13).
3. Use [#97](https://github.com/Delaser/RingWorld/issues/97) to freeze hashes,
   stage the loader-specific Modrinth versions, verify clean downloads, update
   public documentation, and preserve rollback material.
4. Keep a final owner go/no-go immediately before hosted state changes.

## Deferred compatibility phase

Broader third-party testing is tracked by
[#98](https://github.com/Delaser/RingWorld/issues/98) and begins only after the
standalone release is approved. Create is the first named integration target.
Compatibility fixes must not fork geometry, saves, packets, or renderer
contracts between loaders.

## Build boundary introduced by #90

- `src/main/java` and `src/client/java` are shared Minecraft/common sources.
- `src/platform/fabric/java` and `src/platform/fabricClient/java` contain the
  Fabric-owned entrypoints and adapters.
- `verifyLoaderBoundary` fails the build if Fabric or NeoForge API references
  enter either shared source tree.
- `src/platform/neoforge/java` and the `neoforge` ModDevGradle module now
  consume the same shared sources rather than copying domain logic. Use
  `:runServer` for Fabric and `:neoforge:runServer` for NeoForge; an
  unqualified `runServer` is ambiguous.
