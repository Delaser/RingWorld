# Mainline 1.3 backport development candidate

This is the retained development ledger. Release preparation now uses
`1.3.0+mc1.21.1`; the development artifact hashes and bounded evidence below
remain historical rather than being relabelled as final release evidence.

This implements the [authorized 1.3 plan](MAINLINE_1_3_BACKPORT_PLAN_2026-10-02.md)
on `codex/mc1211-1.3-parity`, starting from backport `220179e`. The upstream
runtime authority is `f1b243af48f44452179f7a109877799584c8f47d`; reviewed mainline
tip is `a46965c547c07e91e5c1f02e999f376192fe5a94`. The candidate version is
`1.3.0-dev+mc1.21.1`. This is a local development build, not a published release
or a completed graphical parity qualification.

## Implemented

- Fixed one-block authoritative Atlas sampling and size admission, including
  legacy saved fidelity IDs and readable FULL ID 4. Saved geometry, seed,
  mapping, wall style and optional generation choices remain authoritative.
- Atlas format 10 and metadata/tile v4, with independent water/light nibbles
  in disk, tile, snapshot, interpolation and local downsampling paths. Both
  initial capture and live recapture derive water from real surface fluids.
- Medium local detail by default/reset/session teardown. Low/Medium/High
  remain local choices; new-world UI no longer offers source fidelity or
  Archipelago. Existing saved Archipelago settings remain readable.
- Water tint before surface filtering; authored water sprite bounds in the
  1.21.1 terrain shader uniforms; progressive shared distance haze.
- Forward OpenGL native-depth reconstruction and an ordered distant tail,
  adapted to GLSL 150. The backport's accepted continuous compositor remains:
  draw after section compilation, retain current visible-section/LevelChunk
  readiness proofs, streaming floor and live overlap. Mainline's ordered
  coverage implementation was not substituted for the accepted backport ramp.
- Resource-derived wall face colors with map-color fallback, model-reload
  cache invalidation, alpha-weighted independent wall-strip mips, repeat-U
  derivative filtering and strip-local per-mip V bounds. Content keys use
  the actual rendered column count and skip unchanged wall rebuild/uploads.
- Outlines and mining cracks use height-dependent tangent scale. Rigid
  block entities retain their existing pose behavior.
- Additional structure locating searches finite Z independently of periodic
  X, visits canonical X chunks once and avoids search-radius overflow.
  Legacy rim textures share the server's exact coordinate material hash.
- One five-page creation draft with Apply, Cancel, discard and restore;
  ten-preset wall picker; proportional preview section, pan controls and full
  ring overview. Use locks the seed; Apply commits it to vanilla. Blank input
  locks the resolved numeric preview seed. Manual vanilla edits clear the
  preview source marker.
- Non-pausing Generation/Display map pages, local detail control and optional
  technical details. Complete client cache snapshots wait for ten seconds of
  quiet tile traffic; forced disconnect saves still retain the latest state.
- Expanded 26-capture creation and Generation/Display/technical Atlas
  fixtures, format-5 acknowledgement checks and bounded cold production Atlas
  load deadlines. An independent format-10 disk verifier binds settings,
  fingerprint, water/light nibbles and durable completeness.

## Validation ledger

The unchanged baseline passed both loader builds before edits. The integrated
candidate's final runtime rebuild passed 530 Fabric/common and 553 NeoForge
tests (1,083 total), with zero failures, errors or skips, both builds, loader
boundaries and dependency inventory. The normal check graph also enforces
the backport handoff shader bindings and preserved draw/readiness contract.
Independent runtime-jar inspection passes exact Minecraft 1.21.1/Java 21
metadata, MPL-2.0/license bytes, new client classes/mixins, Atlas v4 payload
IDs and actual water uniforms/shader resources.

| Local development artifact | SHA-256 |
| --- | --- |
| Fabric `ringworld-1.3.0-dev+mc1.21.1.jar` | `b7c5296d5cbe0ed42897d4f1c96195eb05b05d0d1ff29f90fc6a2580fcdaabe7` |
| NeoForge `ringworld-neoforge-1.3.0-dev+mc1.21.1.jar` | `b58f74c6d2e96bb9cda6fc34cf6fdb3c37ee0b502d5b65dad56f0f07f819b018` |

Runtime and source jars are under `build/libs/` and `neoforge/build/libs/`.
Inspected runtime copies and a license are retained under ignored
`.codex-tmp/parity-1.3-candidate/`; they are not official release staging or
clean/pushed release evidence.

Both real dedicated loaders completed fresh 2,048×128 headless runs with
river and additional structures enabled, normal all-dimension saving and
262,144 durable one-block cells. Fabric used historical Performance ID 0;
NeoForge used FULL ID 4. Independent disk verification passes for each. These
are different fresh seeds, not a same-seed visual comparison.

| Loader | Atlas SHA-256 | Water cells | Lit cells |
| --- | --- | ---: | ---: |
| Fabric Small | `6a62dc254e04f173157a7e8308e001a75b15c717bef19c387cc14250b68fbd8b` | 215,076 | 13,716 |
| NeoForge Small | `aa93150ab8bd2f289edd218ae6c8c6c1bf15a997a17ff273c3d75f025d4db192` | 241,259 | 11,549 |

The three independent Python identity tests match two frozen Java hashes,
including a negative seed and retired wall pattern, and exercise omitted
CODEC defaults. Ad hoc malformed-disk checks reject
truncated cells, trailing bytes, invalid presence and format 9.

Both loaders completed the copied 16,384×256 production recapture, with all
16,384 chunks and 4,194,304 one-block cells. The prior format-3 settings migrate
to format 5 without changing geometry, seed or noise mapping; the old Atlas
is invalidated and rebuilt from saved terrain. Independent disk readers
verify format 10 and identical Atlas SHA-256 on both loaders:
`a5d81a585b96aecafbdf6742761cea21d67df1b128f1993ce04dc36f91c65b55`.
Each has 1,655,272 water cells and 27,411 lit cells. Recorded capture elapsed
time is 821,807 ms for Fabric and 821,857 ms for NeoForge; these bounded,
budgeted headless times do not measure client frame pacing or transfer cost.

Both dedicated production worlds reopen with complete data immediately
(report elapsed 0 ms), normal all-dimension saves and the identical Atlas
hash. Saved settings remain unchanged despite deliberately enabling FULL,
river and additional structures in the later bootstrap configuration.
The original `Backport Production 16384` save is read-only input, with a
207-file SHA-256 manifest before and after. Existing September evidence and
Small fixture worlds were copied into ignored evidence directories before
any fixture preparation replaced its disposable outputs.

Both dedicated loaders also pass the aggregate structure fixture for seed
`987864623`, with 128 seam-strip chunks, twelve biome families, a crossing
mineshaft, cave/ore/tree/loot observations, periodic terrain queries, both
finite rims, stronghold/portal/locate checks and a validated persisted
monument at canonical chunk `(606,3)`. This is one matched regression seed,
not the full multi-seed matrix.

Machine receipts are retained in `.codex-tmp/parity-1.3-evidence.json`, with
the base commit, branch, modified/added-file hashes, XML test totals,
artifact inspections, log hashes, independent disk results and original
save manifests. Dedicated run logs use `parity-1.3-{fabric,neoforge}-`
`headless-small`, `headless-production`, `production-reopen` and
`structure-regression` names. The successful Fabric reopen log has suffix
`production-reopen-2`; its earlier invocation failed Gradle argument parsing
before any world activity. The final build/check log is
`.codex-tmp/parity-1.3-final-contract-build.log`.

## Fabric graphical checkpoint and remaining gates

The initial Fabric creation launch could not create a window on this worker.
A JVM thread dump placed it in GLFW's startup error
dialog, before Minecraft renderer/resource initialization. An isolated hidden
GLFW probe independently returns `GLFW_API_UNAVAILABLE`:
`WGL: The driver does not appear to support OpenGL`. The failed run and
diagnostic are retained. Later launches on 2 October successfully initialized
the renderer, resources and shaders; the earlier host blocker is resolved.

Fresh Fabric source-development clients now pass the 26-capture creation
fixture and expanded 13-capture Atlas UI fixture. Reviewed images cover all
five editor pages, compact walls, vanilla seed application, Generation and
Display tabs, technical details, pause/resume/cancel/retry and completion.
The Atlas run also proves two ordered live revisions and normal disconnect
with raw client-session teardown. Its native captures are 854×480; configured
GUI scale 4 is bounded by that window size. This is not an assertion of a
1920×1080 effective GUI-scale-4 Atlas pass.

Copied production noon/dusk projection runs pass tangent, handoff and
radial-up captures at 16 chunks, Fancy, clouds off, FOV 70 and Medium session
LOD. Their native fixture resolution is 1280×720. Small-ring projection and
an additional land-rich camera view also pass. The Small natural seam/both
engineered-rim fixture passes four 1920×1080 captures; its 428 movement
frames average 16.663 ms, maximum 25.711 ms, with none above 50 ms. Production
settled stages average about 16.66 ms but include frames above 50 ms, so this
does not establish hitch-free production rendering.

Unaltered captures, logs, a local gallery and PNG dimensions/hashes are under
`.codex-tmp/parity-1.3-visual-review/`. Capture logs use the
`parity-1.3-screenshots-fabric-` prefix. Separate walkthrough copies preserve
the original source saves. A first-run tutorial toast can cover the pause
menu's top-right RingWorld Map button; moving that button or accommodating
the toast remains a UI polish follow-up. Distant canopy/shoreline detail also
remains an Atlas approximation, visible in these captures.

These are bounded Fabric development-source passes. NeoForge graphical and
packaged frozen-candidate qualification are still outstanding.

Complete the remaining gates serially on both loaders:

1. NeoForge's 26-capture menu fixture and expanded Atlas Generation/Display/
   technical fixture, including seed source marker, cancellation and compact
   views. Review layout screenshots rather than relying only on PASS markers.
2. Optional river/water/light live-revision, partial streaming, disconnect and
   reopen; map/compass persistence; curved object/interaction captures.
3. Same-process layout switching, dedicated two-client gameplay and state
   continuity, worldgen/structure and persisted-raid regressions.
4. Prewarmed production lifecycle and noon/dusk/night/rain projection,
   natural seam/both rims, early streaming, foliage rebuild and Fabulous
   handoff. Compare tone, water, distant depth and measured frame pacing.
5. Exact final candidate review and release staging from a clean pushed
   revision only after qualification. No upload or listing change is made.

The maximum Large source budget and client memory/transfer/upload/cache
costs still need measurement. Copied September format-9/hidden-Archipelago
saves and the remaining multi-seed/raid/gameplay cases also need their
separate fixtures; the completed copied production run does not stand in
for those cases.

Create/Flywheel stays benched. Linux/macOS, third-party compatibility, support
metadata broadening and public publication remain outside this candidate's
evidence. The historical format-6 qualification scripts and publication
records are not relabelled as format-10 or 1.3 evidence.

## Dependency closure

Normal dependency resolution exposed eight unpinned POM/module files for
already selected Guava/JUnit metadata. Each cached file was independently
compared with its official Maven Central bytes before adding its exact
SHA-256 pin. All previous artifact pins are unchanged; no toolchain/library
version, verification mode or repository was weakened. The cross-file
inventory now enforces 373 components and 760 artifacts/pins. No ordinary
build used `--write-verification-metadata`.

The project usage script still reports Windows `WSAStartup` error 10093. The
account usage tool independently confirmed 94% weekly allowance remaining
at the first integration milestone and 93% at the production milestone, above
the required pause threshold.

## Release preparation and live-update repair

The first release-labelled optional visual run exposed a stale Atlas lamp
cell even though the saved real chunk contained a lit redstone lamp. Dense
one-block invalidation could leave unloaded neighbouring cells continually
requeued, while exact-cell traffic starved collapsed overflow tiles. The
shared recapture path now relies on the existing whole-chunk load capture for
unloaded cells, and alternates exact and overflow work within its unchanged
64-cell tick budget. A regression test forces every exact cell to be requeued
and requires overflow progress even with a one-cell drain budget.

The repaired clean dual build passes 531 Fabric/common and 554 NeoForge cases
(1,085 total), with no failures, errors or skips. All shared named RingWorld
classes compare byte-identically across the same-build Fabric development
archive and NeoForge runtime archive. Fabric's fresh optional visual run
passes partial streaming, authored light on/off, saved custom wall/sky state,
two normal disconnects and reopening. Final dual-loader graphics, packaged
runtime and multiplayer evidence is recorded separately when complete.

The new `scripts/stage_backport_1_3_release.py` is the exact Java 21/1.21.1
profile: it requires clean pushed source, a mandatory clean dual build,
loader/MPL/archive validation and named shared-contract equality before
staging only one runtime jar per loader. It leaves the historical 26.1.2
stager unchanged. `scripts/plan_backport_1_3_curseforge_upload.py` independently
checks both stages and emits dry-run Release metadata with the immutable
source URL, exact Minecraft/Java/loader tags and Fabric API only on Fabric.
Neither script uploads, reads a token or modifies a listing.
