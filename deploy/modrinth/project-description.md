# RingWorld

RingWorld turns the Minecraft Overworld into a finite cylindrical band that
genuinely loops around itself. Walk around the circumference and return to the
same blocks without a duplicate lap or corrective teleport. Nearby terrain
curves away from the player while a terrain atlas continues the visible ring
beyond normal chunk distance.

## Requirements and installation

Install this Fabric mod in Minecraft Java 26.1.2 with Java 25, Fabric Loader
0.19.3 or newer, and Fabric API 0.155.2+26.1.2. RingWorld is required on the
dedicated server and on every connecting client; all must use the same version.
Add the jar to an existing Fabric instance like any other mod. Create a new
RingWorld world from the Create World screen, or configure a dedicated server
before its first Overworld load. Saved RingWorld dimensions are immutable.

## Alpha compatibility

This is an experimental engine-level alpha. Back up worlds before testing.
Mods that replace renderers or shaders, gravity, chunk or world-generation
internals, entity tracking, distance rules, or networking may conflict. Do not
assume Sodium, Iris, large world-generation suites, or another loader are
compatible unless that exact combination is explicitly tested.

RingWorld is Fabric-only today. A future NeoForge version may use this project
as a separate loader-specific release after its runtime validation passes.

## Licence and source

RingWorld is open-source software under the Mozilla Public License 2.0. The
exact corresponding source revision is recorded with every release manifest.
Minecraft is a trademark of Microsoft; RingWorld is not affiliated with Mojang
or Microsoft.

