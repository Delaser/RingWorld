# Sun rendering rollback snapshot — 2026-07-26

This is a frozen description of the sun and shadow-panel visual that was active
on 2026-07-26. It exists so later experiments can be compared with, or reverted
to, this implementation. Do not silently rewrite this file to describe a new
design; add a new dated snapshot and note the replacement instead.

**Status:** superseded later on 2026-07-26 by the small fixed sun with a
continuous global dimming/colour-tone cycle. The panel mesh, pipeline, and
timing code described below are no longer active.

## What the player sees

- The Overworld sun is one fixed object toward the physical centre of the ring.
- At the width midline it is at local zenith.
- Moving across canonical Z tilts it toward the actual centre rather than
  keeping it pinned to the camera.
- Walking around canonical X, including through the seam, does not make it
  rise, set, or drift.
- The normal moon is hidden and the stars do not rotate.
- Twenty dark panels orbit the star. One crosses the visible solar disc per
  24,000-tick Minecraft day, producing dusk, night, and dawn.

This is still a global visual/gameplay day cycle. Different canonical X
positions do not yet receive different local eclipse phases.

## Active source ownership

| Source | Responsibility |
| --- | --- |
| [`RingGeometry.java`](../src/main/java/dev/ringworld/world/RingGeometry.java) | Direction from the canonical camera position to the physical ring centre |
| [`RingSkyCycle.java`](../src/main/java/dev/ringworld/world/RingSkyCycle.java) | Fixed-sun constants, panel dimensions, orbital speed, and eclipse phase |
| [`SkyRenderingMixin.java`](../src/client/java/dev/ringworld/client/mixin/SkyRenderingMixin.java) | Vanilla suppression, ring-centred frame, render order, and panel draw |
| [`RingSkyCycleTest.java`](../src/test/java/dev/ringworld/world/RingSkyCycleTest.java) | Timing, scale, speed, wrapping, and fixed-angle regression tests |
| [`RingWorldClient.java`](../src/platform/fabricClient/java/dev/ringworld/client/RingWorldClient.java) | Automated noon, dusk, and midnight screenshot sequence |

## Per-frame positioning

`SkyRenderingMixin.ringworld$updateFixedSky` runs after vanilla sky state
construction, guarded by `ClientRingState.geometry() != null`.

It forces:

```text
sunAngle = 0 radians
moonAngle = 0 radians
starAngle = 0 radians
sunriseAndSunsetColor = 0
```

The sun direction is not derived from player yaw or a camera-relative sky
rotation. The active calculation is:

```text
starDirection = geometry.directionToRingCenter(cameraCanonicalPosition)
starTiltRadians = atan2(starDirection.z, starDirection.y)
```

`RingGeometry.directionToRingCenter` converts the one physical ring centre into
the camera's tangent frame and normalizes the result. On the width midline the
direction is local `+Y`. Canonical Z displacement introduces the corresponding
local Z component.

The shared sun/panel model frame applies:

```text
rotate +X by starTiltRadians
rotate +Y by -90 degrees
```

This maps the vanilla sun's model `+Y` direction toward the physical star while
keeping model `+X` across the ring width. Both sun and panels receive the same
frame, which is why their occlusion remains aligned.

## The sun draw itself

RingWorld does not currently use a custom sun mesh, texture, or shader.

`SkyRenderingMixin` invokes Minecraft 1.21.11's private
`SkyRenderer.renderSun` through `ringworld$invokeRenderSun`. Vanilla supplies:

```text
translation = (0, 100, 0)
quad scale = (30, 1, 30)
pipeline = POSITION_TEX_COLOR_CELESTIAL
texture = vanilla celestial-atlas sun sprite
alpha = celestial render alpha, including weather attenuation
```

The `SUN_RENDER_DISTANCE = 100` and `SUN_HALF_WIDTH = 30` constants in
`RingSkyCycle` intentionally mirror that vanilla transform. The visible bright
disc occupies an estimated `0.2625` of the padded sprite width; that estimate
is used to size the panels, not to crop or rescale the rendered sun.

Derived current scale:

```text
visible sun angular half-width = about 4.50275 degrees
visible sun angular diameter = about 9.00550 degrees
```

## Suppression and render order

The first vanilla sun draw is cancelled by the `renderSun` injection whenever a
RingWorld geometry is active. The cancellation is temporarily bypassed only
while `ringworld$renderingCenteredSun` is true. `renderMoon` is always cancelled
for an active RingWorld.

At the tail of `renderCelestialBodies`, the active order is:

1. draw the complete-ring texture;
2. push the ring-centred star frame;
3. set `ringworld$renderingCenteredSun = true`;
4. invoke vanilla `renderSun`;
5. clear the bypass flag in a `finally` block and pop the frame;
6. draw the nearer shadow-panel array in that same star frame;
7. later world rendering draws authoritative terrain and entities.

This ordering means the distant ring covers stars, the sun appears in front of
the ring, and the panels appear in front of the sun.

## Shadow-panel calibration

The current array is constructed once in `SkyRenderingMixin`:

```text
panel count = 20
segments per curved panel = 8
panel spacing = 18 degrees
panel orbit radius around the star = 75 render units
sun distance from camera = 100 render units
nearest panel distance = 25 render units
panel full physical width = 3.9375 render units
panel angular full length = about 5.98851 degrees
array rotation period = 480,000 ticks / 20 Minecraft days
array speed = 0.00075 degrees per tick
```

The panel mesh orbits a point translated to `(0, 100, 0)`, the same point used
by the sun. The nearest panel face therefore passes between the camera and the
sun instead of orbiting the player. Its physical width is calculated to match
the visible solar disc at the nearest approach.

`RingSkyCycle.shadowPanel(worldTime + tickProgress)` sets the current rotation:

```text
time 12000 = leading edge first contacts the sun
time 18000 = panel centred on the sun / full night
time 24000 = trailing edge clears the sun
```

The twenty evenly spaced panels make the next panel repeat this eclipse once
per ordinary Minecraft day, even though the entire array needs twenty days for
one revolution.

## Reverting an experiment to this snapshot

Restore these pieces together:

1. the four fixed sky-state assignments in
   `ringworld$updateFixedSky`;
2. `directionToRingCenter` plus the `atan2(z, y)` tilt;
3. the two rotations in `ringworld$applyStarFrame`;
4. cancellation of the ordinary sun and moon;
5. the ring → vanilla sun → panel render order;
6. every sun and panel constant in `RingSkyCycle`;
7. the curved 20-panel mesh and its translate-then-rotate transform;
8. the assertions in `RingSkyCycleTest`.

Then run:

```sh
./gradlew test build
```

For visual comparison, use the automated captures:

```text
run/screenshots/ringworld-fixed-sun-day.png
run/screenshots/ringworld-shadow-dusk.png
run/screenshots/ringworld-shadow-night.png
```

Also inspect from the width midline, both width edges, four canonical X
quadrants, and directly across the circumference seam. A clean launch alone
does not prove the sun is still world-centred or correctly occluded.
