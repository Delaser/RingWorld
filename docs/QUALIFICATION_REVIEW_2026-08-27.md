# Version qualification evidence review — 2026-08-27

## Retained 26.1.x coverage

The independent read-only composite reviewer reports
`COMPOSITE_COVERAGE_REVIEWED` for all 60 fixture slots. This is explicitly
**not one monolithic all-PASS invocation**, and not a new runtime execution.

| Minecraft | Loader | Exact frozen-candidate fixtures | Source-ABI fixtures | Total |
| --- | --- | ---: | ---: | ---: |
| 26.1 | Fabric | 6 | 4 | 10 |
| 26.1 | NeoForge | 6 | 4 | 10 |
| 26.1.1 | Fabric | 6 | 4 | 10 |
| 26.1.1 | NeoForge | 6 | 4 | 10 |
| 26.1.2 | Fabric | 6 | 4 | 10 |
| 26.1.2 | NeoForge | 6 | 4 | 10 |

The six exact-candidate fixtures are world generation, Atlas recovery,
multiplayer, raids, production lifecycle and production rendering. Creation
UI, Atlas UI, map/compass and curved-object fixtures are source-ABI evidence;
they are not silently relabelled as frozen-jar or packaged-launcher tests.

Inputs:

- Quick matrix: `20260823T130347Z-a493af8d7261`.
- Primary aggregate: `20260826T215217Z-68410e5f8e85`, source `14ff4e5`,
  with 55 PASS results, one failed raid and four unrun dependent fixtures.
- Repaired NeoForge 26.1 raid: `20260827T040412Z-ee7ba84a5b3b`, source
  `f27a180`. The fix observes a fresh startup log, not the previous process's
  startup marker.
- Explicit downstream replacements from `20260826T145215Z-b593bba25512`,
  source `aa64b3f`: NeoForge 26.1 map/compass, production lifecycle, curved
  objects and production rendering.

The reviewer revalidates strict quick evidence, candidate identities,
terminal hashes, complete manifest coverage, evidence classes, and the
presence/hash of terminal-bound PNG/log artifacts, including relocated files.
An independent check of the primary aggregate also verified 61 terminal
references and 343 retained captures/logs for its 55 passing results.

Reproduce from the main repository with its retained ignored evidence:

```sh
python3 scripts/review_composite_nightly_evidence.py \
  --quick-run-id 20260823T130347Z-a493af8d7261 \
  --primary-aggregate dist/qualification/nightly-matrix/20260826T215217Z-68410e5f8e85/terminal.json \
  --raid-repair dist/qualification/ringworld/26.1/neoforge/20260827T040412Z-ee7ba84a5b3b/26.1-neoforge/evidence/nightly/07-raid-seam/terminal.json \
  --downstream-aggregate dist/qualification/nightly-matrix/20260826T145215Z-b593bba25512/terminal.json \
  --downstream-fixture map-compass \
  --downstream-fixture production-lifecycle \
  --downstream-fixture curved-objects \
  --downstream-fixture production-render
```

## Remaining release boundary

This closes the retained-results reconciliation step only. It does not prove
newly changed jars, final-candidate world upgrades, packaged graphical clients,
experimental Vulkan, or third-party compatibility. Public support remains
the existing 26.1.2 release until the release and publication gates finish.

The stale August 13 `1.1.0+mc26.1` jars were replaced by the metadata-only
local review candidates recorded in `CURRENT_STATE.md`. Those replacements are
byte-equal to the retained quick candidates outside the approved public
metadata fields. They remain local preparation only: final-candidate upgrades,
packaged runtime review, and owner approval are still required before staging
or publication. The held publisher's execute-time checkout equality currently
requires the frozen build-source revision; a later authorized publication must
either stage from that revision or add reviewed provenance handling rather than
weakening that check.

26.2 additionally needs fresh frozen candidates after the reversed-depth
correction, complete nightly coverage and copied-world upgrades. Publication
and live-server/world changes remain unauthorized by this evidence review.
