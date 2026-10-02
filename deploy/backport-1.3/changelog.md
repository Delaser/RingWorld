# RingWorld 1.3 for Minecraft 1.21.1

RingWorld 1.3 brings the newer world-creation tools, Atlas detail and visual
improvements to the Minecraft 1.21.1 backport, with separate Fabric and
NeoForge releases.

## Create your ring

- A new five-page editor keeps **Ring, Terrain, Walls, Sky and Preview** in
  one draft. Apply your changes together, cancel them, or restore defaults.
- Choose from ten wall presets, then customize thickness, materials,
  pattern and decay. The editor also fits smaller windows and larger GUI scales.
- Preview a seed, pan around its terrain and inspect the whole ring before
  creating the world. **Use** locks the preview seed; **Apply** carries it
  into Minecraft's world-creation screen. Editing the seed there clears the
  preview marker.
- Optional rivers and additional structures are available when creating a
  new world. Existing worlds keep their saved generation choices.

## See more of your world

- The distant terrain Atlas now samples every block and carries separate
  water and lighting data, improving coastlines and surface detail.
- Distant block and lighting updates continue through large batches of
  edits, without unloaded areas holding up the update queue.
- Water rendering uses the active resource pack's water textures, with
  improved tinting, depth blending and distant haze.
- Distant walls use colors derived from their materials and smoother texture
  filtering. Unchanged wall textures are reused instead of rebuilt repeatedly.
- Block outlines and mining cracks follow the ring's curvature more accurately.
- Structure locating now respects the finite width independently of the
  periodic circumference, including searches near the seam and rims.

## Manage generation and detail

- The RingWorld Map separates **Generation** and **Display** controls.
  Keep playing while generating the full ring, and pause, resume or cancel
  the job from the map.
- **Low, Medium and High** control distant detail locally. Medium is the
  default; changing it does not change the world's dimensions or terrain.
- Technical information is optional, and Atlas cache writes wait for a quiet
  period after incoming updates to reduce repeated disk work.

## Updating and installation

Back up existing worlds before updating. Saved dimensions, seeds and terrain
remain authoritative. Older Atlas caches are regenerated from the real world
under the new format, so the first generation after an upgrade can take time
and use more memory and disk space. The Atlas is visual terrain only; nearby
Minecraft chunks still provide blocks, collisions and gameplay.

Install the file for your loader on both the server and every client, and
update them together to RingWorld 1.3. This release targets **Minecraft
1.21.1 and Java 21**. Fabric also requires Fabric Loader 0.16.14 or newer
and Fabric API for 1.21.1; NeoForge requires 21.1.239 or newer.

Only the Overworld is curved and periodic; the Nether and End retain their
normal geometry. Broad modpack, shader-pack and Create/Flywheel compatibility
is not claimed. Distant trees and shorelines remain an approximation of real
terrain, and very large custom rings cost more to generate and render.

RingWorld is licensed under MPL-2.0. Corresponding source for these files:
{{RINGWORLD_CORRESPONDING_SOURCE_URL}}
