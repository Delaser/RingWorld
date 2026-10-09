# Atlas terrain/wall closure — #264

Unreleased rendering change on `codex/atlas-wall-closure`. Tracks
[issue #264](https://github.com/Delaser/RingWorld/issues/264), following the
[pre-fix investigation](ATLAS_WALL_GAPS_2026_10_09.md). Outside building was
already merged in #263 and is not changed here.

## Geometry and materials

`RingSurfaceMesh` closes both finite terrain edges with vertical side-material
faces. Their top vertices are the exact existing surface lattice; their lower
edge overlaps the lowest adjacent wall crest. This closes elevated land above
the authored rim without stretching the industrial wall texture over mountains.
The existing inward terrain sampling and half-block overlap are retained.

Wall decay now shapes a continuous crest across X and wall thickness. Each rim
has at most four cap bands, sharing their boundary vertices with the inner and
outer faces and each other. Heights use the same captured saved style and seed
as real generation. This is a bounded LOD approximation of blocky decay, not
an exact mesh of every missing block. The wall colour texture remains opaque
at every mip; the fragment shader does not discard wall pixels by alpha.
Decay is therefore closed geometry rather than holes cut in an unchanged prism.

The nominal wall top is the alignment plane. Exterior bottom is exactly
`nominal top - saved wallHeightBlocks`, captured from server-owned World settings.
The current generated-world convention places that nominal top at world minimum
Y plus saved height. Terrain samples cannot independently deepen the exterior
wall. The positive outer plane is corrected to `maxWidthZ + 1`, matching the
real block face. Inner face bottoms remain local to each segment/edge, retaining
the guard against distant wall faces covering valleys in the sky pass.

The saved study's setting is height 160, nominal top Y=96 and bottom Y=-64.
A read-only inspection of outer real wall columns X=0 and X=2047, Z=63 confirms
wall blocks at Y=-64 through Y=95. Terrain above the wall top is preserved by
real generation and is separate from configured wall height. The owner reported
an apparent lower-bottom mismatch and clarified that top alignment must control
the downward extent. The new API makes that contract explicit; visual approval
of the bottom join remains pending.

No distant underside is added. Vertex overhead is bounded at
`segments * (36 + 12 * min(4, thickness))`; default thickness five costs 84
additional vertices per segment. Existing terrain LOD budgets, proxy depth
compression, smooth handoff, finite Atlas footprint, and dimension scope remain
unchanged. Mesh/texture work stays on the serial worker using captured inputs;
GPU work remains on the render thread.

## Verification

Geometry regressions cover exact terrain-edge joins, full exterior depth,
aligned-top height anchoring at several elevations and heights, retained decay,
shared cap/face/X-seam boundaries, walls below reference terrain, and bounded
one/five/32-block thickness at Low/Medium/High. The isolated
[`WallGapDiagnostic`](diagnostics/WallGapDiagnostic.java) now fails if closing
terrain faces or lowered caps disappear. Its repaired run passes with 1,024
triangles covering the formerly open elevated span and 1,242 lowered cap
triangles; deterministic decay still removes 755 sampled real blocks in the
original control. Output: `logs/atlas-wall-closure/geometry-diagnostic.log`.

Final cross-version development builds and targeted native captures pass:

| Minecraft | Fabric Java tests | NeoForge Java tests | Native captures per loader |
| --- | ---: | ---: | --- |
| 26.1.2 (26.1 ABI) | 457 | 457 | Low, Medium, High; normal exit 0 |
| 26.2 | 457 | 457 | Low, Medium, High; normal exit 0 |
| 26.3 | 460 | 460 | Low, Medium, High; normal exit 0 |

All Java cases pass with zero failures/errors/skips. The CI static contract
suite passes 354 tests. The [native gallery](media/atlas-wall-closure/index.html)
banks the original owner capture, all six Medium captures and two additional
quality examples. All eighteen final captures and the per-cell JSON report are
retained in `logs/atlas-wall-closure/final-results.json` and adjacent logs.
No RingWorld surface-build or shader failures were recorded.

The first final 26.2 NeoForge launch logged a nonfatal vanilla keyboard callback
NPE while its separate early splash was initializing, then completed all captures
and normal shutdown. A controlled run with the existing test convention
`config/fml.toml: earlyWindowControl=false` completes the same three captures
and normal exit without ERROR lines. The initial log is retained; it is not
labelled a clean startup. Dev-profile authentication warnings are unrelated to
the rendering check and do not establish authenticated multiplayer coverage.

 These are source-development checks, not qualification of
a frozen release candidate or a substitute for the complete release suite.

## Native capture procedure

Set `ringworld.captureAtlasWalls=true` and `ringworld.backgroundTestWindow=true`.
The opt-in fixture opens a disposable save named `Atlas Wall Review`, supplied
by copying the saved fully generated study. It keeps the saved camera pose,
mutes master sound, hides HUD/clouds, switches Low → Medium → High, waits for
settling and a coherent published mesh, saves each screenshot, then stops
normally. For NeoForge, disable only the separate early splash in the disposable run's
`config/fml.toml` with `earlyWindowControl=false`. The copied-world helper
accepts only Minecraft's file-fix backup and
completion sequence. Normal clients and original saves do not enter that flow.

Local logs and all captures: `logs/atlas-wall-closure/`. A first 26.2 Fabric
launch stopped at the vanilla file-fix prompt; it was cancelled before entering
the world. Adding this fixture to the existing narrow upgrade helper allowed
subsequent automatic runs. Retain that initial log; it is not a runtime PASS.

Review mode uses `ringworld.floatingStructureReview=true` on the original saved
study. `ringworld.keepReviewPose=true` keeps the saved pose/heading instead of
resetting to the original building-study viewpoint. With the background-window
flag, it shows the muted game without activating it and returns normal controls.
