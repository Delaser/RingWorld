# Minecraft 1.21.1 optional-visual Atlas format-8 service review

Reviewed source/test commit `61ee9ee0b8bb78bee6537aaf6c24026406a7110c`
and documentation commit `a2ee13ab060c2926993449d511a89f4498f96006`
against parent
`637eb60473bfd5cc98e2420947b917f0b945dc5d` in the implementer worktree.
Scope was limited to format-8 cell/tile semantics, authoritative sampling,
storage ownership and rejection policy, revision/partial/reconnect behavior,
surface invalidation, existing loader transport, tests, and batch boundaries.
No builds, runtime fixtures, or production edits were performed by the
reviewer.

## Disposition

**Pass; no actionable findings or blockers.**

## Review checklist

- [x] Format 8 stores the documented cell exactly: presence, signed-short
  exposed top-face height, low-24-bit RGB, and one unsigned block-light byte.
  Both disk and tile writers emit that order. Mutation, disk load, and tile
  application enforce light values 0–15; content comparison includes light;
  and an absent incoming tile cell cannot erase a present cached cell.
- [x] Initial chunk capture and bounded recapture use the same authoritative
  sample: `WORLD_SURFACE` height plus one for the exposed top face, the existing
  texture-luminance-corrected water/grass/foliage colour rules, the explicit
  `0x6F6365` mycelium top colour, and the maximum of surface-block emission,
  block light at that block, and block light one block above.
- [x] Old caches fail closed. The loader requires the exact format-8 header,
  geometry, world hash, sample step/grid, non-negative revision, bounded light
  bytes, and no trailing data. An invalid current dimension cache rebuilds
  without falling back to a root cache; an invalid/old legacy cache is not
  copied. Atlas format and sample semantics remain included in the world hash.
- [x] Storage remains dimension-owned at
  `<dimension>/data/ringworld/terrain-atlas.rwat.gz`. Service state is keyed by
  `ServerLevel` identity, load is Overworld-only, block invalidation has an
  explicit Overworld guard, loader lifecycle adapters call the common service
  only for the Overworld, and unload checkpoints then removes that exact world
  state. Nether and End acquire no Atlas writer or cache.
- [x] Initial and partial population continue to mutate one Atlas without
  manufacturing revisions. Surface recaptures mark one pending generation,
  the end-level tick commits it monotonically, and changed cells publish into
  the existing set-backed dirty-tile queue. The server sends each client's
  coalesced tiles before its revision commit and retains world-specific stream
  ownership through unload/disconnect cleanup.
- [x] Client metadata, tile, preview, and revision paths remain world-hash
  bound. Cache reuse requires matching format, geometry, world hash, and exact
  metadata revision; partial caches still request the full authoritative tile
  set, present cells merge monotonically, stale revisions cannot roll back,
  and a revision commit forces durable save. The focused reconnect test proves
  the block-light byte survives partial-cache save/clear/reinstall, while the
  existing session clear prevents an in-memory Atlas from crossing worlds.
- [x] A relevant surface edit expands to the Atlas-cell footprint covering the
  vanilla 15-block light radius. The helper uses canonical periodic X,
  floor-mod wraps both seam directions, clamps rows to finite Z, validates its
  center/radius, and includes the changed cell. Repeated cells and tiles
  coalesce in the existing exact-cell/overflow-tile sets, with recapture still
  bounded to 64 cells per tick.
- [x] Both loader transports remain correct and unchanged from the parent.
  Fabric and NeoForge register the established metadata-v2, tile-v2,
  revision-v1, and request-v2 payloads; Fabric routes client payloads directly
  in arrival order, while NeoForge uses its ordered `enqueueWork` handlers.
  Both server request paths require the completed settings handshake. No new
  Atlas channel ID is needed because format-owned tile bytes are paired with a
  world hash containing format 8, and the required `settings_v5` gate rejects
  older peers before Atlas streaming.
- [x] The source/test commit changes only four common Atlas/service classes and
  four focused tests. The sole `RingSurfaceLod` change is a server-capture
  colour constant; no renderer consumes the new light channel in this batch.
  The documentation commit changes only the technical datasheet. There is no
  renderer, shader, resource, placeholder, creation UI, command, server preview
  worker/publication, Create integration, worldgen, packaging, support metadata,
  or verification-metadata scope creep.

The implementer reports one Java 21 invocation compiling Fabric/common and
NeoForge and all 40 focused format-8 storage/tile, sampling, invalidation,
recapture, revision, client-cache, storage-path, and protocol cases passing on
each loader. Per assignment, this independent review did not rerun builds or
tests; it verifies the committed implementation, test surface, documentation
claim, and diff boundaries.
