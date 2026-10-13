# Sodium compatibility development — 13 October 2026

This work starts from RingWorld 1.4 source `57efcb0` on
`codex/mod-compatibility-core` (PR #292). It does not replace the candidates in
Windows release qualification. Development validation is separate from release
qualification, publication and owner approval.

## Cause and repair

Sodium replaces the terrain shader, vertex format and chunk visibility pipeline.
The initial RingWorld core refactor alone therefore preserved vanilla rendering
but left Sodium's live blocks flat beneath a curved Atlas. Its stock distance,
fog and visibility decisions also disagreed with the Atlas handoff.

RingWorld now keeps its data in a private 176-byte `RingWorld` uniform block,
owned by `GameRenderer`, instead of replacing Minecraft's `Globals` include or
publisher. Terrain and cloud entry points use the RingWorld namespace; pipeline
builders select them for matching stock materials. Unrelated mod shaders retain
their original resources and bindings.

Projection, water, coverage and atmosphere are shared GLSL includes. A thin
Sodium adapter composes these into Sodium's runtime terrain source after vertex
decoding. It preserves material sampling, alpha cutout, and the 26.3 OIT phase
ordering. It supplies both horizontal and full canonical distances, keeping the
accepted altitude-aware Atlas handoff. No Sodium shader implementation or JAR is
bundled. Unrecognized shader anchors fail with a clear contract error instead of
silently drawing a flat world.

Visibility queries use the same immutable `RingRenderView` as the vanilla
renderer. Flat-only section occlusion and CPU face rejection are disabled in a
RingWorld Overworld; curved frustum/distance checks and GPU backface culling
remain. A curved parent cannot mark its flat children wholly inside the frustum.
Stationary correction commands invalidate the visibility graph from
`needsUpdate`, before the older renderer can skip its update method. They do not
force new chunk meshes.

Three Minecraft API adapters are necessary, but none defines another projection:

| Game line | Sodium path | Binding and visibility adaptation |
| --- | --- | --- |
| 26.1 / 26.1.1 / 26.1.2 | OpenGL shader interface | Bind the existing private GPU buffer, provide camera Y, then restore indexed/range and generic buffer bindings |
| 26.2 | Minecraft RenderPass | Declare and bind stock Globals plus private RingWorld; pass curved occlusion policy into the asynchronous tree build |
| 26.3 | RenderPearl RenderPass | Same contract with the moved GPU API and explicit shader varying/OIT conventions |

Optional mixins are enabled only when Sodium and their target classes are
present. Discovery does not initialize foreign classes. Dedicated servers do not
need Sodium; multiplayer tests load it on both clients only.

## Native validation

Native tests used hidden, muted Minecraft clients on an Apple M2 / macOS OpenGL
4.1 backend. Each run records the source revision, game/loader pins, Sodium
SHA-512, stopped-world inventory, logs and PNG hashes. These are development
runs, not frozen-candidate release fixtures. No-mod controls use the same copied
world, camera, weather, view distance and capture path.

| Minecraft | Sodium build | Fabric seam / both rims | NeoForge seam / both rims |
| --- | --- | --- | --- |
| 26.1 | 0.8.9, mc26.1.1 build | PASS after readiness repeat | PASS |
| 26.1.1 | 0.8.9, mc26.1.1 build | PASS | PASS |
| 26.1.2 | 0.9.2, matching game build | PASS | PASS |
| 26.2 | 0.9.2, matching game build | PASS | PASS |
| 26.3 | 0.9.2, matching game build | PASS | PASS |

The main matrix traverses the seam naturally and captures the forward view,
reverse join and both wall rims. Its fully sampled natural Medium source is
16,384 x 256 blocks, seed 2939526885284649652. Both the live terrain and Atlas now
follow the same curve; the large sky gaps in the unchanged-1.4 Sodium controls
are closed. The accepted wall pattern remains visible on both sides.

Additional checks:

- Nearby correction off/on, distances 1/8/3, resource reload, lower and above-build
  views, and disconnect reset: 26.1 Fabric, 26.1.2 Fabric, 26.2 Fabric, 26.3 NeoForge.
- Curved rigid objects, including block entities: 26.1.2 Fabric and 26.3 NeoForge.
- Sunset, sunrise, day, night, rain and thunder at two heights; reload; Nether,
  End and Overworld return: 26.1.2, 26.2 and 26.3 NeoForge.
- Natural Medium tangent/handoff/up views in noon, dusk, night and rain:
  26.1.2, 26.2 and 26.3 Fabric.
- Both rims with outside building disabled: 26.1 Fabric and 26.3 NeoForge.
- Dedicated server plus two Sodium clients: 26.1.2 Fabric and 26.3 NeoForge. All
  seven actual entity-extraction stages pass, including both exterior sides,
  distance rejection, dimension return, periodic seam and falling; vanilla void
  damage also passes. No speculative extra entity-culling override was needed.

## Capture and harness findings

Preserve failed evidence; a fixture marker alone is not visual acceptance.

- The old 2,400-client-tick parity limit expired during complete Medium Atlas
  transfer, including a no-mod control. Parity now uses the established
  6,000-tick cold-load allowance. Completion, pose, section and image gates remain.
- One 26.1 Fabric first image coincided with the first completed GPU Atlas upload
  and still showed the preceding incomplete texture/fog during crossfade. The
  next image was normal. Capture readiness now requires a coherent, complete,
  published detailed scene and a complete preceding crossfade side (or its
  retirement). It does not demand the newest revision or an empty worker queue:
  harmless newer updates must not starve an already coherent complete scene.
- One first-matrix wall image was blank; a separate unchanged-code wall run did
  not reproduce it. An idle mesher does not prove the teleport's chunks have
  arrived. Parity now waits for the camera and requested rim chunks and requires
  consecutive settled ticks. The final matched 26.1 no-mod/Sodium repeat verifies
  all four views. No exterior-neighbour prototype was integrated without proof.
- NeoForge 26.2 fresh-world automation first stopped on Sodium's nonfatal
  deprecated-metadata warning screen. Its disposable profile now suppresses that
  screen while retaining the warning log. Fatal mod errors remain fatal and no
  normal user's configuration changes.
- The initial additional projection driver used `ringProjectionSource` instead
  of Fabric's `ringProjectionWorld` and failed before Minecraft started. The
  retry uses the existing correct task property. A later shell inherited JDK 21;
  Fabric compile attempts failed before launch. Retries explicitly use Java 25.

## Evidence and limits

The local `logs/sodium-compatibility` bank contains original baseline failures,
matched controls, all matrix reports, extra fixtures and two-client logs/captures.
The source and world/mod hash manifests identify the exact inputs. Selected
before/after GIFs are banked under `docs/media/sodium-compatibility`; labels use
Minecraft's font and original native pixels are unedited apart from GIF palette
encoding and an added label strip.

This validates the listed Sodium builds on the tested OpenGL host. It does not
certify every graphics driver, future Sodium shader layout, arbitrary modpack,
Iris/shader packs, Nvidium or Vulkan. Other replacement renderers still need
format/binding adapters to the same shared contract. Content/resource-pack
compatibility benefits from removing shared shader-state ownership but must be
tested, not inferred.

The draft PR remains separate from the active Windows release source. Merging
and qualifying replacement release JARs are later steps; these results cannot
qualify an older frozen candidate or silently change its publication metadata.

## Submerged-camera follow-up

The prior matrix does not qualify underwater visibility. The Atlas fragment
shader omitted environmental fog although live terrain retained it. A new
opt-in `ringworld.captureUnderwater` client fixture creates only a disposable
world with a sealed pool, checks the actual camera fluid, and records above,
submerged, near-surface, resurfaced and post-reload frames. Both loader hooks
use the same fixture; existing release captures are unchanged. Baseline/fix
results will be recorded here after native validation.
