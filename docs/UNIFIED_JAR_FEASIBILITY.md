# Unified Fabric + NeoForge JAR feasibility

## Decision

A single physical RingWorld JAR for Fabric and NeoForge is **technically
feasible and worth a bounded prototype**. It is not yet safer or simpler than
the current two-artifact release, so separate loader JARs remain authoritative
until the merged candidate passes both complete runtime pipelines and host
installation tests.

“Unified” means one file containing both loader descriptors and both narrow
adapter sets. It does not mean running Fabric and NeoForge in one process, and
it does not remove the loader-specific code. It would still be one JAR per
Minecraft ABI line: the 26.1.x and 26.2 builds cannot be fused into one
cross-version binary merely because each loader pair can be fused.

## Current artifact evidence

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
- Current shared compiled output is byte-identical.
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
   attached to the version. CurseForge's public file index models one loader
   value. Both host applications therefore need an unlisted clean-install
   experiment before one public record can be claimed.
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
