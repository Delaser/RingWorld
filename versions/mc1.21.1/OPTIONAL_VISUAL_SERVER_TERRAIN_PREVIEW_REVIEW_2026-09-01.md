# Minecraft 1.21.1 optional-visual server terrain-preview review

Reviewed source/test commit `7e95b6ac3e1733d864bd96a4b35c7addf842ab65`
and documentation commit `4295d0dfe795e6c98e7b34b1a80a7f82fa26c723`
against parent `a2ee13ab060c2926993449d511a89f4498f96006` in
the implementer worktree. Scope was limited to the staged in-world server
preview sampler/service, immutable generator isolation, world/session identity,
cancellation and publication order, existing dual-loader transport, lifecycle
cleanup, tests, and batch boundaries. No builds, runtime fixtures, or
production edits were performed by the reviewer.

## Disposition

**Pass; no actionable findings or blockers.**

## Review checklist

- [x] Preview sampling is chunk-free and non-persistent. The server-thread
  capture reads the retained generator configuration and existing saved
  settings but never requests a chunk or invokes a storage save. The worker
  sampler performs only detached `getFirstOccupiedHeight` noise-column and
  `BiomeSource.getNoiseBiome` queries and encodes results in memory; no
  `ServerLevel`, chunk-loading, filesystem, or world-save API reaches it.
- [x] Stage dimensions and order match the stable contract: CURRENT
  512×16 colour/128×8 terrain, HIGH 1024×32/256×16, VERY_HIGH
  2048×32/512×16, and ULTRA 4096×64/1024×32, each capped only by the actual
  ring geometry. Wire values remain 0–3 in declaration order, and the single
  daemon executor iterates that order deterministically.
- [x] Background generation is isolated from retained worldgen state. Capture
  freezes the Atlas/settings world hash, geometry, seed, terrain mapping, wall
  height/style, build-height bounds, biome source, generator-settings holder,
  and frozen noise-registry lookup on the server thread. The worker constructs
  a new `NoiseBasedChunkGenerator`, applies the saved RingWorld generator
  fields, creates a fresh `RandomState` and immutable `LevelHeightAccessor`,
  and owns all resulting mutable noise/climate caches.
- [x] Every stage is bound to the intended world and client session. One job is
  keyed by `ServerLevel` identity and its immutable Atlas world hash;
  server-thread publication re-reads the current authoritative Atlas and
  requires the same job, same hash, incomplete Atlas, and a live subscriber.
  `terrain_preview_v2` repeats the hash in both envelope and validated body,
  while the client accepts it only beside current matching Atlas metadata.
- [x] Cancellation and stale-result rejection are fail-closed. Each job owns a
  cancellable `Future`; the sampler checks interruption during both terrain
  and colour passes. World unload, authoritative Atlas completion, world-hash
  replacement, and last-subscriber disconnect cancel and remove the job.
  Already queued server tasks cannot publish after removal, and defensive byte
  copies prevent a producer or later joiner from mutating retained stage data.
- [x] Stage delivery is strictly increasing. The worker enqueues stage results
  serially onto the owning server thread; `RingTerrainPreviewJobState` rejects
  duplicate/older stages and wrong hashes; existing subscribers receive each
  accepted stage, while later subscribers receive the latest retained stage
  immediately. `ClientRingState` independently refuses an older or duplicate
  stage so delayed loader delivery cannot replace newer session state.
- [x] Both loader paths remain correct and unchanged from the parent. Fabric
  and NeoForge already register `terrain_preview_v2` as a required clientbound
  channel and route it through their ordered client handlers. The shared Atlas
  coordinator now requires that capability before metadata and sends through
  the existing loader transport adapter. Fabric and NeoForge disconnect hooks
  both call `clearPlayer`, removing preview subscriptions and cancelling the
  worker when the final subscriber leaves.
- [x] The committed source/test range is confined to the shared server
  coordinator, three new server/common preview classes, one client-state
  monotonicity guard, and four focused tests. The documentation commit changes
  only the technical datasheet. There is no client renderer, shader, resource,
  in-world placeholder, creation-screen preview, UI/HUD, Atlas-light rendering,
  sky rendering, command, complete-ring mesh, Create integration, worldgen
  persistence, packaging, support metadata, or verification-metadata scope
  creep.

The first implementer validation stopped before tests at a Java static-type
issue involving the runtime Mixin interface on final 1.21.1
`NoiseBasedChunkGenerator`; retaining the established `ChunkGenerator` static
type resolved that compile-only issue without weakening isolation. The
implementer reports the corrected Java 21 gate compiling Fabric/common and
NeoForge and all 43 focused sampler, order, identity, cancellation, transport,
lifecycle, client-session, and Atlas cases passing on each loader. Per
assignment, this independent review did not rerun builds or tests; it verifies
the committed implementation, test surface, documentation claim, and diff
boundaries.
