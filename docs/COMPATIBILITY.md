# Compatibility contract

## Compatibility work based on 1.4 (13 October 2026)

`codex/mod-compatibility-core`, based on `57efcb0`, preserves RingWorld's accepted
visuals while reducing ownership of Minecraft's shared shader state. The active
Windows 1.4 qualification and its frozen candidates remain unchanged.

RingWorld owns an independent 176-byte uniform block on `GameRenderer`, with
matching allocation, per-frame updates and shutdown cleanup. Minecraft retains
its normal `Globals` include and publisher. Terrain/cloud entry points use the
RingWorld namespace, selected by matching stock pipeline builders; unrelated
mod shaders retain their own assets and bindings. Incremental packaging removes
obsolete stock-namespace RingWorld overrides.

Vanilla terrain, clouds and Atlas reuse the same projection/handoff/material
GLSL; CPU visibility uses immutable `RingRenderView` snapshots. Optional renderer
adapters may translate vertex formats, binding and visibility hooks. They must
preserve the same geometry, altitude-aware depth handoff, water, atmosphere and
wall alignment. A flat fallback or disabled Atlas is not compatibility.

Sodium adapters now pass native development validation on all ten supported
Minecraft/loader cells, using 0.8.9 for 26.1/26.1.1 and matching 0.9.2 builds for
26.1.2/26.2/26.3. Corrected natural-world captures close the flat/live-versus-curved
Atlas gaps. Additional checks cover correction commands, reloads, objects,
weather/time, both other dimensions, exterior walls and two-client visibility.
See the [cause, implementation and validation record](SODIUM_COMPATIBILITY_2026_10_13.md)
for exact scope, evidence and retained failed diagnostics.

Older OpenGL adapters restore indexed/range and generic buffer bindings; newer
adapters declare the stock and private buffers through RenderPass. Visibility
uses shared curved bounds and refreshes its graph when stationary correction
settings change. Shader composition uses Sodium's original runtime resources;
no Sodium implementation or dependency is bundled. Unknown source anchors fail
clearly rather than silently drawing flat terrain. Optional mixins are absent
without Sodium and do not initialize foreign classes during discovery.

Development runs can load a clean mod directory through
`-PringCompatibilityModsDir=PATH_TO_MOD_JARS` on either loader. This does not
bundle dependencies and is rejected in formal vanilla release qualification.
Keep source/world/mod hashes with diagnostic captures. Native validation used
macOS OpenGL; it does not certify every driver, future Sodium build, shader pack
or modpack. Other replacement renderers still need narrow adapters to the same
contract. Content and texture-pack compatibility must be tested, not inferred.

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

Version 1.3 and later accept newer NeoForge builds above each jar's minimum,
while keeping its Minecraft version limits. Exact tested loader pins remain in
qualification records; accepting a loader version is not evidence it was tested.
Published 1.2 jars still have their old upper caps, including an exact
`26.3.0.7-beta` requirement for Minecraft 26.3.

The release gate covers this exact foundation:

| Component | Supported contract |
| --- | --- |
| Minecraft | Java Edition 26.1, 26.1.1, 26.1.2, 26.2 or 26.3; matching jar required |
| Java | 25 |
| Loader | Fabric Loader 0.19.3 or NeoForge at/above the matching JAR minimum; tested pins remain in qualification records |
| API | Matching Fabric API on Fabric only |
| Renderer | Vanilla OpenGL; the draft Sodium adapter has the native development coverage above; experimental Vulkan is not qualified |
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

These remain unsupported by this source branch. Published JARs retain their
original compatibility inventories.
The loader probe reports a clear early log error when it finds their mod IDs;
it does not crash the game or claim that every version necessarily fails in
the same way.

| Mod / family | Mod ID | Conflict |
| --- | --- | --- |
| Iris | `iris` | Owns a shader pipeline without RingWorld's curved material contract |
| VulkanMod | `vulkanmod` | Replaces the OpenGL renderer and shader contract |
| Canvas Renderer | `canvas` | Replaces Fabric's vanilla-compatible renderer path |
| Distant Horizons | `distanthorizons` | Adds an independent flat-world distant LOD |
| Bobby | `bobby` | Retains client chunks outside the transient presentation chart |
| Immersive Portals | `imm_ptl_core` | Rewrites world views and entity/chunk relationships |
| Gravity Changer | `gravity_changer` | Changes the vanilla `-Y` intrinsic gravity contract |
| C2ME | `c2me` | Rewrites chunk and world-generation internals |
| OptiFabric | `optifabric` | Replaces terrain and shader internals |
| Nvidium | `nvidium` | Replaces Sodium terrain with a separate unsupported rendering pipeline |

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

### Historical initial diagnostics, 13 October 2026

The following intermediate statuses are superseded by the completed development
validation linked above. Original failed evidence is retained.

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

The initial adapter/native control pair also exceeded the old visual-parity
2,400-client-tick Atlas limit. The parity fixture now uses the existing lifecycle
6,000-tick cold-load allowance on both resource ABIs. A Medium Atlas alone needs
2,048 server ticks at the ordinary tile budget, before startup and tick delays.
Completion, rendered-section, pose and screenshot checks are unchanged; this
changes only the opt-in fixture deadline, not game behaviour or the active frozen
Windows qualification. Preserve the original failed runs.

Native follow-up: the first corrected 26.1.2 Fabric control/Sodium pair completes
and the large sky gaps at the seam are closed. The newer 26.2/26.3 no-mod controls
also complete. Their Sodium startup checks identified the moved occlusion policy:
the modern adapter modifies the boolean passed to `prepareRenderTrees`, which
the worker captures; the older adapter retains `shouldUseOcclusionCulling`. A
shared visibility-demand hook marks only the visibility graph dirty when correction mode or
distance changes, covering stationary-camera command changes without remeshing.
These repairs require repeat native checks; earlier failed evidence is retained.

The projection-change check runs before `needsUpdate` returns: older Sodium
skips its update method entirely when the graph is clean, so checking inside
update would miss a command issued with a stationary camera. The shared demand
hook covers both old and asynchronous renderers without new meshes. The first
corrected 26.2 and 26.3 Fabric seam/rim capture runs now complete as well; wider
loader coverage and native correction/reload checks remain in progress.
