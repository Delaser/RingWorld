# Create standalone kinetic-visual D2 spike (2026-08-29)

Immutable source commit
`66c8c81c8be3fe54ea16d4d2db0315bb7b931080` addresses a distinct exact-tuple
defect in standalone Create kinetic block-entity visuals. It applies only to
Minecraft 1.21.1, NeoForge 21.1.239, Create 6.0.10, and Create's nested
Flywheel 1.0.6. This is implementation and qualification evidence, not
published support or release metadata. Fabric Create remains unqualified.

## Reproduced defect

The unmodified phase-3B adapter curved `ContraptionVisual`, but ordinary
Flywheel block visuals retained their flat root. The frozen high-chart
indirect encased-large-cog reproducer rendered the moving dark cog footprint
at the flat projection while its stationary casing rendered at the curved
physical-ring projection. The matched backend-OFF frame used RingWorld's
vanilla block-entity path and agreed with the curved casing.

This classified the defect as a created, live, drawn-but-flat child visual.
It was not an entity/tracking loss, visual-creation failure, distance rejection,
or indirect-only disappearance. In the frozen center-near frame the flat ROI
contained 38 dark pixels before the fix. The corrected indirect frame and OFF
reference both contain zero there; the corrected curved ROI contains 78 dark
pixels and OFF contains 63.

## Narrow exact-ABI correction

Two new physical-client mixins raise the exact tuple's boundary from four to
six client mixins while leaving the four server mixins unchanged:

- `StorageKineticEmbeddingMixin` wraps exact Flywheel `Storage.add`,
  `Storage.lambda$recreateAll$4`, `Storage.remove`, and `Storage.invalidate`.
  Only objects that are `KineticBlockEntity` instances in the attached client
  Overworld receive a child `VisualEmbedding`; non-KBE visuals and entity
  storage keep their native contexts.
- `VisualizationManagerKineticEmbeddingMixin` updates those children in exact
  `VisualizationManagerImpl.render(RenderContext)` immediately before the sole
  `Engine.render(RenderContext)` invocation. The audited call occurs after
  Flywheel's `syncUntil(frameFlag)` planning boundary.

The no-hard-link plugin verifies those exact fields, methods, descriptors, and
the single engine-render invocation through Mixin's bytecode provider. It also
preflights `KineticBlockEntity`, `BlockEntityStorage`, and `VisualEmbedding`.
There is no `@Pseudo`, optional injector, dead CPU culling target, shader
replacement, or global/static position registry.

The render injection reads `ClientRingState.geometry()` itself. The disposable
late-geometry phase can suppress only the value passed onward through a tiny
`RingCreate610ClientDiagnostics` hook, and only when the explicit kinetic
fixture property is set. That hook has no Create or Flywheel types, performs no
class discovery, and is inert in ordinary and Create-absent clients; production
ownership and transform math do not refer to or load the fixture class.

Each eligible visual is constructed through its own child context before any
subclass allocates instances. Geometry is deliberately not an allocation
precondition: a child begins at identity and the same child/visual transitions
to curved placement when `ClientRingState.geometry()` becomes live. The frame
transform uses the block-position origin and shared `RingObjectTransform`:

`E = T(curved anchor relative to render origin) * Rz(tangent) * T(-flat anchor relative to render origin)`

`N = Rz(tangent)`

Thus `E * T(flat anchor) * native local` equals
`T(curved anchor) * Rz(tangent) * native local`. Invalid or non-finite live
inputs reset both matrices to identity for that frame; a prior curved matrix is
never retained. The normal pre-handshake identity interval is silent.

Native visual deletion runs while its child still exists. If child allocation
succeeds but the initial identity transform fails, the provisional child is
deleted before the primary failure is rethrown. Cleanup failures are suppressed
behind native construction/setup/deletion failures rather than replacing them.
Successful recreation installs the new identity as the sole owner before
attempting old-child deletion, stale entries are removed before deletion, and
bulk invalidation clears the map before attempting every child deletion. Bulk
failures are aggregated with suppressed followers. Ownership is therefore
structurally empty even when a delete throws. Counters distinguish successful
deletions from `failedDeletes`; a failed deletion can never produce a balanced
diagnostic. Ownership remains per Flywheel storage and object identity, not by
BlockPos, so unload, render-origin recreation, level teardown, and backend
reload cannot retain a stale positional owner.

## Bounded D2 evidence

Four disposable runs retain 48 fixed-camera captures: high indirect, high
explicit instancing, low reverse-chart indirect, and high backend OFF. Each ON
capture records the exact visual identity, `EmbeddedEnvironment`, matrix index
1, finite curved pose, canonical server position, nearest-image client
presentation, real RPM/phase, projected curved/flat/reference bounds, and
three materially separated kinetic phases. Center/edge and near/far views all
keep the target within vanilla tracking range. OFF records no Flywheel visual
or child owner and remains the unchanged vanilla reference.

The ON lifecycle sequence proves native render-origin recreation, remove,
re-add, and shutdown cleanup. Each ON run finishes with eight children created,
eight successfully deleted, zero failed deletions, and zero retained owners.
OFF remains zero throughout. Focused tests also prove create-before-geometry
identity to curved transition with the same matrix objects, malformed-live
reset, both charts, camera unequal to render origin, a nontrivial native local
point/rotation, and origin recreation. A Flywheel-free pure ownership table
test suite covers initial-transform cleanup, primary-plus-cleanup suppression,
multi-failure bulk release with an already-empty map, stale removal before a
throwing delete, and unambiguous replacement ownership after old-child cleanup
failure.

The machine-readable manifest is ignored runtime evidence at
`neoforge/run-create-compat-kinetic-visual-d2-high/d2-matrix-manifest.json`
(SHA-256 `30b6a2570331d15251fbdfadc6ec1910b48c82a4574c8f1abd0adc3207772752`).
That manifest and its retained run paths are evidence for immutable source
commit `66c8c81c8be3fe54ea16d4d2db0315bb7b931080`; changing source requires a
fresh run and new manifest hash rather than relabelling this evidence.
Its matched screenshots are bound by these hashes:

- frozen pre-fix indirect:
  `3f6d19d0c4998b045665dc9a52ebb97ec15e5a2c4d24f86fd0ce436eea68699f`;
- corrected high indirect:
  `4bc1ce3111ecbbc816ef018d0f14c09bb143bbe08d5ec04df6b440da88ed1a3a`;
- matched high OFF:
  `64423cd80ab0a78ef801a70e3a948c64da2705347dbbf4c6400d65a0dd848f40`.

The four D2 physical-client runs apply all six client targets; the three server
targets needed by their bounded world path also apply. The exact dedicated
server independently applies all four server targets, and the Create-absent
dedicated server remains 0+0. Strict plugin tests retain the qualified
4-server + 6-client expectation. The C.1 indirect glued-bearing control passes
unchanged, showing that the independent `ContraptionVisual` embedding is
neither replaced nor doubled.

Fixture-calibration failures and shutdown stalls are explicitly not
qualification evidence.
The first late-geometry attempts waited for the target before moving the
player into the target's streamed range, so no client block entity or visual
existed; the final phase establishes the fixed near camera before testing the
identity child. The first instancing re-add sampled the newly created child in
its legitimate one-frame identity state; the final assertion waits boundedly
for the pre-render update and then requires the fresh child to be curved. One
full exact-client replay later spent several minutes in vanilla
`ChunkMap.processUnloads` during disposable integrated-server shutdown and was
stopped as non-evidence; a freshly prepared D2 retry then completed the same
durable save/reopen fixture in 1 minute 43 seconds. During D2.1, two further
full-fixture attempts reached that same audited vanilla
`ChunkMap.processUnloads` shutdown loop before reopen and were likewise
discarded. The bounded D2 lifecycle runs, exact/absent boots, and C.1 control
completed. None of these attempts motivated a production topology, tracking,
or shutdown change.

## Limits and forward guidance

D2 intentionally stops at the smallest real encased-cog spike. It does not yet
qualify the broader stationary kinetic network or the queued piston, gantry,
and pulley matrix. No shared topology, tracking, persistence, protocol,
renderer pipeline, public API, dependency, or support-metadata change is part
of this correction. Backend OFF is untouched because no Flywheel manager or
child embedding owns that path.

The defect class is relevant guidance for future 26.1/26.2 compatibility work:
an exact Create/Flywheel adapter must curve standalone KBE child environments,
not only contraption embeddings. The backport-specific ABI mixins must not be
copied to mainline until an exact mainline Create/Flywheel tuple is resolved
and the construction, render, and deletion descriptors are re-audited.
