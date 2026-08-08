# Modrinth release workflow

Last updated: 2026-08-08. Issues
[#33](https://github.com/Delaser/RingWorld/issues/33) and
[#94](https://github.com/Delaser/RingWorld/issues/94) track this workflow.

RingWorld is distributed as separate normal Fabric and NeoForge runtime jars,
not as a Prism instance or client bundle. Both target `0.2.0+mc26.1.2`,
Minecraft 26.1.2, and Java 25. Fabric requires Loader 0.19.3 or newer plus
Fabric API 0.155.2+26.1.2; NeoForge requires 26.1.2.87 or newer. A server and
every connecting client must use the same RingWorld version and loader.
Cross-loader multiplayer is not claimed.

## Stage locally

Run the following single command at the repository root:

```sh
JAVA_HOME=/path/to/jdk-25 \
PATH="$JAVA_HOME/bin:$PATH" \
python3 scripts/stage_modrinth_release.py --loader both --build
```

It runs the clean Fabric and NeoForge Gradle test/build gates, validates each
expected runtime jar, compares their shared contract, and creates ignored
review directories at `dist/modrinth/0.2.0+mc26.1.2/fabric/` and
`.../neoforge/`. The pair gate requires matching versions and byte-identical
shared mixins, settings/geometry, compatibility API, protocol models, and
shader assets before either stage is written. Use `--loader fabric` or
`--loader neoforge` only when deliberately writing one loader's review
directory; both artifacts are still freshly built and pair-validated. The
script has no upload implementation, makes no network requests, accepts no
token, and cannot change a Modrinth listing. Java is always checked before the
build; `--build` remains only as a harmless compatibility spelling. The CLI
accepts no alternate jar or metadata paths, so a cached artifact cannot be
relabeled as the current source revision.

Each generated loader directory contains exactly one jar. That jar alone is a
potential upload file; `STAGING-MANIFEST.json`, `SHA256SUMS.txt`, the project
description, and the loader-specific changelog are operator-review material.
The generated `PROJECT_DESCRIPTION.md` and `CHANGELOG.md` each contain the
same exact immutable public commit URL derived from the verified checkout;
they are the recipient-facing corresponding-source route if their text is
copied to Modrinth. The manifest repeats that provenance for local package
assembly, but is not the source-delivery route. Staging fails if either public
text lacks its one generated source-link placeholder or contains a hard-coded
GitHub commit/tree/blob URL or short/full SHA. Stage only from a clean,
pushed public branch commit: the script requires this repository's exact
public HTTPS `origin` and requires `HEAD` to equal the current branch's
upstream. This avoids the circular and invalid practice of embedding a
commit's own hash in source that changes that commit. Confirm both revisions
and jar hashes before a separately authorized manual upload.

## Fail-closed checks

Staging rejects sources, development, and Javadoc archives; unexpected names;
missing compiled classes or mixin descriptors; archive source files; account,
save, option, server, log, or runtime-state files; private-key material;
unsafe or duplicate archive paths; and replacement of an unrecognized stage.
It also rejects missing or stale MPL-2.0 metadata, a missing or different
embedded `LICENSE-RINGWORLD.txt`, cross-loader metadata, inconsistent mod or
Minecraft versions, wrong Loader/NeoForge requirements, stale Fabric API or
compatibility API metadata, and incorrect author/contact/environment data. It
refuses a dirty checkout, an origin other than the exact public HTTPS URL, a
missing branch upstream, or an unpushed/different upstream revision.

Run focused checks with:

```sh
python3 -m unittest \
  scripts/test_verify_distribution_license.py \
  scripts/test_stage_modrinth_release.py \
  scripts/test_prepare_release_packages.py
```

## Manual release gates

The existing Fabric alpha upload is not modified by this workflow. Any later
upload or listing change needs explicit owner authorization and must use only
the reviewed staged jar for that loader. Inspect each archive and checksum;
install it into clean and existing matching-loader clients; launch a clean
matching-loader dedicated server; then run the two-client handshake, seam,
combat, block, boat, teleport, reconnect, atlas, and production-geometry
gates. Link `docs/COMPATIBILITY.md` and state untested renderer, shader,
gravity, world-generation, chunk, and networking combinations as risks, not
support claims.

### Non-graphical dedicated-server smoke

Before a release, create separate disposable empty server directories for
Fabric and NeoForge. Each contains only the official matching 26.1.2 loader
runtime, `eula.txt`, `server.properties`, `config/ringworld.properties`, and
the matching staged RingWorld jar; only Fabric also contains Fabric API
0.155.2+26.1.2. Verify each jar with
`scripts/verify_distribution_license.py --loader <loader>` and compare its
SHA-256 with its `SHA256SUMS.txt`. Launch with Java 25 and `nogui`, wait for
the normal server-ready message, then issue `stop` and require a clean exit.
Generated libraries, logs, and world data are test state, not release files.

The 2026-07-31 clean-server smoke passed with the staged
`0.2.0+mc26.1.2` artifact, Fabric API 0.155.2+26.1.2, and Java 25. It loaded
RingWorld's 2048-by-416 bootstrap layout, reached the ready state, and stopped
cleanly.

The historical Fabric-only checkpoint is recorded in
`FABRIC_RELEASE_CANDIDATE_2026-08-01.md`. The current dual-loader machine
evidence and package hashes are in
`DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md`. Neither record authorizes an
upload or deployment.

### Graphical installation smokes

On 2026-08-01, a fresh ignored Fabric client fixture with no RingWorld source
outputs, no account data, and no saved world loaded only Fabric Loader, Fabric
API, and the staged runtime jar. It reached the complete resource/shader and
texture-atlas initialization path, remained stable at the title screen with
`testMode=false`, produced no crash report, and was stopped cleanly. The log is
retained locally at `/tmp/ringworld-release-title/run/logs/latest.log`.

The separate ignored existing-instance Fabric fixture first loaded a disposable
`release-probe` companion Fabric client mod alone. RingWorld's exact staged jar
was then added in-place without replacing the fixture state. The second launch
reported both `[release-probe] companion initialized` and RingWorld's bootstrap
settings before completing the same resource/shader initialization, with no
crash report. Its local evidence is
`/tmp/ringworld-release-modded/run/logs/latest.log`. The probe and both
fixtures are test instrumentation only and are not source, stage, or release
artifacts. On 2026-08-02, a loader-labelled NeoForge macOS package installed
into a disposable Prism instance, loaded NeoForge 26.1.2.87 and the packaged
RingWorld jar, completed resource/shader and texture-atlas initialization, and
remained stable without a crash. The local upgrade tests also prove that
Fabric and NeoForge use separate managed Prism instances, including when one
bundle is extracted over the other, while preserving user data and unrelated
mods.

The Modrinth page must use the generated description and matching loader
changelog (or copy their generated immutable source URL verbatim), alongside
installation and world-creation guidance, exact client/server requirements,
the MPL-2.0 statement, and current alpha notes. Do not store a token in the
repository, generated stage, Gradle properties, documentation, shell history,
or client bundle.
