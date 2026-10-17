# RingWorld 1.4 for Minecraft 1.21.1

This update brings the 1.4 feature set to matched Fabric and NeoForge builds.

## World generation and server work

- Atlas checkpoints compress and write in the background, with bounded work on
  the server thread and normal save/shutdown durability.
- Atlas generation adapts its concurrency to server load. Fixed 1/2/4/8 settings
  remain available, and player-loaded chunks receive priority without starving
  background generation.
- Scheduled saves spread their work across ordinary ticks. Lighting snapshots
  avoid repeatedly copying the whole lookup table.
- The Atlas distinguishes supported terrain from qualifying thin manufactured
  platforms above an air gap, reducing distant floating-building artefacts.

## Building and controls

- Building beyond either rim is enabled by default. World controls and operator
  commands can disable new player placement while preserving existing builds.
- Exterior blocks and nearby entities remain visible within normal tracking
  distance, including across the X seam.
- Nearby block correction starts enabled, with an adjustable distance of 1-8
  chunks. The default is three chunks; client settings reset on disconnect.

## Rendering

- Ring-edge walls have material closure and bounded decay geometry across their
  thickness.
- Live terrain and the Atlas account for vertical separation during their
  handoff, including elevated views and foliage rebuilds.
- Clouds share the saved wall height, and the flat Overworld horizon band is
  removed. Underwater cameras hide the distant Atlas and recover it after
  resurfacing, including after a resource reload.
- Rain keeps the ring visible through a shared weather fade, blending the Atlas
  continuously with nearby terrain.
- Texture construction reuses sample storage instead of creating a result
  object for every texel.
- Ring mesh uploads are spread across render calls to reduce upload hitches.
  Atlas cache snapshots use explicitly owned native storage and batched writes
  to reduce heap pressure and cache-worker overhead.
- Closed client worlds and stopped integrated servers release their cached
  references when disconnecting or reopening worlds.
- Wall previews use smaller assets while preserving their controls and draft
  behaviour.

## Installation and existing worlds

Use the Fabric or NeoForge file for Minecraft 1.21.1 with Java 21. Fabric requires
Fabric API. Servers and clients require matching RingWorld versions.

The existing world's saved dimensions and terrain identity remain authoritative.
Older supported Atlas caches are rebuilt or migrated to the current format;
the Atlas is a visual overview and does not replace saved terrain. Save downgrade
and connections between different Minecraft versions are unsupported.

## Known limitations and verification

Rare brief client hitches and occasional longer server pauses remain possible
during heavy Large-world terrain transitions. The completed comparisons did
not establish a general client slowdown against 1.3, and server stall rates
were lower in the measured workload. These measurements are not a guarantee
for every machine or modpack.

Build, gameplay, UI, rendering and save/reopen checks cover Windows x64 with
the vanilla renderer. Extended stress and hour-long soak qualification were
not completed under the revised release scope. No new Sodium,
shader-pack, Create/Flywheel, macOS, Linux or broad modpack compatibility is
claimed. Minecraft 1.20.1 Fabric/Forge is not included in this release.

Corresponding source for these files: {{RINGWORLD_CORRESPONDING_SOURCE_URL}}
