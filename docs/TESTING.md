# Testing

RingWorld needs tests at three levels:

1. pure geometry/topology unit tests;
2. a real integrated client/server smoke world;
3. two real clients on a dedicated server.

Rendering and mixin behavior cannot be proven by unit tests alone.

## Active port checkpoint

The current `codex/minecraft-26.1-port` branch requires Java 25. Common and
client compilation now pass together, and the development build runs all 89
unit/parameterized cases:

```sh
JAVA_HOME=/path/to/jdk-25/Contents/Home \
PATH="$JAVA_HOME/bin:$PATH" \
./gradlew clean test build --console=plain
```

See `MINECRAFT_26_1_COMPILER_BASELINE.md` for the historical 95-error inventory
and its resolution. A green build and dedicated-server launch do not establish
client rendering, gameplay, or multiplayer compatibility.

## Unit and build validation

Run:

```sh
./gradlew test build
```

Expected artifact:

```text
build/libs/ringworld-0.2.0+mc26.1.2.jar
```

The 2026-07-28 suite contains 89 unit/parameterized cases:

| Class | Coverage |
| --- | --- |
| `RingGeometryTest` | Seam continuity, presentation charts, default walking length, physical/tangent transforms, noise seam, culling envelope, visibility math, query windows |
| `RingChunkTopologyTest` | Canonical chunk images, joined-edge distance, periodic entity simulation distance, watch windows, incremental seam diff, long teleport, finite whole-ring filter |
| `RingDimensionReportTest` | Full-height radial safety, rims, walls/clouds, allocation bounds, safe-small and production costs |
| `RingDimensionMatrixTest` | Safe-small, narrow, production, long/narrow, and wide/medium layouts at 6/12/28/64 chunk views |
| `RingLayoutFingerprintTest` | Immutable layout and rim semantic identity |
| `RingRenderProfileTest` | Shared handoff values, texture/mesh budgets, and whole-ring clamping |
| `RingSkyCycleTest` | Fixed angle, reduced vanilla-sun size, noon/dawn/dusk/midnight tone keyframes, smooth interpolation, time wrapping |
| `RingTerrainAtlasTest` | Seam interpolation, colour/height interpolation, tile/disk round-trip, completion, cache monotonicity, world hash |
| `RingSurfaceLodTest` | Texture-luminance colour correction, relief shading, flat-colour preservation, periodic-X/clamped-Z mip filtering, one-pixel stability, malformed input rejection |
| `RingWorldSettingsStorageTest` | Dimension-owned settings path and legacy settings migration plan |
| `RingTerrainAtlasServerStorageTest` | Dimension-owned server atlas path and legacy atlas migration source |

Inspect machine-readable results under:

```text
build/test-results/test/
```

After any mapping or game-version migration, also search active Java and
descriptor text for `class_`, `field_`, and `method_`. The active unobfuscated
26.1 source permits no intermediary residue. `ServerLevel` entity tick
eligibility is in the private synthetic `lambda$tick$0`; its exact descriptor
is documented in `MIXIN_MAP.md`. A clean compile alone is not evidence that a
required mixin still applies.

## 26.1 dedicated-server storage gate

Run storage migration gates only from a disposable worktree/run directory.
Never point them at `dist/`, the public service, or the only copy of a world.
The 2026-07-28 checkpoint demonstrated:

- a fresh 2,048×416 server world reached `Done`, saved settings and atlas under
  `dimensions/minecraft/overworld/data/ringworld/`, and stopped cleanly;
- a copied 1.21.11 RingWorld completed Mojang's upgrade and copied its legacy
  settings byte-for-byte into the new path without modifying the source;
- an invalid legacy atlas was rejected and rebuilt at the authoritative new
  path rather than being silently trusted;
- the exact required `ServerLevel.lambda$tick$0` mixin applied at runtime.

These are server/storage gates only. They do not replace `runClient`, visual,
seam, or two-client multiplayer validation.

## 26.1 integrated safe-small client gate

The 2026-07-28 isolated Java 25 client gate first confirmed that every startup
mixin and shader resource loaded to the UI. It then ran the destructive
2,048×416 creative harness twice:

- a no-pregeneration topology run completed two natural wraps and every
  representative gameplay/rim probe with 8.37/8.41 ms seam/rim averages and
  no frames over 50 ms;
- a full-pregeneration run completed all 13,312 atlas cells at roughly 82
  cells/sec, built a 2,048×416 GPU texture and 79,872-vertex surface, and saved
  both tangent and radial-up complete-ring captures;
- both full-atlas natural crossings preserved yaw/pitch and emitted zero
  correction packets; canonical storage, block interaction, entities,
  projectile collision, boat, AI, fluid, explosion, collision, rim, wall top,
  and exterior void all passed;
- the full-atlas run averaged 8.41/8.37 ms and recorded one isolated frame over
  50 ms in each measured phase while generation/upload work was active.

This establishes the safe-small functional renderer and gameplay gate, not
final visual tuning. Inspect both complete-ring images for colour, live/LOD
handoff, local proxy exclusion, and width-edge alignment. The current
16,384×256 production default still needs its multi-size visual/resource gate.

The 26.1 `--quickPlaySingleplayer` projection task did not enter the selected
world during this checkpoint, so the successful complete-ring captures came
from the integrated harness. Treat that task as an S4 harness-port item rather
than weakening the visual gate.

## Local automated smoke world

The opt-in harness is destructive to its own test world: it teleports players,
clears camera/flight lanes, places fixtures, breaks blocks, spawns entities,
and changes time. Use only an isolated Gradle run directory or disposable
packaged world.

Configure `run/config/ringworld.properties` before launch:

```properties
widthBlocks=416
circumferenceBlocks=2048
wallHeightBlocks=160
testMode=true
testViewDistanceChunks=28
pregenerateTerrainAtlas=true
```

`testMode` does not itself override dimensions. The 128×26-chunk safe-small
test size comes from 2048×416 bootstrap values. It keeps about 70 radial blocks
of clearance above the top vanilla build plane while retaining the visibly
tight curvature of the retired 1600×320 fixture. The parameterized geometry
matrix is tracked in
[`DIMENSION_SCALING_PLAN.md`](DIMENSION_SCALING_PLAN.md).

Then run:

```sh
./gradlew runClient
```

The client creates a creative world named `RingWorld Automated Test` using the
fixed seed `-2162056627494116761`.

The harness exercises:

- ordinary terrain generation;
- creative/flying test setup;
- two natural seam crossings;
- camera yaw/pitch continuity and corrective-packet count;
- seam-adjacent explicit setup teleport remains on the nearest presentation
  image without evicting continuously watched destination chunks;
- seam block break/update;
- static and moving entity visibility/querying;
- projectile/entity collision;
- unoccupied boat motion;
- ground AI pathing;
- water scheduled ticks;
- explosion exposure/impulse;
- proximity particles;
- canonical chunk-holder audit;
- periodic block collision;
- exterior void and textured five-block rim;
- fixed small sun and its noon, warm-dusk, and cool-midnight tones;
- upward ring visibility and frame pacing.

The arrow, moving item, navigator, and boat intentionally start on the high-X
side and continue into canonical chunk zero. They are a shared regression for
entity simulation eligibility as well as individual gameplay systems. A
failure where several stop around X=0 while retaining velocity indicates a
stale chunk-level simulation graph, not four unrelated collision failures.

The second circuit keeps both seam approaches at quarter-block motion in the
cleared Y=120 seam lane. Up to a 4,096-block circumference, its non-seam middle
flies near the build ceiling with a circumference-derived step clamped to 4–8
blocks per tick, waits for chunks ahead, and logs progress every 600 ticks.
Larger matrix cases use an explicit test-setup teleport to sample the far-side
chart, wait for the seam approach, and then perform the same natural
quarter-block crossing. This keeps production and long-ring topology tests
bounded without pretending that thousands of unrelated generated chunks are a
seam requirement.

Expected screenshots in `run/screenshots/`:

```text
ringworld-automated.png
ringworld-seam.png
ringworld-second-wrap.png
ringworld-boundary.png
ringworld-fixed-sun-day.png
ringworld-tone-dusk.png
ringworld-tone-night.png
ringworld-visible-arch.png
ringworld-visible-up.png
```

The filename `ringworld-visible-arch.png` predates the active texture renderer.
It now captures the tangent/along-ring projection and live/LOD handoff.
`ringworld-visible-up.png` separately captures the radial view through the
ring diameter; neither direction substitutes for the other on a large layout.

Search `run/logs/latest.log` for `[test]`. A useful pass includes true values
for:

- terrain present;
- circumference wrap;
- canonical player plane;
- seam block interaction;
- periodic entity query;
- moving entity canonicalization;
- second seam wrap;
- block collision;
- projectile collision;
- vehicle crossing;
- AI path;
- fluid flow;
- explosion reach;
- exterior void, rim present, and shortened top.

The initial terrain and three sun-tone screenshots may be captured while
pregeneration continues. The final `ringworld-visible-arch.png` capture waits
for a complete current-world atlas before exercising the configured
`testViewDistanceChunks` live/LOD handoff (normally 6, 12, or 28); only then
does the harness reduce view distance for seam traversal. Its pitch is derived
from the configured distance, current camera height, and sampled target
surface on the physical cylinder so each image actually intersects its claimed
handoff rather than using one hard-coded upward angle. When pregeneration is
explicitly disabled and no complete client cache exists, the harness logs a
skipped LOD capture after 600 ticks and continues its topology/rim probes at
six chunks. A skipped capture is not LOD evidence for that matrix case.

## Large-ring projection capture

For a non-destructive two-direction capture of an existing complete-atlas
world under `run/saves/`, use:

```sh
./gradlew runProductionProjectionClient \
  -PringProjectionWorld="RingWorld Automated Test (3)"
```

The client waits for the current atlas to reach 100%, then writes:

```text
run/screenshots/ringworld-projection-tangent.png
run/screenshots/ringworld-projection-up.png
```

The tangent capture looks horizontally along canonical +X, where the cylinder
most visibly encountered the old chunk-derived far cutoff. The radial capture
looks straight up through the largest surface diameter. The log records the
active level far plane, opposite reference-surface distance, far width-edge
distance, geometry, texture size, and mesh vertex count. The probe changes
camera yaw/pitch only; it does not move the player or edit the world.

The harness logs individual probes rather than one final aggregate boolean, so
review the complete group.

When the projectile probe fails, its diagnostic includes position, velocity,
age, cached chunk, and current `shouldTickEntityAt` result. A folded position
alone is not a pass: the projectile must remain tick-eligible and actually hit
the seam-adjacent target.

If the client reaches the presentation side of the seam but its interaction
fixture has not arrived, it logs presentation X, camera chart/crossing count,
and both logical/canonical client block states every 200 ticks. This turns a
previously silent wait into a packet/chart diagnostic; it does not waive the
block-interaction assertion.

Set `testMode=false` again for ordinary play. `RingWorldConfig` is cached for
the process lifetime; restart Minecraft after editing it manually. The
world-creation editor updates the cache itself.

When changing the creation editor, open it from Create World and leave it
visible for multiple frames at GUI scale 4. Minecraft 26.1 permits only one
menu-blur layer per frame; custom screens must not call a background
extraction method inside `extractRenderState`, because
`Screen.extractRenderStateWithTooltipAndSubtitles` already owns that pass.
At a 1920-by-1080 window this also exercises the compact 480-by-270 logical
layout. Verify that the RingWorld entry shares the vanilla footer row without
overlapping Create or Cancel, then exercise all four editor cases:

1. enter an invalid layout and confirm that the error is visible and
   **Use for new world** is disabled;
2. select **Safe small** and confirm `2048×416`, wall height `160`, and a valid
   cost preview;
3. select **Production** and confirm the configured production dimensions and
   a valid cost preview;
4. enter a distinct valid custom layout, confirm its preview, choose
   **Use for new world**, reject the immutable-layout confirmation once, then
   accept it and verify that Create World shows the new C×W summary.

Keep the editor open for at least several frames in each case and treat any
duplicate-blur exception, clipped controls, footer overlap, missing validation
message, or stale C×W summary as a failure. This is a local UI test; do not
create or connect to the live server.

## Non-destructive join screenshot

For a real saved world without the automated traversal, start the client with:

```text
-Dringworld.captureJoinFrame=true
```

After terrain settles, the client writes:

```text
screenshots/ringworld-join-diagnostic.png
```

This flag does not move the player or modify blocks. It is useful for launch,
black-screen, and first-frame regressions, but it captures the player's current
pose and therefore may not show the distant ring.

## Dedicated two-client regression

The Gradle project defines:

```sh
./gradlew runMultiplayerServer
./gradlew runMultiplayerClientA
./gradlew runMultiplayerClientB
```

Run each in its own terminal. Runtime state is isolated under:

```text
run-multiplayer/server/
run-multiplayer/client-a/
run-multiplayer/client-b/
```

On a fresh checkout, the server may first generate files and require EULA
acceptance. Use the test geometry in each isolated
`config/ringworld.properties` and configure the dedicated server port expected
by the clients (default harness property 25566).

The tasks set:

```text
server:  -Dringworld.multiplayerTest=true
clientA: -Dringworld.multiplayerTestRole=A
clientB: -Dringworld.multiplayerTestRole=B
clients: -Dringworld.multiplayerTestPort=25566
```

The automated clients wait for Minecraft's initial resource reload to report
`isFinishedLoading()` before connecting. Do not remove that gate: a world may
otherwise begin random display ticks while particle sprite providers are still
unprepared. The harness uses the supported minimum simulation distance of five
chunks. Client A derives its next positive seam from its current presentation
chart; canonical X=2044 may correctly arrive as presentation X=-4, so the
driver must never aim at one hard-coded presentation seam. The vehicle probe
likewise compares the boat against the seam image nearest each observer rather
than canonical `C`. The server holds the boat on the high side until both
clients acknowledge that they have acquired the entity, then advances it
through deterministic canonical samples. That separates actual seam
reindexing/interpolation failures from client-startup packet timing.
Intentional-teleport return checks likewise compare periodic positions rather
than requiring canonical `C-4` to appear in one particular client chart.
Fixture initialization removes stale automated boats from a reused harness
world. The server detects a fold from the large canonical-coordinate
discontinuity plus its small positive periodic step, so an overloaded tick
does not have to sample the player inside the final one-block interval.

The scenario verifies:

- both clients connect and acknowledge geometry;
- canonical players remain one short periodic distance apart;
- client presentation movement is smooth through the seam;
- server player query and tracking cross the seam;
- real melee damage crosses the seam;
- a block interaction/update crosses the seam;
- a server-owned boat stays visible and canonical;
- an intentional long teleport re-keys the client chart;
- client B disconnects and reconnects cleanly;
- both clients report their phase matrix.

Success is:

```text
[multiplayer] full scenario result=true
```

in `run-multiplayer/server/logs/latest.log`.

The isolated Minecraft 26.1.2/Java 25 run on 2026-07-28 achieved that result
on a fresh 2,048×416 server. Both clients acknowledged format 2; the natural
seam crossing was canonical with 0.25-block maximum packet and tick samples;
visibility/query/distance, real melee, block update/interaction, shared boat,
long teleport, periodic return, planned disconnect, and reconnect all passed.
The clients were then stopped and the server saved all dimensions and exited
cleanly.

The integrated visual/seam harness deliberately holds position for 300 client
ticks after its first seam screenshot. This keeps the seam chunks resident
through the server's 240-tick projectile, navigation, fluid, vehicle, and
explosion observation window. Only then does it begin the accelerated second
circuit; the server waits for a real high-X then low-X traversal before moving
the player to the rim capture.

Client screenshots are named:

```text
ringworld-multiplayer-a.png
ringworld-multiplayer-b.png
```

Do not substitute two integrated single-player windows for this test. The
dedicated server path exercises entity IO, real watch state, and independent
client charts.

## Same-process saved-layout switch

The deterministic layout-switch client opens two existing local saves in one
JVM. It verifies the first layout and atlas, disconnects, confirms geometry and
atlas state were cleared, opens the differently sized second save, and checks
that the new handshake and atlas agree:

```sh
./gradlew runLayoutSwitchClient
```

The checked-in development run expects `run/saves/RingWorld Automated Test
(10)` to be 4,096×2,048 and `RingWorld Automated Test (6)` to be 32,768×512.
The harness does not assume those numeric values in code; it requires the two
loaded geometries and fingerprints to differ. Search `run/logs/latest.log` for:

```text
[layout-switch] result=true
```

Override `ringworld.layoutSwitchFirst` and `ringworld.layoutSwitchSecond` in a
custom Loom run when using other existing save folders. This harness opens and
saves both worlds but does not move the player or edit terrain.

## Manual playability checklist

Use creative mode and an ordinary render distance (the current test profile is
28 chunks).

### Movement

- Walk and sprint normally; look for per-block or per-tick jitter.
- Cross X=0/C slowly in both directions.
- Cross while jumping, falling, flying, swimming, riding, and using elytra.
- Confirm yaw and pitch do not change.
- Confirm velocity does not reset.
- Open F3 and verify Ring X wraps into `[0,C)`.

### Multiplayer

- Put players on opposite canonical sides of the seam.
- Verify nameplates/models are adjacent and tangent-aligned.
- Chat, hit, interact, throw items, fire projectiles, and ride a vehicle.
- Break/place a block while the other player watches.
- Reconnect both sides of the seam.

### Rendering

- Look upward with high but practical render distance.
- Inspect both live/texture transition bases and the zenith.
- Move along X and Z; the distant atlas must stay anchored to the world.
- Verify real chunks overwrite the visual LOD near the player.
- Verify the stand-in is partially transparent but retains a recognizable
  terrain silhouette at the nominal chunk edge, then becomes opaque beyond the
  live range without a hard line or raised fog belt.
- Verify the stand-in becomes visible through the final live terrain band
  rather than appearing only after the last chunk. Move and rotate while
  watching the handoff; the fixed dither must not sparkle or form a visible
  checkerboard.
- Inspect water and other translucent live surfaces at the handoff.
- Stand beside both rim walls and verify no local proxy surface or atmospheric
  curtain is drawn over the wall or exterior void.
- Confirm cobblestone textures are visible on the same rim blocks that provide
  collision; press against both inner faces and inspect their tops.
- Walk while looking at the far ring and check that mip transitions do not
  shimmer or expose the canonical U seam.
- Compare a grassy live-chunk slope with the aligned atlas surface. Grass must
  retain its biome green rather than the dirt-brown map colour of the block
  underneath it, and the proxy top face must not sit one block below live
  terrain.
- Stand below or beside an opaque mountain and look upward along the
  circumference. Loaded terrain bent into view behind the mountain must remain
  present instead of disappearing at the mountain's flat silhouette. Repeat
  while rotating the camera and check for section-scale popping.
- Inspect clear/rain and day/dusk/night. At each phase, compare an exposed live
  top surface with the aligned proxy in the transition band; the proxy must
  follow the same RGB lightmap exposure rather than retaining a bright-green
  nighttime floor. Repeat with changed gamma, night vision, and a lightning
  flash when practical.
- Look at clouds from ground and near wall height.
- Check both wall edges and exterior void.

### World lifecycle

- Save/quit/rejoin at the seam.
- Leave a world with a complete atlas, create a different-seed world with the
  same dimensions, and watch the entire atlas-generation interval. The old
  ring must disappear immediately; no complete-ring surface should render
  until the new atlas reaches 100%, after which its terrain must differ.
- Die and respawn.
- Use `/tp` for a disjoint X move.
- Enter and return from Nether and End.
- Reload a chunk containing entities and scheduled ticks.

## Performance collection

Record:

- render and simulation distances;
- ring dimensions;
- atlas completion;
- average/max frame time and slow-frame count;
- client RSS and CPU after initial meshing settles;
- server tick time during atlas pregeneration;
- chunk pending-task count;
- whether the test is integrated or dedicated.

Do not compare the removed forced-100-chunk experiment with the active
28-chunk+texture path as if they load the same amount of real geometry.

## Failure triage

| Failure | First evidence to collect |
| --- | --- |
| Crash on join | Crash report, latest log, mixin target failure |
| Infinite falling/empty chunks | Server chunk/worldgen log, ring settings, exterior Z |
| Black screen | Player collision pose, render log, shader compile messages |
| Seam pop/rubber-band | Player packet steps, correction count, yaw/pitch, server canonical X |
| Missing remote entity | Server tracker result and client projected X |
| Block visible but unusable | Outbound action packet canonical position and reach result |
| Upward chunk disappearance | Curved frustum envelope, RingWorld section-occlusion override, and terrain shader camera origin |
| Texture follows player | Atlas world hash, U/V mapping, global mesh model transform |
| Hard LOD seam | Actual view distance, proxy alpha/reveal curves, atlas/live alignment |
| Proxy brighter/greener at night | `Sampler2` lightmap binding and full-sky texel coordinates in `ring_surface.fsh` |
| Rim collides but is invisible | Boundary `BuiltChunk.shouldBuild`, exterior-neighbour exception, section rebuild |
| Server hitching | Atlas generation future and pending chunk tasks |
