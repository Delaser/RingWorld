# Modrinth release workflow

The owner resumed Modrinth publication for the six existing 1.3 JARs on
14 October 2026. All six are listed and their hosted downloads match the
qualified hashes; see [the publication record](RELEASE_1_3_MODRINTH_PUBLICATION_2026-10-14.md).
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

The owner approved a dedicated PAT named **RingWorld release uploads**, with
only **Create versions** and **Read versions**, expiring **12 January 2027**.
Its value is stored in this Mac's default Keychain as a generic password:
service `RingWorld.Modrinth`, account `MODRINTH_TOKEN`. Storage and exact
read-back were verified; no credential value belongs in this repository.

Retrieve it privately into the publisher process's `MODRINTH_TOKEN` environment
when an upload is authorized. It is not globally exported and is not installed
on the Windows build PC. Keychain access by a different executable may require
an OS access prompt; the storing Python process used Security.framework directly.
The narrow token does not grant account-profile access, version edits/deletion,
project edits or payout permissions. No successful upload using this PAT has
yet been performed; the six 1.3 files were uploaded through the dashboard.

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
