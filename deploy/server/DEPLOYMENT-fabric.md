# RingWorld Fabric dedicated-server overlay

This overlay contains RingWorld {{RINGWORLD_VERSION}} for Fabric and its
required Fabric API {{FABRIC_API_VERSION}} jar. Obtain Minecraft
{{MINECRAFT_VERSION}} with Fabric Loader {{FABRIC_LOADER_VERSION}}, then place
both packaged jars in that runtime's `mods/` folder. Do not install the
NeoForge package or a NeoForge loader into this server.

The rest of this overlay is a generic template. Copy `server.properties.example`
to an untracked `server.properties`, keep `eula=false` until the operator has
reviewed Mojang's EULA, and copy the RingWorld config before the first Overworld
load. Back up the world before replacing either managed jar.
