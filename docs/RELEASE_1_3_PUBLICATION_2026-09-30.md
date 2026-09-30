# RingWorld 1.3 CurseForge publication — 2026-09-30

The owner visually checked the staged 26.1.1 NeoForge build, then authorized
CurseForge publication of all six 1.3 JARs. The files are submitted as Release
files with automatic publication after approval. Fabric files declare Fabric
API as a required dependency. Every file is tagged Client, Server, Java 25,
its exact Minecraft versions and its matching loader. Modrinth and optional
launcher bundles were not uploaded.

Source JAR revision: `f1b243af48f44452179f7a109877799584c8f47d`.

| Minecraft | Loader | CurseForge file | Final review state | Staged and CDN SHA-256 |
| --- | --- | --- | --- | --- |
| 26.1–26.1.2 | Fabric | [9017621](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9017621) | Approved | `d47b92b43de08f08726c72f8c9c2dd56d63e9d757f260c45be7993f0a27ae0af` |
| 26.1–26.1.2 | NeoForge | [9017623](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9017623) | Approved | `7d21ff3807cce833a8dd048475a41a4638c0c2704bb8247fd565e68e0493d69a` |
| 26.2 | Fabric | [9017613](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9017613) | Approved | `00c960be8b0a006ea8d55e5185af6ccfe9d391cdac7fd7a55ddcfe7adae85e01` |
| 26.2 | NeoForge | [9017620](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9017620) | Approved | `c2b3a1a57741f3f2f1e35ccc702ce8d3cf2913cd61b3e76b46e20913f4d6b91a` |
| 26.3 | Fabric | [9017580](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9017580) | Approved | `aa3df8882554b8b15f1abfa6334ff3970649314308f0e4cf2b658af91dc03db5` |
| 26.3 | NeoForge | [9017549](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9017549) | Approved | `9779daf3080132eafc45781ac7eadf1bff2136996b435e7e38324725476df83a` |

The 26.3 pair was submitted through the authenticated author dashboard; the
other four used the repository's CurseForge API publisher with a token supplied
for this release. Each API response returned a file ID and the author listing
showed all six files. The hosted download for each ID was streamed and hashed;
all six matched the immutable staged JARs. All six showed Approved in the
author dashboard on final verification.

Automated release evidence, including the reviewed composite 26.1.x fixture
coverage, is summarized in the [release preparation record](../deploy/qualified/1.3/README.md).
