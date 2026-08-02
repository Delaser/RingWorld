# RingWorld Fabric dedicated-server overlay

This overlay contains the RingWorld Fabric jar and its required Fabric API jar.
Obtain the matching Minecraft 26.1.2 Fabric server runtime separately, then
place both packaged jars in that runtime's `mods/` folder. Do not install the
NeoForge package or a NeoForge loader into this server.

The rest of this overlay is a generic template. Copy `server.properties.example`
to an untracked `server.properties`, keep `eula=false` until the operator has
reviewed Mojang's EULA, and copy the RingWorld config before the first Overworld
load. Back up the world before replacing either managed jar.
