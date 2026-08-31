# Optional visual features changelog

This is the human-readable handoff for the optional visual feature batch
developed after RingWorld 1.1. It tells the Minecraft 1.21.1 backport team what
players and operators should receive. Implementation contracts and the exact
backport sequence are in
[`OPTIONAL_VISUAL_FEATURES_TECHNICAL_DATASHEET_2026-08-30.md`](OPTIONAL_VISUAL_FEATURES_TECHNICAL_DATASHEET_2026-08-30.md).

## Source change set

| Item | Value |
| --- | --- |
| Source baseline | `7903311` (`main`, RingWorld 1.1 publication merge) |
| First feature commit | `b404495` |
| Current feature head | `2733581` |
| Source branch | `codex/optional-features-156-155-162` |
| Pull request | [#244](https://github.com/Delaser/RingWorld/pull/244) |
| Backport target | `port/mc-1.21.1` |
| Status when written | 26.2 Fabric and NeoForge development checks pass; not yet a frozen release candidate |

The source range is:

```sh
git diff 7903311..2733581
```

Do not treat the four commits as a guaranteed conflict-free cherry-pick onto
Minecraft 1.21.1. They include Minecraft 26.1/26.2 renderer, GUI, networking,
world-generation, and build-fixture code. The behavior described below is the
contract; the technical datasheet identifies the adapter boundaries.

## Player-facing additions

### Configurable ring walls

New RingWorlds can select the construction and condition of both finite rim
walls. The saved style controls:

- wall thickness from 1 to 32 blocks;
- one of ten material palettes;
- one of four selectable pattern families; and
- top-edge decay from 0% to 100%.

The ten presets are:

| Preset | Thickness | Palette | Pattern | Decay |
| --- | ---: | --- | --- | ---: |
| Weathered | 5 | Weathered stone | Masonry | 25% |
| Ancient | 6 | Ancient masonry | Masonry | 40% |
| Escarpment | 8 | Natural rock | Gradient | 15% |
| Ring alloy | 5 | Ring alloy | Panels and ribs | 5% |
| Industrial | 7 | Industrial | Panels and ribs | 10% |
| Overgrown | 6 | Overgrown ruin | Hybrid | 70% |
| Monolith | 4 | Clean monolith | Panels and ribs | 0% |
| Nether | 7 | Nether fortress | Masonry | 25% |
| Obsidian | 5 | Obsidian bastion | Panels and ribs | 8% |
| Wood | 4 | Timber rampart | Panels and ribs | 20% |

The Industrial palette includes deterministic sea-lantern accents at 0.1% of
wall blocks. Decay follows broad failure zones and smaller fractures across the
wall's length and thickness. It removes material only from the top edge down;
it does not punch isolated holes through otherwise solid wall columns.

`Clustered` and `Strata` pattern IDs still decode so development saves remain
readable, but they are no longer offered when creating or editing a style.

The distant Atlas wall now derives its colours from the exact block palette
used by real generation. It also renders closed inner, outer, and top faces.
Changing a preset therefore does not require a separately maintained shader
colour table, and the incomplete-Atlas view no longer shows an open-backed wall
curtain.

### Independent sky and sun choices

World creation now exposes two independent visual settings:

- **Sky:** Atmosphere, Night, or Void.
- **Sun:** Small, Large, or None.

The profile is owned and saved by the server. Gamemasters can change it while
the world is running:

```text
/ringworld sky atmosphere|night|void
/ringworld sun small|large|none
```

Changes are broadcast immediately and survive a restart. They do not change
the vanilla day clock, skylight, weather, spawning, sleep, crop growth, or
daylight-sensor behavior.

The fixed star field is now oriented in physical ring space. Travelling half a
lap rotates the local view of the same stars by 180 degrees instead of pinning
them to the player's camera. Dark sky choices also remove the bright proxy edge
glow. A matching lower sky hemisphere and a short fog blend below the wall top
remove the flat vanilla horizon line when viewing from a rim at dusk.

### Fast seed preview during world creation

The RingWorld creation editor has a **Seed preview** action. It reads the actual
seed entered in Minecraft's world-creation state and shows a full-loop map at
the selected circumference-to-width ratio.

The preview:

- runs asynchronously;
- cancels stale work when the seed or layout changes;
- samples the real periodic terrain-height and biome sources;
- creates no chunks, structures, caves, or save directory; and
- is explicitly an approximation, not a promise of exact block placement.

The image is letterboxed to its true aspect ratio rather than stretched into a
conventional square map.

### Progressive in-world Atlas placeholder

An incomplete Atlas no longer starts with the old procedural green map. It
starts as neutral grey and is replaced by seed-derived terrain stages:

| Stage | Colour texture | Terrain-height samples |
| --- | ---: | ---: |
| Current | 512×16 | 128×8 |
| High | 1,024×32 | 256×16 |
| Very high | 2,048×32 | 512×16 |
| Ultra | 4,096×64 | 1,024×32 |

Each better stage replaces the previous one. Authoritative Atlas cells always
override preview cells as real surface sampling progresses. The preview keeps
a light 20% maximum haze; the grey fallback can use up to 88% generation fog.
The former colour-smear effect at the boundary of generated data is removed.

The normal HUD displays a half-size `Ring Atlas Generating: X%` indicator and
hides it when complete. Stage-by-stage preview diagnostics live in the Atlas
map/status screen rather than permanently occupying the play HUD.

### Distant nighttime lights

Atlas format 8 stores the exposed block-light value for every surface cell.
At night, lamps and settlements appear on the distant ring as restrained warm
pinpricks. The effect fades out in daylight and does not change gameplay light.

Surface invalidation covers the nearby 15-block light footprint, including
across canonical X=0, so placing or removing a light can update the completed
Atlas without regenerating the full ring.

The approved default rendering curve is Gamma with falloff `2.0` and peak
`1.25`. Development clients can tune it live without changing server or saved
state:

```text
/ringworld ringlights
/ringworld ringlights show
/ringworld ringlights reset
/ringworld ringlights <falloff> <peak>
```

Accepted ranges are 0.5–6.0 for falloff and 0.1–3.0 for peak. The tuning is
process-local and intentionally not persisted.

## Corrections included in the batch

- Mushroom Fields/mycelium Atlas cells use measured vanilla top-texture colour
  `#6F6365` instead of Minecraft's saturated pink map colour.
- The complete Atlas terrain mesh stops at the playable inner wall faces.
  Closed style-derived wall proxies remain separate, eliminating the false grey
  ramp produced when a height field tried to represent a vertical wall.
- A hidden half-block overlap beneath each inner wall face closes a visible
  depth/projection crack between terrain and the distant wall.
- Dark sky modes blend distant-ring edges into their own backdrop colour rather
  than atmosphere fog.
- The wall style's real thickness is used for playable-interior calculations,
  dimension reports, portal search/creation bounds, Atlas meshing, and UI maths.

## World and multiplayer compatibility

- RingWorld settings advance from format 3 to format 4.
- Worlds created before configurable walls migrate to the exact former
  five-block cobblestone/mossy-cobblestone style. They are not silently given
  the new 25% decay default.
- The wall style is immutable terrain/layout identity and is included in the
  layout fingerprint and Atlas world hash.
- The sky profile is saved separately because it is visual-only and may change
  live. It is excluded from layout and Atlas identity.
- Atlas disk caches advance to format 8. Older caches rebuild automatically.
- The complete settings channel changes to `ringworld:settings_v5`. Old clients
  and servers must fail cleanly rather than decoding a changed payload prefix.
- `ringworld:sky_profile_v1` carries live sky changes.
- `ringworld:terrain_preview_v2` carries disposable seed-preview stages.
- Both the server and every player still require a matching RingWorld build.

## Configuration additions

New bootstrap properties are:

```properties
wallPreset=WEATHERED_FORTIFICATION
wallThicknessBlocks=5
wallPalette=0
wallPattern=1
wallDecayPercent=25
wallStyleFormat=1
skyBackdrop=ATMOSPHERE
sunStyle=SMALL
```

The in-game editor is preferred over manual numeric palette/pattern editing.
The old combined `skyPreset` property is still read and mapped to the equivalent
new sky/sun pair.

## Validation completed on the source implementation

As of 2026-08-30:

- 373 shared unit/parameterized tests pass on Fabric and NeoForge against the
  manifest-pinned Minecraft 26.2 inputs;
- both loaders pass the 17-capture creation/settings/seed-preview fixture;
- both loaders pass the 11-capture Atlas UI fixture, including completion,
  a live block revision, disconnect, and cleared client state;
- a matched same-seed Fabric gallery covers all ten wall presets and five
  representative sky/sun combinations; and
- the owner approved the wall, sky/sun, staged preview, creation seed preview,
  and Gamma Atlas-light presentation.

This evidence is source-state development evidence. It is not a 1.21.1 test
result and must not be reused as proof that the backport works.

## Deliberate limits

- The seed preview does not predict structures, caves, trees, ores, or exact
  block surfaces.
- Atlas lights are coarse exposed surface light, not per-block shadows or
  transparent volumetric lighting.
- Ring-light tuning is a development command, not a saved user preference.
- The distant wall does not yet stream arbitrary player construction/damage as
  a dedicated vertical-wall overlay. Real nearby chunks remain authoritative.
- No Create compatibility work is part of this handoff.
- This batch does not change canonical topology, nearest-image gameplay,
  gravity, the finite Z ownership model, or Nether/End behavior.

## Backport completion criteria

The 1.21.1 implementation is complete only when it provides the same behavior,
migration, stable IDs, and failure policy and has its own Java 21 evidence for:

1. settings/storage migration and fingerprint identity;
2. wall generation, decay, seam periodicity, portal-safe bounds, and reload;
3. creation UI and chunk-free seed preview;
4. settings, sky, preview, Atlas tile, and light networking on both loaders;
5. incomplete-to-complete Atlas rendering and session cleanup;
6. sky orientation, dark-edge blending, lower horizon, and sun choices;
7. live surface-light invalidation across X=0;
8. dedicated-server and two-client operation; and
9. real graphical review on the exact 1.21.1 Fabric and NeoForge artifacts.
