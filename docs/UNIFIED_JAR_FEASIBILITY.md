# Unified Fabric + NeoForge JAR feasibility

## Decision

A single physical RingWorld JAR for Fabric and NeoForge is **technically
feasible**. Three current 1.3 prototypes pass both loaders' dedicated-server
and automatic client-render checks. Production staging, complete qualification,
settled visual comparisons and host installation checks remain open, so
separate loader JARs remain the authoritative published release.

“Unified” means one file containing both loader descriptors and both narrow
adapter sets. It does not mean running Fabric and NeoForge in one process, and
it does not remove the loader-specific code. It would still be one JAR per
Minecraft ABI line: the 26.1.x and 26.2 builds cannot be fused into one
cross-version binary merely because each loader pair can be fused.

## 1.3 exploration — 2026-10-01

The current target is **three exported files instead of six**: one each for
26.1.x, 26.2, and 26.3, with Fabric and NeoForge sharing the exact same bytes
within each line. Both intermediate loader builds remain useful; no new
framework, bootstrap loader, or source restructuring is needed.

Work is isolated on `codex/unified-jar-exploration`. All outputs are explicitly
named `PROTOTYPE`; the published 1.3 artifacts and ordinary release validators
remain unchanged.

### Archive results

The exact published 1.3 loader pairs for 26.1.x and 26.2 fuse directly. The
published 26.3 pair fails the strict comparison on 42 shared Mixin classes.
Their executable instructions and signatures match, but Fabric's compile API
encodes several injection annotation members as arrays, while NeoForge's
older API encodes them as scalar values. Silently selecting either set of
classes would bypass the merger's safety contract.

The opt-in `-PringUnifiedJarPrototype=true` flag instead pins **only compile
classpaths** to `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7` in both 26.3 build
modules. The older lines and each loader's runtime Mixin remain unchanged.
With this flag, both 26.3 builds pass their 451 tests each and all shared non-manifest entries are
byte-identical. The existing strict merger then succeeds without modification.
Its five archive-policy tests also pass.

An additional comparison against published 26.3 finds no changed NeoForge
class files and no added or removed files in either loader. Fabric differs only
on the 42 annotation-bearing classes and its descriptor; NeoForge differs only
on its descriptor. The fresh descriptors use the local build's default exact
Minecraft predicates rather than the staged release's bounded predicates.

| Minecraft line | Inputs | Unified SHA-256 | Archive |
| --- | --- | --- | --- |
| 26.1.x | Published 1.3 pair | `ac2907e4bdbf8d7159a50b717e3f19218fa18bce10f2a428a84f0d4b001a277f` | PASS |
| 26.2 | Published 1.3 pair | `fc78770c9670cfdd7f9df6b4a18a2a82f0b24882f81abe284d99dce924a4f824` | PASS |
| 26.3 | Fresh 1.3 source builds with common compile API | `9044e86dd8964af2da3f704b9ec89b1ce3287cb0bd526743be918e6fb6f5043b` | PASS |

The first two prototypes contain 432 entries; the fresh 26.3 prototype has
435. Each preserves Fabric's manifest, both loader descriptors/adapters, and
the MPL licence. Fusion reports remain `release_acceptance: false`.

### Runtime evidence

All six dedicated-server checks pass. Runtime libraries were cloned from
retained qualification installations into disposable directories; worlds were
created fresh at 2,048×416 blocks, with Atlas pre-generation disabled. Each
server loaded RingWorld, reached `Done`, ticked for 15 seconds, accepted
`save-all flush` and `stop`, saved, and exited with code zero. Fabric has Fabric
API installed; NeoForge's `mods/` contains only the unified RingWorld JAR.

| Minecraft | Fabric server | NeoForge server | Fabric render client | NeoForge render client |
| --- | --- | --- | --- | --- |
| 26.1.2 | PASS | PASS | PASS (41 s) | PASS (58 s) |
| 26.2 | PASS | PASS | PASS (144 s) | PASS (93 s) |
| 26.3 | PASS | PASS | PASS (93 s) | PASS (117 s) |

All six clients loaded the exact per-line prototype from `mods/`, rendered
the complete Atlas, captured tangent, live/Atlas handoff and radial views,
then saved and exited normally. Each installed JAR's SHA-256 matches its
fusion report. Client NeoForge versions are the release build baselines:
26.1.2.87, 26.2.0.69 and 26.3.0.7-beta. Fabric client versions match the
server versions above. The copied source world was generated on 26.1.2;
newer Minecraft clients performed their normal file-fix upgrade on their
disposable copies. NeoForge also logged the expected missing Fabric data-pack
warnings for the copied Fabric world. Neither prevented the captures or save.

**Visual qualification remains open.** The first 26.3 NeoForge handoff capture
was unusually grey and lacked nearby rendered chunks despite an automatic
capture PASS. A control using the exact published NeoForge-only JAR rendered
normal colours. A second run of the unchanged unified SHA above also rendered
normal water/Atlas colours, but nearby chunk coverage still differed between
captures. This does not establish a deterministic merge regression or visual
parity. Cold-start/capture readiness is a candidate explanation, not a proven
cause; compare matched, settled captures during full qualification. Control
and repeat evidence are retained in `baseline-projection-client-smokes.json`
and `recheck-projection-client-smokes.json` under the exploration root. The
published control SHA is
`9779daf3080132eafc45781ac7eadf1bff2136996b435e7e38324725476df83a`.

These are bounded prototype checks, not full release qualification. The
26.1.x artifact was exercised here on 26.1.2 only; 26.1 and 26.1.1 remain
additional release cells. Dedicated NeoForge runtimes use 26.1.2.112,
26.2.0.88, and 26.3.0.37-beta. Fabric uses 0.19.3 on the older lines and
0.19.5 on 26.3. The 26.3 Fabric server passes with runtime Mixin 0.17.4,
demonstrating that the common compile annotation API does not require a runtime
downgrade.

Evidence locations, relative to the repository root:

- `dist/qualification/unified-1.3-exploration/artifacts/`: the three prototypes
  and per-line fusion reports;
- `dist/qualification/unified-1.3-exploration/26.3-build/`: both fresh builds,
  test XML and reports;
- `dist/backlog-runtime/unified-1.3-exploration/<line>/<loader>/`: exact installed
  mod hashes, server logs and `development-smoke.json`;
- `dist/qualification/unified-1.3-exploration/projection-client-smokes.json`:
  all six successful commands, exact prototype hashes and elapsed times;
- `dist/qualification/unified-1.3-exploration/`: local exact-JAR client launch
  scripts, init scripts and client reports. The Atlas fixture ordinarily runs
  source classes; its local init script selects the empty frozen runtime
  source set and installs the retained prototype in `mods/` instead. The
  projection profiles already select the empty runtime source set; their local
  init script installs the exact JAR and caps this disposable client at 30 FPS.

The initial 26.1.2 Fabric Atlas GUI run reached complete Atlas rendering and
proved block-edit revisions, but hit the local 420-second process timeout
during integrated-server disconnect. Its `client-smokes.json` records failure;
it is **not** counted as a full Atlas GUI PASS. Rendering smokes instead open
independent copies of that generated world, capture tangent, live/Atlas
handoff and radial views, save and stop. The timeout and disposable launcher
preparation corrections are separate from mod failures and do not replace
release-suite gates.

### Smallest production path

The bounded archive/server/automatic render-client spike is complete. Production work
remaining:

1. Add an explicit universal artifact mode to staging/inspection, preserving
   strict single-loader validation as the default. Export one fused file per
   line and retain the intermediate loader files internally.
2. Run the complete release suite against the merged hashes on both loaders,
   including 26.1 and 26.1.1, resource reloads and multiplayer.
3. Verify host installation and loader-specific Fabric API handling before
   publishing. A smaller artifact count does not reduce runtime test coverage.

## Historical artifact evidence — 2026-08-31

The 2026-08-31 local 26.1.2 development outputs were compared entry by entry:

| Measurement | Result |
| --- | ---: |
| Fabric files | 345 |
| NeoForge files | 346 |
| Paths shared by both | 335 |
| Byte-identical shared paths | 334 |
| Differing shared paths | 1 |
| Differing path | `META-INF/MANIFEST.MF` |
| Fabric-only paths | 10 |
| NeoForge-only paths | 11 |

All common classes, shaders, resources, mixin configurations, build identity,
and the embedded MPL licence are byte-identical. Fabric-only content is its
descriptor and loader adapters. NeoForge-only content is its descriptor,
loader adapters, early-login mixin, and NeoForge mixin configuration. This is
an unusually clean merge surface.

Fabric's manifest carries Loom split-environment and client-only entry data;
NeoForge's manifest contains only `Manifest-Version`. A prototype should retain
the Fabric manifest and prove that NeoForge accepts it. It must not silently
choose between any later non-identical class or resource.

## First prototype checkpoint

The bounded fuse tool in `scripts/build_unified_jar_prototype.py` now enforces
the archive policy below without changing Gradle, release staging, packages, or
published files. Its five synthetic tests cover deterministic fusion, opposite
metadata, conflicts, missing metadata/licence, duplicate entries, unsafe paths,
signatures, and symlinks.

The real default 26.1.2 development outputs fused successfully:

| Artifact | SHA-256 |
| --- | --- |
| Fabric input | `e4a689f7d4ce0da94b55c963a329c33fa657ebfac147abcdd0743b9bb784977f` |
| NeoForge input | `4782b60000d1d0a7aa21f7e55bdb59236ca2b0ae8ba15684930ef5d259951937` |
| Unified prototype | `699e030aaaea68e34e5181c0e7f63a3cc0a85d1fa0c257119a1036406a36571e` |

The output contains 356 files, both loader descriptors, one Fabric-preserved
manifest, no duplicate paths, and an explicit `release_acceptance: false`
report. That exact hash was installed into independent copies of the retained
26.1.2 Fabric and NeoForge dedicated-server fixtures. Both loaders discovered
RingWorld, opened the copied 16,384×256 world, reached `Done`, accepted normal
`stop`, saved, and exited cleanly. Retained log SHA-256 values are:

- Fabric: `e263b9f916555e06144c4f770dd0fd4c5b0bfdfde98ffe7965a00774793e9d72`;
- NeoForge: `74a2d77b3c17b5496902b4b07ad528989e0194988ed078dd14a63d30446c1557`.

The copied NeoForge world logged an expected saved-mod-version difference
because the disposable world came from 1.1 while this development build still
uses its local 1.0.0 version property. It did not block loading. These two
smokes prove dedicated discovery/classloading only. No graphical client,
fresh-world, multiplayer, package, mixed-loader, or host behavior has been
tested with the merged file.

Repeat the static experiment after building both loader artifacts:

```sh
mkdir -p dist/unified-jar-prototype
VERSION='replace-with-built-mod-version'
python3 scripts/build_unified_jar_prototype.py \
  --fabric-jar "build/libs/ringworld-${VERSION}.jar" \
  --neoforge-jar "neoforge/build/libs/ringworld-neoforge-${VERSION}.jar" \
  --output-jar dist/unified-jar-prototype/ringworld-unified-PROTOTYPE.jar \
  --report dist/unified-jar-prototype/report.json
```

The tool refuses to replace an existing output. Delete or archive an old
ignored prototype deliberately before rerunning it; never point the output at
a reviewed or published artifact.

## Why this can work

- Fabric discovers a root `fabric.mod.json`; its entrypoints name only Fabric
  adapters.
- NeoForge discovers `META-INF/neoforge.mods.toml`; its mod entrypoint and
  mixins name only NeoForge adapters.
- Loader-neutral code already lives in common source trees.
- Platform classes use separate packages or distinct class names.
- Shared compiled output is byte-identical in the retained prototypes; the
  26.3 build needs the common annotation compile API described above.
- Minecraft 26.1+ uses official runtime names, avoiding the historical need to
  merge differently remapped common classes.

The relevant upstream contracts are the
[Fabric metadata specification](https://docs.fabricmc.net/develop/loader/fabric-mod-json),
[NeoForge mod-file specification](https://docs.neoforged.net/docs/gettingstarted/modfiles/),
[Modrinth create-version API](https://docs.modrinth.com/api/operations/createversion/),
and [CurseForge API schema](https://docs.curseforge.com/rest-api/).

## Why it is not a release simplification yet

The download count becomes smaller, but qualification does not. The same bytes
must still be proven independently on Fabric and NeoForge clients, servers,
worlds, renderers, and multiplayer fixtures.

The main risks are:

1. A loader or third-party scanner may inspect dormant classes and try to
   resolve the other loader's API types.
2. Fabric's split-environment manifest must still keep client classes off a
   dedicated server classpath.
3. Fabric requires Fabric API while NeoForge does not. Each JAR descriptor can
   express that distinction, but host dependency metadata may not be
   loader-conditional.
4. Existing verification and staging deliberately reject a JAR with both
   descriptors. The frozen-candidate inspector, licence verifier, Modrinth
   stager, qualified-release stager, and package builder need an explicit
   universal mode—not relaxed ambiguity checks.
5. Modrinth accepts multiple loader names on one version, but dependencies are
   attached to the version. CurseForge's upload API accepts arrays of version
   tags and file-level project relations. Neither documented dependency object
   provides a loader condition. Host application behavior therefore needs a
   clean-install experiment before automatic Fabric API installation on only
   Fabric can be claimed. The presence of a singular loader filter in a public
   query API is not evidence that a file can have only one loader tag. See the
   [CurseForge upload contract](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api)
   and [Modrinth version contract](https://docs.modrinth.com/api/operations/createversion/).
6. A merge plugin or ZIP overlay that silently resolves conflicts could ship
   loader-divergent common code. RingWorld needs a fail-closed merger.

Architectury would help organize a multi-loader source project but normally
still produces separate artifacts. RingWorld already has the useful part of
that architecture, so adopting it is not a prerequisite. Jar-in-Jar and a
bootstrap shim likewise do not solve loader discovery or conditional host
dependencies.

## Recommended prototype

Keep both existing loader builds as intermediate artifacts and add an
experimental, deterministic fuse step:

1. Build and independently verify the Fabric and NeoForge JARs.
2. Compare every shared path. Reject the merge if any path other than the
   reviewed manifest differs.
3. Start with the Fabric JAR, preserving its manifest and split-environment
   attributes.
4. Add only NeoForge-exclusive paths, including its descriptor, platform
   classes, mixin configuration, and early-login mixin.
5. Reject duplicate ZIP entries, signatures, unsafe paths, missing MPL data,
   missing descriptors, unexpected service entries, or nondeterministic
   timestamps/order.
6. Emit an ignored experimental JAR, a path inventory, both input hashes, and
   the merged hash. Do not teach release staging to accept it yet.
7. Launch that exact hash on Fabric client/server and NeoForge client/server.
8. Open the same copied world, complete Atlas handshake/rendering, and stop
   normally on both loaders.

Only after those four smokes pass should the qualification tools gain an
explicit `universal` artifact kind. The current “exactly one descriptor” checks
must remain the default for ordinary loader-specific candidates.

## Acceptance path

### Gate 1 — archive contract

- both descriptors present and valid;
- both platform inventories present;
- one canonical manifest and one MPL licence;
- all shared non-manifest paths byte-identical;
- deterministic output and fail-closed conflict handling;
- Fabric API required only by `fabric.mod.json`;
- no signature or service-loader surprises.

### Gate 2 — bounded runtime spike

- Fabric title screen, fresh world, dedicated startup, and clean stop;
- NeoForge title screen, fresh world, dedicated startup, and clean stop;
- no attempt by either loader to resolve the other loader's classes;
- copied production world opens without format migration or regeneration.

### Gate 3 — exact-candidate qualification

Run the existing worldgen, Atlas, creation UI, projection, lifecycle,
two-client gameplay, raid, package, macOS, and Windows gates on the exact
merged hash. Green intermediate loader JARs do not qualify the merged file.
Mixed-loader client/server connections remain unclaimed unless all four
client/server combinations pass their own matrix.

### Gate 4 — host behavior

- test an unlisted Modrinth version tagged Fabric and NeoForge;
- prove Fabric installs include Fabric API and NeoForge installs do not;
- test CurseForge's loader tags and dependency behavior with an unlisted file;
- if host metadata cannot be conditional, publish two loader-specific version
  records pointing to identical JAR bytes rather than providing a broken
  one-click install;
- retain the last separate-loader candidates as rollback artifacts.

## Recommendation

Proceed with a local fuse-tool prototype in a dedicated issue branch. Do not
replace the current build modules, verifiers, release records, or hosted files
during the spike. The prototype is successful only if it reduces operator and
user artifact complexity without weakening exact-loader dependency handling or
any existing qualification gate.
