# Optional world generation

The new-world generation panel exposes Continuous ring river and More structures,
both Off by default. These choices are saved with the world and shared by both
loaders. Archipelago remains implemented but is hidden for this release.

## Atlas detail

The server captures one sample per block for every world. World creation shows
the source size but offers no quality selector. The 16,777,216-cell limit applies
to this fixed source; reduce Around or Across if the ring exceeds it.

Each player independently selects Low, Medium or High on the RingWorld Map or
with `/ringworld lod`. Medium is the default and reset target. Changing detail
does not alter generation settings or regenerate the server Atlas.

| Client detail | Display sample step | Maximum texture | Height-mesh step |
| --- | ---: | ---: | ---: |
| Low | 8 blocks | 4,096×1,024 | 8 blocks |
| Medium | 2 blocks | 16,384×1,024 | 4 blocks |
| High | 1 block | 32,768×2,048 | 1 block |

Hardware texture limits still apply. Legacy serialized fidelity IDs are retained
as metadata; they no longer select production sampling or client quality.

## Historical fidelity benchmark

The following measurements describe the former selectable server profiles,
not the current fixed one-block source.

A repeatable local benchmark for the default 16,384×256 ring measured these
deterministic source sizes on 2026-08-31. Timings are a single development-Mac
sample and are informative rather than release performance evidence.

| Profile | Cells | Raw Atlas | Gzip | Save | Load | CPU texture/mips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Performance | 16,384 | 128 KiB | 62 KiB | 87 ms | 85 ms | 69 ms |
| Balanced | 65,536 | 512 KiB | 240 KiB | 352 ms | 208 ms | 109 ms |
| High | 262,144 | 2 MiB | 975 KiB | 1.02 s | 594 ms | 160 ms |
| Very high | 1,048,576 | 8 MiB | 3.77 MiB | 3.31 s | 1.84 s | 284 ms |

Reproduce it with `./gradlew runAtlasFidelityBenchmark`; the full report is
written to `build/reports/ringworld/atlas-fidelity.md`.

## Archipelago

`RingMacroTerrain` builds seed-derived island anchors in periodic X cells. It
uses nearest periodic distances, width-scaled elliptical islands, layered edge
noise and a guaranteed seam-adjacent anchor. The macro field modifies the
continent and near-surface density signals; normal Minecraft surface rules,
caves, aquifers, features, structures and blocks still build the final world.
Its vertical influence is deliberately limited to the surface band so it
cannot turn an island column solid to build height.

## Continuous river

The river centreline and width are compact functions of the saved seed and
ring dimensions. Multiple low-frequency periodic harmonics give a smooth
closed route whose centre, tangent and width agree at X=0/C. The channel stays
well inside the finite width, carves the real density field around sea level,
and uses Minecraft's river biome so water, banks and ordinary biome decoration
agree. It is not a downhill-flow simulation: Minecraft water remains static.

## More structures

The initial implementation affects registered built-in random-spread
Overworld structure sets only. It preserves each placement's biome,
frequency, exclusion and interaction checks, keeps a rim margin, canonicalizes
candidate X, and excludes the separately guaranteed ocean monument. The
stronghold and optional monument policies remain unchanged. A bounded locate
extension searches the same additional periodic candidates, so `/locate` and
explorer-map queries can find generated landmarks. Modded structure sets are
not multiplied implicitly.

## Repeatable validation

The guaranteed-structure fixture accepts these options on both loaders:

```sh
./gradlew :runStrongholdTestServer \
  -PringStrongholdTestCircumference=2048 \
  -PringStrongholdTestWidth=128 \
  -PringWorldLayout=ARCHIPELAGO \
  -PringContinuousRiver=true \
  -PringMoreStructures=true \
  -PringAtlasFidelity=VERY_HIGH

./gradlew :neoforge:runStrongholdTestServer <same properties>
```

The fixture verifies periodic terrain heights, finite rims, saved policy,
eight evenly spaced real river biome/channel samples, the stronghold/portal
room, and a normal server stop. On 2026-08-31 that exact feature combination
passed Fabric and NeoForge on Minecraft 26.1.2 and 26.2. The 26.2 pass caught
and corrected an earlier bounded carve that could leave high terrain floating
over the channel; the channel floor is now absolute and open upward. Pure
tests cover seam identity, land/ocean presence, structure-candidate
periodicity, settings codecs, admission limits and fingerprints.

Before release, retain matched visual review of Archipelago, the complete
river and all Atlas profiles, plus production-size Atlas recovery, mixed-client
networking, multi-seed structure balance and package evidence. Passing source
tests do not substitute for those release gates.
