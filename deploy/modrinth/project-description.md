# RingWorld

RingWorld turns the Minecraft Overworld into a finite cylindrical band that
genuinely loops around itself. Walk around the circumference and return to the
same blocks without a duplicate lap or corrective teleport. Nearby terrain
curves away from the player while a terrain atlas continues the visible ring
beyond normal chunk distance.

## What it does

- The two ends of the circumference are real neighbours. Players, mobs,
  vehicles, redstone, fluids, projectiles, and block interactions can cross the
  join.
- The Overworld has a configurable circumference, finite width, and breakable
  cobble-and-moss rim walls. Nether and End remain ordinary Minecraft.
- Nearby terrain is made from real Minecraft chunks. A lightweight curved
  surface continues the view through the sky without forcing a huge render
  distance.
- New-world presets cover Small 2,048×128, Medium 16,384×256, and Large
  32,768×512 rings. Dimensions are fixed after the first Overworld load.

## The ring fills in over time

You can start playing straight away. The distant ring is prepared in layers:

1. Minecraft generates normal playable chunks around you.
2. RingWorld draws a fogged, biome-flavoured stand-in to bridge the horizons
   and rim walls.
3. The server samples missing surface chunks into the Ring Atlas and streams
   the results. New sections softly replace the stand-in instead of popping.
4. At 100%, the fog and placeholder disappear and the full curved terrain
   texture and height mesh take over.

`Ring Atlas Generating: X%` appears at the top left while this is happening and
disappears when it is complete. Open **RingWorld Map** from the pause menu to
see the live rate and ETA, or to start, pause, and resume generation when you
have permission. Progress is saved across restarts; you do not need to walk a
lap.

Atlas generation creates real chunks and uses CPU, disk, and time. Small rings
finish quickly. Medium and Large rings can take tens of minutes or longer,
depending on the machine, seed, server load, and other mods. The world remains
playable while it runs, but the distant ring is deliberately foggier and less
detailed until completion. The Atlas is visual only: real chunks still own
collision, entities, lighting, and block interaction.

## Requirements and installation

Install the matching Fabric or NeoForge artifact in Minecraft Java 26.1.2 with
Java 25. Fabric requires Fabric Loader 0.19.3 or newer and Fabric API
0.155.2+26.1.2; NeoForge requires NeoForge 26.1.2.87 or newer. RingWorld is
required on the dedicated server and on every connecting client; all must use
the same loader and RingWorld version. Add the jar to an existing matching
loader instance like any other mod. Create a new RingWorld world from the
Create World screen, or configure a dedicated server before its first
Overworld load. Saved RingWorld dimensions are immutable.

## Compatibility

RingWorld is an engine-level mod. Back up worlds before changing versions.
Mods that replace renderers or shaders, gravity, chunk or world-generation
internals, entity tracking, distance rules, or networking may conflict. Do not
assume Sodium, Iris, large world-generation suites, or another loader are
compatible unless that exact combination is explicitly tested.

Fabric and NeoForge files are separate loader-specific releases. Do not put
both files in one instance, and do not assume cross-loader multiplayer works.

## Licence and source

RingWorld is open-source software under the Mozilla Public License 2.0. The
exact corresponding source revision for this release is
[this immutable commit]({{RINGWORLD_CORRESPONDING_SOURCE_URL}}).
Minecraft is a trademark of Microsoft; RingWorld is not affiliated with Mojang
or Microsoft.
