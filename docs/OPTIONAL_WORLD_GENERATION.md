# Optional world generation

RingWorld's new-world screen owns four immutable generation choices. They are
saved with the Overworld, included in its fingerprint and Atlas identity, sent
to every client, and applied identically by Fabric and NeoForge. Existing
worlds migrate to the former defaults.

| Setting | Choices | Default | Effect |
| --- | --- | --- | --- |
| Ring detail | Performance / Balanced / High / Very high | Balanced | Coordinates authoritative Atlas sampling, GPU texture resolution, and terrain-height mesh resolution. |
| World layout | Vanilla / Archipelago | Vanilla | Retains ordinary periodic Minecraft terrain or applies a seed-derived ocean/island macro field. |
| Continuous ring river | Off / On | Off | Carves and biomes one closed, navigable water channel around the circumference. |
| More structures | Off / On | Off | Adds a deterministic second candidate grid for built-in random-spread Overworld structure sets. |

Climate Tour is deliberately not implemented. It was removed from the first
scope at owner direction; no hidden enum value or partial climate-sector code
is retained.

## Atlas fidelity

One server-owned profile defines the source data. A client never fabricates
detail beyond that source.

| Profile | Source step | Maximum texture | Height-mesh step |
| --- | ---: | ---: | ---: |
| Performance | 16 blocks | 2,048×512 | 16 blocks |
| Balanced | 8 blocks | 4,096×1,024 | 8 blocks |
| High | 4 blocks | 8,192×1,024 | 4 blocks |
| Very high | 2 blocks | 16,384×1,024 | 4 blocks |

The creation screen derives cell count, raw Atlas storage, GPU dimensions and
mesh vertices from the selected dimensions before a world is created. The
existing 16-million-cell hard limit still rejects unsafe combinations before
allocation. Changing profile changes the saved fingerprint and cache hash, so
stale lower-resolution data cannot be silently reused.

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
