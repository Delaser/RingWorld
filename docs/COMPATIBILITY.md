# Compatibility contract

## Compatibility work based on 1.4 (13 October 2026)

The owner requires a broadly compatible rendering architecture, using Sodium as
the first test case, without reducing RingWorld's accepted visual quality.
Work is isolated on `codex/mod-compatibility-core`, based on `57efcb0`.
The active Windows release qualification and its frozen candidates remain
unchanged. No third-party combination is newly qualified by this work yet.

The first core change removes RingWorld's replacement of Minecraft's shared
`Globals` include and buffer publisher. RingWorld data instead occupies a private
176-byte `RingWorld` uniform block under `assets/ringworld`. The frame renderer owns, updates and closes the private buffer independently of
Minecraft’s `GlobalSettingsUniform`; Minecraft updates its own uniforms normally; the standard default-binding path adds the private buffer,
and only shaders consuming it get the corresponding pipeline binding. This keeps
the existing projection, fade, water, atmosphere and nearby-correction values.
Incremental resource processing removes obsolete packaged Globals and former
Minecraft-namespace RingWorld includes. The existing GLSL projection, handoff and
material rules now live in `ringworld:projection.glsl`, `ringworld:handoff.glsl`
and `ringworld:material.glsl`. Terrain, clouds and Atlas entry points reuse these
resources; replacement renderers should adapt formats and bindings to them.
Terrain/cloud entry points also use the RingWorld namespace. Pipeline builders
select these private equivalents for the matching stock materials; unrelated
mod shaders are left alone. No Minecraft shader assets are overwritten. Artifact
pair checks include the private shader resources, and incremental copies remove
the former stock-namespace entry points.
`RingRenderView` captures an immutable CPU projection for visibility workers;
worker queries must not read mutable client state.

Use one shared visual contract for the vanilla renderer and replacement-renderer
adapters. Adapters may translate vertex formats, buffer binding and visibility
hooks; they must not introduce an alternative ring geometry or poorer fallback.
Keep the current CPU/GPU projection, height-dependent visibility, seams, wall
alignment, water matching and stable depth handoff as acceptance criteria.
Content mods following Minecraft's material/binding paths should benefit from
the reduced shared-state ownership. A renderer which bypasses those paths still
needs a thin adapter; do not infer universal compatibility or shader-pack support.

The draft Sodium adapter translates its decoded camera-relative terrain into
that shared projection, carries horizontal/vertical handoff distances, and calls
the shared water, coverage and atmosphere functions. Its visibility adapter uses
`RingRenderView` bounds and disables flat-only occlusion/face rejection in a ring;
ordinary GPU backface culling remains. The older OpenGL path temporarily binds
the private buffer and restores the indexed and generic bindings. Newer paths
use declared RenderPass bindings. Shader composition retains Sodium's decoded
format and material sampling; no Sodium shader implementation is bundled. Unknown
source anchors fail with a contract error instead of silently producing flat
terrain. Adapters are absent when Sodium is absent, with discovery performed
without initializing its classes. This is experimental and unqualified until
native startup, reload, all required views and movement pass on each supported
version and loader. Other replacement renderers still need their own narrow
format/binding adapters to the same contract.

Development tests can load a clean mod set through
`-PringCompatibilityModsDir=PATH_TO_MOD_JARS`. This applies to both loaders, never
bundles those JARs and is rejected in formal vanilla release qualification mode.
Keep the mod inventory and hashes with every diagnostic capture. No-mod controls,
loader startup and screenshot completion are distinct from visual acceptance.

Validation in progress: fresh natural-world controls and Sodium runs across
26.1, 26.1.1, 26.1.2, 26.2 and 26.3, on both loaders; compare both rims, natural
seam traversal, terrain/Atlas curvature and fade, water/translucency, objects,
clouds, weather/time and resource reloads. The initial 2048x128 floating-build
save has rim gaps even with vanilla and is unsuitable for attributing #249 to
Sodium. Preserve its diagnostic logs but use the stopped, fully sampled natural
16384x256 Minecraft 26.1 source for clean forward-compatible copies.

The initial private-buffer change passes all six source/loader CI builds and the
26.1.2 Fabric and NeoForge unit suites. Its natural Medium no-mod comparison
preserves both rim views within about 0.004 mean RGB levels (0–255), and seam
views within 0.13–0.21. This is preservation evidence for the initial change,
not a visual gate for later edits or a third-party support claim. The subsequent
shared-GLSL extraction and independent frame publisher need their own native
checks. The pinned baseline matrix retains logs, source/world/mod hashes and
paired captures; screenshot completion remains pending manual visual review.
Sodium's neighbour-ready trial is retained in ignored diagnostic evidence; it does not fix
the independent terrain-shader and visibility pipelines. Keep Sodium listed as
unsupported until the complete matrix and visual review pass.

The following older release records remain historical qualification evidence.

RingWorld is an engine-level mod, not an ordinary content mod. Both the server
and every client need the matching game version, loader and RingWorld build.
Compatibility contract version 1 remains the shared contract in the 1.1
Fabric and NeoForge releases for 26.1, 26.1.1, 26.1.2 and 26.2.

One unchanged jar per loader covers the three qualified 26.1.x patches;
26.2 has separate jars. Exact dependencies are in the [README](../README.md)
and host records are in the [1.1 release record](RELEASE_1_1_PUBLICATION_2026-08-27.md).
Later stable versions use the same fail-closed intake described in
[`MINECRAFT_VERSION_SUPPORT_PLAN.md`](MINECRAFT_VERSION_SUPPORT_PLAN.md).

## Supported baseline

The upcoming 1.3 release accepts newer NeoForge builds above each jar's minimum,
while keeping its Minecraft version limits. Exact tested loader pins remain in
qualification records; accepting a loader version is not evidence it was tested.
Published 1.2 jars still have their old upper caps, including an exact
`26.3.0.7-beta` requirement for Minecraft 26.3.

The release gate covers this exact foundation:

| Component | Supported contract |
| --- | --- |
| Minecraft | Java Edition 26.1, 26.1.1, 26.1.2, or 26.2; matching jar required |
| Java | 25 |
| Loader | Fabric Loader 0.19.3 or the exact qualified NeoForge build for that game version |
| API | Matching Fabric API on Fabric only |
| Renderer | Vanilla OpenGL terrain, entity, cloud, and shader pipeline; experimental Vulkan is not qualified |
| Server | Integrated and dedicated servers with RingWorld installed |
| Client | Matching RingWorld settings/atlas channel generations and format |

Content mods that only add blocks, items, recipes, sounds, mobs, or data-pack
content are the lowest-risk category, but they are not certified merely by
belonging to that category. Mods must still respect the finite Z band, the
periodic canonical X plane, and RingWorld's world-generation ownership.

Texture-only resource packs are expected to load, but the distant atlas uses
sampled map/biome colour rather than every pack's live block textures. Shader
packs are unsupported because RingWorld supplies its own terrain and cloud
shader contract.

## Known unsupported combinations

These are deliberately listed as unsupported for the current dual-loader
release.
The loader probe reports a clear early log error when it finds their mod IDs;
it does not crash the game or claim that every version necessarily fails in
the same way.

| Mod / family | Mod ID | Conflict |
| --- | --- | --- |
| Sodium | `sodium` | Replaces the chunk renderer and terrain shader path |
| Iris | `iris` | Owns a shader pipeline without RingWorld's extended globals |
| VulkanMod | `vulkanmod` | Replaces the OpenGL renderer and shader contract |
| Canvas Renderer | `canvas` | Replaces Fabric's vanilla-compatible renderer path |
| Distant Horizons | `distanthorizons` | Adds an independent flat-world distant LOD |
| Bobby | `bobby` | Retains client chunks outside the transient presentation chart |
| Immersive Portals | `imm_ptl_core` | Rewrites world views and entity/chunk relationships |
| Gravity Changer | `gravity_changer` | Changes the vanilla `-Y` intrinsic gravity contract |
| C2ME | `c2me` | Rewrites chunk and world-generation internals |
| OptiFabric | `optifabric` | Replaces terrain and shader internals |
| Nvidium | `nvidium` | Replaces the already-unsupported Sodium terrain renderer |

The Java inventory is authoritative for early detection:
`RingCompatibilityContract.VERSION == 1`. Adding a new entry does not break
the API version. Changing the meaning of a published status, coordinate
domain, or method does.

## Public read-only API

`RingWorldApi.API_VERSION == 1` exposes:

```java
boolean isRingWorld(ServerLevel world)
RingWorldSettings settings(ServerLevel world)
RingGeometry geometry(ServerLevel world)
Vec3 canonicalPosition(ServerLevel world, Vec3 intrinsicPosition)
Vec3 nearestPresentationPosition(ServerLevel world, Vec3 canonicalPosition,
                                 double referencePresentationX)
Vec3 physicalPosition(ServerLevel world, Vec3 intrinsicPosition)
RingPhysicalPose physicalPose(ServerLevel world, Vec3 intrinsicPosition,
                              float yawDegrees, float pitchDegrees)
```

The API never mutates settings, world state, entities, or a client's chart.
Canonical conversion is appropriate when data enters authoritative storage.
Nearest-presentation conversion is for observer-local display only and must
never be persisted. Physical pose is a render/visualization result containing
the physical position, circumference tangent, local up, width direction, and
view direction; it is not a request to replace vanilla gravity or physics.

Third-party custom payloads remain opaque to RingWorld. A compatible mod owns
its packet conversion and must use canonical values at server ownership
boundaries and nearest images for observer relationships. The current API has
no event that grants another mod ownership of RingWorld's client chunk chart,
shader globals, saved settings, or topology folding.

## Loader boundary

The compatibility catalog, pose math, cost model, settings, and coordinate
rules are loader-neutral. Fabric and NeoForge use narrow discovery adapters
that obtain loaded mod IDs and log matching contract entries without changing
the public contract or coordinate semantics. Any later loader or Minecraft
version remains unsupported until its own runtime and multiplayer gates pass.

## Reporting an unlisted conflict

Include the RingWorld version, Minecraft version, loader and API versions,
complete mod list, relevant `latest.log` section, and whether the failure also
occurs with only RingWorld and Fabric API. An unlisted combination is
**untested**, not automatically supported.

## Validation

- The 2026-08-31 audited source passes 377 Java unit and parameterized cases
  per loader with zero failures, errors, or skips. Coverage fixes the
  compatibility/API versions, exact mod-ID matching, immutable inventory,
  cardinal physical-pose basis, measured production reference, checked
  maximum scaling, and exact atlas transport bounds.
- A real Fabric 0.19.3 client with the resolved Fabric API baseline reached the
  main menu, initialized the vanilla/Indigo renderer, and loaded all RingWorld
  resources and shaders without a false compatibility match or crash.
- Positive conflict detection is a pure deterministic test. The release gate
  does not install unsupported third-party mods merely to force their startup
  paths.

### Baseline diagnostic matrix, 13 October 2026

On unchanged 1.4 source `57efcb0`, 19 of 20 planned no-mod/Sodium startup and
capture cases completed, including the separately banked 26.1.2 Fabric pair.
26.3 NeoForge with Sodium timed out at approximately 90% client Atlas transfer;
it did not report a shader or startup crash. Retain that failure and retry it
serially. Completed captures still require visual review. Matched 26.1 and
26.3 Fabric seam images confirm a flat/live terrain and curved Atlas mismatch,
with visible sky gaps at the handoff. Both rims render in the current default
natural Medium world; the historical #249 missing-wall claim is not reproduced
by this case. The adapter and private entry-point routing need native checks
before any support status changes.
