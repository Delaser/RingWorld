# Optional visual features technical datasheet

This document is the engineering contract for reproducing PR
[#244](https://github.com/Delaser/RingWorld/pull/244) on Minecraft 1.21.1.
The companion
[`OPTIONAL_VISUAL_FEATURES_CHANGELOG_2026-08-30.md`](OPTIONAL_VISUAL_FEATURES_CHANGELOG_2026-08-30.md)
describes the intended user experience.

## 1. Authority and integration rule

The source implementation is the range `7903311..2733581` on
`codex/optional-features-156-155-162`. Its common behavior is authoritative.
Minecraft 26.x APIs, mixin descriptors, GUI classes, shader pipeline details,
payload registration APIs, and Gradle run definitions are not.

Backport policy:

1. preserve shared data meanings, stable IDs, validation, migration, and
   failure behavior;
2. reuse loader-neutral classes where they compile cleanly on Java 21;
3. isolate Minecraft 1.21.1 differences under `versions/mc1.21.1/` and the
   existing Fabric/NeoForge adapter surfaces;
4. do not copy Minecraft 26.x adapter code into common classes;
5. do not read or include decompiled Minecraft source; and
6. do not claim support until the backport has its own runtime evidence.

## 2. Change identity

| Contract | Before this batch | After this batch |
| --- | ---: | ---: |
| `RingWorldSettings.FORMAT_VERSION` | 3 | 4 |
| `RingLayoutFingerprint` internal version | 2 | 3 |
| `RingGenerationBoundary.RIM_STYLE_VERSION` | 1 | 3 |
| `RingTerrainAtlas.FORMAT_VERSION` | 6 | 8 |
| `RingWallStyle.FORMAT_VERSION` | absent | 1 |
| `RingSkyProfile.FORMAT_VERSION` | absent | 1 |
| `RingTerrainPreview.FORMAT_VERSION` | absent | 1 |
| complete settings payload | `settings_v3` | `settings_v5` |
| settings acknowledgement | `settings_ack_v3` | unchanged |
| live sky payload | absent | `sky_profile_v1` |
| staged preview payload | absent | `terrain_preview_v2` |

The skipped channel numbers are intentional historical identities. Never
rename `settings_v5` to look sequential: channel names are protocol ABI.

## 3. Data ownership

| Data | Owner | Persistent | Identity-bearing | Live mutable |
| --- | --- | --- | --- | --- |
| Dimensions, seed, terrain mapping | Overworld `RingWorldSettings` | yes | yes | no |
| Wall style | Overworld `RingWorldSettings` | yes | yes | no |
| Sky/sun profile | Overworld `RingSkySettings` | yes | no | yes, gamemaster command |
| Atlas height/colour/light cells | dimension-owned Atlas cache | yes | derived from layout | yes, revisioned recapture |
| Staged terrain preview | server job and client session | no | world-hash checked | replaced stage-by-stage |
| Creation seed preview | creation-screen process state | no | seed/layout hash only | cancelled/rebuilt on edit |
| Ring-light Gamma tuning | client process | no | no | yes, local command |

Do not move sky presentation into `RingWorldSettings`: doing so would make a
visual command alter immutable terrain identity and invalidate the Atlas.

## 4. Wall-style contract

### 4.1 Record and codec

The common record is:

```java
RingWallStyle(
    int thicknessBlocks,
    Palette palette,
    Pattern pattern,
    int decayPercent,
    int formatVersion
)
```

Validation:

- thickness: 1–32 inclusive;
- decay: 0–100 inclusive;
- non-null palette and pattern; and
- format must equal 1.

Saved codec keys are `thickness`, `palette`, `pattern`, `decay`, and `format`.
Palette and pattern are saved as stable numeric IDs, not enum ordinals.

### 4.2 Stable palette IDs

| ID | Enum | Dominant real blocks |
| ---: | --- | --- |
| 0 | `WEATHERED` | cobblestone, mossy cobblestone, stone, andesite |
| 1 | `ANCIENT` | stone brick, cracked/mossy brick, cobblestone |
| 2 | `NATURAL` | stone, tuff, andesite, cobblestone, moss |
| 3 | `ALLOY` | smooth stone, polished diorite, quartz, prismarine brick |
| 4 | `INDUSTRIAL` | deepslate brick/tile, basalt, tuff brick, raw copper |
| 5 | `OVERGROWN` | stone brick, mossy/cracked brick, cobblestone, moss |
| 6 | `MONOLITH` | smooth stone, calcite, polished andesite |
| 7 | `NETHER` | Nether/red Nether brick, blackstone, magma |
| 8 | `OBSIDIAN` | obsidian, crying obsidian, blackstone, gilded blackstone, amethyst |
| 9 | `WOOD` | oak/spruce/dark-oak logs, oak planks, stripped spruce |

Industrial sea lanterns are a separate deterministic one-per-thousand sample.
Do not fold them into the 0–99 material roll; that would raise the minimum
frequency to 1%.

### 4.3 Stable pattern IDs

| ID | Enum | New-world selectable |
| ---: | --- | --- |
| 0 | `CLUSTERED` | no; decode only |
| 1 | `MASONRY` | yes |
| 2 | `STRATA` | no; decode only |
| 3 | `PANELS` | yes |
| 4 | `GRADIENT` | yes |
| 5 | `HYBRID` | yes |

`RingWallPattern` owns deterministic material, rare-accent, and block-presence
sampling. Canonical X must be wrapped before hashing so the generated style is
periodic at the seam. Layered coordinate hashes produce coarse structural
regions, medium fractures, and block-scale noise. Decay must remain
top-connected per `(canonical X, rim depth)` column.

### 4.4 World-generation integration

`RingWorldGeneratorAccess` gains wall-style get/set methods.
`NoiseChunkGeneratorMixin` stores the style and defaults to `LEGACY` until the
Overworld attaches its saved settings. `RingWorldServer` installs the style
before generation work can use it.

`RingGenerationBoundary.installRim` receives `(chunk, geometry, wallHeight,
style, worldSeed)`. It scans every Z in the chunk that overlaps either wall,
computes inward depth, removes stale block entities, and either places the
sampled material or air for top-connected decay.

All wall material recognition, migration, Atlas colour derivation, and tests
must include the complete palette block set. The single
`styledRimBlockForRoll(style, roll)` mapping is the source of truth for both
real blocks and distant-wall colours.

### 4.5 Dependent geometry

Replace assumptions that the wall is always five blocks thick in:

- dimension validation and the live maths report;
- playable interior bounds;
- portal search, candidate filtering, and portal-creation anchors;
- complete and incomplete Atlas mesh limits;
- shader wall-proxy dimensions; and
- confirmation/configuration UI copy.

Compatibility overloads may retain the old five-block constant only for legacy
tests and callers. Authoritative loaded-world paths must use the saved style.

## 5. Persistence and migration

### 5.1 RingWorld settings format 4

`RingWorldSettings` adds optional codec field `wallStyle`, defaulting to
`RingWallStyle.LEGACY`. The format-4 constructor requires a style. Formats 1–3
must reject any non-legacy style.

Migration behavior:

```text
format 1/2 -> retain legacy terrain mapping -> LEGACY wall -> format 4
format 3   -> retain saved terrain mapping  -> LEGACY wall -> format 4
new world -> current terrain mapping         -> configured wall -> format 4
```

The `LEGACY` style is exactly 5 thick, Weathered palette, Clustered pattern,
0% decay. The new default is not a migration default.

### 5.2 Layout fingerprint

Fingerprint version 3 mixes all former fields plus:

- thickness shifted by 9;
- palette ID shifted by 13;
- pattern ID shifted by 21;
- decay shifted by 29;
- wall-style format shifted by 37; and
- rim semantic version 3 shifted by 25.

Use the existing `RingLayoutFingerprint.mix` sequence and unsigned integer
conversion exactly. Both peers independently recompute it.

### 5.3 Sky saved data

`RingSkySettings` is separate Overworld saved data with storage ID
`ringworld:sky_settings`. Its codec contains one `profile` field using
`RingSkyProfile.CODEC`. Missing data defaults to Atmosphere + Small and is
marked dirty. A new world writes the bootstrap profile alongside creation of
the immutable RingWorld settings.

Legacy bootstrap `skyPreset` values map as follows:

| Old combined value | Sky | Sun |
| --- | --- | --- |
| `MINECRAFT_ATMOSPHERE` | Atmosphere | Small |
| `SPACE_HABITAT` | Night | Small |
| `DISTANT_STAR` | Night | Large |
| `NIGHT_HABITAT` | Night | None |
| `MINIMAL_VOID` | Void | None |

## 6. Network ABI

### 6.1 Complete settings

`ringworld:settings_v5` field order is:

```text
VarInt width
VarInt circumference
Long generatorSeed
VarInt wallHeight
VarInt surfaceReferenceY
VarInt terrainNoiseMapping
VarInt wallThickness
VarInt wallPaletteId
VarInt wallPatternId
VarInt wallDecayPercent
VarInt wallStyleFormat
VarInt skyBackdropId
VarInt skyLightSourceId
VarInt skyProfileFormat
VarInt ringSettingsFormat
Long layoutFingerprint
```

The sky fields are included so the first installed client frame has a complete
profile, but they are excluded from the fingerprint. `settings_ack_v3` remains
unchanged because its own byte layout is unchanged.

### 6.2 Live sky profile

`ringworld:sky_profile_v1` is S2C:

```text
VarInt backdropId
VarInt lightSourceId
VarInt profileFormat
```

Backdrop IDs: Atmosphere 0, Night 1, Void 2. Light-source IDs: Small 0,
Large 1, None 2.

### 6.3 Staged terrain preview

`ringworld:terrain_preview_v2` is S2C:

```text
Long atlasWorldHash
VarInt stageWireValue
ByteArray compressedPreview (maximum 2 MiB)
```

The compressed preview body is zlib/deflate:

```text
u8 previewFormat (=1)
i64 worldHash
u16 columns
u16 rows
repeat columns*rows:
    i16 height
    u8 red
    u8 green
    u8 blue
EOF (trailing data is an error)
```

Limits are 4,096 columns, 64 rows, 262,144 cells, and 2 MiB compressed. The
client rejects invalid size, format, dimensions, trailing data, unknown stage,
or a world-hash mismatch.

### 6.4 Capability gate

Both loader servers must register and require `settings_v5`, `sky_profile_v1`,
and `terrain_preview_v2`. Both clients must register handlers before login.
Missing required channels produce a clear RingWorld out-of-date disconnect;
never reuse an old channel ID with a longer codec, because Netty will reject
unread trailing bytes before RingWorld can explain the mismatch.

## 7. Atlas format 8

### 7.1 Persistent cell

Each gzip-compressed disk cell contains:

```text
boolean present
i16 exposedTopFaceHeight
i32 RGB (low 24 bits)
i8 exposedBlockLight (0..15)
```

The file header remains magic, format, world hash, geometry, step, grid size,
and revision. Format mismatch invalidates and rebuilds the cache; do not attempt
an in-place format-6/7 cell migration.

Tile encoding/decoding carries the same present, height, colour, and unsigned
block-light fields. An absent incoming tile cell must not erase a present cell
from a more complete local cache. Equality/revision decisions include light.

### 7.2 Sampling and invalidation

`RingAtlasPregenerationService` records the light level at the exposed surface
sample. Surface block colour rules remain the former texture-corrected biome
rules, with mycelium explicitly set to `0x6F6365`.

When a relevant surface block/light changes, invalidate the changed Atlas cell
and the cell footprint covering the vanilla 15-block light radius. X expansion
must wrap periodically. Coalesced dirty tiles retain the established ordered
revision-commit protocol.

### 7.3 GPU representation

The GPU surface texture uses RGB for terrain albedo and alpha for normalized
block-light strength. Mips average channels independently; unlit cells remain
opaque terrain rather than becoming transparent or darkening neighboring RGB.

The fragment shader applies a warm additive contribution at night and fades it
to zero with daylight exposure. `GlobalSettingsMixin` appends the local tuning
mode/falloff/peak values to the RingWorld globals written every frame.

Default Gamma parameters are falloff `2.0`, peak `1.25`; clamp ranges are
0.5–6.0 and 0.1–3.0. These are client process state only.

## 8. Seed-preview pipelines

There are two consumers of the same loader-neutral sampler.

### 8.1 Creation UI

`RingSeedPreviewScreen` obtains the real pending seed through the
world-creation-screen adapter, builds current geometry from the editor, and
runs `RingTerrainPreviewSampler.generate` on a daemon executor. It debounces
edits, increments a generation token, cancels stale futures, and uploads only
the matching result as a dynamic texture. Closing the screen releases the GPU
texture and worker state. No world save may be created.

Minecraft 1.21.1 needs a version adapter for reading/writing the pending seed
and obtaining the selected generator/registry context. Do not make the common
sampler depend on the 1.21.1 Create World screen class.

### 8.2 In-world placeholder

After settings acknowledgement, the server sends Atlas metadata. If the Atlas
is incomplete and previews are enabled, one single-threaded daemon job per
Overworld computes Current, High, Very high, and Ultra in order. Completed
stages are published back on the server thread and sent to connected clients.
The latest stage is immediately sent to later joiners.

The job is cancelled when the world unloads, the world hash changes, or the
authoritative Atlas becomes complete. `-Dringworld.disableSeedPreview=true`
disables this disposable path for diagnostics.

`RingSurfacePlaceholder.resolve` behavior is:

```text
complete Atlas        -> not a placeholder path
missing preview       -> neutral #6B706F at reference height
real Atlas cell       -> authoritative colour/height
missing Atlas cell    -> current preview colour/height, sampled at GPU resolution
```

Do not restore the retired procedural placeholder or vertical colour smearing.
Preview fog is capped at 20%; grey fallback fog is capped at 88%, both clearing
with the established quintic smoothstep of Atlas completion.

## 9. Renderer changes

### 9.1 Wall and terrain separation

The terrain height mesh ends at the two inner wall faces. Wall proxies are
closed prisms with inner, outer, and top faces and persist after Atlas
completion because a height field cannot represent a vertical wall. A
half-block hidden overlap below each inner face prevents a depth crack.

`RingWallShaderStyle` converts the authoritative real-block palette to up to
five map colours, encodes pattern/decay/seed metadata, and supplies both the
palette matrix and vertex colour. `RingSurfaceGpu.createVertexBuffer` therefore
adds a `vertexArgb` argument in both 26.1 and 26.2 adapters; the 1.21.1 adapter
must expose the equivalent vertex-colour path for its buffer API.

### 9.2 Sky

`SkyRenderingMixin` must adapt these behaviors to 1.21.1 descriptors:

- force the visual sun/moon angle to the fixed RingWorld angle;
- counter-rotate star orientation using canonical longitude;
- Atmosphere leaves Minecraft's upper sky colour intact;
- Night uses `#050810` and at least 0.88 star brightness;
- Void uses `#010103` and zero star brightness;
- suppress vanilla moon and camera-relative sun;
- render the selected fixed sun at half-width 3 (Small), 15 (Large), or not at
  all (None);
- use the existing smooth day-cycle tint/intensity; Large multiplies alpha by
  0.72;
- render a matching lower sky hemisphere and suppress the later black lower
  disc; and
- render the ring proxy before the selected centred sun.

`FogRendererMixin` must be retargeted to the 1.21.1 fog method. For an
Atmosphere profile it blends fog toward live sky colour across the final 16
blocks below wall top. Night fog is `#050810`; Void fog is `#010103`. Fluid fog
is never overridden.

The ring fragment shader receives backdrop ID. Atmosphere edge pixels retain
live fog matching; Night/Void edge pixels blend to their exact backdrop colour
to remove pale outlines.

## 10. UI and commands

### 10.1 Creation screens

`RingWorldCreationScreen` adds:

- a `Rim` button opening `RingWallStyleScreen`;
- independent `Sky` and `Sun` cycling controls;
- `Seed preview` beside `Use layout` and `Back`;
- selected rim thickness in dimension validation and metrics; and
- confirmation text including wall and sky choices.

The rim editor has two rows of ten presets, palette and selectable-pattern
cycling, thickness and decay fields, live validation, descriptors/materials,
and responsive layouts down to 320×270 logical size.

### 10.2 Server commands

`/ringworld sky` and `/ringworld sun` retain the existing gamemaster permission
gate. They update Overworld `RingSkySettings`, mark saved data dirty, and
broadcast `sky_profile_v1` to capable players.

### 10.3 Client command

`/ringworld ringlights` is registered through the loader's client-command API.
It must not be sent to the server. `show`, `reset`, and numeric arguments update
only `RingAtlasLightTuning` and shader globals.

### 10.4 HUD and diagnostics

`RingAtlasHudRenderer` draws existing generation progress at scale 0.5 in the
top-left and renders nothing after completion. The version-owned GUI mixin
delegates to this common renderer. The four staged-preview status rows appear
on `RingWorldMapScreen` using `RingTerrainPreviewHud`, not on the play HUD.

## 11. Loader and version adapter inventory

### Shared/common classes added

- `RingWallStyle`, `RingWallPattern`
- `RingSkyProfile`, `RingSkySettings`
- `RingTerrainPreview`, `RingTerrainPreviewSampler`,
  `RingTerrainPreviewStage`, `RingTerrainPreviewHud`
- `RingAtlasLightProfile`
- `RingSkyProfilePayload`, `RingTerrainPreviewPayload`
- `RingTerrainPreviewGenerator`

### Shared client classes added

- `RingWallStyleScreen`, `RingSeedPreviewScreen`
- `RingAtlasHudRenderer`, `RingAtlasLightTuning`
- `RingWallShaderStyle`
- development capture clients for appearance, lighting, and seed preview

### Loader adapters changed

- Fabric/NeoForge server payload registration and send capability checks;
- Fabric/NeoForge client payload registration and handlers;
- Fabric/NeoForge client command registration;
- client session install/clear behavior for wall, sky, preview, and Atlas light;
- loader development-run and capture task wiring.

### Minecraft-version adapters changed

- GUI hidden-state setter used by deterministic capture clients;
- GUI/HUD mixin delegation to the compact Atlas renderer; and
- GPU vertex-buffer creation with supplied ARGB.

### Mixin changes

- `NoiseChunkGeneratorMixin`: saved wall-style attachment;
- `PortalForcerMixin`: style-thickness-safe portal bounds;
- `CreateWorldScreenMixin`: seed-preview/world-creation access;
- `GlobalSettingsMixin`: wall/style/backdrop/light shader globals;
- `SkyRenderingMixin`: physical star field, profile sky/sun, lower atmosphere;
- new `FogRendererMixin`: rim-top and dark-profile fog matching.

Every 1.21.1 mixin target and descriptor must be verified against official
mappings. A successful compilation alone is not descriptor/runtime evidence.

## 12. Recommended backport order

1. **Pure models/tests:** wall style/pattern, sky profile, light profile,
   preview container/stages/HUD policy.
2. **Persistence:** settings format 4, legacy migration, sky saved data,
   fingerprint v3, Atlas format 8.
3. **Server worldgen:** attach style, generate palettes/decay, propagate real
   thickness to reports and portal bounds.
4. **Protocol models:** `settings_v5`, `sky_profile_v1`, `terrain_preview_v2`.
5. **Fabric and NeoForge transport:** registration, capability gates,
   handlers, disconnect policy, session cleanup.
6. **Atlas service:** colour/light capture, radius invalidation, preview worker,
   staged publication, cache rebuild.
7. **Creation UI:** world-creation seed adapter, rim editor, preview screen,
   responsive capture hooks.
8. **Renderer:** placeholder merge, closed wall mesh, shader palette/light,
   star/sky/sun/fog paths.
9. **Commands:** live server sky/sun and local ring-light tuning.
10. **Qualification:** unit, both loader builds, server, two-client, GUI,
    Atlas lifecycle, graphical review, package review.

This order deliberately lands identities and migration before the rendering
that consumes them. Do not temporarily emit a different wire format under the
final channel IDs.

## 13. Minimum test matrix for 1.21.1

| Area | Required evidence |
| --- | --- |
| Pure tests | all wall IDs/presets, deterministic seam samples, top-connected decay, sky IDs, preview codec/limits, light-profile bounds |
| Storage | formats 1/2/3 migrate to legacy wall; format 4 round-trips custom wall; sky persists independently; invalid formats fail closed |
| Fingerprint | every wall field changes identity; sky changes do not; client/server recomputation matches |
| Atlas | format 8 save/load/tile round-trip; old format rebuild; mycelium colour; 0–15 light; absent tile cannot erase cached cell |
| Worldgen | all ten palettes, four selectable patterns, 0/25/70/100 decay, both rims, X=0 periodicity, reload |
| Portals | minimum/maximum safe Z respects 1- and 32-block walls; existing and newly created portals remain in playable interior |
| Network | matching login succeeds; old `settings_v3` peer fails clearly; preview/sky malformed payloads fail closed; reconnect clears stale state |
| Seed preview | two seeds produce different identities; correct aspect ratio; edit cancellation; no save/chunks; normal close |
| Atlas transition | grey -> four preview stages -> authoritative cells -> complete mesh; no smear, open wall, grey ramp, or inner-face crack |
| Lighting | day suppression, night pinpricks, lamp add/remove, 15-block footprint, X=0 invalidation, local command bounds |
| Sky | every 3×3 sky/sun combination, half-lap star inversion, dusk wall-top horizon, no dark edge glow, vanilla gameplay clock unchanged |
| Multiplayer | dedicated server plus two clients see same wall/sky; live sky update; Atlas revision sync; old client rejected |
| Packaging | exact Fabric and NeoForge jars, MPL metadata/licence, no 26.x class or dependency leakage |

## 14. Useful source tests and fixtures

The 26.x implementation extended or added these reusable pure suites:

- `RingWallStyleTest`
- `RingSkyProfileTest`, `RingSkyCycleTest`
- `RingTerrainPreviewTest`, `RingTerrainPreviewHudTest`
- `RingAtlasLightProfileTest`
- `RingWorldSettingsStorageTest`
- `RingLayoutFingerprintTest`
- `RingTerrainAtlasTest`
- `RingSurfacePlaceholderTest`, `RingSurfaceMeshTest`,
  `RingSurfaceGenerationFogTest`, `RingSurfaceLodTest`
- `RingSettingsHandshakeTest`, `RingProtocolIdentityTest`

The graphical source fixtures are:

- 17-capture creation/settings/seed-preview flow;
- 11-capture Atlas UI/lifecycle flow;
- same-seed appearance comparison gallery;
- small Atlas-light comparison client; and
- Medium Industrial village-light review world.

Port the fixture intent and assertions. Do not preserve 26.x method descriptors
or Gradle run configuration when 1.21.1 supplies a different launch API.

## 15. Backport risks

| Risk | Why it matters | Mitigation |
| --- | --- | --- |
| 1.21.1 world-creation internals differ | pending seed/generator context may not be exposed the same way | narrow version adapter; prove no-save preview with two seeds |
| 1.21.1 rendering pipeline predates 26.x GPU APIs | buffer, uniform, sky and fog hooks will differ materially | preserve renderer inputs/outputs; implement version-owned GPU/mixin adapters |
| Payload APIs differ between loader versions | registration timing and capability queries may differ | keep codecs common; isolate transport; test old-peer rejection on each loader |
| Background generator sampling | Minecraft generator objects may have thread-affinity differences | retain cancellation and server-thread publication; stress test joins/unload; move sampling behind a safe executor policy if required |
| Expanded invalidation | light-radius updates can create many dirty cells | coalesce into existing tile revision batches; retain bounded publication cadence |
| Save identity divergence | changing IDs/defaults creates incompatible layouts | copy stable numeric IDs and legacy/default distinction exactly |
| Shader ABI drift | extra globals can misalign uniforms silently | version the shader/global contract and verify actual rendered captures |
| Wall blocks unavailable/renamed | palette may not map one-to-one | use the closest 1.21.1 vanilla block only with an explicit documented mapping and owner review |

## 16. Handoff checklist

- [ ] Rebase or verify source head before implementation; record any commits
  after `2733581` that alter this contract.
- [ ] Copy these two handoff documents into the active backport branch.
- [ ] Link the implementing PRs/issues to this datasheet.
- [ ] Keep shared models loader-neutral and Java 21 compatible.
- [ ] Record every 1.21.1 adapter difference in this directory.
- [ ] Run focused tests after each numbered integration stage.
- [ ] Run complete dual-loader backport qualification before any support claim.
- [ ] Update user documentation, protocol docs, operations docs, rendering
  docs, and `AGENTS.md` when the backport behavior lands.
