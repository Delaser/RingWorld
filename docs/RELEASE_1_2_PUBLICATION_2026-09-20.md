# RingWorld 1.2 CurseForge publication — 2026-09-20

All six Release jars were submitted with descriptive change-only patch notes and automatic publication after approval. Modrinth is deferred; no launcher bundles were published. The owner explicitly directed completion of the final uploads after the usage boundary was reached.

Source: `ed914bf09f806c1289d4ea6b7127f6a0140f67f4`. All six CDN downloads match their staged SHA-256 hashes. Fabric declares required Fabric API; NeoForge has no external dependency relation. All files declare Java 25, Client and Server, their exact game versions and matching loader.

| Minecraft | Loader | File | State at submission review | SHA-256 |
| --- | --- | --- | --- | --- |
| 26.1–26.1.2 | fabric | [8931547](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8931547) | Approved | `80b39b600a763ce7b2d59a68b608c1921745db08e3a9980a9ec85d33bd7dac80` |
| 26.1–26.1.2 | neoforge | [8931570](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8931570) | Approved | `4805b2322254daae7e8da0a772d45b74d601b5be2eab5a3f83ab1d544285ad9b` |
| 26.2 | fabric | [8931608](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8931608) | Approved | `f9146a5f6c9e80076aff1667749fff450f705d828eea11d6ee8faa8cc81c0c6a` |
| 26.2 | neoforge | [8931628](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8931628) | Under Review | `8c7b4342b468ad1a7355d2c514b3aee291de52895cb209c43109e76a7e781f1c` |
| 26.3 | fabric | [8931687](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8931687) | Processing | `17e7b7120e874832b9b6974d1867a8e57bbf197efaa1136659938a108cd3be7f` |
| 26.3 | neoforge | [8931727](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8931727) | Baking | `72371c601e2d6832086f563b33252945c14fa2e005110fd5718ef1dd31588ab6` |

## Validation

Both 26.3 loaders pass 441 Java cases, 19 creation captures and the complete Atlas UI/live-edit/disconnect fixture. Python: 433 passed, two expected skips. Fresh frozen release qualification and staging pass for all version lines:

- 26.1.x: `20260920T141732Z-7d0f8596f540`.
- 26.2: `20260920T143018Z-44c07f4a082b`.
- 26.3: `20260920T144223Z-2c53bf50d09b`.

The first final 26.3 quick run failed creating a Gradle cache directory during disk exhaustion. Generated caches were removed; unchanged source passed the complete rerun above. Earlier full 26.3 runtime qualification remains recorded separately. Older full runtime sweeps, saved-world upgrades and optional launcher checks were waived by the owner.
