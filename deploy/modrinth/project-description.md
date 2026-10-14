# RingWorld

RingWorld wraps the Overworld into a ring. Keep travelling around it and you’ll come back to where you started. Look up and you can see the other side.

Build across the join, sail through it, or chase a mob around the whole ring. Blocks, redstone, water and projectiles work across it too. Walls run along the two edges, while the Nether and End stay unchanged.

## Make it your ring

Choose a preset or set your own dimensions:

- **Small:** 2,048 blocks around, 128 across.
- **Medium:** 16,384 blocks around, 256 across. Our recommended starting point.
- **Large:** 32,768 blocks around, 512 across.

Preview your seed and wall style before creating the world. Choose wall materials, adjust their height and decay, and pick a RingWorld Structure, Masonry or Gradient pattern. The default RingWorld Structure walls include embedded machinery, channels and structural details. The underside uses matching materials too.

You can also choose sky and sun presets, add a continuous river around the ring, or increase structure density. Nearby block correction reduces squeezed and stretched blocks on smaller rings. It defaults on and can be adjusted in Display settings.

Building beyond the side walls is enabled by default. The World settings menu and an operator command control it for the whole world, including multiplayer. Turning it off keeps existing builds.

## See the world overhead

The Ring Atlas shows terrain beyond your normal render distance as a low-poly 3D model. It updates as the world changes, so you can see distant terrain without keeping every chunk loaded around you.

Real chunks become playable first. While background generation runs, a seed-based biome map fills the distant ring and gains detail progressively. The full 3D Atlas appears once generation is complete and verified. Check its progress in the corner of the screen or open RingWorld from the pause menu. Progress is saved, so generation can resume later.

Atlas generation uses processor time and disk space. Larger rings take longer; the time depends on the host machine. Automatic generation speed starts with four concurrent chunk requests and slows down under sustained performance pressure, recovering when there is capacity again. You can also set the rate manually.

Choose **Low, Medium or High** Atlas detail in Display settings or with a command. This is a personal performance setting: players on the same server can choose different levels. The distant Atlas is hidden underwater so normal water fog keeps its appearance.

Thin floating platforms made from recognized manufactured materials are left out of the Atlas to prevent artificial mountains underneath them. Nearby real blocks still render normally; natural terrain and unrecognized materials remain in the distant model.

## Versions and compatibility

RingWorld 1.4 supports **Minecraft 26.1, 26.1.1, 26.1.2, 26.2 and 26.3**, on **Fabric and NeoForge**. A separate Minecraft 1.21.1 backport is also available in beta; the commands below describe the 1.4 release.

Shader compatibility has improved by preserving Minecraft’s shared shader state. **Sodium support is experimental.** This does not mean that Iris or shader packs are generally supported. See the [compatibility notes](https://github.com/Delaser/RingWorld/blob/main/docs/COMPATIBILITY.md) for tested configurations and limits.

[Source code](https://github.com/Delaser/RingWorld) · [Report a bug](https://github.com/Delaser/RingWorld/issues)

Open source under MPL-2.0.

**Compatibility warning:** RingWorld heavily modifies Minecraft’s rendering and world generation. It may conflict with other mods that change those systems, including shaders and rendering or world-generation replacements.

## Slash commands

Commands below apply to RingWorld 1.4. In examples, `|` separates choices; type just one choice. Replace values in `<angle brackets>` with a number. Values in `[square brackets]` are optional.

### Personal display settings

These affect your client only and do not require operator permissions.

| Command | What it does |
| --- | --- |
| `/ringworld lod` or `/ringworld lod show` | Shows your Atlas detail setting and the server’s sampling resolution. |
| `/ringworld lod low` / `/ringworld lod medium` / `/ringworld lod high` | Selects one of the three Atlas detail levels. |
| `/ringworld lod reset` | Returns Atlas detail to Medium. |
| `/ringworld distortion` or `/ringworld distortion show` | Shows nearby block correction and its range. |
| `/ringworld distortion on` / `/ringworld distortion off` | Enables or disables nearby block correction. Enabled by default; changes appearance only. |
| `/ringworld distortion distance <1–8>` | Sets the correction range in chunks in each direction. Default: 3. |
| `/ringworld ringlights` or `/ringworld ringlights show` | Shows your distant Atlas lighting profile. |
| `/ringworld ringlights <falloff> [peak]` | Advanced Atlas light tuning. Falloff: 0.5–6.0. Peak brightness: 0.1–3.0; omitted peak uses 1.25. |
| `/ringworld ringlights reset` | Restores the midpoint Atlas lighting profile. |

### World and server controls

These require cheats in singleplayer or operator permission level 2 on a server. Changes apply to the world, not just your client.

| Command | What it does |
| --- | --- |
| `/ringworld atlas status` | Shows Atlas generation status and progress. |
| `/ringworld atlas start` | Starts Atlas pregeneration. |
| `/ringworld atlas pause` | Pauses Atlas pregeneration. |
| `/ringworld atlas resume` | Resumes paused Atlas pregeneration. |
| `/ringworld chunk_gen_rate` | Shows the current generation rate policy. |
| `/ringworld chunk_gen_rate auto` | Automatically adjusts generation speed to available performance. Default. |
| `/ringworld chunk_gen_rate 1` / `2` / `4` / `8` | Sets a fixed number of concurrent chunk requests. This is not a CPU-core count, lasts for the current world session, and does not start or resume generation by itself. |
| `/ringworld building outside show` | Shows whether building outside the side walls is allowed. |
| `/ringworld building outside on` / `/ringworld building outside off` | Enables or disables outside building for everyone. Saved with the world; enabled by default. Existing builds are kept when disabled. |
| `/ringworld sky atmosphere` / `/ringworld sky night` / `/ringworld sky void` | Changes the Overworld sky backdrop for everyone. |
| `/ringworld sun small` / `/ringworld sun large` / `/ringworld sun none` | Changes the Overworld sun style for everyone. |
