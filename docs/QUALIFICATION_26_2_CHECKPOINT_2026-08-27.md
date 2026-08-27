# 26.2 qualification checkpoint — 2026-08-27

**Automated qualification is complete; pause before publication.**
No merge, upload, website deployment or live-server/world change occurred.
Fresh authenticated packaged-client review and owner release approval remain.
The final static qualification workflow passes **341 tests**.

## Completed coverage

- 26.1.x: the retained 60-slot composite review remains complete; see
  [its independent review](QUALIFICATION_REVIEW_2026-08-27.md).
- 26.2: paired quick `20260827T103705Z-a0890015e3e3` passes both loaders
  from clean pushed binary source `1cfac9b10648054e46f4303f6e8b87df9b9bcdba`.
- Primary nightly `20260827T104447Z-6af7691cc891` remains **16 PASS,
  2 FAIL, 2 INCOMPLETE**. Its four failed/skipped curved-object/render
  checks pass in targeted aggregate `20260827T122329Z-768fe4857612`.
  The latter is intentionally **INCOMPLETE** because it selects only four checks.
- Composite review covers **20 slots, 22 terminal records, 124 retained
  artifacts**, with `monolithic_pass=false`. No full rerun is needed merely
  to replace that reviewed composite; neither aggregate is relabelled.
- Eight copied-world forward-upgrade routes pass.
- Four staged server overlays pass disposable localhost startup/normal-stop
  checks. Twelve client/server archives pass package inspection.
- No public support claim or host metadata was changed.

The 26.2 composite report is
`dist/qualification/nightly-matrix/20260827T122329Z-768fe4857612/composite-review.json`.
Primary aggregate SHA-256:
`8eed94ce73f7a3837fa83c50fc7b56076180a8406dca52284d41421a674ce18e`;
repair aggregate SHA-256:
`735737e3bc34466d97ca92441a57dd1c6278cec046c503f23189fb8641db9f7e`.

| Targeted repair | Run ID |
| --- | --- |
| Fabric curved objects | `20260827T122329Z-538bf2e0cf6a` |
| Fabric production render | `20260827T122726Z-149c7105842c` |
| NeoForge curved objects | `20260827T123257Z-8335d460e5f3` |
| NeoForge production render | `20260827T123812Z-45397e7fa31f` |

The source-ABI fixture repairs handle periodic presentation coordinates and
26.2 beds no longer implementing `EntityBlock`. Both red-bed halves and all
other object requirements remain checked. The earlier boat-fixture fix tests
factory-created entities instead of overly broad registry class metadata.
Later fixture/report changes do **not** exist in the unchanged frozen jars.

## Copied-world upgrades

Each target runs only a disposable copy of a passed source world. The checks
preserve RingWorld settings and the logged seam/worldgen/structure/loot facts.
They do not constitute a complete saved-block/biome-palette or Atlas inventory.

| Loader | Source → target | Passing run ID |
| --- | --- | --- |
| Fabric | 26.1 → 26.1.1 | `20260827T125130Z-904e8b33f8f2` |
| Fabric | 26.1 → 26.1.2 | `20260827T125143Z-83e7ad17fc91` |
| Fabric | 26.1.1 → 26.1.2 | `20260827T125156Z-5337d0f9b601` |
| Fabric | 26.1.2 → 26.2 | `20260827T130532Z-35717b2b65e0` |
| NeoForge | 26.1 → 26.1.1 | `20260827T130111Z-a43235fd8e72` |
| NeoForge | 26.1 → 26.1.2 | `20260827T130133Z-8da10ff61e5b` |
| NeoForge | 26.1.1 → 26.1.2 | `20260827T130155Z-8858a7ba4736` |
| NeoForge | 26.1.2 → 26.2 | `20260827T130546Z-aea8099f3733` |

Terminals are retained under
`dist/qualification/ringworld/<target-version>/<loader>/<run>/<target-cell>/evidence/nightly/05-world-upgrade/terminal.json`.
Operator records are grouped under
`dist/qualification/operator-forward-upgrade-20260827/`:
`20260827T124507Z-92569` (three Fabric patch routes),
`20260827T130110Z-94797` (three NeoForge patch routes), and
`20260827T130531Z-95352` (two cross-line routes).
Their source revisions are respectively `8dae789`, `f9426c8`, and `e76aebb`;
these are distinct operator executions, not a single aggregate invocation.

The independently rehashed/revalidated eight-route review is
`dist/qualification/operator-forward-upgrade-20260827/forward-upgrade-review.json`,
SHA-256 `824372a1245c20d2c00ba75a2c7a2a9e59fbb36fe193670197af190e91533298`.
It rechecks terminal bindings, installed jars, settings and resume logs, and
reapplies the current settings/worldgen contract. This review is not a new
runtime run or a full-world inventory claim.

Two failed cross-line attempts remain immutable:
`20260827T125209Z-ee17369da94a` completed the runtime but its validator looked
up the source cell in the target manifest; `f9426c8` fixes that lookup.
`20260827T125803Z-da5b19865ee8` then rejected the newly predicted
`minecraft:sulfur_caves` biome. Inspection established that these biome/family
lists sample the current generator's `BiomeSource`, not saved chunk palettes.
`e76aebb` therefore reports explicit cross-stable-line before/after/add/remove
sample deltas while retaining every other comparison. Same-line reloads remain
fully strict, and malformed/empty lists, settings, count and structure drift
remain failures. Both passing cross-line runs add only sulfur caves to the
sampled biome list; family sets and all other checked facts match.

## Candidate identity and packages

| Loader | Unchanged 26.2 frozen SHA-256 |
| --- | --- |
| Fabric | `7cf2a56aea4946f27b953df65f296abc2f72c0379d7da52562bd10a499273a01` |
| NeoForge | `1d3070be8be479b6438254e033ed3afe905e5d04da632dab5a20e395f4e3bd5a` |

The 26.2 metadata-only release stages are under
`dist/qualified-release/fixture-fix-20260827/1.1.0+mc26.2`;
review archives are under `dist/qualified-package-review/fixture-fix-20260827`.
The similarly named older 26.2 stage is superseded.

- Fabric staged SHA-256:
  `dbc4d0ff170a8b3850c85edf859865e8ce10a12a7296a6f83dd324a193138949`.
- NeoForge staged SHA-256:
  `15e2d2c9e84ed9a421351bce34ad5f24dfafe2349772cbe42bf34b8cda1ce0a5`.

26.1.x stages remain at `dist/qualified-release/1.1.0+mc26.1`.
Exact source revisions, jar hashes and owner steps are in the
[release handoff](RELEASE_1_1_OWNER_HANDOFF.md). Metadata-only equivalence,
checksums, nested MPL licensing, source links and runtime pins pass.
Publication dry runs validate without tokens, network calls or host changes.

Server-overlay evidence is retained under
`dist/qualification/package-overlay-smoke-20260827/` in
`overlay-runtime-smoke.json` (26.2) and
`overlay-runtime-smoke-26.1.2.json` (26.1.x packages on 26.1.2).
All four reach `Done` and stop normally, exit 0. The first 26.1.2 NeoForge
setup accidentally copied diagnostic JVM flags and is explicitly excluded;
its clean setup passes. These smokes do not prove installer/network
provisioning, graphical package operation, multiplayer or worldgen completion.

## Visual and owner boundary

Real-client production render and curved-object captures were inspected on
both loaders. Complete overhead ring visibility is retained. The stepped,
foggy real-water/Atlas handoff also appears in retained 26.1.x baseline evidence;
do not describe it as either newly seamless or a new 26.2 regression.
Measured frame gates pass, but isolated long-frame spikes are not zero.

Remaining owner gates:

1. Fresh authenticated Prism packaged-client checks on macOS, plus real Windows
   checks for both loaders and both version lines. Never copy an existing
   account/token store into qualification state.
2. Exact-candidate gameplay/visual confirmation and release go/no-go.
3. Separate explicit publication authorization bound to each stage's source
   revision and jar hash. The publishing checkout must match that source;
   a later fixture/docs checkout cannot bypass the guard.

**Stop here before publishing. The live demo server and world are untouched.**
