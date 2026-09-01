# Optional visual Atlas block-light renderer review (2026-09-02)

## Verdict

**PASS.** The independently reviewed batch stays within the bounded Atlas
block-light renderer slice. It consumes the existing format-8 exposed-light
field as an independent surface-texture alpha channel, preserves opaque
terrain RGB through partial, complete, and mip paths, adds bounded process-
local Midpoint/Gamma tuning, and applies a warm night-only shader contribution
whose visibility fades with the live daylight lightmap. It adds no client
command or adjacent sky, fog, server, persistence, or wall-model work.

Reviewed history:

- parent: `b4cc14548514f06bc689a52231820a7409d60228`;
- source and tests: `d10ece21b4cde1f8491f09f84cc50ab84b5db1d4`;
- implementation checkpoint documentation:
  `8816cd0e6e2063557f6faf060ee3b7a7df0f7f39`.

## Evidence

### Format-8 light to independent texture alpha

- Complete surface builds bilinearly sample the existing Atlas colour, height,
  and 0-15 block-light value together. Partial builds retain the already-
  resolved real/preview/neutral RGB and sample light only when the current
  Atlas sample is present; missing authored data receives zero light
  (`RingSurfaceTextureRenderer.java:319-357`).
- `RingSurfaceLod.blockLightAlpha` rejects non-finite values, clamps the vanilla
  0-15 range, and maps it to the full 0-255 byte. The renderer writes that byte
  into alpha only after computing terrain relief RGB
  (`RingSurfaceLod.java:106-112` and
  `RingSurfaceTextureRenderer.java:359-376`).
- Coverage no longer depends on alpha: neighbour-height fallback uses finite
  height data, wall pixels explicitly carry zero authored terrain light, the
  fragment shader no longer discards alpha-zero surface samples, and final
  proxy alpha is written independently (`RingSurfaceTextureRenderer.java:701-703`
  and `ring_surface.fsh:79-82,136-153`). Thus an unlit pixel remains visible
  terrain rather than transparent geometry.

### Correct mip/channel handling

- `RingSurfaceLod.buildNextMipRgbLight` validates source dimensions, preserves
  periodic X and clamped Z sampling, and averages alpha, red, green, and blue
  as four independent channels (`RingSurfaceLod.java:114-155`). An unlit cell
  therefore contributes its ordinary RGB instead of erasing or darkening a
  lit neighbour through coverage-weighted filtering.
- The existing texture builder uses this function for every generated mip
  after populating the base block-light alpha
  (`RingSurfaceTextureRenderer.java:378-404`). Previous/current texture morphs
  consequently interpolate light and RGB consistently without changing the
  established texture lifecycle.

### Local profile, globals, and shader contribution

- `RingAtlasLightProfile` defaults Gamma to falloff `2.0` and peak `1.25`,
  retains the established Midpoint mode, rejects non-finite/out-of-range
  values, and enforces inclusive falloff `0.5..6.0` and peak `0.1..3.0`
  (`RingAtlasLightProfile.java:7-45`).
- `RingAtlasLightTuning` holds only one volatile process-local profile. It
  begins at the documented Gamma default, permits bounded Gamma replacement,
  and resets to Midpoint; it has no persistence or command registration
  (`RingAtlasLightTuning.java:5-24`).
- The existing per-program globals mixin writes mode/falloff/peak only when a
  shader declares `RingWorldAtlasLight`, and the ring-surface descriptor
  declares a matching four-float default
  (`GlobalSettingsMixin.java:102-117` and `ring_surface.json:22`).
- The fragment shader derives daylight exposure from the existing full-sky,
  zero-block-light lightmap sample. It clamps authored alpha, applies either
  Midpoint or bounded Gamma response, multiplies by night visibility, adds the
  warm `1.00/0.63/0.28` contribution, and reaches exactly zero as daylight
  rises (`ring_surface.fsh:132-150`). Final geometry alpha remains
  `proxyAlpha`, not sampled light (`ring_surface.fsh:151-153`).

### Focused tests

- `RingSurfaceLodTest` proves 0, midpoint, full, clamped, and non-finite light
  conversion plus independent RGB/light mip averaging.
- `RingAtlasLightProfileTest` proves documented defaults, Midpoint selection,
  shader mode, inclusive bounds, and rejection outside them.
- `RingAtlasLightTuningTest` proves process-local Gamma replacement and
  Midpoint reset while restoring the default after each case.
- `RingAtlasLightGpuSourceContractTest` binds complete/partial light packing,
  independent mip selection, uniform/global declarations, warm night math,
  zero-light walls, removal of alpha-as-discard/coverage, and independent final
  proxy alpha.

## Scope and checks

The exact parent-to-documentation range changes twelve files: two light
profile/tuning classes, the existing global/renderer/surface-LOD seams, the
ring-surface fragment/descriptor pair, four focused tests, and the backport
technical datasheet. It changes no command, HUD/UI, sky/sun/star/fog mixin,
protocol, server, world generation, persistence format, wall model/geometry,
Create integration, packaging, dependency, verification, or release path.

Independent read-only checks performed:

- resolved and compared all three exact hashes and inspected the complete
  `b4cc145..8816cd0` name/status and production/test/doc diff;
- traced complete and partial Atlas `SurfaceSample.blockLight` through byte
  normalization, base texture packing, mip generation, texture morph sampling,
  shader response, and independent proxy alpha;
- checked finite handling, inclusive tuning limits, process-only ownership,
  uniform arity/defaults, daylight endpoints, zero-light walls, and removal of
  every alpha-as-opacity use line by line;
- searched added production lines and changed paths for commands and every
  forbidden adjacent subsystem; none was present;
- ran `git diff --check b4cc145..8816cd0`; it reported no whitespace errors;
- confirmed the implementer worktree was clean at `8816cd0`.

Per the independent-review constraint, the reviewer ran no build or graphical
fixture. The implementation checkpoint records clean-parent and post-change
Java 21 compilation of both loader graphs and all 19 focused profile, tuning,
alpha/mip, renderer/global, and shader-source cases passing on Fabric/common
and NeoForge, with only unchanged pre-existing mixin-target warnings.

## Residual risk

No real client compiled or executed the GLSL and no night-light graphical
fixture ran. Source and Java checks therefore do not prove live lightmap
thresholds, partial-Atlas visual blending, texture-alpha sampling on the actual
driver, lamp footprint/intensity balance, weather interaction, or cross-loader
visual parity. The checkpoint correctly defers the client command and broader
lighting matrix and makes no runtime-rendering support claim.
