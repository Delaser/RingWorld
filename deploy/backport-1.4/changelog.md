# RingWorld for Minecraft 1.21.1

RingWorld’s complete-ring Overworld is now available for Fabric and NeoForge. Travel across the seam without a border teleport, see the far side overhead, and build beyond the world’s rims. The Nether and End stay vanilla.

## Bringing the 26.x experience to 1.21.1

This first 1.21.1 release carries over the core RingWorld features from 26.x: the Ring Atlas, adaptive terrain generation, background saves, rain fade, and smooth handoff between nearby terrain and the distant ring. Building beyond either rim is enabled by default.

The backport also includes improvements for its Minecraft version: ring mesh uploads are spread across render calls, Atlas cache writes are batched, and client and integrated-server caches are released when worlds close.

## Requirements

Requires Minecraft 1.21.1 and Java 21. Fabric also requires Fabric API. Install the same RingWorld version on the server and every connecting client.

This backport is still a work in progress. Please report issues on GitHub: https://github.com/Delaser/RingWorld/issues

RingWorld is open source under MPL-2.0. Corresponding source: {{RINGWORLD_CORRESPONDING_SOURCE_URL}}
