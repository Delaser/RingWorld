# Code review and focused cleanup — 9 October 2026

## Findings and changes

The review covered shared/version source selection, server Atlas request and
lease lifecycle, client cache/session teardown, surface renderer worker and
GPU ownership, seed preview cancellation, payload bounds and unused-source
candidates. This is a targeted development review, not a guarantee that every
code path or third-party mod combination is defect-free.

One confirmed version-parity defect was repaired: the 26.3 surface renderer
called a legacy `RingSurfaceMesh.build` overload. That overload substitutes
legacy wall materials, zero decay and seed zero, and derives height instead
of accepting the captured saved height. The renderer now passes the saved
wall height, style and generator seed just like 26.1/26.2. Defaults could hide
the height error; the material/decay loss was real. A regression compares the
whole renderer after normalising its five GPU package imports. It failed on
the previous source and passes on the repaired source.

The 434-line 26.3 seed-preview screen differed from the shared screen by one
`RandomState.create` API call. A small factory in the existing per-version
`RingMinecraftClientAccess` adapters now owns that difference. All versions use
the shared screen. Preview workers still receive immutable captured inputs,
construct their own generator and retain the original cancellation behavior.
Adapter contract coverage now includes all three source ABIs.

Three byte-identical 26.3 overrides were removed: `SpriteContentsAccessor`,
`RingWallMaterialColors`, and `ringworld_handoff.glsl`. Gradle's existing source
selection falls back to their shared definitions. The intentional GPU/mixin
ABI adapters remain separate. No source generation or dependency was added.

The cleanup removes about 490 net source lines. This mostly reduces maintenance
and drift: Gradle already packaged only one copy of each override, so it does
not imply a corresponding reduction in JAR size. Wall preview assets were
already compressed in #260. Runtime release-test fixtures are retained because
qualification uses them inside the exact candidate JARs. No saves, screenshots,
release evidence, build caches, or unrelated worktrees were deleted.

No additional demonstrated correctness defect was found in the lifecycle paths
reviewed. Larger future refactors of the build scripts or embedded fixtures
would need separate scope and qualification; they are not part of this cleanup.

## Validation

All three supported source ABIs must build and test on both Fabric and NeoForge.
Seed preview and wall captures must also run in muted hidden native clients,
preserving the user's foreground application. Evidence is retained under
`logs/code-review-debloat/`; final results are recorded here after those runs.
These checks are source-development evidence, not fresh frozen release
qualification or owner visual sign-off.
