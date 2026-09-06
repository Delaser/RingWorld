# Minecraft 26.3 prerelease testing — 2026-09-06

Owner intends to wait for stable Minecraft 26.3 before the next RingWorld release. Research only: no 26.3 build/client launched and no support claim established.

## Verified availability

- Mojang manifest latest release is 26.2; latest snapshot is 26.3-pre-2, released September 4. Game metadata requires Java 25.
- Fabric metadata offers Loader 0.19.5 for 26.3-pre-2. Published Fabric API 0.159.4+26.3 has embedded dependencies Minecraft ~26.3-, Loader >=0.19.3, Java >=25 (read directly from the jar).
- NeoForge releases Maven metadata currently has no version containing 26.3. Latest listed 26.2 artifact is 26.2.0.79. Full dual-loader testing awaits suitable tooling.
- No firm stable release date was established from the official material checked.

Sources:
- https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
- https://www.minecraft.net/en-us/article/minecraft-26-3-pre-release-2
- https://meta.fabricmc.net/v2/versions/loader/26.3-pre-2
- https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.159.4+26.3/
- https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml
- https://www.minecraft.net/en-us/article/minecraft-26-3-snapshot-2
- https://www.minecraft.net/en-us/article/minecraft-26-3-pre-release-1

## Recommended experimental intake

Pin exact prerelease/tooling in an isolated experimental configuration. Resolve Loom/build-tool compatibility before compilation; published Loader/API availability alone does not prove RingWorld compiles. Start Fabric compilation and mixin validation, then client and dedicated-server smokes. Prioritize Atlas transparency/depth handoff, water, rims, night, both available rendering backends, macOS fullscreen/input, periodic terrain and structures, then multiplayer and copied-world upgrades. Use disposable worlds or copies; do not reopen upgraded saves in older clients.

26.3 changes order-independent transparency, depth copying, macOS fullscreen/input, and worldgen/data registries. These directly justify early investigation of RingWorld rendering and generation adapters. Existing version source selection is not proof of 26.3 ABI compatibility. Keep older source adapters reproducible.

Repeat affected checks on subsequent prereleases/RCs; freeze the final stable game and loader artifacts and run final qualification before publication. Prerelease evidence reduces migration risk but cannot qualify different final release bytes. Current policy already permits explicitly experimental prerelease intake in MINECRAFT_VERSION_SUPPORT_PLAN.md.
