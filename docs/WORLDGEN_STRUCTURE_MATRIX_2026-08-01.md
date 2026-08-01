# Worldgen and structure seam matrix — 2026-08-01

Issue #72 adds a repeatable, disposable dedicated-server matrix around the
existing guaranteed-stronghold gate. It validates ordinary vanilla worldgen
near both canonical sides of the circumference seam without pretending that a
finite ring can contain every random structure for every seed.

## Command

Run with Java 25 after accepting only the ignored test server's local EULA:

```sh
python3 scripts/run_worldgen_structure_matrix.py
```

The runner creates three fresh worlds and reloads the production world once.
It writes ignored logs and `summary.json` under
`build/reports/ringworld-worldgen-matrix/`. It fails if a run lacks its
complete PASS record, loads the wrong immutable layout, changes stable evidence
after reload, misses a required biome family, or lacks the aggregate structure,
carver, feature, loot, seam-crossing, and saved-policy evidence below.

## Passing matrix

| Case | Layout | Outcome |
| --- | --- | --- |
| `ringworld-regression-1` fresh | 16,384×256 | 14/14 major biome families; 128 seam-strip chunks; 243,634 cave-air blocks, 52,465 ores, 15,732 logs, four canonical mineshaft starts whose bounds cross the seam, 132 references, 15 loot containers; satisfied monument at `(484,-2)` with three spawn-override entries |
| `ringworld-regression-1` reload | 16,384×256 | Exact equality with the fresh record for seed/layout, sampled biomes, terrain counts, starts, crossing bounds, references, loot, and saved monument outcome/candidate |
| `ringworld-matrix-0` fresh | 2,048×416 | Nine biome families; 208 seam-strip chunks; 508,159 cave-air blocks, 74,850 ores, 2,625 logs, two seam-crossing mineshafts, 122 references, six loot containers; satisfied monument at `(63,-8)` |
| `ringworld-matrix-3` fresh | 2,048×416 | Eight biome families; 208 seam-strip chunks; 769,253 cave-air blocks, 71,262 ores, 943 logs, one canonical mineshaft, 64 references, six loot containers; deterministic `UNSATISFIED/SEARCH_BUDGET_EXHAUSTED` monument policy |

The production biome sample contains badlands, beach, cave, desert, forest,
jungle, mountain, ocean, plains, river, savanna, snowy, swamp, and taiga
families. Sampling uses the authoritative periodic climate sampler and requires
the same biome at canonical X and X+C.

Each run generates full chunks for four columns on either side of the seam and
the complete finite-width chunk range. Structure starts must be owned by a
canonical chunk. Start identities, source-chunk/structure/reference tuples, and
canonical loot positions must be unique; structure references may not point to
an alias X chunk. Actual air below sea level, ore tags, log tags, randomizable
loot containers, and structure metadata provide carver, feature, loot, and mob
spawn-policy evidence.

Every seed also passes the deterministic in-band stronghold gate: all 12 real
portal-frame blocks exist in the generated portal room, the frame orientation
activates, periodic locate returns the correct image, and a folded Eye retains
its target direction. The separate #71 two-client gate physically enters and
returns through an End portal. Together these automate the risky mechanics
that an ordinary manual Eye flight would exercise.

## Deliberate limits

- The generated ordinary seam structures in this deterministic matrix are
  mineshafts. It does not claim that every vanilla structure has been forced
  across the seam.
- The ocean-monument guarantee may legitimately be unsatisfied after its
  bounded search. Matrix mode accepts only that persisted terminal state;
  the normal dedicated guarantee gate still requires a satisfied seed.
- Spawn evidence checks vanilla structure-controlled spawn metadata, including
  the monument's guardian overrides. It is not a long-duration natural mob
  population benchmark.
- Third-party world generators, structure-placement mods, and `/locate`
  extensions remain unsupported unless separately tested.
- Ordinary survival Eye throwing remains a useful exploratory check, but is no
  longer a blocker for #72 because deterministic locate, live Eye motion,
  generated frame activation, and physical client End travel are automated.
