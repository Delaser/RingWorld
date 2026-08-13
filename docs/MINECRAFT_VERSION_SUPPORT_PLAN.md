# Minecraft version support plan

Status: approved policy and active implementation. Phases 0–3 are implemented;
the six quick cells pass with one unchanged jar per loader. Phase 4 has real
six-cell Atlas-recovery, worldgen/structure, and creation/settings UI
source-ABI evidence; its remaining client, gameplay, lifecycle, and rendering
fixtures are pending.

## Support model

Minecraft 26.1 is RingWorld's source and build compatibility floor. The
currently proven and published release remains Minecraft 26.1.2. Minecraft
26.1 and 26.1.1 must not be advertised as compatible until the qualification
matrix in this document passes on both Fabric and NeoForge.

For the 26.1 patch line, the preferred outcome is one Fabric jar and one
NeoForge jar that each work unchanged on every stable 26.1.x release. That
claim is valid only when the exact same jar SHA-256 has passed every matrix
cell. Minecraft 26.1.2 remains the recommended 26.1.x runtime because it
contains the later official hotfixes.

Each subsequent stable Minecraft line, beginning with 26.2, is a deliberate
intake and qualification project. It may use a separate RingWorld artifact
when Minecraft, loader, shader, or mixin ABI changes require one. Snapshots,
pre-releases, and release candidates are excluded unless they are explicitly
labelled as experimental.

"Support every subsequent version" therefore means:

- every stable Minecraft release enters the automated intake pipeline;
- it is advertised only after the required loader/version cells pass;
- a failed or incomplete cell remains unsupported and is reported honestly;
- older supported lines remain reproducible rather than being silently
  rebuilt against moving dependencies.

## Initial matrix

The first qualification project covers six cells:

| Minecraft | Fabric | NeoForge |
| --- | --- | --- |
| 26.1 | qualification pending | qualification pending |
| 26.1.1 | qualification pending | qualification pending |
| 26.1.2 | published and proven | published and proven |

All six cells use Java 25 and isolated Minecraft, loader, API, run-directory,
cache, port, and evidence definitions. A 26.1.x-wide artifact is permitted
only if each loader's jar is byte-identical across its three cells.

## Version manifest

Keep one reviewed manifest entry per Minecraft release. It owns:

- exact Minecraft client and server versions;
- required Java generation;
- Fabric Loader, Fabric API, NeoForge, Loom, and ModDevGradle versions;
- dependency download URLs and checksums;
- expected mixin targets and shader/resource ABI;
- isolated run directories, ports, seeds, and fixture profiles;
- support state: `pending`, `passing`, `failing`, or `published`.

Never use a floating `latest` dependency in a qualification or release job.
The orchestrator must reject an absent checksum, dirty source tree, unexpected
artifact hash, reused live-world path, or overlapping fixture lock.

## Qualification tiers

### Quick

Run for each pull request that affects shared code, a platform adapter, build
wiring, mixins, shaders, resources, persistence, protocol, or packaging:

- resolve every manifest cell;
- compile shared, Fabric, and NeoForge sources;
- run the full unit/parameterized suite;
- validate mixin targets, access changes, payload registrations, shader
  includes, metadata, licence, and jar contents;
- launch a fresh dedicated server for each loader/version cell.

### Nightly

Run quick qualification plus disposable runtime coverage:

- fresh-world generation and saved-settings reload;
- representative worldgen, structures, portals, and finite-rim checks;
- unattended Atlas generation, checkpoint, resume, and cache identity;
- integrated client resource/shader load and world entry;
- natural seam travel, reconnect, stateful blocks, and representative combat;
- forward world copies within a supported line, such as 26.1 to 26.1.1 and
  26.1.1 to 26.1.2. Never downgrade a world.

### Release

Run nightly qualification plus the complete frozen-candidate gates:

- full dual-client multiplayer matrix;
- production-size worldgen/structure and Atlas gates;
- production projection, visual parity, lifecycle, and layout switching;
- graphical Windows and macOS smoke runs, with one GUI fixture per GPU;
- exact candidate staging, shared-contract comparison, package verification,
  and immutable checksums;
- owner review of the generated evidence and explicit release approval.

Visual automation should compare stable landmarks, masks, topology, missing
geometry, seam continuity, and broad colour ranges. It must not require exact
pixel equality across GPUs.

## Evidence and orchestration

The planned orchestrator is a loader- and version-aware wrapper around the
existing Gradle fixtures. Its intended interface is:

```text
python3 scripts/run_minecraft_qualification.py --tier quick|nightly|release
```

This command does not exist yet. When implemented it must:

1. select explicit manifest cells;
2. acquire an exclusive profile lock and allocate distinct ports;
3. create disposable server/client/cache directories;
4. run bounded phases with clear timeouts and terminal markers;
5. record command, source revision, dependency versions, artifact hashes,
   world identity, mapping identity, logs, screenshots, and verdict;
6. write immutable evidence below
   `dist/qualification/<ringworld>/<minecraft>/<loader>/<run-id>/`;
7. classify each cell as `PASS`, `FAIL`, or `INCOMPLETE` and fail closed.

Hosted CI can own compilation, unit tests, jar inspection, and headless server
gates. Self-hosted Linux can run heavier dedicated-server matrices. Graphical
Windows and macOS workers own the client/render gates. Jobs may run in
parallel only when their run directories, caches, ports, and GPUs do not
overlap.

## Stable-version intake

For every new stable Minecraft release:

1. add a pinned manifest entry without changing any support claim;
2. compare client and server jars against the previous supported version;
3. audit every RingWorld mixin target, packet path, saved-data boundary,
   renderer hook, shader include, loader adapter, and Java/toolchain change;
4. implement the smallest shared or version-owned adapter needed;
5. run quick, nightly, then release qualification;
6. verify forward copies of representative supported worlds while preserving
   the originals;
7. publish and advertise the version only after every required cell passes;
8. retain the evidence and pinned build inputs so the result is reproducible.

A new Minecraft release is a porting project even when the source compiles
without edits. A successful launch alone is not compatibility evidence.

## Implementation order

1. Add the version manifest and schema validator.
2. Parameterize Fabric and NeoForge dependencies and isolated run profiles.
3. Build the quick orchestrator and machine-readable evidence schema.
4. Qualify 26.1, 26.1.1, and 26.1.2 with exact same-jar checks.
5. Add nightly world, Atlas, multiplayer, and forward-upgrade phases.
6. Add release-tier graphical and packaging orchestration.
7. Update mod metadata to a 26.1.x range only if all six initial cells pass.
8. Use the completed process as the mandatory intake path for 26.2 and later.

Until step 7 passes, public installation documentation and hosted files must
continue to name Minecraft 26.1.2 exactly.

## Definition of done

The first rolling-version release is complete only when all of the following
are true:

- 26.1, 26.1.1, and 26.1.2 have terminal Fabric and NeoForge verdicts;
- every advertised cell passed with the exact hosted jar for its loader;
- the supported range is encoded consistently in the jar, generated release
  metadata, README, compatibility documentation, and both host listings;
- representative worlds pass forward-copy tests and no downgrade is claimed;
- clean client and dedicated-server installations pass on both loaders;
- Modrinth and CurseForge each serve files whose downloaded hashes match the
  frozen candidate;
- dependency, loader, game-version, environment, release-channel, licence,
  and corresponding-source metadata are verified after publication;
- the previous published release remains available as rollback until the new
  release is proven healthy;
- the evidence index can reproduce which source, dependencies, commands,
  artifacts, machines, and results justified the support claim.

## End-to-end delivery plan

### Phase 0 — tracker and frozen policy

Create one GitHub epic for the 26.1.x qualification and one issue per phase
below. Each issue owns a bounded file area, required commands, evidence path,
and exit condition. Record these non-negotiable decisions in the epic:

- 26.1 is the compatibility floor;
- 26.1.2 remains recommended and published until replacement approval;
- support is per loader and exact Minecraft version;
- the same-jar result is preferred but never fabricated;
- only stable Minecraft releases enter the required matrix;
- publication and live-server changes require explicit owner authorization.

Exit: the tracker matches this document and no issue describes 26.1 or 26.1.1
as already supported.

Implemented: GitHub epic
[#168](https://github.com/Delaser/RingWorld/issues/168) links the bounded
Phase 1–10 issues. The tracker preserves the no-live-world and explicit-owner-
authorization boundaries.

### Phase 1 — pinned version manifest

Add a machine-readable manifest, schema, and pure validator for the initial
six cells. The manifest separates:

- Minecraft identity and Java generation;
- loader/build-plugin/API versions and immutable download checksums;
- shared RingWorld artifact identity and public host version identity;
- exact test profiles, timeouts, ports, and allowed parallelism;
- expected game-version tags on Modrinth and CurseForge;
- current qualification and publication state.

The validator must reject floating dependencies, duplicate cells, unsupported
release types, missing checksums, shared paths or ports, and a published state
without immutable evidence.

Exit: deterministic tests cover valid 26.1.x data and every fail-closed rule.

Implemented: [`../config/minecraft-version-matrix.json`](../config/minecraft-version-matrix.json)
contains the initial six cells. The 26.1.2 cells retain exact published
artifact evidence. Exact Fabric inputs are pinned for 26.1 and 26.1.1. The
only official NeoForge runtimes for those Minecraft patches are beta builds,
so they are pinned only as trial inputs and cannot become support claims until
the complete gates pass. All four earlier-patch cells remain `pending`.
Published state is host-specific: a host is counted only when its downloaded
file hash matches the immutable artifact. Submission, review, and baking are
recorded without being described as publication.
Each cell additionally pins the official server-runtime installer and SHA-256.
Fabric uses the exact Fabric Installer jar; NeoForge uses the installer jar
matching that cell's NeoForge coordinate. Build plugin, Loader, and universal
jars must not be substituted for these external-runtime inputs.
Validate with:

```sh
python3 scripts/validate_minecraft_version_matrix.py
python3 -m unittest scripts/test_validate_minecraft_version_matrix.py
```

### Phase 2 — version-aware build layout

Parameterize Minecraft and loader inputs without spreading version checks
through shared topology code. Keep geometry, persistence, protocol, worldgen,
render math, and tests common. Put genuine Minecraft ABI differences behind a
narrow version-owned source set or adapter.

Per-cell source builds are ABI diagnostics. They do not prove a same-jar
runtime claim because Loom and ModDevGradle launch development source sets.
For each cell:

1. resolve only its pinned dependencies;
2. compile common, client, Fabric, and NeoForge boundaries as applicable;
3. verify every mixin target and injection descriptor;
4. validate shader includes, payload types, saved-data codecs, and metadata;
5. build into an isolated output directory;
6. record the runtime jar SHA-256 and shared-contract fingerprint.

First attempt the current 26.1.2 source unchanged on 26.1.1 and 26.1. Where
it fails, use the smallest version adapter. Do not fork topology or gameplay
logic merely to satisfy a build.

If one jar per loader across 26.1.x remains viable, build that frozen candidate
once against the oldest supported ABI (26.1), then copy the untouched jar into
external production-style loader runtimes for 26.1, 26.1.1, and 26.1.2. Every
runtime gate must record the loaded jar path and exact candidate hash. A
per-cell rebuild can reveal ABI failures but can never substitute for this
same-file evidence.

Exit: all six cells compile and package, or each failed cell has a precise ABI
report and remains unsupported.

Current checkpoint: paired opt-in `ringQualificationRoot` and
`ringQualificationCell` properties confine both loader build outputs, declared
game directories, fixture preparers, and verifiers to one disposable cell
below `dist/qualification`. Qualification multiplayer, raid, and smoke ports
are independently routed. Omitting the pair preserves historical developer
paths. This is an isolation foundation, not the Phase 2 exit: script-owned
fixtures and the external production-runtime adapter remain outstanding.
The raid-seam preparation script and worldgen matrix runner now accept an
explicit qualification cell root below `dist/qualification`; their historic
paths remain the default. In qualification mode they derive all destructive
fixture paths below `<cell>/run` and reject traversal or user/live paths.
As an initial ABI diagnostic, the unchanged common source now compiles and
packages for Fabric 26.1 and 26.1.1 and the pinned NeoForge beta trials for
26.1 and 26.1.1. Each isolated cell passes 337 tests and renders target-specific
loader metadata. This did not promote the cells at that earlier checkpoint;
the later Phase 3 Fabric triplet now supplies external runtime and same-file
evidence, while NeoForge remains pending.

### Phase 3 — quick qualification orchestrator

Implement the planned `run_minecraft_qualification.py` interface with:

- `--tier quick`, explicit cell selection, and `--all-supported`;
- exclusive per-profile locks, PID-aware stale-lock recovery, and unique ports;
- isolated Gradle homes, runs, caches, logs, worlds, and output directories;
- bounded subprocesses and terminal `PASS`, `FAIL`, or `INCOMPLETE` results;
- resumable orchestration without reusing mutable runtime state as evidence;
- JSON summary plus a human-readable matrix report.

Quick qualification runs the complete unit/build suite, jar/metadata/licence
inspection, shared-contract comparison, mixin/ABI audit, and a fresh dedicated
server boot/clean stop for every selected cell. Runtime compatibility uses the
frozen jar installed into an isolated production-style loader profile, never a
Gradle development source-set run.

Exit: one command produces a fail-closed six-cell quick report on a clean
checkout, and deliberate corruptions are rejected by tests.

Fabric's refreshed loader-side Phase 3 run is
`20260813T072608Z-b7c68e555818`: 26.1, 26.1.1, and 26.1.2 all passed with the
same frozen jar SHA-256
`d7a66942e275fb3fab9386230293bb5fee21adaa3f5eeecfc61b9f8a205c8296`.
NeoForge reached the same refreshed exit in run
`20260813T080722Z-377cfb994c93`: 26.1, 26.1.1, and 26.1.2 passed with one
unchanged frozen jar SHA-256
`53558ed53bfe73e856710b8fafe60cf81e353e1fde19d43782ebd2f7843d7314`.
These runs are quick dedicated-server evidence, not the Phase 4
gameplay/rendering matrix or a publication decision.

Current checkpoint: the runner and pure model implement selection, safe path
and command planning, pinned download plans, lock/port policy, and
deterministic reports. Dry-run stays write-free and `INCOMPLETE`. A non-dry
quick run now executes the reviewed isolated build/unit command, then requires
exactly one runtime jar under `<cell>/build/<loader>/libs`. It strict-checks
the loader metadata, exact canonical MPL file/declaration, diagnostic build identity, and a
computed SHA-256 before recording the artifact in immutable cell evidence.
That per-cell diagnostic result is not a same-file frozen-candidate claim.

When and only when all three 26.1.x cells for one loader are selected, the
runner now performs a bounded frozen-candidate preflight. It starts one
separate synthetic build cell from that loader's `26.1` inputs, supplies only
the reviewed closed qualification metadata ranges, and writes below the same
disposable qualification run. It accepts one direct runtime JAR plus at most
its canonical Gradle `-sources.jar` sibling, retains an immutable candidate at
`frozen-candidates/<loader>/`, and re-inspects the retained file for exact
oldest-ABI identity, approved ranges, MPL metadata, and the canonical embedded
RingWorld licence. Each complete-triplet `SHARED_CONTRACT` result cites that
same retained pathname and SHA-256. A partial loader selection does not build
a candidate and remains explicitly `INCOMPLETE`; three per-cell diagnostic
builds can never be substituted for this shared file. A complete triplet now
installs the external-runtime adapter, but no triplet has completed the real
runtime matrix yet, so this remains preparation rather than a broader support
or release claim.

If that complete-triplet frozen preflight fails, the runner fails fast before
any per-cell diagnostic build or artifact inspection. It still writes immutable
cell and matrix reports: the first applicable cell records the concrete
`SHARED_CONTRACT` failure, its earlier diagnostic phases are marked
`FROZEN_PREFLIGHT_ABORTED`, and every later selected cell is marked
`CELL_ABORTED_AFTER_FAILURE`. This optimization never applies to a partial
triplet, whose diagnostics remain useful ABI evidence.

The serial runner now executes its reviewed build/unit and per-cell diagnostic
artifact adapters by default. Before process work it requires a clean source tree including no
untracked files, a full HEAD equal to its upstream, the reviewed public
origin, Java 25, and hashes of the manifest and Gradle wrapper. Each cell gets
its own `GRADLE_USER_HOME`, `--no-daemon`, `--max-workers=1`, held OS lock, fresh output root, and
immutable report. The shared-contract adapter is available only after the
complete-triplet frozen preflight; the external runtime adapter is installed
only under that same complete-triplet/provenance condition.
The Gradle 9.5.1 wrapper distribution is pinned with Gradle's published
binary ZIP SHA-256, so a fresh isolated cell verifies the downloaded tool
before executing it.

The optional `--gradle-distribution-zip /absolute/external/gradle-9.5.1-bin.zip`
input may seed that exact ZIP into each disposable Gradle home. It is accepted
only as a non-symlinked regular file outside the repository and operator home,
is rehashed against the checked-in wrapper checksum at every Gradle boundary,
and never creates an extraction or `.ok` marker. Gradle still performs its
normal pinned wrapper verification.

An operator may optionally provide
`--gradle-dependency-cache /absolute/worker-provisioned-cache` to reuse a
Gradle read-only dependency-cache input across otherwise isolated builds. The path
must already exist, be a directory with no symlink component, and remain
outside the checkout, `dist/`, disposable cell/build/run state, and operator
home. The runner passes it only as `GRADLE_RO_DEP_CACHE`; every diagnostic and
frozen-candidate command still receives its own `GRADLE_USER_HOME`. Cache use
is recorded as non-authoritative acceleration in input/frozen evidence. It
does not enable `--offline` or any other offline qualification mode: pinned
inputs must still resolve and qualify normally. The operator is responsible
for provisioning this path from a compatible Gradle `caches/modules-2`
directory without lock or cleanup files and for preventing writes while a
qualification run reads it; the runner revalidates the path immediately at
each Gradle command boundary but does not treat cache contents as trusted.

The separately tested external runtime executor now implements the planned
pinned/no-redirect downloads, official installer, installer-owned Mojang
server hash, exact mod inventory, loopback port, loader/RingWorld/ready marker,
fatal-output, interactive stop, save, and clean-exit controls.
Runner provenance normalizes the real `java -version` output to its Java 25
version token before strict terminal validation; the full raw toolchain line
remains in the ordinary source-provenance evidence.
The installed server is revalidated with the manifest's declared algorithm
(the official Mojang entries currently use SHA-1); its strict runtime inventory
also records a separate SHA-256 digest.
The installer is bound to the reviewed command path and checksum; terminal
validation then uses that reviewed loader-specific name instead of a
hard-coded loader-neutral label. The strict schema independently compares the
name, URL, SHA-256 algorithm, and value with the canonical manifest cell, with
pure Fabric and NeoForge acceptance/mismatch coverage.
The executor creates one validated empty disposable runtime root before the
official installer; this is required by Fabric Installer 1.1.1 and remains the
same contained boundary for NeoForge.
A strict pure terminal schema defines the additional provenance, installed-runtime
inventory, log hashes, candidate identity, ordered markers, and same-file
group evidence required for `PASS`; its structural adapter rejects an
executor result that lacks any of those independent records. Its bounded phase
bridge now uses the runner's single-owner cell lock and runs the unchanged
frozen candidate only in selected complete loader triplets. The bridge
converts nested reviewed manifest identities, immutable
installer/server logs, exact installed inventories, and timestamped semantic
markers into the strict schema, but deliberately reports `INCOMPLETE` if the
pre-run provenance, frozen-candidate inspection, or all-three-cell same-file
proof is absent. A runner may lend its live cell lock only through the exact
lock object, path, and run ID; the external executor verifies that capability
before skipping its normal standalone acquisition. The bridge also re-opens
and fully validates the retained frozen jar before any installer, download,
or runtime activity. The default runner now
passes that capability for a complete loader triplet, but until the six real
runs exist no quick cell is qualified. A
passing smoke phase first exclusive-creates a schema-validated raw
`strict-terminal-evidence.json` below its cell evidence directory; this is
separate from the ordinary scheduler report and carries a SHA-256 reference.

The `Qualification static guard` GitHub Actions workflow runs the pure matrix,
range, frozen-candidate, evidence, runner, executor, and external-runtime-plan
tests on both Ubuntu and Windows. The Windows leg exercises the real Windows
file-lock and space/Unicode path backend. Its one Python command uses the
single cross-platform `PYTHONPATH=scripts` import root. It deliberately performs **no**
Gradle build, Minecraft launch, installer invocation, runtime download,
network request, credential use, package creation, or publication. A green
static guard proves only that the qualification tooling contracts remain
portable; it is never runtime, same-file, or release evidence.

Qualification-only Fabric and NeoForge candidates have also completed a real
Java 25 build from the 26.1 ABI using reviewed closed 26.1–26.1.2 metadata.
Normal resource generation remains exact to the existing 26.1.2 release. Pure
range checks cover all six manifest targets, but this is not same-file runtime
evidence and does not change support status.

### Phase 4 — nightly runtime matrix

Wrap the existing qualified fixtures rather than creating parallel test
implementations. Adapt them to drive isolated production-style profiles with
the exact candidate jar. For every passing cell, automate:

- new-world creation, settings persistence, reload, and immutable dimensions;
- cardinal/seam worldgen, structures, terrain mapping, portals, and rim bounds;
- Atlas generation, pause/resume/recovery, completion, revision, and cache
  identity;
- integrated-client login, resources, shaders, current settings handshake,
  and normal disconnect;
- seam travel, block interaction, stateful blocks, combat, vehicles, beds,
  death/respawn, maps/compasses, and reconnect;
- Overworld/Nether/End lifecycle and normalized portal destinations;
- loader-parity checks on shared settings, protocol, mixins, and shaders.

Use safe-small geometry for breadth and bounded routine cost. Retain one
production-size scheduled profile for scale-sensitive Atlas and rendering
behavior.

Exit: the nightly report is repeatable, isolates failures to a cell and phase,
and cannot touch a live server or a user's normal client/world directories.

Current Phase 4 foundation: `minecraft_nightly_qualification_model.py` is a
pure, non-executing fixture contract. For one quick-qualified cell it fixes
the order, isolated cell-relative runtime/world/evidence paths, unique
per-fixture ports, timeouts, terminal markers, and required outputs for the
existing creation/reload, worldgen, Atlas, UI, client, multiplayer, raid,
map/compass, lifecycle, curved-object, and production-projection fixtures.
It accepts only an absolute, hash-identified frozen candidate, a hash-identified
quick terminal record, and a separate immutable production-world input for
both lifecycle/portal and final production-render fixtures. The first item is
the existing creation-settings UI capture; it does not overclaim world reload
or persistence. Server worlds are rooted at each fixture runtime's `world`
child. Fixtures never share state, except that Atlas prewarm recovery may
restart only its own just-created world after validating an interrupted
checkpoint. The model does not read, copy, create, or run any input. A concrete
external-candidate slice now exists for Atlas recovery: it assembles an
official isolated runtime, revalidates the frozen candidate and strict quick
record, waits for durable partial Atlas bytes before stopping stage one, and
requires byte-identical resume, growth, and a self-halting complete stage two.
Its process and persistence tests use synthetic local children/data and do not
establish a Minecraft runtime PASS. The companion pure
`minecraft_atlas_recovery_qualification.py` contract now defines the first
runtime slice precisely: one genuine partial schema-2 `INTERRUPTED` report,
then a complete clean restart of the same disposable world. It binds the raw
reports to separately inspected persisted settings and Atlas headers/files,
including mapping 4, 2,048x416 geometry, wall height 160, stable world/layout
identity and Atlas path, exact totals, hashes, and ordered stage markers. It
does not by itself establish a nightly PASS.
The bounded persistence parser now decodes the real dimension-owned settings
NBT and Atlas-v6 file independently, derives the Java-compatible layout and
Atlas identities, counts durable cells and complete chunks, and binds an exact
pre-restart checkpoint hash. This closes the report-only evidence gap. The
Atlas recovery fixture now passes all six 26.1.x cells using one exact frozen
jar per loader. Fabric runs are `20260813T091340Z-0f6a75a06e36` (26.1),
`20260813T084030Z-a3030342d49c` (26.1.1), and
`20260813T084918Z-2a61b8523682` (26.1.2). NeoForge runs are
`20260813T092207Z-56b8d1593d37` (26.1),
`20260813T085803Z-abc3ee37973d` (26.1.1), and
`20260813T090427Z-21cef9b5920b` (26.1.2). Every run proves a durable partial
checkpoint, exact-byte resume, complete 13,312-cell Atlas, stable mapping-4
identity, clean exits, and schema-2 `COMPLETE`. The remaining Phase 4 client,
gameplay, lifecycle, and rendering fixtures are still pending.
The operator entry point is intentionally explicit:

```sh
python3 scripts/run_atlas_recovery_qualification.py \
  --cell 26.1-fabric \
  --quick-run-id 20260813T072608Z-b7c68e555818
```

Use the matching NeoForge quick run ID for a NeoForge cell. The command refuses
a dirty/unpushed checkout, a mismatched quick record or frozen jar, and any
pre-existing destination. It creates only a new ignored qualification run.

The second concrete external slice is the existing worldgen/structure matrix,
now driven without Gradle or a development classpath. Its pure plan and
contract plus a bounded process runner and official-runtime executor cover
four ordered stages: production fresh/reload in one world, a deliberate
seam-crossing safe-small seed, and the saved terminal monument-policy seed.
The executor installs three fresh runtimes, reuses only the production world
for its reload, injects the existing Java fixture properties at the real
launcher boundary, independently decodes saved settings, and parses exactly
one matrix/monument/PASS record per stage. It cannot pass without mapping 4,
format 3, all fourteen biome families, nonzero caves/ores/logs/structures/
references/loot, a seam-crossing structure, both monument policy outcomes,
and clean self-halted exits. The slice passes all six cells. Fabric runs are
`20260813T073235Z-1e16c008e584` (26.1),
`20260813T083349Z-0cdcffa76005` (26.1.1), and
`20260813T083518Z-b942314e7e0d` (26.1.2). NeoForge runs are
`20260813T082128Z-c2fae65dec2c` (26.1),
`20260813T083644Z-e7ed932a1499` (26.1.1), and
`20260813T083822Z-03549862d588` (26.1.2). Each loader used one unchanged
frozen jar across all three patch runtimes.

```sh
python3 scripts/run_worldgen_qualification.py \
  --cell 26.1-fabric \
  --quick-run-id 20260813T072608Z-b7c68e555818
```

Use quick run `20260813T080722Z-377cfb994c93` for NeoForge. The retained
candidate exists once below that quick run's 26.1 loader root; selecting a
26.1.1 or 26.1.2 cell uses its own strict quick record while resolving that
single reviewed candidate. Missing or duplicate candidate roots fail closed.

The first graphical-client slice reuses the existing menu-only creation UI
fixture through a fresh production-style Prism profile. It verifies one
reviewed Prism 11.0.3 macOS archive, the exact retained RingWorld jar, Java 25,
and Fabric API when applicable; creates no account record and launches with an
explicit offline fixture name; and requires the existing thirteen screenshots,
PASS marker, clean self-halt, bounded logs, exact mod inventory, and no
`level.dat`. Its pure/fake-process tests do not qualify a real client. The
operator command is intentionally explicit and accepts no existing Prism data
root:

```sh
python3 scripts/run_creation_ui_qualification.py \
  --cell 26.1-fabric \
  --quick-run-id 20260813T072608Z-b7c68e555818 \
  --prism-archive /absolute/path/PrismLauncher-macOS-11.0.3.zip \
  --java /absolute/path/to/java-25/bin/java
```

The archive must have SHA-256
`b8e06ef55ec78fceddfa9f4270b3d4d93f2606b83f70ad6a2c6dde90f2b65408`.
NeoForge uses its matching refreshed quick run. Real cross-version graphical
execution through a completely fresh Prism data root is blocked before
Minecraft starts: official Prism requires a valid Microsoft account during
its setup wizard even when the requested launch mode is offline. Qualification
must never copy a user's normal `accounts.json` or tokens. Keep this path as an
explicit authenticated-disposable-profile or owner release gate.

The automated source-ABI alternative is
`scripts/run_gradle_creation_ui_qualification.py`. It drives the same real
Minecraft client fixture using the exact manifest Minecraft/loader/API pins
inside a new qualification cell, requires all thirteen PNG captures and the
fixture PASS marker, rejects any created `level.dat`, and writes immutable
terminal evidence. Its report explicitly says it is not a production launcher
or frozen-candidate-jar test:

```sh
python3 scripts/run_gradle_creation_ui_qualification.py --cell 26.1-fabric
```

The runner accepts the same optional reviewed acceleration inputs as quick
qualification: `--gradle-dependency-cache` for an existing external read-only
modules cache and `--gradle-distribution-zip` for the exact wrapper-pinned
Gradle archive. Neither changes evidence identity or enables offline mode.

This is the repeatable cross-version GUI ABI gate. Packaged-client login stays
separate so the matrix cannot turn a credential workaround into false release
evidence.

The source-ABI gate passes all six manifest cells from clean pushed commit
`077615493e0f8a7b58e92aec51e9ec83535cb08f`, with thirteen captures and no
created world in every cell. Fabric run IDs are
`20260813T101541Z-e87eced07877`, `20260813T101904Z-f32dbc8917e9`, and
`20260813T102213Z-d33b1a707c5b` for 26.1 through 26.1.2. NeoForge run IDs are
`20260813T102535Z-618362c64a62`, `20260813T105844Z-fdefa2c044f5`, and
`20260813T110726Z-2e96621d7486`. This closes the creation/settings UI ABI
slice only; it does not convert Gradle-launched classes into frozen-candidate
or production-launcher evidence.

The next source-ABI client slice reuses the integrated Atlas map/control
fixture through `scripts/run_gradle_atlas_ui_qualification.py`. It keeps the
same clean-source, exact dependency, Gradle user/project cache, and optional
reviewed acceleration boundaries, but requires one disposable safe-small
world, all eleven Atlas UI captures, complete generation, and the fixture's
ordered placement/removal revision probe:

```sh
python3 scripts/run_gradle_atlas_ui_qualification.py --cell 26.1-fabric
```

The source and contract are implemented; no real cross-version Atlas UI PASS
is claimed until each cell's immutable terminal record exists.
The fixture's build-label assertion is selected independently by Gradle from
the active `release_label` and `mod_version`. This retains the published 1.0
identity check for ordinary development runs while requiring each exact
qualification cell to display its diagnostic build identity.

All qualification Gradle commands set both a disposable `GRADLE_USER_HOME`
and a cell-contained `--project-cache-dir`. The latter is mandatory for Loom:
without it, an otherwise isolated client launch reads its launch configuration
from the checkout's shared `.gradle` directory.

### Phase 5 — world-upgrade qualification

Create immutable source-world fixtures for each supported starting version.
Test copies only:

- 26.1 to 26.1.1 and 26.1.2;
- 26.1.1 to 26.1.2;
- same-version save/reopen for every cell;
- legacy RingWorld saved-settings and Atlas migrations already supported by
  the codebase.

Verify canonical blocks/entities, inventories, structures, portal links,
settings identity, terrain mapping, Atlas invalidation/rebuild, and player
position. Never run reverse-version or downgrade tests against the same save.

Exit: every advertised forward path has a passing copied-world report; all
unsupported directions are documented.

### Phase 6 — release qualification

Freeze one candidate commit and run the release tier without code or docs
changing underneath it:

- production worldgen and structure matrix on both loaders;
- production unattended Atlas completion and recovery;
- production projection, visual parity, natural seam motion, both rims,
  weather/exposure, lifecycle, and same-process layout switching;
- full dedicated two-client multiplayer matrix;
- creation UI and Atlas UI fixtures at supported GUI scales;
- real packaged Windows and macOS graphical smokes;
- clean dedicated-server package smokes;
- current unit/build/staging and distribution tests.

If one 26.1.x jar per loader is the goal, the candidate jar for that loader is
built once against 26.1 and must have one identical SHA-256 in all three
external version-cell reports. Development/source-set launches are supporting
ABI diagnostics only. If the frozen file cannot pass every cell, publish
distinct version-specific artifacts and say so clearly.

Exit: immutable release evidence is complete and the owner records a go/no-go.

### Phase 7 — candidate staging and documentation freeze

Extend the existing fail-closed staging workflow rather than bypassing it.
Staging must consume the qualification manifest and produce, per loader:

- exactly one runtime jar for each required artifact identity;
- SHA-256/SHA-512 manifests and archive inventory;
- exact source revision and MPL-2.0 corresponding-source link;
- generated Modrinth version metadata and changelog;
- generated CurseForge upload worksheet and changelog;
- the exact supported Minecraft version list proven by evidence;
- dependency relations: Fabric API required for Fabric, none invented for
  NeoForge;
- rollback metadata identifying the previous hosted release.

Choose the public RingWorld version at freeze. A broader tested compatibility
range is a user-visible capability; prefer a new minor version if runtime or
support behavior changed, and a patch version only for metadata/tooling-only
corrections. Never relabel an old binary outside staging.

Update README, installation, compatibility, operations, testing, release,
showcase, and host copy together. Explain progressive Atlas generation and
its layered placeholder without promising a fixed completion time.

Exit: a clean pushed commit can be restaged reproducibly, and an independent
reviewer returns a release go decision for the exact hashes.

### Phase 8 — Modrinth publication

Modrinth's official version API accepts one or more jar files plus version
name/number, changelog, dependencies, game versions, loaders, release type,
environment, and listed/draft status. RingWorld should continue to publish one
standalone runtime jar per loader version rather than a bundled Minecraft
client.

Add an optional Modrinth publisher only after staging is trustworthy:

- dry-run is the default and writes the exact multipart/metadata plan;
- credentials come only from an environment variable or OS credential store;
- require an explicit `--execute`, exact stage manifest, clean pushed source,
  expected project ID, and a fresh owner authorization marker;
- create a draft or unlisted version first when the host permits it;
- never edit/delete prior releases automatically;
- redact tokens and authorization headers from all logs.

For each loader, verify before listing:

- correct project, loader, game-version tags, client-and-server environment,
  release channel, primary jar, and public version number;
- Fabric API is a required project dependency only for Fabric;
- changelog contains the immutable corresponding-source URL;
- project licence, source, issues, installation, and compatibility links are
  current.

After publication, download the CDN file without using the build output,
compare its hashes, inspect its metadata/licence, and record the returned
version/file IDs. The official API currently requires an authenticated
version-create request and accepts `.jar` files with explicit loader and game
version lists; implement against the current API rather than a remembered
payload shape.

Exit: both hosted files are downloadable, independently hash-verified, and
their listing metadata matches the frozen evidence.

### Phase 9 — CurseForge publication

CurseForge provides an official author Upload API. Implement a separate
dry-run-first publisher against the Minecraft CurseForge author endpoint, not
the consumer API or CurseForge-for-Studios API. Authentication uses a project
author token in the `X-Api-Token` header. Never put that token in a query
string, repository file, Gradle property, generated stage, log, shell history,
or distributable archive even though the API also supports a query parameter.

The publisher must retrieve the current game-version/dependency vocabulary,
then submit a multipart `POST` to
`/api/projects/{projectId}/upload-file` with exactly:

- `metadata`: generated JSON containing changelog, Markdown changelog type,
  display name, proven game-version IDs or names, release type, optional
  manual-release hold, and project relations;
- `file`: the one staged runtime jar whose hash matches the release manifest.

The official endpoint returns the new file ID. Record it immediately in the
release evidence. The publisher must require:

- Minecraft Mod project `1645598`;
- one separate file for Fabric and one for NeoForge;
- Client and Server compatibility and only the proven game-version tags;
- Release/Beta/Alpha channel matching the approved candidate;
- Fabric API project `306612` as required only on the Fabric file;
- immutable source URL and current changelog;
- no Minecraft jar, Prism bundle, server overlay, source/dev jar, or combined
  loader archive.

Use the same controls as the Modrinth publisher:

- dry-run is the default and emits a redacted request plan;
- `--execute` requires a clean pushed source, exact staged manifest, expected
  project ID, current author token from an environment variable or OS
  credential store, and fresh owner authorization;
- validate version IDs/names using the official game-versions endpoint rather
  than retaining unexplained numeric IDs;
- send Fabric API as a `requiredDependency` relation only for Fabric;
- set `isMarkedForManualRelease` when the owner wants approval without
  immediate publication;
- never update, archive, or delete an existing file automatically;
- reject an upload if the returned file ID, response type, or requested
  metadata is missing or inconsistent.

CurseForge places new files under review and may request changes. Do not
resubmit while a file is under manual review unless the host asks for it. The
API automates submission, not moderation or approval. A
Release file normally syncs to the client by default; Alpha files generally
require users to opt in, so the selected channel must match the intended
audience.

After approval, download each hosted file, compare hashes, run the same
distribution verifier, and record file IDs/status. Keep the previous approved
release live until the replacement is confirmed.

Exit: both loader files are approved/downloadable, hash-verified, and visible
with correct dependency and version metadata.

### Phase 10 — post-publication closure

After both hosts are verified:

1. tag the exact source commit and push the tag;
2. publish or finalize the GitHub release notes and checksums;
3. update the showcase site to prefer host links, not mutable direct jars;
4. test fresh installs from each host rather than local stages;
5. update the demo server only under separate owner authorization and preserve
   its world plus the previous executable as rollback;
6. monitor crash reports, host moderation messages, and compatibility issues;
7. close the release epic with links to evidence, hosted IDs, and hashes;
8. archive generated secrets-free evidence and delete transient credentials.

If a post-release blocker appears, stop promoting the new version, archive or
unlist only the affected hosted file, document the reason, and leave the last
known-good release available.

## Critical path and safe parallelism

The critical path is:

```text
manifest -> version-aware builds -> quick matrix -> nightly matrix
-> upgrade matrix -> frozen release matrix -> staging -> owner approval
-> Modrinth/CurseForge submission -> hosted-file verification
```

Safe parallel work includes pure manifest/tooling tests, host-copy drafting,
and loader-specific headless jobs with isolated files and ports. Do not run
two graphical fixtures on one GPU, stage while the source tree is changing,
run multiple profiles against one directory/port, publish before owner
approval, or mutate the live demo server as part of qualification.

## Known blockers and fallback decisions

- If a loader has no compatible release for a Minecraft patch, that cell
  stays unsupported; the other loader may proceed only with a loader-specific
  claim.
- If mixin or shader ABI differs within 26.1.x, prefer a narrow adapter. If the
  resulting binary cannot remain identical, publish patch-specific jars.
- If a host does not expose a required Minecraft version tag, do not use an
  inaccurate nearby tag. Retain the verified artifact and publish when the tag
  exists or document the host-specific limitation.
- If graphical infrastructure is unavailable, classify the release result as
  incomplete rather than substituting headless evidence.
- If a host is still reviewing a file, report that state; do not claim that
  the file is available in its launcher/client until it is approved.

## Host references

- [Modrinth: create a version](https://docs.modrinth.com/api/operations/createversion/)
- [Modrinth API overview and authentication](https://docs.modrinth.com/api/)
- [CurseForge author Upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api)
- [CurseForge: file types, release channels, dependencies, and uploads](https://support.curseforge.com/en/support/solutions/articles/9000197242-file-project-types-and-additional-fields)
- [CurseForge: project and file statuses](https://support.curseforge.com/support/solutions/articles/9000197905-project-statuses-101)
- [CurseForge: project submission guidance](https://support.curseforge.com/support/solutions/articles/9000199552-project-submission-guide-and-tips)
