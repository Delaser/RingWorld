# Optional visual wall-appearance GPU review (2026-09-02)

## Verdict

**PASS.** The independently reviewed batch stays within the bounded wall
appearance GPU slice. It deterministically encodes the saved wall palette,
pattern, decay, and world-seed variation; carries the encoded ARGB through a
narrow Minecraft 1.21.1 surface-buffer adapter; and adds only the shader inputs
and wall-face logic required to style the closed CPU mesh. Ordinary terrain
retains its existing Atlas/preview, handoff, fog, and lighting path.

Reviewed history:

- parent: `90f57ab24a4dd445286667b26d94ccc2808cfddb`;
- source and tests: `5b4956e75d6df4869b1b8be4906e5d53b1d6a900`;
- implementation checkpoint documentation:
  `b4cc14548514f06bc689a52231820a7409d60228`.

## Evidence

### Loader-neutral palette and metadata model

- `RingWallShaderStyle` derives colours from the actual block states selected
  by `RingGenerationBoundary.styledRimBlockForRoll`, converts them through the
  active world's map-colour lookup, and preserves cumulative 0-100 material
  ranges (`RingWallShaderStyle.java:16-63`).
- If more than five adjacent material runs exist, the smallest run is merged
  deterministically with its smaller neighbour using length-weighted RGB; a
  shorter palette is padded by repeating its final colour
  (`RingWallShaderStyle.java:25-30,65-87`).
- Four RGB colours and their cumulative thresholds occupy the 16-float matrix.
  The fifth colour occupies the vertex RGB, while the six pattern IDs and five
  deterministic seed bits fit in the vertex alpha byte. Decay remains a
  separate normalized value, so it is not truncated by metadata packing
  (`RingWallShaderStyle.java:31-45`).
- `Encoded` defensively copies the palette both on construction and access,
  preventing later callers from mutating the cached shader state
  (`RingWallShaderStyle.java:99-105`).

### Exact 1.21.1 GPU and renderer seam

- `RingSurfaceGpu` contains the version-owned buffer API. It allocates the
  exact `POSITION_TEX_COLOR` stride, emits the shared CPU mesh as triangles,
  applies the supplied ARGB to every vertex, and uploads one static
  `VertexBuffer` (`RingSurfaceGpu.java:11-29`). No loader API is introduced.
- Mesh rebuild derives one encoding from the authoritative client-session
  `RingWallStyle` and generator seed, sends its ARGB through that adapter, then
  publishes the matching immutable encoding beside the replacement buffer
  (`RingSurfaceTextureRenderer.java:693-710`). Session cleanup clears the
  encoding before closing the buffer and textures
  (`RingSurfaceTextureRenderer.java:725-734`).
- Draw uploads only the palette matrix and compact wall-style vector through
  explicit 1.21.1 uniforms (`RingSurfaceTextureRenderer.java:145-162`). The
  shader descriptor declares matching `matrix4x4` and four-float entries.

### Wall-only shader behavior and terrain preservation

- The vertex shader adds only intrinsic Z width, allowing the fragment shader
  to derive wall depth while retaining the existing position, distance,
  height, UV, and colour varyings (`ring_surface.vsh:11-37`).
- The fragment shader decodes the constant interpolated alpha byte into
  pattern and seed, generates deterministic fine/coarse/rib/gradient rolls,
  selects the four matrix colours or fifth vertex RGB, and applies bounded
  decay weathering (`ring_surface.fsh:29-60,68-81`).
- Styling is guarded by the pre-existing out-of-range-V wall marker. The
  ordinary `0..1` terrain path mixes the prior/current surface textures without
  multiplying by the packed wall vertex colour, then continues through the
  unchanged proxy, handoff, fog, and lightmap logic
  (`ring_surface.fsh:63-67,82-133`). This prevents the fifth palette colour or
  metadata alpha from tinting/fading real terrain.

### Focused tests

- `RingWallShaderStyleTest` proves deterministic repeat encoding, monotonic
  matrix thresholds, valid normalized channels, pattern/seed alpha packing,
  independent decay, palette/seed independence, and defensive array copying.
- `RingWallAppearanceSourceContractTest` binds the 1.21.1
  `POSITION_TEX_COLOR`/`setColor(vertexArgb)` adapter, renderer encoding and
  uniforms, matching shader/descriptor declarations, wall-marker guard,
  intrinsic-width plumbing, palette fallback, and the absence of ordinary
  terrain multiplication by packed vertex metadata.

## Scope and checks

The exact parent-to-documentation range changes nine files: one loader-neutral
model, one 1.21.1 GPU adapter, the existing surface renderer, the existing
ring-surface vertex/fragment/descriptor trio, two focused tests, and the
backport technical datasheet. It changes no Atlas-light/night-tuning, sky,
sun, star, fog-mixin, command, HUD/UI, protocol, server, world-generation,
persistence, Create, packaging, dependency, verification, or release path.

Independent read-only checks performed:

- resolved and compared all three exact hashes and inspected the complete
  `90f57ab..b4cc145` name/status and production/test/doc diff;
- traced all 100 wall-material rolls through run reduction, threshold packing,
  ARGB metadata, buffer emission, uniform upload, and fragment selection;
- verified the six pattern IDs plus five seed bits cannot overflow the alpha
  byte and that decay remains separately normalized;
- checked matrix/descriptor/vector arity, vertex/fragment varying agreement,
  wall-marker coverage for all inner/outer/top V values, session cleanup, and
  ordinary terrain non-regression line by line;
- searched added production lines for every forbidden adjacent subsystem; none
  was present;
- ran `git diff --check 90f57ab..b4cc145`; it reported no whitespace errors;
- confirmed the implementer worktree was clean at `b4cc145`.

Per the independent-review constraint, the reviewer ran no build or graphical
fixture. The implementation checkpoint records clean-parent and post-change
Java 21 compilation of both loader graphs and all six focused deterministic
model and adapter/shader-source cases passing on Fabric/common and NeoForge
after adding the test-only vanilla registry bootstrap.

## Residual risk

No real client loaded or compiled the GLSL resources in this checkpoint, and
no graphical wall matrix ran. Source and Java compilation therefore do not yet
prove 1.21.1 runtime uniform/matrix orientation, ARGB channel interpretation,
pattern appearance, projected wall continuity, or cross-loader visual parity.
Atlas block-light/night contribution and all sky/sun/fog work are deliberately
absent. The checkpoint documentation accurately defers those later slices and
makes no rendered-visual support claim.
