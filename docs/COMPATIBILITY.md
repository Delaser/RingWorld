# Compatibility contract

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

- 337/337 Java unit and parameterized cases pass per loader. Coverage fixes the
  compatibility/API versions, exact mod-ID matching, immutable inventory,
  cardinal physical-pose basis, measured production reference, checked
  maximum scaling, and exact atlas transport bounds.
- A real Fabric 0.19.3 client with the resolved Fabric API baseline reached the
  main menu, initialized the vanilla/Indigo renderer, and loaded all RingWorld
  resources and shaders without a false compatibility match or crash.
- Positive conflict detection is a pure deterministic test. The release gate
  does not install unsupported third-party mods merely to force their startup
  paths.
