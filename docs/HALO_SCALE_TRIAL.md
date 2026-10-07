# Literal Halo scale trial

Unmerged experiment on `codex/halo-scale-trial`, enabled only by the JVM flag
`-Dringworld.haloTrial=true` with the exact dimensions below. Not a release
feature or selectable normal preset. Nearby-distortion and floating-Atlas
experiments are disabled in this run.

The owner requested literal Halo dimensions, disabled pregeneration, and a
placeholder Atlas that still uses the world seed (7 October 2026).
[Installation 04 reference](https://www.halopedia.org/Installation_04): diameter
10,000 km, surface width 318 km. Assuming one block equals one metre:

| Dimension | Trial |
| --- | --- |
| Around | 31,415,920 blocks (nearest 16-block multiple of π × 10,000,000) |
| Across | 318,000 blocks |
| Rendered reference diameter | 9,999,997.92 blocks |
| Seed | 67890 by default; `-PringHaloTrialSeed=<seed>` selects another |
| Wall and vertical terrain | Existing Minecraft build height and 160-block wall setting |

The trial reproduces diameter and surface width. It does not reproduce Halo's
many-kilometre structural thickness or create extended-height Minecraft terrain.

## Bounded placeholder

A normal one-block Atlas would contain 9,990,262,560,000 cells, approximately
120 TB before overhead, spanning 39,024,463,125 canonical chunks. It must never
be allocated or pregenerated. The normal limits remain unchanged.

The trial bypasses new-world size admission only for its exact dimensions and
only with `pregenerateTerrainAtlas=false`. The server does not create an Atlas
service state. Client geometry installs an empty 1,918×20 placeholder handle,
460,320 estimated bytes, solely to use the existing incomplete-Atlas renderer.
Its snapshots preserve the bounded dimensions; it cannot be saved or encoded
as real Atlas tiles. No player-loaded chunks populate this handle.

The disposable integrated client samples the chosen world's actual generator
and random state on a separate daemon worker, using the existing staged seed
preview sampler (512×16 through 4,096×64 colours, bounded terrain samples).
Results use the existing placeholder path. This is a coarse seed-derived view,
not complete or exact terrain, and it contains no distant player structures.

Large-radius live terrain/cloud shaders use the stable half-angle form for
vertical displacement rather than subtracting two five-million-block radii.
The CPU object projection uses the equivalent form for this opt-in geometry.
Normal-sized shader behaviour is unchanged.

## Run and limitations

`:runHaloTrialClient` creates/opens a seed-specific disposable world under
`logs/halo-scale-trial/run`. Its preparation task sets pregeneration off in that
isolated config. It selects spectator flight, clear midday, 12-chunk live view,
and a viewpoint near X=100,000, Y=160, Z=0, looking up along the ring. After the
Ultra seed preview and rendering settle, it captures `halo-literal-scale.png`
and leaves the game open. Existing owner saves are untouched.

The circumference exceeds vanilla's roughly 30-million-block positive world
coordinate envelope. The trial therefore starts safely away from the canonical
seam; full-circuit travel, seam crossing, extreme-coordinate precision and
multiplayer are not qualified. Initial spawn is constrained only in this trial.
Normal world menus still reject this unsupported size. To discard, remove the
isolated run directory after saving/closing the client and discard this branch;
do not merge its unrelated preceding experiments into main.

## Validation

26.3 Fabric client compilation and Fabric/NeoForge unit suites pass, with
462 cases per loader and zero failures, errors or skips.
Focused checks cover dimension rounding, preserved normal admission/allocation
limits, empty bounded snapshots at every LOD level, and bounded surface meshes.
Runtime and other version qualification will be recorded as completed; this
document does not establish release qualification.

26.3 Fabric runtime PASS (7 October): cold shader/resource load, literal-size
world creation, save/reopen, all four seed preview stages, bounded proxy upload
and a HUD-free capture looking along the ring. Final preview is 4,096×64,
299,208 mesh vertices, zero authoritative cells. No `.rwat.gz` Atlas files were
created. Ultra preview completed about 34 seconds after world entry on reopen.
One initial wall GPU upload took 61 ms; steady frame pacing is not qualified.
The first setup used creative flight and fell before capture; spectator flight
and explicit local camera orientation corrected the demo. The final screenshot
is `logs/halo-scale-trial/run/screenshots/halo-literal-scale.png`; the game is
left open for owner review. Development-account authentication/Realms errors
are present, with no trial shader/render/world-generation exception observed.
Nether/End runtime checks and other supported Minecraft versions remain
unqualified for this experiment.
