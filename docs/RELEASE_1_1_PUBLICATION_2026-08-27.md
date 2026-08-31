# RingWorld 1.1 publication — 2026-08-27

The owner authorized **“merge, publish”** after the recorded Windows sign-off
and four passing macOS packaged-runtime smokes. PR
[#232](https://github.com/Delaser/RingWorld/pull/232) merged as
`b52e172670dfb27f559a6669c699ad3ebae7a450`. Publication reused the exact
approved runtime jars; it did not rebuild them from the later merge commit.

## Host records

All eight independent CDN downloads match the SHA-256 values below and pass
the nested MPL/loader licence inspection. Each upload is a standalone runtime
jar, marked Release, for both client and server. Fabric declares Fabric API
as required; NeoForge has no Fabric API dependency.

| Minecraft | Loader | Modrinth version | CurseForge file | Runtime SHA-256 |
| --- | --- | --- | --- | --- |
| 26.1, 26.1.1, 26.1.2 | Fabric | [MAOO1Dt4](https://modrinth.com/mod/ringworld/version/MAOO1Dt4) | [8749660](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8749660) | `cdf564d260a0c2405dafeeede6ec4abd14ae48cb4ab44ed233c6d380355d5663` |
| 26.1, 26.1.1, 26.1.2 | NeoForge | [BLYIvCCY](https://modrinth.com/mod/ringworld/version/BLYIvCCY) | [8749674](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8749674) | `0c2353032bc6bf9b308c6be58ada45a343ecb5ad838e393f3f4bc3526ef065e1` |
| 26.2 | Fabric | [fdPHLgt9](https://modrinth.com/mod/ringworld/version/fdPHLgt9) | [8749725](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8749725) | `dbc4d0ff170a8b3850c85edf859865e8ce10a12a7296a6f83dd324a193138949` |
| 26.2 | NeoForge | [Vtx8QJ0x](https://modrinth.com/mod/ringworld/version/Vtx8QJ0x) | [8749688](https://www.curseforge.com/minecraft/mc-mods/ringworld/files/8749688) | `15e2d2c9e84ed9a421351bce34ad5f24dfafe2349772cbe42bf34b8cda1ce0a5` |

Public version numbers are `1.1.0-fabric+mc26.1`,
`1.1.0-neoforge+mc26.1`, `1.1.0-fabric+mc26.2`, and
`1.1.0-neoforge+mc26.2`. The 26.1 files each cover the three exact tested
patches; 26.2 uses separate jars. Java 25 is required.

Corresponding source, linked from the GitHub releases and originally included
in each upload's changelog:

- 26.1.x: [`3e94b04f4b42aa22dbe3d57f2ac169745226ec05`](https://github.com/Delaser/RingWorld/commit/3e94b04f4b42aa22dbe3d57f2ac169745226ec05).
- 26.2: [`1cfac9b10648054e46f4303f6e8b87df9b9bcdba`](https://github.com/Delaser/RingWorld/commit/1cfac9b10648054e46f4303f6e8b87df9b9bcdba).

GitHub releases at those exact source commits also contain the two matching
runtime jars and SHA-256 lists:
[v1.1.0+mc26.1](https://github.com/Delaser/RingWorld/releases/tag/v1.1.0%2Bmc26.1)
and [v1.1.0+mc26.2](https://github.com/Delaser/RingWorld/releases/tag/v1.1.0%2Bmc26.2).

At the verification checkpoint, CurseForge's 26.1.x files were Approved;
26.2 Fabric was Baking and 26.2 NeoForge was Under Review. All were submitted
with automatic publication after approval. Modrinth's four versions exist and
their CDN files verify, but the project itself still says **Under review**.
These observations do not claim public launcher discovery before moderation
finishes. Host status can advance without another source change.

### Changelog-only correction — 2026-08-28

At owner request, all four existing CurseForge files were edited in place.
The public What's new sections now contain changes only:

- 26.1.x Fabric/NeoForge: added support for 26.1 and 26.1.1 on the named
  loader, alongside 26.1.2.
- 26.2 Fabric/NeoForge: added 26.2 support on the named loader; updated the
  distant-ring renderer for the reversed-depth buffer; adapted world-generation
  hooks and RingWorld menus to 26.2.

The generic feature list and installation/source boilerplate were removed
from those notes. Source provenance remains available in the project Source
repository and exact GitHub releases above. The four file IDs, jar bytes,
loader/version tags and dependency relationships were not changed; there was
no deletion, download or re-upload. The public file pages were checked to
verify the corrected text, separately from the author forms.

## Publication method and remaining automation work

The owner-approved staged manifests were rechecked before submission. The
CurseForge API first rejected its dependency JSON: omit empty relations, use
integer `projectID`, and include the dependency slug. The publisher now covers
those cases with four focused passing tests. Corrected requests subsequently
received HTTP 500; no successful API file ID was returned. The author listing
was checked for duplicates before falling back to its signed-in upload UI.
Modrinth likewise used the signed-in author UI; no stored API token was found.

This release therefore proves reviewed host delivery and downloaded-byte
identity, **not** a successful unattended end-to-end API upload. Preserve that
distinction. Do not retry the already published files to test the API.
Follow-up: [#235](https://github.com/Delaser/RingWorld/issues/235).
Only runtime jars went to the mod hosts; no Minecraft jar, launcher profile,
account data, world, or credential was uploaded.

## Optional Windows packages

The unlisted download page is refreshed separately with four version/loader
installers and the exact reviewed package bytes. Each installer checks its
own manifest and checksum and uses a separate installation directory.
The 26.1-line convenience packages launch 26.1.2. The corrected NeoForge 26.2
package includes the official-installer-derived Prism component from #234.

| Package | SHA-256 |
| --- | --- |
| 26.1.x Fabric Windows | `16d985b86019aee6f9546aaaf1f609f436d27c0ebdb49467fdffa7cf64a025bb` |
| 26.1.x NeoForge Windows | `2c530721b2aa184f7a8b4e8d26042c06716df958bf8d3ea3e1c5c885352f7a19` |
| 26.2 Fabric Windows | `18f66afa0835b84810d20b1fb95a87bdc7da9e3ada5c2d17d23681aa627a11c9` |
| 26.2 NeoForge Windows | `ad3bc4e10862a40a13e0c36a9d4cab3b017f022d1c10be85adf60c14e97d0435` |

The earlier 1.0 downloads remain unchanged. The live demo server, world and
the owner's running client were not upgraded, restarted or reset. Connecting
clients still need the version and loader matching their chosen server.

The new revision-2 kit's independent HTTP download matches SHA-256
`d75a88d800e1d388f50ba4329784a8b2addffee984a1989d11f785f64241c607`.
All new remote assets pass the upload inventory and the prior 1.0 files pass
their unchanged-before/after checks. The live page was inspected in-browser.

## Evidence and limits

- [26.1.x composite review](QUALIFICATION_REVIEW_2026-08-27.md).
- [26.2 composite and eight forward-upgrade routes](QUALIFICATION_26_2_CHECKPOINT_2026-08-27.md).
- [Exact-candidate owner handoff](RELEASE_1_1_OWNER_HANDOFF.md).
- [macOS package evidence and repaired NeoForge component](MACOS_PACKAGE_REVIEW_2026-08-27.md).

Retained local host downloads and operator reports are under ignored
`dist/release-publication-20260827/`. Host downloads were independently hashed
and inspected, not rerun as a new graphical test matrix. OpenGL qualification
does not cover Minecraft's experimental Vulkan backend or third-party
renderer compatibility. Keep a pre-upgrade world backup; never downgrade a
save opened by a newer Minecraft version.
