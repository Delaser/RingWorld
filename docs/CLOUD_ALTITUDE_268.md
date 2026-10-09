# Dynamic cloud height and curved faces (#268)

The visible RingWorld cloud deck already followed the saved wall height, but
vanilla CPU face selection still used the environment's cloud-height attribute
(default Y192.33). On the saved 160-block-wall study, the actual deck is
Y104–108. Looking from Y130 therefore used the wrong cloud faces.

`RingCloudBounds.baseHeight` now supplies both CPU cloud height and GPU Globals:

`cloud base = dimension minimum Y + saved wall height + 8`

For an Overworld bottom of -64, wall heights 64, 160 and 256 yield cloud bases
8, 104 and 200. The renderer reads the saved client state each frame; it does
not depend on a preset, literal study altitude or local generation defaults.
Vanilla four-block Fancy cloud thickness is retained.

The shared `CloudRendererMixin` adjusts the first height argument of `render`
on 26.1/26.2 and `prepare` on 26.3. It is active only when
`ClientRingState.geometry()` identifies the RingWorld Overworld. Native worlds,
Nether and End keep vanilla height and face policy. Switching policies marks
cached cloud geometry for rebuild.

Aligning heights alone is insufficient for a curved ring: a distant cell can
bend above the eye while the nearby deck is below it. Fancy cells therefore add
only the top or bottom cap vanilla omitted. Existing directional colours,
side-face selection and inside-face flags remain. This fits vanilla's existing
inside-cloud worst-case buffer capacity. Fast clouds already use one
non-culled face and do not receive duplicate coplanar faces. Shader fog, weather,
finite inner-wall clipping and 26.3 OIT remain unchanged.

## Validation

The opt-in native fixture uses a disposable `Atlas Wall Review` copy:

```
-Dringworld.captureAtlasWalls=true -Dringworld.captureCloudAltitude=true
```

It selects Medium detail and captures Fancy/Fast at eye heights cloud-base -16,
+2 and +16, then stops normally. The local matrix tests saved wall height 160
on Fabric and 256 on NeoForge across all three supported source ABIs. The
runner changes only the copied save's settings, never the owner's original
world. Generated terrain is a test backdrop; this does not regenerate its
physical walls to a new height. All clients remain muted and avoid focus.

Build, test, runtime logs, six-cell results and raw captures are retained under
`logs/cloud-altitude-fix/` (ignored generated evidence). Numeric regression
checks cover three wall heights and a different dimension minimum Y.

All six source builds and Java suites pass, with zero failures or skipped cases:

| Source line | Fabric Java cases | NeoForge Java cases | Native cloud captures |
| --- | ---: | ---: | ---: |
| 26.1.x (26.1.2 runtime) | 458 | 458 | 6 per loader |
| 26.2 | 458 | 458 | 6 per loader |
| 26.3 | 461 | 461 | 6 per loader |

The static qualification suite passes 355 checks. Every native client stops
normally with no cloud mixin, rendering or shader errors. One 26.2 Fabric run
records a Minecraft Services profile-key request connection timeout; this is
retained separately in the results and is unrelated to cloud rendering.

This is targeted cloud/rendering validation, not a frozen release suite.
Weather, a medium-ring traversal, X-seam motion and owner visual acceptance
remain separate review checks. Normal top/underside shading and altitude-based
fog still legitimately affect appearance; this patch fixes the wrong face
classification and omitted curved caps.
