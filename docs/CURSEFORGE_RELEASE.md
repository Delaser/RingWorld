# CurseForge release workflow

CurseForge project `1645598` is the official RingWorld Minecraft Mod listing:
`https://www.curseforge.com/minecraft/mc-mods/ringworld`. The project and its
first files were submitted for moderation on 2026-08-09. CurseForge may keep
the public page unavailable until moderation is complete.

This document remains the exact 26.1.2 host record and manual upload runbook.
The qualification, staging, and dual-host delivery plan for 26.1.x and later
stable lines is
[`MINECRAFT_VERSION_SUPPORT_PLAN.md`](MINECRAFT_VERSION_SUPPORT_PLAN.md).

## Author Upload API

CurseForge provides an official author API for automated project-file uploads.
It uses a token generated from the CurseForge author account and supplied in
the `X-Api-Token` header. A multipart request to
`https://minecraft.curseforge.com/api/projects/{projectId}/upload-file`
contains a JSON `metadata` field and the actual `file`; a successful response
returns the new CurseForge file ID. The metadata supports changelog and type,
display name, game-version IDs or names, `alpha`/`beta`/`release`, optional
manual-release hold, and dependency relations. Current game-version and
dependency values are available from the corresponding official API
endpoints.

The existing releases were uploaded manually. Future automation must remain
dry-run-first, read its token only from an environment variable or OS
credential store, redact authorization data, consume only a verified staged
jar, require explicit owner authorization for execution, and record the
returned file ID. Fabric API is a `requiredDependency` only for the Fabric
file. Upload automation does not bypass CurseForge moderation and must never
archive, delete, update, or resubmit files automatically.

Official reference:
[CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api).

## Current 1.0 uploads

On 2026-08-10 the owner-authorized 1.0 files were submitted as `Release`:

| Loader | Display name | SHA-256 | Current host state |
| --- | --- | --- | --- |
| Fabric | `RingWorld 1.0 for Fabric` | `ec06f6dbf81a6ac1c662f87f2fdb6a3d30297222e61da7094212b125a568c421` | Under Review |
| NeoForge | `RingWorld 1.0 for NeoForge` | `ad818e6aec7aaf64f4d0618975c667d8b9163965e83e32408d8fc98797e700d5` | Baking |

Both use exact source commit `f3a5ce12a3d72a7e2253e893ca385e27f3fe7448`.
Fabric declares Fabric API project `306612` as a required dependency;
NeoForge has no external relation. These states may advance asynchronously
through CurseForge review without another source change.

## Historical alpha 3 uploads

The owner-authorized Fabric and NeoForge alpha 3 uploads use the same exact
runtime jars and corresponding source as the verified Modrinth files:

| Loader | Display name | SHA-256 |
| --- | --- | --- |
| Fabric | `RingWorld 0.2.0 for Fabric — alpha 3` | `9ec25789e1418fd3b1877c3c23d8388cbb880a0ed562ef5f0608498df0605097` |
| NeoForge | `RingWorld 0.2.0 for NeoForge — alpha 3` | `ac8b8776d85038512bb85dab8967a32a53e8d33128a4ccae17b51b65b214938a` |

Both files target Minecraft 26.1.2 and Java 25, are marked `Alpha`, and are
required on client and server. The Fabric file declares Fabric API project
`306612` as a required dependency. The NeoForge file has no external
dependency. Both changelogs link corresponding source commit
`94c8c9eb8a1a0e3d399ffd08a87af5c70b60b9b7`.

Both files reached CurseForge's `Under Review` state after upload processing.
The project is in the `Mods` class with `World Gen` as its main category and
`Dimensions` and `Player Transport` as additional categories. It declares
Mozilla Public License 2.0, permits third-party distribution, allows comments,
and points its public source field at `https://github.com/Delaser/RingWorld`.

The media gallery uses the six in-game images from the official showcase page,
not automated test-fixture captures: `ring-snow-arch`, the open/tight/compact
ratio views, and the distant/nearby Atlas views. CurseForge's 2 MiB media limit
required the 3.2 MiB snow PNG to be uploaded as a visually equivalent JPEG;
the other showcase files were accepted unchanged.

## Future upload checklist

1. Stage and verify both jars from one clean, pushed commit using
   `scripts/stage_modrinth_release.py --loader both --build` under Java 25.
2. Record the exact commit and SHA-256 values before opening either host.
3. Upload one standalone runtime jar per loader. Never upload Minecraft,
   development/source jars, Prism bundles, server overlays, or both loaders in
   one file.
4. Select Client and Server, Java 25, Minecraft 26.1.2, and only the matching
   loader. The completed owner gate authorizes the 1.0 files as Release.
5. Add Fabric API as a required relation only to the Fabric file.
6. Put the exact immutable source-commit URL in both changelogs. Confirm the
   project remains MPL-2.0 and its Source tab still points to the public repo.
7. Wait for malware scanning, file review, and project moderation. Once
   downloadable, fetch each hosted jar, compare its SHA-256, and run the
   loader-specific distribution/licence verifier.
8. Record hosted file IDs, review status, hashes, and any moderation feedback
   in the current release-candidate evidence.
9. Reuse approved showcase imagery for the gallery. Do not publish diagnostic
   captures with test overlays, debug text, or known visual defects as release
   screenshots.

The 2026-08-10 owner instruction explicitly authorizes the matched 1.0 upload,
showcase-link update, and rollback-safe NeoForge demo migration. Future uploads
or world changes require a new explicit go/no-go.
