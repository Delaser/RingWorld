# RingWorld 1.3 functionality and backport plan

Audit date: 2 October 2026. This document inventories the current mainline
experience, compares it with the local Minecraft 1.21.1 implementation, and
defines the work needed for equivalent functionality and visual quality on
Fabric and NeoForge. It is a plan; no runtime changes or fresh runtime
qualification are included in this audit.

The backport already contains the core ring engine and the September optional
generation, wall and Atlas feature batch. The remaining work is a substantial
data, rendering and UI update. Port the one-block Atlas contract first, then
the interaction and material corrections, then the unified editor and
generation/display panel. Preserve the qualified 1.21.1 streaming compositor
throughout and verify the resulting experience on both loaders.

## Source and evidence

| Reference | Exact source | Meaning |
| --- | --- | --- |
| Mainline reviewed here | `a46965c547c07e91e5c1f02e999f376192fe5a94` | `origin/main`, merged PR #252 for 1.3. |
| Published 1.3 runtime source | `f1b243af48f44452179f7a109877799584c8f47d` | Final repaired source used for the six release jars. There are no subsequent changes to `src/`, the root build file or the NeoForge build file at the reviewed main tip. |
| Local backport | `220179e380283074467b76094378fff049dbd8f4` | `codex/mc1211-new-feature-parity`; clean before this documentation audit. |
| Previously ported feature source | `27bb33d822623997cef87298b7f23c0c916fa34a` | September feature checkpoint, used to isolate intervening mainline changes. |
| Public backport integration tip | `f4522daeda564a767b622d2829dfe3db7a535ddf` | `origin/port/mc-1.21.1`; distinct from our local feature branch. |

Remote references were fetched without resetting the backport. An ignored
source snapshot and machine-readable comparison are retained under
`.codex-tmp/mainline-1.3-audit/`. The upstream change inventory contains 126
source/build files since the prior feature source, including the new 26.3
adapter tree. That is not a count of files that need copying to 1.21.1.

Primary references, pinned to the reviewed mainline source:

- [1.3 publication record](https://github.com/Delaser/RingWorld/blob/a46965c547c07e91e5c1f02e999f376192fe5a94/docs/RELEASE_1_3_PUBLICATION_2026-09-30.md)
- [Final release qualification](https://github.com/Delaser/RingWorld/blob/a46965c547c07e91e5c1f02e999f376192fe5a94/deploy/qualified/1.3/README.md)
- [1.3 feature and rendering notes](https://github.com/Delaser/RingWorld/blob/a46965c547c07e91e5c1f02e999f376192fe5a94/docs/RELEASE_1_3_NOTES.md)
- [Supported-version parity matrix](https://github.com/Delaser/RingWorld/blob/a46965c547c07e91e5c1f02e999f376192fe5a94/docs/RELEASE_1_3_PARITY.md)
- [Implemented UI screenshots](https://github.com/Delaser/RingWorld/tree/a46965c547c07e91e5c1f02e999f376192fe5a94/docs/media/ring-generation-ui-implemented)
- [Optional generation and current display budgets](https://github.com/Delaser/RingWorld/blob/a46965c547c07e91e5c1f02e999f376192fe5a94/docs/OPTIONAL_WORLD_GENERATION.md)
- [Transition comparison and deferred work](https://github.com/Delaser/RingWorld/blob/a46965c547c07e91e5c1f02e999f376192fe5a94/docs/TRANSITION_IMPROVEMENT_PLAN_1_3.md)
- [Local September feature checkpoint](NEW_FEATURE_PARITY_2026-09-07.md)
- [Backport handoff research](HANDOFF_TRANSITION_RESEARCH_2026-08-24.md)

Release state is taken from the final publication/preparation records. The
README still calls 1.2 current and 1.3 upcoming; `CURRENT_STATE.md`, operations,
protocol documentation and some sections of the 1.3 notes also contain
superseded development statements. The source confirms settings format 5 and
`settings_v7`, Atlas format 10 and metadata/tile v4. Earlier notes using v6/v2,
eight-block sampling, map-colour-only walls or unreleased 1.3 status are stale.
Some history calls the work 1.4; the released behavior was incorporated in 1.3.

## Current release state

The publication record reports six approved CurseForge 1.3 files, submitted
30 September, with all six CDN hashes matching the staged artifacts. They
cover 26.1–26.1.2, 26.2 and 26.3, one jar per loader per line, under Java 25.
The reviewed repository records no 1.3 Modrinth upload or optional launcher
bundle release. These are recorded publication results, not a new independent
download or dashboard verification performed in this audit.

All six release candidates passed quick qualification. Full nightly coverage
is 20/20 for 26.2 and 20/20 for 26.3. The 26.1.x record has 60/60 reviewed
composite coverage: 42 original passes and 18 targeted repair passes using
the same frozen jars. Preserve the original failures and repair records; it
was not one uninterrupted all-pass run. Build/unit checks cover all five
Minecraft patches on both loaders. The staged NeoForge jars additionally
passed dedicated-server start/save/normal-stop on newer official loaders
26.1.2.112, 26.2.0.88 and 26.3.0.37-beta.

NeoForge release metadata now expresses a minimum-only loader dependency,
while Minecraft limits and build/qualification pins remain bounded. Acceptance
of later loader versions does not qualify every such version. The final
publication follows owner visual review of 26.1.1 NeoForge; the record does
not establish a separate owner visual review of every version/loader pair.
Saved-world upgrade qualification was outside that mainline release scope.
The backport needs its own copied-save checks because its public Beta and
local feature saves have different cache generations.

## Functionality inventory

| Area | Current mainline behavior | Backport action |
| --- | --- | --- |
| World topology | One canonical server X plane, periodic circumference, finite Z band, ordinary Y height and gravity. Natural seam travel preserves movement and nearby visibility. | Retain the existing implementation and regression coverage. |
| Dimensions | Only the Overworld is periodic and curved; Nether and End stay vanilla. Saved dimensions and terrain identity win over later bootstrap edits. | Retain explicit dimension guards, dimension-owned storage and reopen behavior. |
| Seam gameplay | Nearest periodic relationships cover combat/reach, projectiles, explosions, tracking, mobs/navigation, vehicles, stateful blocks, fluids, ticks and multiplayer effects. | No engine rewrite identified in the intervening shared feature delta; rerun the established gameplay matrix after integration. |
| Maps and navigation | Filled-map pixels/markers, banners, item frames, spawn/lodestone/recovery compasses, structure locate and portal returns use periodic ownership or nearest targets. | Preserve the backport adapters and dedicated fixtures. |
| Generation | Vanilla biomes, caves, ores, trees, loot, structures and aquifers build the real world. Ring-aware terrain/noise and structure policies keep the seam and finite band coherent. | Preserve the 1.21.1 density-domain adapter and detached preview generators. Newer noise APIs are not a reason to replace it. |
| Optional terrain | Continuous ring river and more built-in eligible random-spread structures are saved options, Off by default. More structures retains biome/frequency/exclusion checks; modded sets are not automatically multiplied. | Already implemented; move the controls to Terrain and revalidate actual water, biomes, placement and locate. |
| Archipelago | Macro terrain remains implemented and readable in saved generation settings, but is hidden in the release creation UI. | Remove the ordinary selector. Preserve existing saved Archipelago worlds and their terrain identity. |
| Scarce structures | Periodic stronghold/portal-room policy and an optional one-time ocean-monument search with a persisted outcome. The search requires sufficient width and may find no viable position. | Retain the policy; match width gating and honest UI wording. |
| Rims | Breakable walls on both edges, ten material palettes/presets, three exposed patterns, thickness 1–32 and decay 0–100%. Engineered motifs/relief and patterned underside are retained. | Existing generation is present. Port filtered display materials and the new chooser UI; preserve legacy saved rims. |
| Atlas source | Production capture is fixed to one sample per block, independent of legacy fidelity IDs. Maximum admitted source is 16,777,216 cells. | Replace fidelity-selected source creation, load expectations, admission and diagnostics together. |
| Atlas content | Surface height, top colour, representative side colour, exposed block light and authored surface-water coverage. Disk format 10; twelve estimated bytes per cell. | Port packed light/water semantics and tests; rebuild old caches safely. |
| Atlas generation | Resumable generation of canonical real chunks; durable progress, pause/resume/stop/retry, live recapture and revisioned streaming. Headless prewarm is an operator workflow. | Retain lifecycle safeguards, expand capacities and update readers/fixtures for format 10. |
| Progressive presentation | Nearby chunks first; immediate fogged proxy, cancellable staged seed approximation, improving Atlas and verified complete height mesh. Real chunks provide all collision and interaction. | Preserve the backport's tested opaque safety floor and exact readiness proof. |
| Local detail | Low/Medium/High are player-local display choices; Medium is default and reset target. They do not change world generation, server source or download resolution. Choice resets on disconnect. | Remove the current server-fidelity fallback; expose direct display buttons and retain local commands. |
| Distant depth | Fragment depth is reconstructed before the far clamp and joined to an ordered tail, avoiding flattened distant depth that caused terrain flicker. | Adapt the forward OpenGL path to GLSL 150 and the existing proxy compositor. |
| Wall quality | Texture-derived face colours, isolated filtered wall mip levels, wrapped circumference UVs, alpha-aware decay filtering and reuse when wall inputs are unchanged. | Port the policy through 1.21.1 block-model, native-image and texture APIs. |
| Land and water transition | Continuous distance haze shared by live terrain and proxy; distant real water converges toward authored Atlas tint/opacity and underwater detail fades. Mainline live coverage uses fixed ordered dithering. | Adapt tone/water/fog corrections across the backport's split terrain passes. Compare the existing continuous coverage path before changing its coverage mechanism. |
| Objects and interaction | Rigid entities/block entities remain seated in the curved frame. Selection outlines and mining overlays now also scale tangent width at the target height. | Add the interaction-only scale correction; do not stretch entity or block-entity models. |
| Sky and clouds | Server-owned Atmosphere/Night/Void backdrop plus Small/Large/None sun; curved, finite-width clouds and fixed physical sky/star presentation. Vanilla time, weather, sleep and gameplay light remain authoritative. | Already present; preserve live operator changes, night/void edge colours and backport sun/proxy order. |
| Networking | Required settings handshake and independent fingerprint verification before world packets; revisioned Atlas and authority-checked generation controls. Missing/incompatible peers are rejected. | Keep the early login insertion and Fabric arrival order; advance Atlas metadata/tile IDs to v4 on both loaders. |
| Operator/player controls | Pause-menu RingWorld panel; `/ringworld atlas status|start|pause|resume`, server sky/sun commands, local `/ringworld lod` and `/ringworld ringlights` controls. Stop is available through the panel. | Match existing permission separation and command meanings. |
| Compatibility/API | Read-only coordinate/pose API version 1, compatibility inventory and early warnings for known conflicting render/topology mods. Shader packs and broad renderer replacement compatibility remain unqualified. | Keep the API and inventory. Create remains benched; no new compatibility claim follows from this port. |
| Distribution | Separate Fabric/NeoForge jars, required matching server/client mod, Fabric API on Fabric, MPL-2.0 and source availability. | Keep Java 21, exact Minecraft 1.21.1 metadata, strict dependency verification and both loader builds. |

The ten wall palettes are Weathered stone, Ancient masonry, Natural rock,
Ring alloy, RingWorld, Overgrown ruin, Clean monolith, Nether fortress,
Obsidian bastion and Timber rampart. The exposed patterns are RingWorld
Structure, Masonry and Gradient; retired IDs remain decodable. The RingWorld
preset uses thickness 7/decay 10%, whereas the source `RingWallStyle.DEFAULT`
uses thickness 5/decay 25%. Match these distinct source values rather than
taking the proposal's default description as authority. The editor's static
sample explicitly depicts thickness 7/decay 0%; it is not a live rendering of
the thickness and decay fields.

## UI design and behavior to duplicate

The visual language uses vanilla Minecraft buttons and typography, a dark
translucent framed panel, pale labels, green selection/status accents and
red errors. Tabs and selected buttons use an asterisk. The new organization
reduces separate configuration screens and puts estimates/technical data
behind optional detail controls. This audit inspected actual retained Ring,
Terrain, Sky, compact Walls, Preview, Display and Technical screenshots,
as well as their source handlers; it did not launch a fresh 1.3 client.

| Surface | Design and required interaction |
| --- | --- |
| Vanilla Create World | Footer shows `RingWorld: Small/Medium/Large/Custom`, with a dimension tooltip. Its normal action opens the unified editor. Applying a used preview seed updates vanilla's seed field and marks its provenance; editing the vanilla seed clears the marker. |
| Ring | Small 2,048×128, Medium 16,384×256 and Large 32,768×512 presets, all with wall height 160; custom circumference, width and wall height fields. Medium is recommended, Small warns about portal access, Large warns about generation cost. Size estimates and Restore saved settings are secondary controls. Invalid values remain in the draft and block Apply/Preview. |
| Terrain | Direct toggles for More structures, Ocean monument search and Continuous ring river with short explanatory text. Monument control is disabled below the supported width (160 blocks). No source-fidelity or Archipelago selector. |
| Walls | Large selected material/pattern image, direct material and preset chooser, all three patterns visible, thickness and decay fields with ranges. The chooser lists all ten options in two columns. Compact mode rearranges the image and controls instead of shrinking every element. |
| Sky | Direct three-way backdrop and three-way sun buttons, simple schematic backdrop swatches, and text explaining that time/gameplay do not change. |
| Preview | Seed field, Reroll, explicit Use, applied-seed label, cancellable/debounced approximate terrain image, proportioned local segment, left/right panning and full-ring navigation strip. Typing/rerolling changes only the candidate. Use locks it; later typing must not silently replace the locked seed. Apply commits settings plus the locked seed to vanilla. Back preserves the draft; Cancel discards it through the dirty-draft flow. |
| Draft dialogs | One draft across tab changes, chooser returns and resize. Cancel/Escape on a changed draft offers Discard/Keep editing. Restore saved settings has Restore/Back confirmation and loads bootstrap configuration, not an existing world's saved geometry. Validation returns to the relevant tab. |
| In-world Generation | Non-pausing, authoritative dimension/progress/state/ETA view. Owner or gamemaster gets start/pause/resume/stop/retry controls; ordinary players get read-only status. Generate and Stop have distinct confirmations; stopping retains generated chunks. Optional Technical details shows mapping, cells, elapsed/rate, build and preview stages. Compact Technical view temporarily replaces the normal progress/action area. |
| In-world Display | Direct Low/Medium/High selection, immediate local change and optional detail information. Explicit text says other players can choose differently. Closing returns to the game. |
| HUD | Concise Atlas generation progress and existing preview/ready state; keep implementation diagnostics in the optional details/logs. |

Keep the normal entry and the fixture entry aligned: mainline still retains
older editor classes for historical automation, but the footer now opens
`RingWorldEditorScreen`. A successful old-screen fixture alone would not
prove the new UI was integrated. Require all 26 creation captures plus the
new generation/display/technical captures at GUI scales 1–4 and the
320×270 logical minimum. Check focus, keyboard activation, Escape, narration,
long seed truncation, validation messages and dialog return destinations.

## Main gaps in the local backport

The [September checkpoint](NEW_FEATURE_PARITY_2026-09-07.md) records 515
Fabric/common and 538 NeoForge cases passing, eight optional-world captures
per loader and nineteen creation/editor captures per loader. It already
contains generation models, engineered walls and sample assets, Atlas side
colours, serial CPU preparation, asynchronous cache writing, coherent surface
publication, sky profiles and local LOD commands. Those results belong to
the September code; they do not qualify the proposed 1.3 update.

The source comparison confirms these missing or differing behaviors:

- The backport's source step still follows saved fidelity (typically 8,
  optional smoke 4), and reset follows server fidelity. Mainline fixes source
  at 1 and reset at Medium.
- Atlas is format 9, metadata/tile v3, and has no authored water nibble.
  Its strict decoder currently rejects light bytes above 15; the format-10
  decoder must intentionally interpret both nibbles.
- Creation uses the prior multi-screen layout; the unified editor, chooser,
  seed Use/Apply provenance, local-segment preview and new map pages are absent.
- Wall colours use map colours; wall textures have a single nearest-filtered
  level and are regenerated whenever the terrain mesh rebuilds.
- The proxy clamps vertex depth to a flat far band. It lacks the new fragment
  reconstruction/tail ordering.
- The interaction transform lacks the height-dependent tangent scale.
- Complete cache saves have the existing interval limit but lack the new
  ten-second quiet-after-last-tile-change condition.
- The backport has its own continuous alpha/blend coverage and streaming
  underlay. Mainline's ordered screen-door coverage is a different renderer
  implementation, not a missing backport feature that can be copied verbatim.

Two previously reviewed correctness issues remain in both the current
backport and reviewed mainline source. Track them as explicit hardening work:
additional-structure locate uses the circumference cap for its Z search too,
which misses candidates on wide bands; legacy wall generation keeps the old
coordinate hash while the distant wall texture uses the newer sampler.
Neither should disappear from the plan merely because upstream retains it.

## Data and resource impact

| Preset | One-block cells | Raw estimate at twelve bytes per cell | Previous Balanced eight-block cells |
| --- | ---: | ---: | ---: |
| Small 2,048×128 | 262,144 | 3 MiB | 4,096 |
| Medium 16,384×256 | 4,194,304 | 48 MiB | 65,536 |
| Large 32,768×512 | 16,777,216 | 192 MiB | 262,144 |

These are calculated source estimates, not measured heap, compressed disk,
transfer time or total GPU allocation. One-block source has 64 times as many
cells as the old Balanced source. Chunk counts and terrain region dimensions
are unchanged; sampling, cache copies and tile traffic increase. Multiple
snapshots, display arrays, mip levels, native buffers and old/new textures
make peak process memory considerably larger than one raw Atlas.

| Local level | Display sample step | Texture cap | Mesh step |
| --- | ---: | ---: | ---: |
| Low | 8 | 4,096×1,024 | 8 |
| Medium | 2 | 16,384×1,024 | 4 |
| High | 1 | 32,768×2,048 | 1 |

Actual axes and texture caps are additionally bounded by geometry and
hardware. High's label does not promise that every axis escapes those limits.
Legacy fidelity IDs remain readable metadata, including upstream FULL ID 4;
they no longer choose production capture or local quality. Settings format 5,
generation format 1 and `settings_v7` remain unchanged. Atlas format advances
9→10 and metadata/tile IDs v3→v4; request v2, revision v1 and control/status v1
keep their existing codecs. This is within-version protocol parity, not
permission for cross-Minecraft client/server connections.

## Implementation sequence

### 1 Establish the baseline and preserve existing behavior

- Start the implementation from local `220179e` with both current loader
  graphs. Retain the existing September evidence and public Beta records.
- Apply the semantic upstream delta from pinned `27bb33d` to `f1b243a` in
  reviewable batches. Replacing the branch with main would discard the
  Java 21, loader, storage and renderer adaptations.
- Keep strict dependency pins. Any genuinely necessary new dependency needs
  a separate reviewed inventory update; the UI and shared data changes do
  not themselves require a blanket toolchain upgrade.
- Record a local comparison baseline for compact UI and the settled, early
  streaming, foliage/Fabulous and production handoff views.

Exit: both existing backport builds/contract suites pass and the retained
baseline references are recorded. No support or release metadata changes.

### 2 Port the one-block source and format 10 contract

Primary files: `RingAtlasFidelity`, `RingTerrainAtlas`, `RingDimensionReport`,
`RingWorldConfig`, `RingWorldSettings`, `RingWorldCreationUiModel`,
`RingAtlasPregenerationService`, Atlas metadata/tile payloads and
`RingClientLodTuning`/`RingLodQuality`.

- Use fixed one-block source at creation, loading, admission, server capture
  and cache identity. Preserve saved dimensions, seed, mapping, wall style,
  river/structure flags and hidden layout IDs.
- Capture water from the real exposed surface fluid during initial sampling
  and live recapture. Pack light into the low nibble and water coverage into
  the high nibble; validate public input ranges independently.
- Carry water through snapshots, disk, tiles, bilinear sampling and averaged
  local downsampling. Add the common accepted `RingWaterColor` tint before
  mip filtering; retain the current dedicated-server colour/light fallbacks.
- Keep strict tile geometry, size and trailing-byte checks. Format 9 light
  rules must not be silently reused for packed format 10 data.
- Invalidate old Atlas caches through normal regeneration/recapture. A cache
  update does not regenerate or replace saved terrain blocks. Exercise
  already-generated chunks as well as new generation.
- Use Medium for initial session, reset and disconnect reset. A local change
  must not alter source, world identity, network subscriptions or other players.
- Extend independent NBT/Atlas evidence readers, lifecycle fixtures and
  expected cell counts. Size admission must use one-block cells even when
  legacy settings say Performance/Balanced.
- Handle old custom saves above the new budget explicitly and fail closed;
  do not resize their geometry or attempt an unbounded allocation.

Exit: both loader builds and focused disk/wire/light/water/downsample/hash
tests pass; a disposable complete Small ring transfers and reopens at
262,144 cells; stale peers/caches fail safely.

### 3 Port interaction correctness and fix retained edge cases

Primary files: `RingObjectTransform`, the 1.21.1 `LevelRendererMixin`,
`ChunkGeneratorLocateMixin`, `RingGenerationBoundary` and `RingWallTexture`.

- Add the physical-radius/base-radius tangent scale to selection and mining
  overlay poses only. Adapt the actual 1.21.1 redirect targets and retain
  rigid entity/block-entity transforms.
- Separate periodic X locate bounds from finite Z search bounds; avoid
  duplicate canonical X visits and use safe arithmetic for bounded searches.
- Share the exact legacy material sampler between real rim generation and
  distant wall sampling, supplying the actual rim face Z and canonical X.

Exit: geometry checks cover Y=-60/64/100 on Small/Medium rings; real outline
and mining captures align; wide-band locate and legacy both-rim/seam cases
pass without changing modern wall generation.

### 4 Port wall material quality and resource reuse

Primary files: `RingWallTexture`, renderer/GPU adapter, `RingWallShaderStyle`,
new `RingWallMaterialColors`/`RingMaterialColorAverage`, sprite accessor and
client mixin declarations.

- Resolve representative north/south block-face colours through 1.21.1 baked
  models and CPU sprite mip images, with alpha weighting and map-colour fallback
  for missing/tinted faces. Preserve ARGB↔ABGR conversion at NativeImage APIs.
- Keep palette lookup on the client owner thread and pass immutable RGB
  arrays to workers. Invalidate on model/resource reload without per-frame
  texture scans or GPU readbacks.
- Build power-of-two, alpha-aware mip chains that keep the four wall strips
  separate. Stop before strips merge; clamp each sampled mip inside its face,
  repeat U and retain unwrapped UV derivatives across the periodic seam.
- Upload/filter through the existing OpenGL adapter. Do not import the
  26.x GpuTexture/UBO API or newer renderer ownership semantics.
- Reuse the wall texture based on geometry/style/seed/palette and sampled
  feature-anchor terrain heights. Block-light-only/interior edits should not
  cause a wall upload; rim anchor edits and resource reload must invalidate it.
- Preserve cleanup for cancellation, failed builds/uploads, resource reload,
  local quality changes and disconnect.

Exit: ten palettes and three patterns work, both faces and decay holes remain
correct, wall-motion shimmer improves, a conspicuous temporary resource pack
changes nearby/distant walls then restores on reload, and unchanged revisions
reuse the texture on both loaders.

### 5 Adapt depth and land water transitions

Primary files: `ring_surface.vsh/.fsh`, split `ringworld_terrain*.fsh`,
`ringworld_handoff.glsl`, shader JSON/uniform registration and the backport
renderer/global-state adapters.

- Adapt mainline's forward-depth fragment reconstruction and continuous far
  tail using 1.21.1's actual projection matrix and [-1,1] OpenGL NDC. Retain
  GLSL 150, opacity-dependent depth and invisible-proxy discard. The reversed
  26.2/26.3 branch is not applicable to this backport.
- Match live and proxy distance haze continuously while preserving ordinary
  environmental fog and Night/Void edge colours.
- Supply current water sprite bounds and adapt live-water tint/opacity,
  underwater fading and translucent fog without treating unrelated blue
  blocks, glass, foliage or tripwire as water.
- Preserve the accepted post-`compileSections` proxy/star placement,
  two-distinct-tick exact chunk readiness, exterior exclusion, adjacent fringe
  guard, `(0,0)` safety floor and proxy-drawn gate. Retain Experiment 19's
  fixed `0.58V→0.68V` proxy ramp and live overlap through `1.02V` as baseline.
- Mainline uses an 8×8 ordered coverage pattern. First retain the backport's
  continuous blend and compare its adapted material/depth result. Only change
  coverage if matching-route evidence improves quality without losing the
  streaming proof or introducing foliage/Fabulous regressions. Record an
  intentional adapter difference rather than claiming literal shader parity.

Exit: settled and streaming visuals improve or match the retained backport
baseline at 6/12/28 chunks, including deep/high targets, moving distant cliffs,
both rims, forest, ocean/river/shoreline, underwater, night, rain and Fabulous.
No unsafe coverage frame, canonical UV seam or new depth-occlusion failure.

### 6 Duplicate the creation and in world UI

Primary files: new `RingWorldEditorScreen` and `RingWallChoiceScreen`,
`RingSeedPreviewScreen`, Create World/seed-tab mixins and owner interface,
`RingWorldMapScreen`, creation/Atlas fixture clients and verifier inventories.

- Translate 26.x `GuiGraphicsExtractor` rendering, Identifier imports,
  widget input, blitting and resource access to 1.21.1 `GuiGraphics`,
  ResourceLocation and current widget APIs. Keep the five tabs and source
  layout measurements/colours/labels recognizable.
- Make the footer open the new editor. Retain one draft across tabs, chooser
  screens and resize, with Apply/Cancel/Discard/Restore semantics above.
- Preserve the detached 1.21.1 preview generator and request-generation gate;
  add local section/panning and the full-ring strip without sharing mutable
  worldgen caches or waiting on workers at world creation.
- Implement candidate versus used seed and vanilla provenance synchronizing.
  Test blank/random and textual/numeric seeds as well as later edits/rerolls.
- Add Generation/Display separation with unchanged server authority; local
  Low/Medium/High must be available to read-only ordinary players.
- Keep technical detail conditional and compact layouts functional. Remove
  old source-fidelity/Archipelago selectors from the ordinary route.
- Extend the existing fixture clients and verification lists; do not create a
  competing capture framework or accept the old nineteen captures as proof
  of the new flow.

Exit: at least the upstream 26 creation views and all redesigned in-world
views/actions pass on both loaders; real seed round-trip, dirty cancellation,
restore, resize and read-only display behavior are verified.

### 7 Measure production cost and complete runtime regression

- Apply the ten-second quiet-window condition before complete cache snapshots;
  preserve incomplete checkpoints and forced final disconnect saves.
- Use independent copied saves and purpose-named disposable worlds. First
  complete Small, then production Medium, then the maximum Large budget.
- Measure source capture/transfer/cache save/load separately from settled
  motion. Record heap/native/GPU estimates, snapshot/mesh/terrain/wall upload
  timings, median/p95/p99 frame time and frames over 50 ms. Test quality
  switches, live edits, reloads and reconnects; retain cancellation/teardown data.
- Use fixed poses/routes and record weather, time, clouds, distance, quality,
  resolution and resource packs. Compare before/after on this Windows worker,
  with heavy jobs serialized. Mainline timings are context, not backport targets.
- Repeat dedicated settings/topology/persistence, aggregate multi-seed
  worldgen/structures, two-client seam gameplay and persisted raid fixtures.
  Include two clients selecting different display levels and live authored
  light/water updates with complete/incomplete caches.
- Repeat map/compass persistence, curved objects, same-process layout switch,
  Overworld/Nether/End lifecycle, production noon/dusk/night/rain and both-rim
  natural seam views.
- Test copied public Beta 2 saves and September format-5/Atlas-9 feature saves,
  including legacy rims, hidden Archipelago and non-default noise mappings.
  Confirm blocks, inventories, entities and dimensions remain unchanged.

Exit: both loader rows have retained results for every applicable gate,
performance regressions have explicit resolutions or limits, and fresh
backport visual review covers the final experience. Missing/failing checks
remain visible; no borrowed mainline PASS.

### 8 Freeze and prepare the backport candidate

- Build one immutable candidate per loader from a clean, identified backport
  commit under the reviewed Java 21 dependency graph. Qualify the exact bytes
  on dedicated and graphical clients; inspect licences and shared contracts.
- Keep Minecraft exactly 1.21.1 and use the backport's minimum-only loader
  release metadata. Inspect the final NeoForge artifact rather than copying
  the newer 26.x staging assumptions.
- Prepare local release jars and any requested client/server packages using
  the fail-closed staging procedure. Run package launch/save/normal-stop and
  source-availability checks for any package intended for distribution.
- Update backport notes with final hashes, evidence boundaries, supported
  behavior and remaining compatibility/performance limits. Publication is a
  subsequent owner-directed action.

Exit: concrete staged artifacts and their audit record are ready for review.

## Remaining limits and scope decisions

The mainline transition is still visible. An Atlas heightfield cannot
reproduce trunks, overhangs, transparent stacks, buildings or thin structures
simply by increasing resolution. Mainline High increased p95 frame time by
about 28–29% on two recorded routes without removing the principal cliff/canopy
mismatch. Adaptive geometry and a separate depth-aware compositing target
were deferred by that comparison gate; they are not required 1.3 features.

Cache snapshots and initial/reload GPU uploads still hitch. Mainline wall
upload measurements around 52–74 ms and a later 52 ms snapshot illustrate
remaining costs; they do not predict this worker's results. Texture reuse
and quiet snapshots reduce repetition without proving hitch-free rendering.

Two 26.3-specific fixes have no matching 1.21.1 mechanism: nullable cached sky
colour protection and a dedicated asynchronous PipelineCache compilation
worker. The backport still needs cold-launch/resource-reload regression,
but copying those absent API classes would not provide parity.

Create/Flywheel compatibility remains benched. Broad shader/renderer-mod
support, new game-version support, re-exposing Archipelago, new adaptive LOD,
world resizing/conversion and publishing are separate work. The target is
the released 1.3 experience through safe 1.21.1 adapters, with the two retained
correctness fixes explicitly included.

## Completion checklist

- [ ] Fixed one-block production source, bounded admission and format-10 water/light data on both loaders.
- [ ] Medium session/reset default and genuinely independent player display choices.
- [ ] Height-correct interaction overlays, wide-band locate and legacy wall material consistency.
- [ ] Filtered texture-derived walls, resource-pack/reload support and valid upload reuse.
- [ ] Ordered distant depth and improved land/water haze through the preserved 1.21.1 compositor.
- [ ] Five-tab draft editor, Use/Apply seed flow, chooser, compact UI and new in-world pages.
- [ ] Quiet cache snapshots and measured Small/Medium/Large resource/performance behavior.
- [ ] Copied-save, dedicated, multiplayer, worldgen, lifecycle and visual regression evidence for both loaders.
- [ ] Clean immutable candidate hashes, licence/source/metadata inspection and applicable packaged smokes.
- [ ] Owner visual review and accurate final backport documentation before any publication decision.
