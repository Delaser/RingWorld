# Optional visual sky-profile rendering review (2026-09-02)

## Verdict

**PASS.** The independently reviewed batch stays within the bounded Minecraft
1.21.1 `RingSkyProfile` rendering slice. It adapts the exact monolithic sky
descriptor, implements the three saved backdrops and star policies, keeps the
star field inertially fixed across ring longitude, suppresses vanilla celestial
artifacts, and draws the ring proxy before the selected centered sun. Fog
matching, commands, and every adjacent subsystem remain outside the batch.

Reviewed history:

- parent: `8816cd0e6e2063557f6faf060ee3b7a7df0f7f39`;
- source and tests: `8568f49b56f996e4eea02788db6d8903c39c3556`;
- implementation checkpoint documentation:
  `a85b0c880a52a94ec242b1c37df1181c6401663e`.

## Evidence

### Exact 1.21.1 hook and lifecycle guards

- The cancellable injection names the full reviewed Minecraft 1.21.1
  `renderSky(Matrix4f, Matrix4f, float, Camera, boolean, Runnable)` descriptor
  rather than a copied later-version signature
  (`SkyRenderingMixin.java:57-64`). The bytecode contract test reads the actual
  mapped `LevelRenderer.class` on each loader graph and proves that exact
  method/descriptor exists.
- The branch requires non-null `ClientRingState.geometry()`, a live client
  level, and normal sky effects (`SkyRenderingMixin.java:65-70`). The geometry
  accessor itself returns non-null only in `Level.OVERWORLD`, so Nether, End,
  other dimensions, disconnect, and cleared-session states fall through to
  vanilla. The profile is read from client session state each frame; the mixin
  retains no separate profile cache.
- Camera/world-fog and lava, powder-snow, blindness, and darkness blocks are
  respected before custom draws (`SkyRenderingMixin.java:72-73,165-178,224-230`).
  Invoking the supplied vanilla fog setup/no-fog callbacks is part of reproducing
  the reviewed sky draw state; the batch adds no fog policy or fog mixin.

### Backdrops, lower hemisphere, and stars

- `RingSkyCycle.fixedBackdropRgb` preserves native Atmosphere RGB, selects
  Night `#050810`, or selects Void `#010103`. `starBrightness` preserves native
  Atmosphere brightness, gives Night a pre-weather floor of `0.88`, and gives
  Void zero (`RingSkyCycle.java:32-53`).
- The custom branch draws both `skyBuffer` and `darkBuffer` with the same
  selected colour before stars (`SkyRenderingMixin.java:75-93`). Cancelling the
  remaining vanilla method therefore suppresses the separate black lower disc,
  sunrise fan, camera-relative sun, and moon while retaining matching upper
  and lower hemispheres (`SkyRenderingMixin.java:118-122`).
- Star brightness is multiplied by existing rain visibility. The star pose
  counter-rotates by `-geometry.angleAt(cameraX)`, including periodic
  presentation images, and restores the caller's fog setup after the no-fog
  star draw (`RingSkyCycle.java:25-30` and
  `SkyRenderingMixin.java:94-112`).
- The custom sky branch resets shader colour, blend function/state, and depth
  mask before cancellation (`SkyRenderingMixin.java:114-117`). The later sun
  draw performs the same colour/blend/depth restoration after its quad
  (`SkyRenderingMixin.java:198-220`).

### Fixed selected sun and ordering

- The post-`compileSections(Camera)` hook keeps the established renderer
  placement before terrain. It first calls `RingSurfaceTextureRenderer.render`
  and only then evaluates/draws the selected sun
  (`SkyRenderingMixin.java:156-185`). This preserves the required ring-proxy-
  before-sun order and avoids moving either draw to the tail of `renderSky`.
- `LightSource.NONE` returns without submitting a quad. Small and Large use
  saved half-widths `3` and `15`; the large variant scales final alpha by
  `0.72` (`RingSkyProfile.java:50-66`, `RingSkyCycle.java:55-64`, and
  `SkyRenderingMixin.java:184-207`).
- The sun remains at fixed angle zero and points toward the physical ring
  centre. Only smooth day-time tint/intensity and weather alpha vary; switching
  from game time to `level.getDayTime()` preserves the intended gameplay clock
  (`SkyRenderingMixin.java:186-205`).

### Focused tests

- `RingSkyCycleTest` proves fixed angle/compact sun, quarter/half-lap and
  periodic star counter-rotation, all backdrop RGB policies, Atmosphere/Night/
  Void star rules, Small/Large/None alpha selection, and existing smooth
  day-cycle tint/intensity.
- `RingSkyRenderingSourceContractTest` proves the exact mapped bytecode
  descriptor, profile/session source, backdrop/star/counter-rotation/fixed-sun
  calls, selected sizes and None suppression, absence of moon use, matching
  upper/lower order, and ring-proxy-before-sun source order.

## Scope and checks

The exact parent-to-documentation range changes five files: the existing sky
mixin, loader-neutral sky-cycle model, two focused tests, and the backport
technical datasheet. It changes no fog mixin/policy, command, HUD/UI, protocol,
server, persistence, world generation, Atlas-light, wall/surface model,
Create integration, packaging, dependency, verification, or release path.

Independent read-only checks performed:

- resolved and compared all three exact hashes and inspected the complete
  `8816cd0..a85b0c8` name/status and production/test/doc diff;
- checked the exact descriptor, cancellation boundary, implicit Overworld
  geometry guard, normal-sky/session fallthrough, camera blockers, backdrop and
  star policies, longitude sign/periodicity, celestial suppression, fixed-sun
  direction/size/alpha/time source, and proxy-before-sun ordering line by line;
- traced render-system depth, blend, shader-colour, buffer unbind, and fog-
  callback restoration on the normal custom draw paths;
- searched changed paths and added behavior for fog policy/mixins, commands,
  and every forbidden adjacent subsystem; none was present;
- ran `git diff --check 8816cd0..a85b0c8`; it reported no whitespace errors;
- confirmed the implementer worktree was clean at `a85b0c8`.

Per the independent-review constraint, the reviewer ran no build or graphical
fixture. The implementation checkpoint records clean-parent and post-change
Java 21 compilation of both loader graphs and all 19 focused sky-cycle,
profile, client-teardown, source, and mapped-bytecode-descriptor cases passing
on Fabric/common and NeoForge, with only unchanged pre-existing mixin warnings.

## Residual risk

No real client applied the mixin or rendered the sky matrix. Source, Java, and
bytecode-descriptor checks therefore do not prove runtime injection success,
driver render-state behavior, exact star orientation, lower-horizon coverage,
sun/ring depth composition, weather appearance, or cross-loader visual parity.
Fog matching and live sky/sun commands are deliberately absent. The checkpoint
accurately defers those later slices and makes no graphical support claim.
