# CurseForge release workflow

RingWorld's official project is [1645598](https://www.curseforge.com/minecraft/mc-mods/ringworld).
Use the [current JAR export runbook](JAR_EXPORT.md) for qualification, staging
and the six supported-version/loader files. The 1.0 staging command is historical.

## Submission and verification

Dry-run `scripts/publish_qualified_release.py` for the exact qualified stage and
loader. Review its metadata and staged checksum. After owner authorization,
execution uses `CURSEFORGE_API_TOKEN`, `--execute` and the exact authorization
record. It submits a new held file; it does not publish/promote it or modify
previous uploads. The author UI remains an alternative for the same reviewed
runtime JAR when API submission is unavailable.

Upload only one standalone runtime JAR per file. Select the correct game group,
loader, Client and Server, and release channel. Require Fabric API project
306612 only on Fabric; NeoForge has no external dependency. Changelogs describe
changes and fixes, with the generated immutable corresponding-source link.
Do not paste installation instructions, project descriptions or test reports.

Record the returned ID and moderation state, inspect the author listing, and
independently download/hash each hosted JAR before claiming publication. After
an uncertain failure, inspect the listing before retrying to avoid duplicates.
Keep diagnostic screenshots out of the public showcase gallery.

## Author API contract

The repository publisher uses the author upload endpoint
`https://minecraft.curseforge.com/api/projects/1645598/upload-file`, with
`X-Api-Token`, multipart JSON `metadata` and the runtime `file`. Credentials must
remain outside source, stages and logs. The retained live schema findings require
integer relation `projectID`, a dependency `slug`, and omission of `relations`
when there are no dependencies. The current publisher enforces the reviewed
Fabric API relation and validates metadata before a request.

[Official API reference](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api).
This audit performs no live submission and does not newly qualify the API end
to end. Earlier API failures and UI fallbacks remain in publication records.

## Historical releases

- [1.3 publication](RELEASE_1_3_PUBLICATION_2026-09-30.md): six files, source,
  submitted states and independently matched CDN hashes.
- [1.2 publication](RELEASE_1_2_PUBLICATION_2026-09-20.md).
- [1.1 publication](RELEASE_1_1_PUBLICATION_2026-08-27.md).
- [1.0 owner sign-off and publication context](OWNER_RELEASE_SIGNOFF_2026-08-09.md).

Historical release inputs and approvals do not authorize a new upload.
