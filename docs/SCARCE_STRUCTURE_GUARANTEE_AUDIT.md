# Scarce structure guarantee audit (Minecraft 26.1.2)

This is the source/registry audit and implementation boundary for issue #51.
It must be re-audited after a Minecraft, mappings, or built-in datapack change.

## Current decision

New RingWorlds always guarantee one stronghold and may opt into one built-in
ocean monument. Woodland mansions, villages, outposts, trial chambers, and
other random-spread structures remain unsupported guarantees.

The monument option is deliberately narrow. It binds
`BuiltinStructureSets.OCEAN_MONUMENTS` and
`BuiltinStructures.OCEAN_MONUMENT` by holder key, not salt, spacing, or Java
class. Existing worlds and version-1 policy never acquire the option.

| Structure family | Vanilla placement and constraints | RingWorld status |
| --- | --- | --- |
| Stronghold | Concentric rings; complete variable piece graph and 12-block terrain-adjustment envelope | Mandatory for new worlds; complete graph fitted in-band |
| Ocean monument | Random spread; every biome in radius 29 must have `required_ocean_monument_surrounding`; valid anchor biome; `OCEAN_FLOOR_WG`; 58-block piece beginning 29 blocks before the start chunk | Optional new-world guarantee; implemented and runtime-validated |
| Woodland mansion | Random spread; valid biome plus rotated 5×5 height sample with minimum Y 60; variable template graph | Unsupported |
| Villages/outposts | Biome-specific jigsaw starts, height projection, expansion up to 80 blocks; outpost village exclusion and frequency reduction | Unsupported |
| Trial chambers | Underground jigsaw, padding 10, max distance 116, terrain encapsulation | Unsupported |
| Other small structures | Type-specific biome, heightmap/sea-floor, template, and piece rules | Unsupported until audited separately |

## Saved ownership model

`RingStructurePolicy` format 2 stores the immutable request and
`RingMonumentResolution`:

- `DISABLED` for a new world that did not request it and every legacy policy;
- `PENDING` only during first-world attachment;
- `SATISFIED` with one canonical chunk; or
- `UNSATISFIED` with a stable typed reason.

A pending request resolves on the server thread after geometry and registry
state are attached but before structure-start generation. The terminal result
is installed and `SavedDataStorage.saveAndJoin()` makes it durable once. A
reload validates the saved candidate against current registry/biome data but
never searches again, moves it, or substitutes another location.

The current creation screen and `ringworld.properties` path deliberately reuse
the existing Fabric-backed `RingWorldConfig` bootstrap adapter. The policy,
resolution, candidate order, sampler model, and saved format contain no Fabric
API. A NeoForge adapter must expose the same `requestOceanMonument` bootstrap
choice without changing those common semantics; this is not a NeoForge support
claim.

## Candidate and biome validation

`RingMonumentPlacement` examines at most 512 chunks in a deterministic
seed-derived full-cycle order. Every candidate is canonical and retains a
64-block conservative envelope from X=0/C and both Z rims. The structure-state
adapter also preserves the bound placement's frequency reduction and exclusion
rules.

Minecraft has two monument validation paths that bypass normal chunk biome
creation and call the flat `RandomState.sampler()`:

1. the monument's radius-29 surrounding-biome test; and
2. the base structure's final anchor-biome test.

`RingClimateSampler` derives the six climate functions and spawn targets from
the same cached periodic router used by RingWorld chunk biome generation.
Narrow mixins supply it to both validation paths only when the generation
context owns the RingWorld Overworld generator. Candidate search uses that
same sampler. Ordinary worlds, Nether, and End retain vanilla behavior.

## Placement and locate

The selected chunk need not belong to vanilla's random-spread candidate grid.
`StructurePlacementMixin` therefore admits only the compatible saved chunk for
only the registry-bound monument placement, after the original frequency and
exclusion checks. All starts, pieces, references, and chunk requests remain in
the single canonical plane. Ordinary monuments may still generate naturally.

Vanilla locate scans only its random-spread grid, so
`ChunkGeneratorLocateMixin` separately considers the saved forced candidate.
It mirrors vanilla's `StructureCheckResult`, canonical `STRUCTURE_STARTS`,
valid-start, `canBeReferenced`, and `addReference` path; compares the result by
nearest-periodic distance; and projects only the returned position into the
query's presentation chart. It never loads an alias chunk.

## Validation evidence

The Java 25 suite contains 206 unit/parameterized cases. Monument tests cover
deterministic order, no repeated/alias candidates, supported dimensions,
bounded exhaustion, seed variation, and policy v1/v2 states.

The disposable `runStrongholdTestServer` gate now enables the monument option.
For seed `ringworld-regression-0` at 16,384×256 it persisted candidate chunk
`(606, 3)`, generated a valid non-empty start bounded at X=9667..9724 and
Z=19..76, located the forced candidate from the adjacent presentation chart,
and exercised unexplored reference handling. A second Java process loaded the
same world/candidate/start and correctly excluded the already-referenced start
from an unexplored query. Both runs logged `[stronghold-test] PASS`.

## Gate for another structure type

Do not generalize the monument override. Each new type needs:

1. exact registry identity and immutable policy state;
2. deterministic canonical candidates and a proven complete footprint;
3. its real biome, terrain, exclusion, and generated-graph predicates;
4. typed deterministic unsatisfied behavior;
5. canonical generation, references, reload, and nearest-periodic locate; and
6. a dedicated runtime fixture proving its essential content or reachability.

Until those gates pass, the type remains unsupported.
