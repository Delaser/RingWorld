# Modrinth release workflow

The owner resumed Modrinth publication for the six existing 1.3 JARs on
14 October 2026, then requested publishing 1.2 followed by 1.3 for historical
release order. See [the final publication record](RELEASE_1_2_1_3_MODRINTH_HISTORY_2026-10-14.md)
for the current IDs and verified downloads; the original six 1.3 IDs were removed.
Earlier published-file history is recorded in
[the 1.1 publication record](RELEASE_1_1_PUBLICATION_2026-08-27.md).
Future uploads still require their own authorized release scope. Use the
[current JAR export runbook](JAR_EXPORT.md); the older 1.0 clean-build staging
command is not the current supported-version matrix export route.

## When publication resumes

Use the same qualified six standalone runtime JARs as the other selected host,
with the matching loader/game tags and public identities. Publish only runtime
JARs, not sources, manifests, launcher bundles or server overlays. Fabric requires
Fabric API project `P7dR8mSH`; NeoForge declares no external dependency.

Dry-run `scripts/publish_qualified_release.py --host modrinth` for the exact stage
and loader. Review the generated notes, immutable corresponding-source link,
version number and dependencies. Execution needs `MODRINTH_TOKEN`, `--execute`
and the exact short-lived owner authorization. It creates an unlisted version;
listing/promotion is a separate action. It has no delete/update/deploy route.
Keep tokens out of repository files, shell history, stages and logs.

### Local credential storage (14 October 2026)

The owner approved a dedicated PAT named **RingWorld release uploads**, expiring
**12 January 2027**. For the historical 1.2/1.3 upload ordering, the owner also
approved retaining **Create versions**, **Read versions**, **Write versions**
and **Delete versions** permissions; they are not temporary permissions.
Its value is stored in this Mac's default Keychain as a generic password:
service `RingWorld.Modrinth`, account `MODRINTH_TOKEN`. Storage and exact
read-back were verified; no credential value belongs in this repository.

Retrieve it privately into the publisher process's `MODRINTH_TOKEN` environment
when an upload is authorized. It is not globally exported and is not installed
on the Windows build PC. Keychain access by a different executable may require
an OS access prompt; the storing Python process used Security.framework directly.
The token grants version management, not account-profile, project-edit or
payout permissions. It has been used successfully for version deletion,
creation and public listing during the historical-release upload. The original
six 1.3 listings were uploaded through the dashboard before this token existed.

The upload API requires the project's canonical ID `3BBL2Elp`. The historical
staging descriptors use the slug `ringworld`; resolve and verify that alias
before transporting the reviewed metadata. Passing the slug directly to version
creation returned HTTP 401 despite valid version permissions. Do not change the
staged JAR, corresponding-source identity or approved project when resolving it.

Record the returned version ID. Inspect hosted loader/game/dependency metadata,
independently download/hash the hosted JAR, and record public/review status before
claiming publication. The MPL-2.0 licence and public corresponding-source route
must remain available. An uncertain upload response requires checking for an
existing version before retrying.

## Retained historical tooling

`stage_modrinth_release.py` retains the 1.0 single-game-line staging CLI and
shared source/JAR verification helpers used by current qualification and
optional packages. Its hard-coded 1.0 descriptors are historical inputs.
Do not relabel current development JARs through that path as a new release.
Source JAR generation and optional package assembly remain available separately;
neither is required to export the six runtime files.

See [qualification](VERSION_QUALIFICATION.md), [release gates](MINECRAFT_VERSION_SUPPORT_PLAN.md)
and the [export audit](JAR_EXPORT_AUDIT_2026-10-09.md).
