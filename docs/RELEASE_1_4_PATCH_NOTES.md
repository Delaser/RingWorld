# RingWorld 1.4

## Server performance

- Spread automatic world saves over several ticks > Reduces the pause when a
  busy server saves > Tested autosave tick: ~387 ms → ~106 ms.
- Reduced bursts of chunk cleanup and location checks while travelling > Less
  freezing when moving between loaded areas > Worst movement tick, excluding
  garbage-collection pauses: ~1,029 ms → ~177 ms in the paired test.
- Reduced temporary memory use when updating lighting > Fewer long pauses while
  chunks load and unload > Memory allocation: ~254 MiB/s → ~181–183 MiB/s;
  worst server tick: ~796 ms → ~266–277 ms across two follow-up tests. The earlier
  682 ms full garbage-collection pause did not recur.
- Moved regular Atlas file writing into the background > Saving the distant
  terrain map no longer does its compression and disk writing on the gameplay
  thread; progress is preserved for resuming later.
- Removed repeated checks of the entire completed Atlas > Reduces recurring
  server work when players view generation progress, especially on Large rings.

## Generation and Atlas updates

- Added automatic chunk-generation speed control > Pregeneration can run faster
  when there is spare capacity and slow down when performance drops > Auto starts
  at four requests; `/ringworld chunk_gen_rate 1|2|4|8|auto` changes it live.
- Made Atlas updates from newly loaded chunks adapt to server load > Reduces
  bursts of work as players explore; the distant view can catch up gradually
  during heavy loading.

## Building

- Enabled building beyond the side walls > Build platforms and structures
  outside the ring > On by default, with a World-menu toggle and
  `/ringworld building outside on|off|show`. On servers, operators control the
  setting for everyone; turning it off keeps existing builds.
- Fixed players and nearby entities disappearing outside the walls > Characters
  and other nearby entities remain visible when stepping off the ring.

## Rendering

- Closed gaps along the distant terrain edges and damaged wall sections > Fewer
  visible holes between the terrain and walls.
- Matched distant walls to the configured wall height and depth > The distant
  wall stays aligned with the real wall, including its top edge.
- Improved the changeover between real blocks and distant terrain at altitude >
  Looking down from high above the ring no longer leaves the same terrain gaps.
- Made clouds follow the configured wall height > Clouds look more consistent
  when viewed from different heights and on rings with different wall sizes.
- Removed the flat Overworld horizon band > Cleaner skies around the ring,
  including sunrise and sunset.
- Added nearby block correction > Reduces squeezed and stretched
  blocks on small rings > On by default; toggle it in Display settings or with
  `/ringworld distortion on|off`. `/ringworld distortion distance <1–8>` adjusts its
  range. This changes appearance only.
- Improved how the Atlas handles floating platforms > Recognized thin,
  manufactured platforms with clear air underneath no longer turn the terrain
  below into artificial mountains in the distant view. Natural terrain and
  unrecognized materials are kept.
- Corrected distant wall settings on Minecraft 26.3 > Wall style, height, decay
  and seed now match the saved world settings consistently across supported versions.

## Mod compatibility

- Reworked the shared rendering core > Improves compatibility with rendering
  mods while preserving the existing ring appearance.
- Improved shader compatibility by preserving Minecraft's shader state > Other
  rendering mods have fewer shared-state conflicts. This does not establish
  general Iris or shader-pack support.
- Added experimental Sodium support and fixed its terrain curve and Atlas
  transition > Real blocks, walls and water line up with the distant ring.
  Sodium compatibility remains experimental; see the compatibility notes.
- Hide the distant Atlas underwater > Nearby blocks keep normal water fog,
  without the far ring showing through; resurfacing restores the ring, including
  after a resource reload.

## Download size and cleanup

- Compressed the wall preview images > Smaller downloads while keeping the
  previews in the selector > Development JARs: ~13 MB → ~3.3 MB, about 75% smaller.
- Tidied shared seed-preview code > Reduces duplication between supported versions.

## Versions and testing

Applies to Minecraft 26.1–26.1.2, 26.2 and 26.3 on Fabric and NeoForge.
Older Atlas caches rebuild automatically for the floating-platform change.

Performance figures come from development tests on a pregenerated Large ring
with three players and a 4 GiB server heap. Autosave/movement and lighting figures
come from separate stages of that testing; they are not chunk-loading times or
guaranteed results on every server. Shorter pauses still occur.

Corresponding source: https://github.com/Delaser/RingWorld/commit/1533878fe3a6eab7562830b3f39cbd7caae6d46a
