# JAR export and publication

This is the current export route for supported Minecraft 26.x releases.
The last published release is [1.3 on CurseForge](RELEASE_1_3_PUBLICATION_2026-09-30.md).
The latest development source requires fresh qualification before another release.

## Outputs

| Candidate group | Qualification manifest | Runtime files |
| --- | --- | --- |
| 26.1–26.1.2 | `config/minecraft-version-matrix.json` | Fabric + NeoForge |
| 26.2 | `config/minecraft-version-matrix-26.2.json` | Fabric + NeoForge |
| 26.3 | `config/minecraft-version-matrix-26.3.json` | Fabric + NeoForge |

Export **six standalone runtime JARs**, one per group/loader. The 26.1.x pair
is built against the oldest ABI and tested unchanged on all supported patches.
Source JARs, manifests, inventories and checksums are review material; upload
only each runtime JAR. Fabric declares Fabric API as required; NeoForge declares
no external dependency. Optional launcher/server bundles are a separate workflow.

## Sequence

1. Choose the public version, confirm all intended fixes/features reach every
   group/loader, and use one clean pushed source commit for all six candidates.
   Ordinary `build/libs` outputs use development settings; a successful build
   alone does not qualify them for upload.
2. Run the complete quick qualification for each manifest. Retain the frozen
   candidates, exact hashes, source provenance and strict terminal evidence.
3. Run the full required release/nightly fixtures against those same frozen
   JARs. Retain failures and any reviewed composite repairs; do not describe a
   composite result as a monolithic PASS. Complete the owner's visual review.
4. Stage each group with explicit manifest, release config and changelog paths.
   Create new release inputs; the tracked 1.1/1.2/1.3 files are historical.
   Every changelog must contain one `{{RINGWORLD_CORRESPONDING_SOURCE_URL}}` placeholder, replaced
   by staging with the immutable frozen-build source link.
5. Check the six staged identities, game versions, loader dependencies, source
   commits, hashes and release evidence together. All six frozen-build commits
   must match. Dry-run the publisher for each selected host/file.
6. After the owner authorizes upload, execute for the exact approved staged
   hashes. Credentials come from host-specific environment variables. The
   publisher creates an unlisted Modrinth version or a held CurseForge file;
   public release/promotion is a separate host action. It does not edit old files.
7. Record returned IDs, inspect host tags/dependencies, independently download
   and hash the hosted files, and record review/publication state. A successful
   submission is not proof that a file is publicly downloadable.

Example staging invocation (substitute the approved run and new release paths):

```sh
python3 scripts/stage_qualified_release.py \
  --manifest config/minecraft-version-matrix-26.3.json \
  --quick-run-id PASSED_QUICK_RUN_ID --from-frozen \
  --config /path/to/new-release/26.3-release.json \
  --changelog /path/to/new-release/26.3-changelog.md
```

Repeat for 26.1.x and 26.2 with their corresponding inputs. `--from-frozen`
changes only the approved public loader/build identity metadata and verifies
all other archive bytes against the tested candidate; it does not recompile
Java. Alternatively, explicitly supplied release JARs must pass the same
byte-equivalence check. The stage records both frozen-build and operator source
provenance. Execution still requires a clean pushed checkout matching the
staged frozen-build source; do not bypass that check to use a later checkout.
Restaging prepares the replacement before retiring the previous export and
restores the previous stage if the replacement promotion raises an error.

```sh
python3 scripts/publish_qualified_release.py \
  --stage dist/qualified-release/APPROVED_ARTIFACT_VERSION \
  --host curseforge --loader fabric
```

This command is a credential-free, network-free dry run. Execution additionally
requires `--execute` and the exact short-lived `--authorization-file`. The
owner's approval permits preparing that concrete authorization record; it does
not require asking again when the same exact upload is already authorized.
Modrinth remains optional until the owner resumes it. On an uncertain upload
response, check the author listing for an already-created file before retrying.

## Guards and remaining manual checks

Staging requires explicit group/config/notes and checks quick evidence, exact
frozen hashes, shared build provenance, public version identities, licence and
archive equivalence. Publication checks the stage format, runtime hash, source
link and generated metadata against the staged identity and canonical notes,
including loader, game versions, official projects and Fabric API relations.

The scripts stage/publish **one candidate group/file at a time**. There is no
single six-file release coordinator, automatic nightly/composite review gate,
owner visual-sign-off record, or built-in hosted-download verifier. Those checks
remain the operator's release checklist; quick qualification alone cannot
replace them. Persist upload output/IDs in the publication record. These are
useful future automation opportunities, not permission to bypass current checks.

`stage_modrinth_release.py` is the historical 1.0 clean-build staging path, not
the current matrix export command. Its shared verification/provenance helpers
and optional packaging support are still used, so the module cannot simply be
deleted. Keep `prepare_release_packages.py` out of the normal six-JAR release.
The unused Gradle Maven publishing plugin has been removed; source JAR generation
remains available. See the [export audit](JAR_EXPORT_AUDIT_2026-10-09.md),
[qualification guide](VERSION_QUALIFICATION.md) and
[release gates](MINECRAFT_VERSION_SUPPORT_PLAN.md).
