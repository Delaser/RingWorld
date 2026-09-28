# RingWorld

RingWorld bends the Overworld into a ring. Keep travelling around it and you’ll come back to where you started. Look up and you can see the other side.

You can build across the join, sail through it, or chase a mob around the whole ring. Blocks, redstone, water and projectiles work across it too. Walls run along the two edges. The Nether and End stay unchanged.

## Choose your world size

Create a new world and open the RingWorld settings. Pick a preset or enter your own dimensions:

- **Small:** 2,048 blocks around, 128 across.
- **Medium:** 16,384 around, 256 across.
- **Large:** 32,768 around, 512 across.

The dimensions are fixed once the world is created.

## Why the distant ring starts out foggy

The Ring Atlas builds a view of the terrain beyond your render distance. As it fills in, more of the ring becomes visible. You can play while it runs, and progress is saved when you quit.

Open **RingWorld Map** in the pause menu to check progress and the estimated time remaining. Players with permission can start, pause or resume generation there. You don’t have to explore the whole ring yourself.

Generating the Atlas takes processor time and disk space. Medium and Large worlds can take tens of minutes or longer, depending on your computer and settings.

## Installation

- RingWorld 1.1 supports **Minecraft Java 26.1, 26.1.1, 26.1.2 and 26.2**, with **Java 25**.
- Download the file for your Minecraft version and loader: **Fabric or NeoForge**. The 26.2 files are separate from the 26.1.x files.
- Fabric also needs the matching **Fabric API**. Don’t install both loader versions of RingWorld together.
- For multiplayer, install RingWorld on the server and every player’s client. Use the same loader and RingWorld version throughout.
- Start a new world. On a dedicated server, configure RingWorld before generating the Overworld for the first time.

Back up your saves before updating. Mods that replace rendering, shaders, world generation or networking can conflict with RingWorld. Check the [compatibility notes](https://github.com/Delaser/RingWorld/blob/main/docs/COMPATIBILITY.md) before adding it to a modpack.

[Source code](https://github.com/Delaser/RingWorld) · [Report a bug](https://github.com/Delaser/RingWorld/issues)

Open source under MPL-2.0. Not affiliated with Mojang or Microsoft.
