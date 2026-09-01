# Minecraft 1.21.1 optional-visual server batch review

Reviewed the read-only implementation worktree at `ed2e51dd998918241f718613dbbce4d3790def27` against the 26.x implementation through `e058c69`, the optional-visual changelog, and the technical datasheet. The final tracked implementation diff reviewed had Git object ID `d202f85601d32071679ce37f39777af1fdcbca77`; the present untracked `RingWorldConfigTest.java` was reviewed separately. No builds or runtime fixtures were run, as requested.

## Findings

### High — non-legacy format-4 walls remain eligible for destructive legacy-rim migration

`src/main/java/dev/ringworld/server/RingWorldServer.java`, `onChunkLoaded` (lines 82–89) queues every loaded boundary chunk. `migrateOneLegacyRimChunk` then calls `RingGenerationBoundary.migrateLegacyRim` without checking the saved wall style. In `src/main/java/dev/ringworld/world/RingGenerationBoundary.java`, `migrateLegacyRim` (lines 123–167) treats any stone-brick block at an outer rim plane above `wallTopExclusive` as the old full-height wall marker, replaces the complete five-block-deep wall below it with legacy cobble/mossy cobble, and deletes matching stone bricks above the top.

That content test pre-dates configurable walls. A new format-4 Ancient wall, any other custom wall, or player construction at the rim can now be silently changed on reload if one stone-brick block occupies the marker column above the configured wall top. For walls thicker than five blocks this also leaves a mixed legacy/custom cross-section. Gate the migration queue and execution with persisted migration eligibility. At minimum, only `RingWallStyle.LEGACY` settings should enter this path; the robust form should distinguish an upgraded format-1–3 world from a newly created format-4 world that deliberately selected the legacy field combination.

### Low — exact legacy block appearance has no direct regression assertion

`src/main/java/dev/ringworld/world/RingGenerationBoundary.java`, `legacyMaterialRoll` and the legacy branch of `texturedRimBlock` (lines 191–210), deliberately preserve the old coordinate hash and 30% mossy-cobble result. The existing storage tests prove formats 1–3 migrate to `RingWallStyle.LEGACY`, while the new `RingGenerationBoundaryTest` proves palette membership and thresholds, but no test compares representative legacy coordinates with the former implementation. Add a focused regression assertion (through a package-visible pure helper or a bounded chunk test) covering cobble and mossy results on both rims and coordinates adjacent to canonical X=0/C. This protects the batch's explicit exact-appearance guarantee from later refactoring into the new seeded pattern sampler.

## Pass checklist

- [x] Bootstrap parsing uses the documented Weathered/Atmosphere/Small defaults, stable preset names, stable numeric palette/pattern IDs, explicit-field precedence, and the five legacy combined sky mappings.
- [x] New-world creation captures the configured wall style and independent sky profile; saved formats 1–3 still decode and upgrade to `RingWallStyle.LEGACY` rather than the decayed new-world default.
- [x] Saved thickness reaches dimension validation/reporting, generator attachment, multi-chunk rim ownership, stronghold/worldgen interior bounds, portal lookup filtering, and portal-creation anchors.
- [x] Wall palettes map to available 1.21.1 vanilla blocks, include the Industrial sea-lantern accent in rim recognition, remove stale block entities, and use top-connected decay.
- [x] New wall sampling canonicalizes X through `RingWallPattern`; the legacy branch retains the former canonical-server-plane hash. Portal queries retain nearest-periodic-image behavior.
- [x] Generator state is attached only from the Overworld path, and portal changes retain explicit Overworld guards.
- [x] No protocol, payload, client, renderer, shader, resource, or loader-transport file is touched by this batch.
- [x] Remaining fixed-five uses were accounted for: compatibility overloads and the old-wall migration are server-scope legacy paths; `RingWorldCreationUiModel` and Atlas/mesh/render consumers belong to the explicitly deferred UI/rendering batches.

Disposition: address the migration eligibility finding before integrating this batch. The remaining reviewed server/config/generation changes are consistent with the handoff contract.

## Follow-up: migration correction review

Status: **blocked; no correction approved**. The implementer explored a style-only gate, then withdrew it and restored a clean worktree at `6b9aff9767b23e37213e5ee250ad8cb08dfe36e7` after review rejected that approach.

The proposed gate would have admitted migration whenever the saved style equalled `RingWallStyle.LEGACY`. That does preserve eligibility for upgraded format-1–3 settings, but it is not valid provenance: a newly created format-4 custom configuration can reproduce the exact same 5/Weathered/Clustered/0 style. Such a world would still be exposed to the destructive content-detected rewrite identified above.

The required correction therefore needs durable saved provenance set only while upgrading formats 1–3, and durable clearing/idempotence once eligible legacy-rim migration is complete so reopen cannot repeat it. Implementing that requires an explicit storage-contract decision; a style comparison alone must not land. The preliminary legacy-hash regression did cover both rim Z values and canonical seam-adjacent X coordinates, but it was withdrawn with the incomplete correction and remains required when the provenance-backed fix is implemented.

## Superseding follow-up: fail-closed automatic block migration

Status: **pass; no actionable findings**. Controller policy superseded the provenance design for this batch. Source/test commit `c4a46d1078dd8901b34645d3d1e4b2f703276072` disables automatic content-detected rim-block migration for every loaded format-4 setting, and documentation commit `c56c0cd62ee038aaccf58ed8e58a6b35a78275d6` records the limitation.

`RingWorldServer.onChunkLoaded` returns before adding any boundary chunk because `automaticLegacyRimMigrationEnabled` is fail-closed for all non-null settings, including exact `RingWallStyle.LEGACY`, a reconstructed equal custom style, and thick custom styles. Deferred execution independently rechecks the same policy and clears any pending map before the dormant `RingGenerationBoundary.migrateLegacyRim` call, so the rewrite helper has no reachable automatic caller. The unchanged `RingWorldSettings.upgradeToCurrentFormat` path and its format-1–3 storage tests still preserve the exact legacy style; only generated-block rewriting is deferred. Direct legacy-hash coverage now includes both rim Z values and X coordinates on both sides of the canonical seam.

The technical datasheet clearly states that existing generated legacy rim blocks remain unchanged until a separately designed explicit migration tool has trustworthy provenance. The implementer reports both loader compiles and all 17 focused tests passing per loader; this independent follow-up added no build or fixture run.
