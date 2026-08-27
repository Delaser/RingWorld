# Adding a Minecraft version

Each stable Minecraft line has a pinned qualification manifest. The original
`config/minecraft-version-matrix.json` covers 26.1, 26.1.1, and 26.1.2;
`config/minecraft-version-matrix-26.2.json` is the pending 26.2 candidate.
Neither adding a manifest nor compiling changes public support claims.

## Repeatable procedure

1. Copy a manifest for the new stable line. Set its `line`, exact Minecraft
   versions, matching Fabric and NeoForge cells, immutable official download
   URLs/checksums, dependency pins, isolated profiles/ports, and host tags.
   Leave status `pending`; do not copy old release evidence. Pin companion
   loader build dependencies too: Gradle configures both projects even when
   building only one loader. These are build inputs, not installed extra mods.
2. Include every patch between the first and last declared patch. Both loaders
   must have the same Minecraft versions. A new line gets its own manifest,
   not an untested wider range on an existing jar.
3. Validate and preview from a Java 25 checkout:

   ```sh
   python3 scripts/validate_minecraft_version_matrix.py config/minecraft-version-matrix-26.2.json
   python3 scripts/run_minecraft_qualification.py --tier quick --manifest config/minecraft-version-matrix-26.2.json --all --dry-run
   ```

   Dry-run is write-free and intentionally reports `INCOMPLETE` (exit 1).
4. From clean pushed source, run quick qualification by dropping `--dry-run`.
   The runner builds one frozen candidate per loader against the manifest's
   oldest ABI, verifies its identity/range, then tests that exact hash in every
   declared patch. The new line can require code adapters; qualification only
   detects incompatibilities, it does not fix them.
5. Run the existing nightly coordinator with that same manifest, the quick
   run ID, and an independently inventoried complete production world saved
   by the oldest target version or an earlier supported version:

   ```sh
   python3 scripts/run_minecraft_nightly_matrix.py --manifest config/minecraft-version-matrix-26.2.json --quick-run-id RUN_ID --production-world /absolute/path/below/dist/qualification --execute
   ```

   It derives the cell count from the manifest and runs the same ten fixtures
   per cell. No version-specific coordinator edits are needed. Optional
   read-only dependency, official wrapper ZIP, and hash-checked Loom seed
   caches accelerate both loader setups; they never substitute for runtime
   evidence. After a successful worldgen
   child only, the coordinator retains its verified source world at its
   original `run/nightly/02-worldgen-seam-structures/production/runtime/world`
   path for later copied-world forward-upgrade qualification; other disposable
   runtime state is removed.
6. Qualify copied-world upgrades, then complete release equivalence, packaging,
   staging, and explicit owner approval. Publishing is separate from testing.

Release equivalence and staging derive frozen-candidate metadata ranges,
quick-evidence cell counts, and public metadata version labels from the selected
manifest contract. The release descriptor's game-version list must match that
contract exactly; publication still requires separate owner authorization.

## What is derived rather than hard-coded

`scripts/minecraft_support_contract.py` derives the oldest build ABI,
qualification version/label, bounded Minecraft and NeoForge metadata ranges,
same-file cell set, and evidence group from the selected manifest. Regression
tests exercise both 26.2 and a synthetic future stable line without changing
runner code. Historical 26.1.x defaults remain only for legacy API callers and
old evidence; operator paths pass the selected manifest contract explicitly.

The scripts do not yet guarantee support for a different Java generation,
loader versioning scheme, or changed Minecraft ABI. Those are explicit porting
changes, not reasons to duplicate the entire test pipeline. Release-equivalence
and staging support are implemented and static-tested; a 26.2 publication still
requires its real clean frozen-candidate evidence, staging execution, and owner
approval.

Version-owned source APIs live under `src/versions/<oldest-ABI>/main/java` and
`client/java`. Both loader builds use the same selected directories:
`gradle/version-sources.gradle` selects the newest checked-in ABI not newer
than the requested stable game version. Shared sources remain in `src/main`
and `src/client`; narrow density-context, surface-signature, GUI, and GPU
adapters hold unavoidable differences. A future release tries that existing
ABI and must pass compilation/runtime checks; selection is not support proof.

The 26.2 rendering/GUI changes were checked against the
[Fabric port notes](https://fabricmc.net/2026/06/15/262.html) and
[NeoForge migration primer](https://docs.neoforged.net/primer/docs/26.2/),
then confirmed against the pinned game bytecode. Decompiled reference material
stays ignored under `dist/`; it is not contributed or distributed source.

## 26.2 checkpoint — 2026-08-27

- Official Minecraft 26.2 client/server, Fabric Loader 0.19.3, Loom 1.17.20,
  Fabric API 0.158.0+26.2, NeoForge 26.2.0.69, and ModDevGradle 2.0.144 pinned.
- Manifest-derived candidate grouping and focused regression tests implemented.
- Both 26.2 loaders compile and package, with all 338 unit/parameterized cases
  passing per loader. The existing 26.1.2 dual-loader builds also still pass
  all 338 cases after selecting the older source ABI. These are exploratory
  source-build results, not clean frozen-candidate runtime evidence.
- Version adapters cover the changed density/surface, registry fixture, GUI,
  render submission, neighbor-readiness, and GPU APIs. Cached exploratory
  Fabric 26.2 creation UI passes all thirteen captures after fixing duplicate
  inherited `DynamicTransforms` binding; its Atlas UI passes all eleven
  captures plus the ordered live-revision and session-clear probe. These do
  not prove world rendering or frozen-candidate compatibility. Matching cached
  exploratory NeoForge creation and Atlas UI checks also pass all thirteen and
  eleven captures respectively, including revision, normal-disconnect, and
  session-clear checks; the full nightly/release gates remain pending. Removed
  density classes and relocated surface suppliers are kept in version-owned
  mixins; a capitalization-only region method rename uses shared alternative
  selectors and retains its required alias redirect.
- Existing 26.1.x evidence is retained; no new publication or live-world change.
- Fresh clean quick run `20260827T054844Z-eab4ee8cebfb` on pushed `8048871`
  passes both 26.2 dedicated-server cells with independently verified frozen
  jar hashes. The full nightly matrix and copied-world upgrades remain pending.
