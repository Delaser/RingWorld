# Revisioned terrain-atlas updates

Issue #68 extends the completed terrain atlas from a one-time snapshot into a
bounded, revisioned visual cache. Real chunks remain authoritative; this only
keeps the distant ring approximation current after exposed terrain changes.

## Ownership and invalidation

`LevelMixin` observes successful server `Level.setBlock` calls. It does no
sampling. The loader-neutral `RingAtlasSurfaceInvalidation` maps the changed
position to one canonical eight-block atlas cell and rejects finite-Z exterior
positions or edits too far below the stored exposed top.

`RingAtlasPregenerationService` remains the only writer. It drains at most 64
recapture cells per server tick, samples only loaded chunks, and requeues an
unloaded cell. Up to 4,096 exact pending cells are deduplicated; excess bulk
work collapses to 16-by-16 atlas tiles rather than growing without bound.
Every coalesced batch that changes stored height or colour advances one
monotonic `long` revision and publishes only its changed tiles.

Block placement/breaking, fluids, explosions, commands, and bulk edits all
reach the shared vanilla block-mutation boundary. The queue therefore avoids
separate competing listeners for each cause.

## Persistence and network ordering

Disk format 6 stores the revision with the existing height, colour, and
presence data. The atlas world hash includes the format, so format-5 server and
client files fail closed and regenerate.

The revisioned payload suite is:

- `terrain_atlas_metadata_v2`: includes the authoritative revision;
- `terrain_atlas_request_v2`: reports client revision and completeness;
- `terrain_atlas_tile_v2`: carries changed tile bytes;
- `terrain_atlas_revision_v1`: commits every earlier tile in that ordered
  connection as one durable revision.

A complete client remains subscribed after its initial transfer. The server
adds changed tiles to every subscription and sends the commit only when that
player's tile queue and the service publication queue are empty. A client
never advances its durable revision per tile, so disconnecting halfway through
a batch cannot make a partial cache appear current. Reconnect reuse requires
the same world hash, dimensions, completeness, and exact revision; otherwise
the client starts a fresh atlas and receives a full authoritative snapshot.

## Validation

- 215 unit/parameterized cases pass, including canonical invalidation,
  bounded overflow, revision persistence/rollback, and channel identities.
- `./gradlew runAtlasUiClient` passes on Minecraft 26.1.2. It generates a
  complete 13,312-cell safe-small atlas, receives the completion revision,
  places a gold block at a sampled surface position, observes height 201 and
  the next committed revision, removes it, observes the restored height and a
  second revision, then exits cleanly.
- The same run exercises the real Fabric payload path, integrated server,
  renderer rebuild coalescing, format-6 save, and final unload checkpoint.

The later #70 production gate moved expensive texture/mip/native-image
preparation to an immutable asynchronous atlas snapshot. Incomplete coverage
still publishes at most once per second; already-complete edits publish after
three quiet seconds or a ten-second maximum delay. The ordered revision commit
forces the durable cache save but no longer requests a second identical GPU
build. See `ATLAS_RELEASE_GATE_2026-08-01.md` for two-client revision sequences,
resource budgets, and the final frame matrix.

The eight-block atlas is intentionally an LOD sample. An edit between sample
points queues and recaptures the correct cell, but may not change that cell's
representative height or colour.
