# RingWorld 1.3 patch notes — draft

These notes describe changes since 1.2. Use the shared section for each Fabric
and NeoForge file on Minecraft 26.1–26.1.2, 26.2, and 26.3. Add the 26.3 section
only to the 26.3 files. Final candidate qualification and packaged-file review
remain before publication.

## All supported Minecraft versions

### World creation and controls

- Rebuilt the RingWorld world-creation editor with separate Ring, Terrain,
  Walls, Sky, and Preview pages. The selected wall style now appears alongside
  its controls.
- Added **Use** to the seed preview. Previewing or rerolling a seed leaves the
  world seed alone; **Use** locks in the previewed seed, and **Apply settings**
  copies it to Minecraft's World tab. That tab shows when the seed came from
  RingWorld. Cancelling the editor discards unapplied changes.
- Reorganized the in-world panel so generation controls are separate from the
  player's Low, Medium, and High distant-ring detail setting.

### Fixes

- Fixed the black block-selection outline and mining cracks sliding sideways
  from the targeted block above or below the ring surface.
- NeoForge files now accept loader builds newer than the stated minimum instead
  of rejecting them because they exceed the loader version used during testing.
  Minecraft version requirements are unchanged.

- Replaced irregular fade speckle with a fixed ordered pattern at the transition
  between real blocks and the distant ring.
- Delayed complete Atlas cache snapshots until incoming tile updates settle,
  reducing repeated cache-copy work during updates.

## Additional changes for Minecraft 26.3

### Distant ring and transitions

- Fixed distant terrain polygons flashing as the camera moves, most visible on
  sharp ridges and high-contrast structures. Reduced distant wall-pattern
  shimmer during movement.
- Made distant wall colours follow the active block textures more closely,
  including resource packs.
- Reduced the pale fog line where loaded land meets the distant ring. Water now
  fades toward the distant colour more gradually, and the Atlas identifies
  actual water rather than guessing from blue colour and sea level. This also
  covers water outside the ocean.

### Stability and performance

- Fixed a crash during resource reload caused by sky rendering immediately
  after renderer recreation.
- Gave 26.3 render-pipeline compilation its own worker so it can finish when
  heavy chunk loading occupies Minecraft's shared workers. This addresses the
  startup/reload stall observed at a 32-chunk view distance.
- Reused unchanged distant-wall textures during Atlas updates, reducing
  repeated GPU uploads.

The transition between real chunks and the distant ring can still reveal
heightfield differences around cliffs, trees, and thin structures. Some Atlas
uploads and cache copies can still cause occasional long frames.

Atlas caches rebuild after updating to 1.3, so the distant ring may take time
to reappear. Update the server and its clients together.
