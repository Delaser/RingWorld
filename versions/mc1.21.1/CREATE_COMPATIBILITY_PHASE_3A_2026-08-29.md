# Create 6.0.10 compatibility phase 3A

This is an implementation checkpoint for the maintained Minecraft 1.21.1
backport. It is **not a compatibility claim or release decision**. The exact
adapter is NeoForge-only while its server ABI is being qualified; Fabric is
unqualified, not declared incompatible. No Create-specific mixin belongs on
the authoritative 26.1 mainline until an exact 26.1 Create/Flywheel target is
available.

## Qualified activation boundary

The optional mixin plugin enables the complete adapter only for this exact
tuple:

- Minecraft `1.21.1`;
- NeoForge `21.1.239`;
- Create `6.0.10`; and
- Flywheel `1.0.6`.

Create absence is silent. A present but different tuple warns once and
disables every adapter mixin. For the exact tuple, the plugin reads target
bytes through Mixin's bytecode provider without loading or initializing the
optional classes. Missing target bytes fail before any adapter mixin is
enabled. The mixin configuration is required and has `defaultRequire=1`; each
audited injection also has exact `require`/`allow` counts, so target or
injector drift fails closed.

The plugin, exact-ABI mixins, configuration, and tuple tests are all in
NeoForge-owned source/resource roots. Shared topology and ownership helpers
remain loader-neutral and contain no Create, Flywheel, or loader references.

## Deliberately isolated dependency graph

The build declares two independently verified, non-transitive compile inputs:

| Coordinate | Artifact SHA-256 | POM SHA-256 |
| --- | --- | --- |
| `maven.modrinth:create:6.0.10+mc1.21.1` | `ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a` | `b5fe1aa9b1d816119c507ee47e2c94aa0c8ba0cb8e55d3c955b21eb7ae325e82` |
| `dev.engine-room.flywheel:flywheel-neoforge-1.21.1:1.0.6` | `31dda15c205eb596d3b3449ef03f6af7363a6cd35b3da4bfe916b304f9e5337e` | `8836fd0cef4cc308bdebe3004b703cf80f593bf13b6451e33673b872dec9db0a` |

The exact reviewed outer Create licence reserves all rights to files below
`src/main/resources/assets`; all other Create files and code are MIT. Flywheel
and Ponder are MIT. The reviewed upstream Registrate licence is MPL-2.0,
although its nested jar contains no licence file. Create's outer NeoForge jar
contains the runtime Flywheel `1.0.6`, Ponder `1.0.82`, and Registrate
`MC1.21-1.3.0+67` jar-in-jars. Ponder and Registrate are not separately
resolved. RingWorld copies none of these projects' source or assets and does
not bundle their jars. The disposable local fixture merely installs the
independently verified outer Create artifact into its isolated runtime.

`createCompatRuntime` is a resolvable, non-consumable, non-transitive custom
configuration which extends no other configuration. It is not inherited by
`runtimeClasspath` or any ordinary run/test. Only the dedicated exact-tuple
preparation task resolves it, verifies the outer Create jar hash, and copies
that one jar into its disposable `mods/` directory. A dedicated isolation gate
requires:

- exactly the two compile modules above and no transitive compatibility
  modules;
- no Create, Flywheel, Ponder, or Registrate module on ordinary NeoForge
  `runtimeClasspath`; and
- exactly the one verified outer Create jar in `createCompatRuntime`.

The reviewed inventory is 371 components, 752 artifacts, and 752 SHA-256
pins. The update added only the two jar and two POM pins; verification metadata
was edited manually and never generated with `--write-verification-metadata`.

## Server topology boundary

The server adapter preserves Create's algorithms and changes only coordinate
domains:

- belt endpoint comparison and traversal translate the comparison/traversal
  target to the nearest presentation image, then use Create's native distance
  and subtraction logic;
- block state/entity lookup, block destruction, waterlogging, placement, and
  sound positions canonicalize at the server ownership boundary;
- reverse-seam traversal stays in one local presentation chart and is not
  canonicalized midway;
- vertical fluid-tank connectivity wraps only the audited controller-position
  comparison; tank width, height, capacity, controller choice, generated
  positions, cache keys, and lookups otherwise remain Create-native; and
- belt and tank `setController`, read, and write paths repair controller X to
  `[0,C)` when immutable Overworld geometry is available.

During block-entity decode before `level` attachment, repair uses only
`RingBlockEntityLoadContext.activeGeometryOrNull()` inside its bounded load
callback. The sole fallback is immutable settings from an attached server
Overworld. With neither source available, repair is deferred rather than
guessing a circumference. Client reads and presentation mapping remain phase
3B work; the server repair helper does not mutate client coordinates.

The resolved Create binary was audited at these exact boundaries:

| Target | Audited method/injection boundary |
| --- | --- |
| `BeltConnectorItem` | `useOn(UseOnContext)`, one `BlockPos.closerThan`; `canConnect(Level,BlockPos,BlockPos)`, two `isLoaded`, three `getBlockState`, two `getBlockEntity`; `createBelts(Level,BlockPos,BlockPos)`, one `playSound`, four `getBlockState`, one `getDestroySpeed`, two `destroyBlock`, one `withWater`, one `switchToBlockState` |
| `ConnectivityHandler` | private static `tryToFormNewMultiOfWidth(BlockEntity,int,SearchCache,boolean):int`, the second `BlockEntity.getBlockPos()` call with named locals `origin` and `axis` |
| `BeltBlockEntity` | `setController(BlockPos)`, `read(CompoundTag,HolderLookup.Provider,boolean)`, and `write(CompoundTag,HolderLookup.Provider,boolean)` |
| `FluidTankBlockEntity` | the same three controller ownership methods and descriptors |

## Phase 3A evidence and remaining work

Under the supported Java `21.0.12.1+1` compiler graph:

- the dependency isolation gate passes with exactly the coordinates and hash
  above;
- the backport dependency inventory passes at 371/752/752;
- Fabric passes 368 tests in 62 suites with no failures, errors, or skips;
- NeoForge passes 371 tests in 63 suites with no failures, errors, or skips;
  and
- the disposable Create-absent NeoForge dedicated server reaches `Done`,
  applies zero compatibility mixins, contains no runtime mod jar, and exits
  through its self-halting verifier;
- the exact-tuple server loads only the verified outer Create jar, discovers
  its nested Flywheel `1.0.6` and Ponder `1.0.82`, reaches `Done`, applies all
  four strict server mixins, and exits cleanly; and
- its bounded server fixture directly invokes Create's server
  `canConnect`/`createBelts` path in both seam directions/presentation charts,
  forms a seam 2x2x2 tank and ordinary 2x2x2 baseline without changing native
  dimensions/controller choice, preserves an ordinary item-vault negative
  control, and verifies in-memory attached-level, load-context, and deferred
  write serialization/repair all produce canonical controller NBT.

This closes the bounded phase-3A server checkpoint, not Create compatibility as
a whole. The fixture does not simulate a real player/client connector click or
client prediction and does not durably restart its world. The existing common
`BeltConnectorItem` mixin is intentionally a vanilla/no-op mapping on a client
`Level`, because `RingCreate610ServerCoordinates` accepts only `ServerLevel`;
its presence must not be mistaken for completed client endpoint mapping.

The remaining qualification must cover real client `useOn` and prediction in
both seam directions, tank fluid-handler capacity equality, belt item transfer,
durable whole-world restart/reload, and client controller presentation mapping.
Phase 3B also owns client belt preview, Flywheel `ContraptionVisual` embedding,
conservative contraption culling, backend-OFF double-transform proof, moving
seam captures, and graphical qualification. The shared
`RingPresentationBounds` null result must map immediately to visible in the
future Flywheel culling adapter.
