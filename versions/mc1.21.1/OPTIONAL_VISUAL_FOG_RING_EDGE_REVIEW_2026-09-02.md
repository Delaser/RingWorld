# Optional visual fog and ring-edge review — 2026-09-02

## Verdict

**PASS with no findings** for the bounded Minecraft 1.21.1 sky-profile fog and
ring-edge matching batch.

Reviewed commits:

- parent: `a85b0c880a52a94ec242b1c37df1181c6401663e`;
- source and tests: `99c0bdcdcf53e96ba841c245b8c0fc496014d723`;
- implementation documentation:
  `dfd57c7457d31301607f21c764d91e81f1d172d0`.

The two reviewed commits form the expected linear pair: the source commit is
based directly on the stated parent, and the documentation commit is based
directly on the source commit. The exact range changes nine expected files and
passes `git diff --check`.

## Hook, guards, and vanilla exclusions

`FogRendererMixin` targets the mapped static 1.21.1 method
`FogRenderer.setupColor(Camera, float, ClientLevel, int, float)` with its full
descriptor and injects at `TAIL`. The matching source-contract test also reads
the mapped `FogRenderer.class` with ASM and requires that exact method and
descriptor. This closes the source-only ambiguity around the version-owned
Mixin target.

The hook requires both current RingWorld client geometry and the active
Overworld. `ClientRingState.geometry()` is itself session- and
Overworld-scoped, while the explicit `Level.OVERWORLD` comparison provides a
second dimension guard. The hook returns without changes for every camera fog
type other than `FogType.NONE`, dimension-special fog, boss-overlay world fog,
blindness, and darkness. It changes only the three vanilla fog-colour fields
and the matching clear colour after vanilla has calculated them. It neither
targets `setupFog` nor sets fog start, end, shape, or mode.

## Colour policy

The wall top is derived through the existing authoritative
`RingGenerationBoundary.wallTopExclusive` path using the level's build limits
and the saved client-session wall height. The Atmosphere profile uses a clamped
cubic smoothstep from zero at 16 blocks below wall top to one at wall top, then
interpolates vanilla fog RGB toward the live level sky RGB. Non-finite inputs
fail closed to no blend.

Night and Void bypass that interpolation and select the required exact colours
`#050810` and `#010103`. Focused unit cases cover the lower endpoint,
midpoint, upper endpoint, clamping, Atmosphere interpolation, and both exact
dark-profile colours.

## Ring-proxy edge matching

The existing surface draw transports the installed backdrop ID through the
previously unused X component of `ColorModulator`; the established reveal,
texture-morph, and generation-fog values remain in Y, Z, and W. The renderer
copies and restores all four prior shader-colour components around the draw,
so the new value does not leak into later rendering.

The fragment shader retains the live `FogColor` edge for Atmosphere and maps
backdrop IDs 1 and 2 to the same exact Night and Void RGB values. Only the
proxy boundary colour feeding the existing reveal blend changes; terrain,
light, wall, texture-morph, generation-fog, and proxy-alpha calculations are
unchanged. No new shader uniform or global contract was introduced.

## Scope and evidence

The exact range contains only the new fog Mixin and registration, shared fog
colour helpers, the minimum ring-surface input and fragment-shader adjustment,
their focused tests, one retained Atlas-light source-contract expectation, and
the technical-datasheet checkpoint. It does not change sky drawing, commands,
HUD/UI, protocol, server, persistence, world generation, Atlas-light
behaviour, wall geometry or style, surface construction, Create integration,
packaging, dependencies, or release metadata.

The implementer reports successful Java 21 compilation of both loader graphs
and a focused rerun of all 23 relevant cases on Fabric/common and NeoForge,
including the mapped-bytecode descriptor check. This independent review did
not repeat builds or launch Minecraft.

## Remaining qualification

The bounded PASS does not prove runtime Mixin application, live Atmosphere
transitions at the rim, Night/Void shader-edge matching on a GPU, or
interaction with third-party fog and rendering modifications. Those remain
for the later focused graphical qualification and broad compatibility work;
they are not blockers for this source-and-test checkpoint.
