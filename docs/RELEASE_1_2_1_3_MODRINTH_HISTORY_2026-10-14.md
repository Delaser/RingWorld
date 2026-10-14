# RingWorld 1.2 and 1.3 Modrinth history — 2026-10-14

The owner requested publishing 1.2 for posterity, followed by 1.3, so the public
file list preserves release order. The original six 1.3 listings were deleted
with explicit owner approval; the replacement IDs below are current. Old URLs
and per-version download counts do not transfer. Existing 1.1 listings were
left unchanged.

All twelve files are listed as Release, with matching Minecraft/loader tags,
the original per-version changelogs and immutable corresponding-source links.
Fabric requires Fabric API (`P7dR8mSH`); NeoForge has no external dependency.
Both client and server require RingWorld. No JAR was rebuilt or relabelled.

## Current files

| Release | Minecraft | Loader | Modrinth version | Downloaded SHA-256 |
| --- | --- | --- | --- | --- |
| 1.3 | 26.1–26.1.2 | Fabric | [rvpk4q5I](https://modrinth.com/mod/ringworld/version/rvpk4q5I) | `d47b92b43de08f08726c72f8c9c2dd56d63e9d757f260c45be7993f0a27ae0af` |
| 1.3 | 26.1–26.1.2 | NeoForge | [e5EfjbID](https://modrinth.com/mod/ringworld/version/e5EfjbID) | `7d21ff3807cce833a8dd048475a41a4638c0c2704bb8247fd565e68e0493d69a` |
| 1.3 | 26.2 | Fabric | [u2Cg4Af7](https://modrinth.com/mod/ringworld/version/u2Cg4Af7) | `00c960be8b0a006ea8d55e5185af6ccfe9d391cdac7fd7a55ddcfe7adae85e01` |
| 1.3 | 26.2 | NeoForge | [nHEkqVcb](https://modrinth.com/mod/ringworld/version/nHEkqVcb) | `c2b3a1a57741f3f2f1e35ccc702ce8d3cf2913cd61b3e76b46e20913f4d6b91a` |
| 1.3 | 26.3 | Fabric | [1dZ8vaTa](https://modrinth.com/mod/ringworld/version/1dZ8vaTa) | `aa3df8882554b8b15f1abfa6334ff3970649314308f0e4cf2b658af91dc03db5` |
| 1.3 | 26.3 | NeoForge | [aHOa9IH8](https://modrinth.com/mod/ringworld/version/aHOa9IH8) | `9779daf3080132eafc45781ac7eadf1bff2136996b435e7e38324725476df83a` |
| 1.2 | 26.1–26.1.2 | Fabric | [ZBxASgAH](https://modrinth.com/mod/ringworld/version/ZBxASgAH) | `80b39b600a763ce7b2d59a68b608c1921745db08e3a9980a9ec85d33bd7dac80` |
| 1.2 | 26.1–26.1.2 | NeoForge | [5cKh1Q9j](https://modrinth.com/mod/ringworld/version/5cKh1Q9j) | `4805b2322254daae7e8da0a772d45b74d601b5be2eab5a3f83ab1d544285ad9b` |
| 1.2 | 26.2 | Fabric | [ONL9lmnt](https://modrinth.com/mod/ringworld/version/ONL9lmnt) | `f9146a5f6c9e80076aff1667749fff450f705d828eea11d6ee8faa8cc81c0c6a` |
| 1.2 | 26.2 | NeoForge | [StcdMeIm](https://modrinth.com/mod/ringworld/version/StcdMeIm) | `8c7b4342b468ad1a7355d2c514b3aee291de52895cb209c43109e76a7e781f1c` |
| 1.2 | 26.3 | Fabric | [sHUldreF](https://modrinth.com/mod/ringworld/version/sHUldreF) | `17e7b7120e874832b9b6974d1867a8e57bbf197efaa1136659938a108cd3be7f` |
| 1.2 | 26.3 | NeoForge | [TIDw5EwF](https://modrinth.com/mod/ringworld/version/TIDw5EwF) | `72371c601e2d6832086f563b33252945c14fa2e005110fd5718ef1dd31588ab6` |

## Verification

- Every staged file was verified against its original CurseForge publication
  record and staging manifest before changing hosted releases.
- Every new primary CDN download was independently SHA-256 checked against that
  same original file. All twelve matched.
- Public API read-back verified listed status, release channel, game versions,
  loaders, required dependencies and corresponding-source links.
- Final public ordering is all six 1.3 files, then all six 1.2 files, followed
  by the older releases. All six removed 1.3 IDs are absent.
- 1.2 source: `ed914bf09f806c1289d4ea6b7127f6a0140f67f4`.
- 1.3 source: `f1b243af48f44452179f7a109877799584c8f47d`.
- Publication used clean pushed checkouts of those exact historical source
  revisions, the existing publisher and exact-file owner authorization records.
  The project slug was resolved to canonical Modrinth ID `3BBL2Elp` for upload.

Local original metadata, deletion records, publication responses, authorizations
and final verification are retained under `logs/modrinth-release-history/`.
No credentials are stored there. The prior [1.3 upload record](RELEASE_1_3_MODRINTH_PUBLICATION_2026-10-14.md)
retains the deleted IDs and original verification as historical evidence.

The owner approved keeping the publication PAT version create/read/write/delete
permissions; its existing 12 January 2027 expiry remains. The value stays in
macOS Keychain; see [credential location](MODRINTH_RELEASE.md#local-credential-storage-14-october-2026).

No 1.4 files, server changes or optional bundles were published. The active
Windows 1.4 qualification remains separate.
