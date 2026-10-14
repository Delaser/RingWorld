# RingWorld 1.4 publication — 14 October 2026

Uploaded the owner-authorized six-file release to CurseForge first, then Modrinth.
Modrinth versions are listed. CurseForge shows all six as Processing at the
post-upload check and will publish them automatically after approval; no manual
release hold is set. CDN availability is verified separately from moderation.
All twelve independently downloaded files match the qualified JAR SHA-256 hashes.
The JARs are byte-identical to the final Windows stages from source
[`1533878fe3a6eab7562830b3f39cbd7caae6d46a`](https://github.com/Delaser/RingWorld/commit/1533878fe3a6eab7562830b3f39cbd7caae6d46a).
No builds or gameplay tests were rerun during publication.

## Qualification and approval

The “1.4 Build” chat confirmed ten quick cells PASS and 100 full fixture cases
accepted through reviewed composite coverage. The original runs and failures
remain retained: this is not a claim of one monolithic 100-case PASS. There are
zero unresolved failures, pending cases, gameplay assertion waivers or source
changes during recovery. One diagnosed installer timeout passed a manual retry.
The audit verified 770 retained artifacts and 690 captures; the transfer also
includes a portable 40-image review gallery.

The build operator's evidence leaves owner visual acceptance pending. The owner
subsequently explicitly instructed this chat to publish the completed release;
that is the release authorization. No additional visual test is claimed here.
The banked earlier build and Windows qualification stages remain preserved.

The full release suite used the vanilla renderer. Separate Sodium development
checks have narrower documented coverage; support remains experimental. The
render-state changes improve shader compatibility but do not qualify general
Iris, Nvidium, Vulkan or shader-pack support.

## Published files

| Minecraft | Loader | Bytes | CurseForge file | Modrinth version | SHA-256 |
| --- | --- | ---: | --- | --- | --- |
| 26.1 | fabric | 3488707 | [9171469](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9171469) | [oYIAzjqq](https://modrinth.com/mod/ringworld/version/oYIAzjqq) | `6fc24354f3b80fa37e1d6261e488027548c492cfabbb54b55ab4a3c3205261f0` |
| 26.1 | neoforge | 3477115 | [9171471](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9171471) | [7mNqdfKW](https://modrinth.com/mod/ringworld/version/7mNqdfKW) | `698568ef6b65d40e56de56d52a1df77972ae04af084b72dc2fb37ed1215ab8d9` |
| 26.2 | fabric | 3488556 | [9171472](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9171472) | [mkq74qTF](https://modrinth.com/mod/ringworld/version/mkq74qTF) | `686cfd422ecdb73edf5f0355aa1a1bb47a4d0c8a5c00a11630c81498464ee854` |
| 26.2 | neoforge | 3476947 | [9171474](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9171474) | [WrhyPQKP](https://modrinth.com/mod/ringworld/version/WrhyPQKP) | `3e30479e7ab67ba3dd672be38641a7aa8e6fd36a2d2ead02021cac9f5ec5a362` |
| 26.3 | fabric | 3493315 | [9171475](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9171475) | [J3ZzQgOZ](https://modrinth.com/mod/ringworld/version/J3ZzQgOZ) | `6d583bbf7775afd5024eb928f067134bf932fd8f73b8da4045d029cbcfbb321f` |
| 26.3 | neoforge | 3481664 | [9171476](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/9171476) | [UdZZwm0o](https://modrinth.com/mod/ringworld/version/UdZZwm0o) | `c0cba972efd6ce9fa428372cb3e1569b81331533d538bac3302a9560321e5d01` |

## Public copy and verification

- [Final simplified grouped patch notes](RELEASE_1_4_PATCH_NOTES.md) were rendered
  with the immutable corresponding-source link in every uploaded changelog.
  Improved shader compatibility and experimental Sodium support are explicit.
- The live CurseForge description was the base for both updated descriptions.
  Both pages have the supported command reference at the bottom, separating
  client-only display commands from operator/world controls. Installation steps
  are removed; the compatibility warning is retained above the commands.
- The tracked templates under `deploy/curseforge`, `deploy/modrinth` and
  `deploy/listings` match. Modrinth's public API body matches the template exactly;
  CurseForge's public page and editor were read back after saving.
- Commands were checked against the shared server tree and both client loader
  registrations. `lod reset` is Medium; lighting reset is the midpoint profile;
  generation rates are concurrent requests, not CPU-core counts.
- Fabric files require Fabric API; NeoForge files have no external dependency.
  All files have their correct Minecraft group, loader and release channel.
  CurseForge also carries Client, Server and Java 25 tags.
- Original transferred stages remain unchanged. A separate publication copy
  changes only `CHANGELOG.md`, `MODRINTH-VERSION.json` and `CURSEFORGE-UPLOAD.json`.
  The publisher verifies each exact JAR, manifest, source and host metadata.
- Transport-only normalization resolves Modrinth's project slug to canonical ID
  `3BBL2Elp`. The owner's public-release instruction authorizes CurseForge's
  `isMarkedForManualRelease=false` and Modrinth's subsequent listed promotion.
- Tokens were read from macOS Keychain without printing or storing them in
  source, stages or logs. Existing permissions sufficed; none were expanded.
- The 44,452,118-byte transfer archive and every payload hash were verified.
  Windows inbound transfer was unreachable; the reverse private-LAN transfer
  succeeded. Both temporary transfer endpoints were stopped; firewall settings
  were unchanged. No public server changes or optional launcher bundles.

Local operational evidence is under `logs/release-1.4-publication/`, including
host responses, final plans, copy-only notes revision, independently verified
CDN records and description screenshots. It is ignored by Git and contains no
credentials. These records supersede the earlier 1.4 candidate's publication hold;
its old evidence and old JAR hashes remain historical, not the published build.
