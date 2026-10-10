# RingWorld 1.4

## Rendering and building

- You can build beyond the ring’s side walls. The world option defaults on and
  can be changed live with `/ringworld building outside on|off|show`.
- Players and other nearby entities stay visible outside the side walls.
- Closed gaps along Atlas terrain edges and in decayed wall sections. Atlas walls
  now use the saved wall height, keep their top aligned, and extend to the correct depth.
- Improved the handoff between real terrain and the Atlas when viewing from high
  above the ring, so terrain stays visible instead of leaving holes.
- Clouds follow the configured wall height and remain consistent across viewpoints.
- Removed the flat Overworld horizon band, including sunrise and sunset.
- Added optional nearby block normalisation to reduce stretched or squeezed blocks
  on small rings. Enable it in Display settings or with `/ringworld distortion on`;
  `/ringworld distortion distance <1–8>` adjusts its range. It defaults off and
  changes rendering only.
- The Atlas skips recognized thin floating manufactured platforms with clear air
  underneath, keeping them from turning the terrain below into artificial mountains.
  Natural and unknown materials remain visible. Atlas format 11 rebuilds older caches.
- Corrected the 26.3 Atlas renderer to use the world’s saved wall style, height,
  decay and seed consistently with the older supported versions.

## Performance and size

- Removed repeated full-Atlas scans when reporting progress in fully generated
  multiplayer worlds, reducing periodic server tick spikes on Large rings.
- Moved periodic server Atlas serialization, compression and file writes to a
  bounded background worker. Changes made during a save remain pending; normal
  completion waits for saved-file verification without blocking gameplay.
  Snapshot preparation is spread across server ticks and reuses its buffer.
  Final stop/unload drains
  the worker so partial pregeneration can resume safely.

- Atlas pregeneration now admits several asynchronous chunk requests at once.
  Auto starts at four and backs down under sustained frame/tick pressure, then
  recovers gradually. `/ringworld chunk_gen_rate 1|2|4|8|auto` controls it live;
  dedicated servers use tick timing rather than client FPS.
  Ready-chunk capture now scales with that policy too, up to four chunks per
  tick within a small time budget, accelerating Atlas rebuilding when there is
  headroom and reducing work under sustained pressure.
- Compressed the wall selector previews, bringing development JARs down from
  roughly 13 MB to roughly 3.3 MB while preserving the selector images.
- Shared seed-preview UI code and removed duplicate sources.

All changes apply to Minecraft 26.1–26.1.2, 26.2 and 26.3 on Fabric and NeoForge.

Corresponding source: {{RINGWORLD_CORRESPONDING_SOURCE_URL}}
