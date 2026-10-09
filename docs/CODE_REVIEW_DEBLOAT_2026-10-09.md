# Code review and focused cleanup — 9 October 2026

## Findings and changes

The review covered shared/version source selection, server Atlas request and
lease lifecycle, client cache/session teardown, surface renderer worker and
GPU ownership, seed preview cancellation, payload bounds and unused-source
candidates. This is a targeted development review, not a guarantee that every
code path or third-party mod combination is defect-free.

One confirmed version-parity defect was repaired: the 26.3 surface renderer
called a legacy `RingSurfaceMesh.build` overload. That overload substitutes
a legacy wall style with zero decay and seed zero, and derives height instead
of accepting the captured saved height. The renderer now passes the saved
wall height, style and generator seed just like 26.1/26.2. Defaults could hide
the height error; the saved crest/decay settings were lost. The wall colour
texture already used saved settings and is unchanged. A regression compares the
whole renderer after normalising its five GPU package imports. It failed on
the previous source and passes on the repaired source. The static CI workflow
also now runs for shared client edits, as well as version-owned edits, so this
guard is not bypassed by changing only the shared renderer.

The 434-line 26.3 seed-preview screen differed from the shared screen by one
`RandomState.create` API call. A small factory in the existing per-version
`RingMinecraftClientAccess` adapters now owns that difference. All versions use
the shared screen. Preview workers still receive immutable captured inputs,
construct their own generator and retain the original cancellation behavior.
Adapter contract coverage now includes all three source ABIs.

Three byte-identical 26.3 overrides were removed: `SpriteContentsAccessor`,
`RingWallMaterialColors`, and `ringworld_handoff.glsl`. Gradle's existing source
selection falls back to their shared definitions. The intentional GPU/mixin
ABI adapters remain separate. No source generation or dependency was added.

The cleanup removes 504 net source lines. This mostly reduces maintenance
and drift: Gradle already packaged only one copy of each override, so it does
not imply a corresponding reduction in JAR size. Wall preview assets were
already compressed in #260. Runtime release-test fixtures are retained because
qualification uses them inside the exact candidate JARs. No saves, screenshots,
release evidence, build caches, or unrelated worktrees were deleted.

No additional demonstrated correctness defect was found in the lifecycle paths
reviewed. Larger future refactors of the build scripts or embedded fixtures
would need separate scope and qualification; they are not part of this cleanup.

## Validation

Code commit `a5ab63aad24bdaa2443c2d5bdacdc42f36ff10da` passes:

| Minecraft source family | Fabric build / Java cases | NeoForge build / Java cases | Native seed-preview / Use / Apply |
| --- | --- | --- | --- |
| 26.1.x (26.1.2 build) | PASS / 485 | PASS / 485 | Both PASS, 26 captures each |
| 26.2 | PASS / 485 | PASS / 485 | Both PASS, 26 captures each |
| 26.3 | PASS / 488 | PASS / 488 | Both PASS, 26 captures each |

There are 2,916 Java cases with zero failures, errors or skips. The complete
Python suite passes 441 tests with two Windows-only launcher tests skipped on
macOS. The renderer parity regression was first run red against the previous
call site. The creation UI runs complete two real seed previews, change preview
identity, and exercise Use/Apply plus the vanilla applied-seed display. All six
runs exit normally with no ERROR/FATAL log entries.

Both 26.3 loaders also complete muted hidden native wall captures at Low,
Medium and High on independent saved-world copies (six images). They regenerate
a complete format-11 Atlas, upload detailed meshes, capture closed edges and
walls, then save and stop normally; logs contain no ERROR/FATAL entries. The
captures were inspected for visible terrain/wall joins. These targeted captures
are not a new day/night/movement performance or full visual acceptance matrix.

ZIP comparisons confirm all required removed-override classes and the handoff
shader still ship through shared-source fallback. Only the expected preview
adapter/screen and 26.3 renderer classes change. Development JAR sizes are
3,412,140–3,436,775 bytes (about 3.25–3.28 MiB). They increase by just 323 bytes on
26.1/26.2 and 371 bytes on 26.3 compared with the pre-review builds. No binary
size saving or gameplay FPS improvement is claimed.

Evidence is retained under `logs/code-review-debloat/`: `build-results.json`,
`build-*.log`, `python-tests.log`, `native-results.json`, `ui-*.log`,
`walls-*.log`, `ui-captures/`, `wall-captures/`, `baseline.json` and
`jar-comparison.json`. Previous disposable fixture evidence/worlds were retained
before replacement; the original study save is untouched. These checks are
source-development evidence, not fresh frozen release qualification or owner
visual sign-off. The subsequent documentation/CI commit does not change the
validated Java or shader sources.
