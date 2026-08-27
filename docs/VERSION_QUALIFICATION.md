# Adding a Minecraft version

The current retained 26.1.x composite evidence review is recorded in
[QUALIFICATION_REVIEW_2026-08-27.md](QUALIFICATION_REVIEW_2026-08-27.md).

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

Optional client bundles and server overlays may be built for Phase 6 package
smokes from a format-1 qualified stage. Select the reviewed manifest and one
explicit loader runtime cell; this derives the exact Minecraft, loader, and
Fabric API pins rather than reusing 26.1.2 defaults:

```sh
python3 scripts/prepare_release_packages.py --loader fabric \
  --stage-manifest dist/qualified-release/1.1.0+mc26.2/fabric/STAGING-MANIFEST.json \
  --qualification-manifest config/minecraft-version-matrix-26.2.json \
  --runtime-cell 26.2-fabric --fabric-api /absolute/path/fabric-api.jar \
  --output /absolute/path/package-review
```

The legacy format-2 `stage_modrinth_release.py` path remains separately
supported. A format-1 stage requires both new arguments and rejects a missing,
wrong-loader, stale-hash, or unpinned dependency input. Package creation is
still local and optional; it is not publication authorization.
The checked-in Prism templates are structural inputs only: format-1 assembly
rewrites their managed Minecraft and loader component versions in its temporary
copy from the validated runtime cell, so operators do not hand-edit JSON.

For the retained 26.1.x 55-PASS aggregate plus its explicitly selected repair
terminals, use `scripts/review_composite_nightly_evidence.py`. It rehashes the
selected aggregate/terminal/retained-artifact inputs and emits a read-only
coverage review which remains explicitly non-monolithic. It is release-audit
input, not replacement runtime evidence or publication authorization.

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

For external dedicated-runtime inputs only, a worker may set
`RINGWORLD_QUALIFICATION_DOWNLOAD_CACHE` to an existing absolute, read-only,
non-symlink directory outside the checkout, home, and qualification tree. Its
read-only regular entries are `<algorithm>/<digest>` (`sha1` or `sha256` with
lowercase exact-length hex), using the manifest's exact checksum. Each
entry is copied into the disposable cell and rehashed before use; a missing
entry falls back to the pinned no-redirect HTTPS fetch, while a bad path,
symlink, oversize entry, or hash mismatch fails closed. This cache is an
optional verified byte seed, never a download bypass or runtime-evidence
substitute.

Local staging inputs for the planned 1.1 release are separate:
`deploy/qualified/26.1.x-release.json` / `26.1.x-changelog.md` and
`deploy/qualified/26.2-release.json` / `26.2-changelog.md`. Their existence is
not a support or release claim. Stage each with its own manifest, quick run
and byte-equivalent public candidates only after its remaining release gates
pass. The 26.2 notes explicitly distinguish the qualified OpenGL path from
unqualified experimental Vulkan and explain that rollback requires a matching
pre-upgrade world backup. Nothing in this preparation uploads or publishes.

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
