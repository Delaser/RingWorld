# New feature parity — Minecraft 1.21.1

Local development branch: `codex/mc1211-new-feature-parity`, based on
`f289793805a104d0ef706254aa47af0127233f92`. Feature source is
`codex/atlas-fidelity-gallery` at `27bb33d822623997cef87298b7f23c0c916fa34a`.
Public main remains `e058c6965b7189316f72e683ebc08ad89f566e11`.
Earlier branches and evidence are retained. This work adds no publication or
support qualification claim. Create compatibility remains benched.

## Ported behavior

- Shared generation settings and editor: four source-fidelity budgets,
  vanilla/archipelago layouts, continuous river, and additional eligible
  structure candidates. Bootstrap choices persist on first world load and
  travel in the common settings handshake on both loaders.
- Engineered wall pattern, industrial relief, patterned natural-bedrock
  underside, RingWorld default preset, three selectable patterns, and the
  upstream real-block material/pattern sample images in the wall editor.
  Retired pattern IDs remain readable; editor normalization takes effect only
  when the user applies the edited style.
- Atlas format 9 side colours across disk, tile streaming, snapshots and local
  downsampling. Steep faces use upper-sample colour; rim bases remain local.
- Client `ringworld lod low|medium|high`, `show`, and `reset`, with matching
  loader command trees and no rebuild for an already selected quality.
  A finer local display budget cannot invent absent server source detail.
- Serial worker preparation of mesh/native bytes and wall/terrain pixels,
  coherent advancing-revision publication, asynchronous coalesced cache writes,
  explicit native-memory cleanup, and render-thread-only GPU uploads.
- Exterior traversal seeding and sky light, forward-depth proxy writes with
  opacity-dependent depth, and matching Lithium POI task overrides.

## Version-specific decisions

Settings use format 5 and `settings_v7`; Atlas metadata/tiles use v3. Older
saved settings receive default generation choices while retaining their
existing geometry, terrain mapping and wall style. Old Atlas caches are
invalidated. These channels do not imply cross-Minecraft networking support.

The 1.21.1 preliminary surface input is a density function, not the newer
height function. Its initial density receives the density-domain macro policy.
The final density and biome routing use the shared generation settings.
Detached preview workers capture these settings with the seed; they never
borrow a live generator's mutable caches.

The OpenGL adapter retains GLSL 150, explicit uniforms, ARGB-to-ABGR image
conversion, and the existing 1.21.1 streaming underlay. It targets the actual
`ViewArea.getRenderSectionAt` call when clamping exterior traversal seeds.
Native mesh packing runs off-thread; `VertexBuffer.upload` consumes and closes
the submitted `MeshData`, with the allocator closed by the owning build job.
Backport payload geometry and block-light validation remain in force.

## Validation checkpoint

The final complete dual-loader build passed 515 Fabric/common and 538 NeoForge
tests, with zero failures, errors or skips. Strict dependency verification
retained 371 components and 752 artifacts without pin or checksum changes.

The existing optional-visual fixture now exercises High source fidelity,
archipelago, river, more structures and engineered walls together. Its added
server check samples actual river water and saved structure policy; its normal
lighting, disconnect and reopen stages remain. The creation fixture adds two
generation-editor captures and checks wall sample availability at compact sizes.
Fresh Windows development fixtures pass on both loaders:

| Fixture | Fabric | NeoForge |
| --- | --- | --- |
| Expanded optional visual smoke | 8 captures, PASS | 8 captures, PASS |
| Creation, preview and editors | 19 captures, PASS | 19 captures, PASS |

Both smokes generated 16,384 cells at a four-block source step, found water at
31 of 32 sampled river-centre positions, verified the saved more-structures
policy, observed authored light changing 15 to 0, and completed two normal
disconnects with renderer/session cleanup and the same world reopened.
Full-size light and compact-editor captures were visually inspected; both
loader contact sheets were reviewed. The editor fixture creates no world.

The first Fabric smoke was stopped after the lamp assertion stalled. Its logs
and four captures remain under `.codex-tmp/new-feature-fabric-first-incomplete`.
The fixture now powers its lamp with a real redstone block and holds only its
test chunk through the on/off captures, releasing that ticket afterward.
The successful fresh replacement is a separate run, not a relabelled pass.

Retained successful run roots are `run-optional-visual-smoke`,
`neoforge/run-optional-visual-smoke`, `run-creation-ui`, and
`neoforge/run-creation-ui`; each contains logs, screenshots and an evidence
contact sheet. Gradle logs are under `.codex-tmp/new-feature-*` (Fabric's
successful in-world run is `new-feature-fabric-smoke-2.log`). Old mainline
smoke captures were copied to `.codex-tmp/pre-new-feature-smokes` before reuse.

After the world smokes, unused procedural-wall shader functions and their
obsolete uniforms were removed. The subsequent NeoForge menu run compiles
the cleaned shader successfully. This cleanup does not change its output.

The local artifact inventory is `.codex-tmp/new-feature-parity-evidence.json`,
SHA-256 `e6fd8ef6364d5ac32ee5c145681ec36b7c6262a8d301d5abf1c04f4cf10b0f60`.
It binds 73 logs, captures, contact sheets and development artifacts to the
code patch and test counts. Development jars (not staged release candidates):

- Fabric: `build/libs/ringworld-0.0.0-backport+mc1.21.1.jar`, SHA-256
  `bc4ce0b49da26f04667cf3f6f331bd7ce8d9ed1f28e0f2a13195cbe86d79e149`.
- NeoForge: `neoforge/build/libs/ringworld-neoforge-0.0.0-backport+mc1.21.1.jar`,
  SHA-256 `335c64b1048f4b690c16e26dbf8a413ad79cf633eedbf6161b82fcd6862735ec`.

These are local development checks. Exact release candidates, full multiplayer
and package qualification, broad compatibility, and production-scale frame
pacing remain separate gates. Render-stall diagnostics are retained; moving
CPU preparation off-thread does not establish hitch-free rendering.
