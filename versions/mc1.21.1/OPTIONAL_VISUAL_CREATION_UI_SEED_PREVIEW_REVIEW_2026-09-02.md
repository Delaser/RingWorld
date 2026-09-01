# Optional visual creation UI and seed-preview review (2026-09-02)

## Verdict

**PASS.** The independently reviewed batch stays within the menu-only
Minecraft 1.21.1 creation flow and implements the requested wall, sky, sun,
bootstrap, and detached seed-preview behavior without creating a world or
expanding the in-world renderer, shader, or server-preview service.

Reviewed history:

- parent: `4295d0dfe795e6c98e7b34b1a80a7f82fa26c723`;
- source and tests: `dc4e025db1900c966133461feda30dba8b314949`;
- implementation checkpoint documentation:
  `0c16fd6cf84cfce631d0747637d1b8840cd46bf3`.

## Evidence

### Creation choices, validation, and persistence

- `RingWorldCreationScreen` retains the selected `RingWallStyle`, sky
  backdrop, and light source independently. The wall button opens the
  preset/custom editor, while the sky and sun buttons advance their separate
  models (`RingWorldCreationScreen.java:104-126`).
- `RingWallStyleScreen` exposes all ten presets and validates custom palette,
  pattern, thickness, and decay before accepting the draft
  (`RingWallStyleScreen.java:42-80,83-140`).
- The selected wall thickness is passed into
  `RingDimensionReport.forVanillaOverworld`, so playable interior and cost
  reporting use the chosen 1-32-block rim rather than the default
  (`RingWorldCreationUiModel.java:141-160`). Existing error/advisory rendering
  remains active, and seed preview is disabled with invalid geometry
  (`RingWorldCreationScreen.java:239-257,337-370`).
- Confirmation names preset/custom rim style, thickness, sky backdrop, sun
  style, monument state, and first-load locking. Acceptance uses the existing
  wall-style/sky-profile `saveBootstrapLayout` overload
  (`RingWorldCreationUiModel.java:192-210` and
  `RingWorldCreationScreen.java:266-291`). This changes only the bootstrap for
  the next new world; it does not create or open a world.

### Exact 1.21.1 boundary and isolated generation

- `CreateWorldScreenMixin` is the narrow pending-creation adapter. It uses the
  public 1.21.1 `getUiState`, `getSeed`, `setSeed`, `getSettings`, and
  `options().seed()` methods and adds no shadowed 26.x descriptor
  (`CreateWorldScreenMixin.java:62-80`).
- Before submission, `RingSeedPreviewScreen` resolves the seed and snapshots
  the selected noise generator's biome source, settings holder/key, frozen
  registry access, and build-height primitives on the client thread
  (`RingSeedPreviewScreen.java:117-154`). The pending
  `WorldCreationContext` generator itself is not sent to or configured by the
  worker.
- The worker constructs a new `NoiseBasedChunkGenerator`, a new `RandomState`
  through the 1.21.1 `asGetterLookup()` adapter, and a detached
  `LevelHeightAccessor`; it then calls only the existing chunk-free CURRENT
  preview sampler (`RingSeedPreviewScreen.java:156-174`). There are no world
  storage, integrated-server, chunk-loading, structure, or save calls in the
  changed production paths.

### Cancellation, seam, and cleanup

- Every edit advances `RingPreviewRequestGate` before interrupting the prior
  `Future`, so a late worker cannot publish into a newer request
  (`RingSeedPreviewScreen.java:94-100`). Removal advances the gate again,
  cancels the worker, and releases the registered dynamic texture
  (`RingSeedPreviewScreen.java:218-230`).
- Accepted pixels use `centeredSeamSourceColumn`, keeping the canonical
  X=C-1/X=0 seam at the preview centre. Rendering preserves the actual
  circumference-to-width aspect ratio instead of stretching the band
  (`RingSeedPreviewScreen.java:185-211,250-265`).
- Focused tests cover public-adapter source contracts, detached generator and
  `RandomState` construction, centered seam use, close invalidation, stale
  publication rejection, wall-thickness cost effects, and independent
  wall/sky/sun confirmation (`RingCreationPreviewSourceContractTest.java`,
  `RingPreviewRequestGateTest.java`, and
  `RingWorldCreationUiModelTest.java`).

## Scope and checks

The exact parent-to-documentation range changes nine files: the two creation
screens, the existing creation screen and mixin, the creation UI model, three
focused tests, and the backport technical datasheet. It changes no renderer,
shader, mixin configuration, payload, server service, world-generation,
packaging, dependency, or release-metadata file.

Independent read-only checks performed:

- resolved and compared all three exact commit hashes;
- inspected the complete `4295d0d..0c16fd6` name/status and source diff;
- searched changed production paths for world creation/storage, server-level,
  in-world renderer/shader, and staged-server-preview entry points; none were
  present;
- checked request ordering, detached inputs, seam mapping, texture ownership,
  validation/persistence wiring, and the focused test assertions line by line;
- ran `git diff --check 4295d0d..0c16fd6`; it reported no whitespace errors;
- confirmed the implementer worktree was clean at `0c16fd6`.

Per the review assignment, no build or graphical/runtime fixture was run by
the reviewer. The implementer checkpoint records passing Fabric/common and
NeoForge client-source compilation, 27 focused Fabric cases, and the corrected
NeoForge adapter/isolation cases.

## Residual risk

The expanded 17-capture menu-only graphical fixture has not yet run. Static
inspection therefore cannot prove small-window label fit, actual texture
upload/release behavior under the client renderer, or visual two-seed seam and
aspect fidelity. Those are qualification items, not source-review failures,
and the checkpoint documentation correctly avoids a graphical/runtime support
claim.
