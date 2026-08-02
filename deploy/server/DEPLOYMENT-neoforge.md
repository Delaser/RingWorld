# RingWorld NeoForge dedicated-server overlay

This overlay contains the RingWorld NeoForge jar only. Obtain the matching
Minecraft 26.1.2 NeoForge 26.1.2.87 server runtime separately, then place the
packaged RingWorld jar in that runtime's `mods/` folder. Fabric API is neither
included nor required; do not install the Fabric package into this server.

The rest of this overlay is a generic template. Copy `server.properties.example`
to an untracked `server.properties`, keep `eula=false` until the operator has
reviewed Mojang's EULA, and copy the RingWorld config before the first Overworld
load. Back up the world before replacing the managed RingWorld jar.
