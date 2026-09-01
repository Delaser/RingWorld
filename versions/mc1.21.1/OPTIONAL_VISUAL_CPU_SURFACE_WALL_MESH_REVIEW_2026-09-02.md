# Optional visual CPU surface and wall-mesh review (2026-09-02)

## Verdict

**PASS.** The independently reviewed batch implements the bounded CPU/client
surface model requested for Minecraft 1.21.1. It establishes authoritative
Atlas-over-preview-over-neutral precedence, the documented incomplete-surface
fog policy, saved-style terrain clipping, and persistent closed rim-prism mesh
data without adding the deferred GPU, shader, lighting, sky, command, UI,
protocol, server, packaging, or graphical-fixture work.

Reviewed history:

- parent: `0c16fd6cf84cfce631d0747637d1b8840cd46bf3`;
- source and tests: `98c0c9e60f816010f5eefbf4e1ecd11e303a7e5f`;
- implementation checkpoint documentation:
  `90f57ab24a4dd445286667b26d94ccc2808cfddb`.

## Evidence

### Surface precedence and transition

- `RingSurfacePlaceholder.resolve` rejects non-positive targets, complete
  Atlases, and a preview with a different world hash. For each target pixel it
  first accepts a real Atlas cell, otherwise samples the supplied preview at
  the target resolution, otherwise emits opaque neutral `#6B706F` at
  `RingGeometry.SURFACE_Y` (`RingSurfacePlaceholder.java:17-53`). The retired
  procedural palette and real-cell dilation are removed.
- The existing renderer obtains the current immutable preview, expands a
  partial target to the greater of Atlas and preview resolution within the
  established render profile, and calls that resolver only for an incomplete
  Atlas (`RingSurfaceTextureRenderer.java:299-325`). The complete branch stays
  on the pre-existing exact Atlas sampling path (`RingSurfaceTextureRenderer.java:317-338`).
- Preview-backed surfaces cap generation fog at 20%; neutral fallback retains
  the existing 88% cap. Both multiply the same clamped quintic completion
  clear (`RingSurfaceGenerationFog.java:5-22`). The renderer applies the policy
  to its already-morphed visible completion, leaving the existing 750 ms
  texture-revision morph intact (`RingSurfaceTextureRenderer.java:119-127`).
- Preview arrival already advances `ClientRingState.terrainAtlasRevision`, so
  the renderer's existing revision gate invalidates and rebuilds the surface
  when a later preview stage arrives. A build completed for an older revision
  remains rejected by the existing snapshot match before upload.

### Saved-style clipping and closed wall prisms

- The renderer now supplies the authoritative client `RingWallStyle` rather
  than the legacy fixed rim thickness (`RingSurfaceTextureRenderer.java:686-696`).
  `RingSurfaceMesh` retains that immutable style and derives its two inner
  faces from the saved thickness (`RingSurfaceMesh.java:41-64,91-106`).
- When walls rise above the reference surface, the terrain lattice spans only
  the playable band plus a half-block overlap hidden beneath each inner wall
  face. Detailed sampling is clamped one full Atlas cell farther inside, so
  saved wall-top height/colour cannot smear into a completed terrain ramp
  (`RingSurfaceMesh.java:127-160`).
- Wall geometry no longer depends on the Atlas being incomplete. Each rim
  contributes an inner vertical face, outer vertical face, and top face for
  every circumference segment; the additional 36 vertices per segment remain
  present in both progressive and detailed/complete meshes
  (`RingSurfaceMesh.java:102-106,163-227`). The mesh also retains the complete
  saved style for the later version-owned palette/GPU adapter.

### Focused tests

- `RingSurfacePlaceholderTest` proves neutral fallback, exact real-cell
  precedence over preview, target-resolution preview sampling, reference
  heights, mismatched-world rejection, and exclusion of complete Atlases.
- `RingSurfaceGenerationFogTest` proves the 20% preview cap, midpoint of the
  same quintic clear, and zero fog at completion while retaining the legacy
  neutral-policy tests.
- `RingSurfaceMeshTest` proves style retention, persistent 36-vertex closed
  prisms at incomplete and complete stages, all six face markers, style-derived
  inner faces, half-block overlap, and one-cell-inset detailed sampling.

## Scope and checks

The exact parent-to-documentation range changes eight files: the existing
surface renderer integration point, three loader-neutral CPU model classes,
their three focused tests, and the backport technical datasheet. It adds no
file and changes no GPU adapter, shader/resource, mixin, Atlas-light, sky/fog
mixin, command, HUD/UI, protocol, server, Create integration, packaging,
dependency, verification, or release-metadata path.

Independent read-only checks performed:

- resolved and compared all three exact commit hashes and ancestry;
- inspected the complete `0c16fd6..90f57ab` name/status and production/test/doc
  diff;
- traced preview publication through the existing revision/snapshot gate and
  verified that complete Atlases cannot enter the placeholder resolver;
- checked target-to-Atlas and target-to-preview sampling, fog endpoints and
  midpoint, inner-face bounds, hidden overlap, detailed sample clamp, prism
  face counts, persistence, and saved-style propagation line by line;
- searched added production lines for GPU-buffer/shader, Atlas-light, sky/fog
  mixin, command, HUD/UI, payload/server, Create, and packaging work; none was
  present;
- ran `git diff --check 0c16fd6..90f57ab`; it reported no whitespace errors;
- confirmed the implementer worktree was clean at `90f57ab`.

Per the independent-review constraint, the reviewer ran no build or graphical
fixture. The implementer checkpoint records clean-parent and post-change Java
21 compilation of both loader client graphs, with the same 14 focused
placeholder, fog, morph, and mesh tests passing on Fabric/common and NeoForge.

## Residual risk

No real client or graphical transition fixture has run for this checkpoint.
The source review therefore does not prove frame-visible grey-to-preview-to-
Atlas morph quality, absence of inner-face depth cracks under the actual 1.21.1
projection, or the appearance of the new outer/top marker values through the
current shader. The checkpoint correctly leaves palette encoding, vertex
colour, GPU-buffer adaptation, shader work, and graphical qualification for
later bounded stages and makes no rendered-style parity claim.
