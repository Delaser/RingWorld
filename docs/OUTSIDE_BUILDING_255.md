# Outside building — issue #255 (development work in progress)

Branch: `codex/outside-building-255`, based on `1914255` after merging #261 and
#262. This feature is not merged or release-qualified yet.

Nearby exterior chunks now use the normal bounded periodic view-distance
window. Natural generation remains void outside finite Z, and Atlas coverage
is unchanged. The saved default-on placement policy is separate from immutable
world geometry. OFF keeps existing builds and collision, rejecting new player
block placement, beds whose head crosses the boundary, and direct bucket use.
Automation and natural fluid propagation are not governed by this player toggle.

`/ringworld building outside on|off|show` uses the existing command permission.
The in-world RingWorld screen has a World page with an Outside building toggle.
Its request is bound to the current layout fingerprint, requires the existing
settings acknowledgement, and rechecks owner/operator permissions on the server.
State broadcasts to players and resets on client session teardown.

The old finite-band visibility queue seed clamp is removed so exterior cameras
seed their own delivered chunks. Curved frustum culling and ordinary view
 distances remain. #254 entity visibility stays independent of this policy.

## Current evidence and remaining work

- Minecraft 26.1.2 Fabric fresh and retained-world two-client runs pass on
  2026-10-09, with normal exits for all three processes in both phases.
- Fresh checks cover actual player placement on both edges, command OFF,
  server rejection of blocks/crossing beds/buckets, unauthorised control,
  existing-platform collision, the ordinary World-page toggle, and natural X
  seam crossing with exterior placement. Reopen checks prove both builds,
  collision and the saved OFF setting persist.
- Fabric's client `/ringworld` root initially intercepted the server command.
  Both client adapters now explicitly forward the three building commands to
  the server, retaining server permissions. The passing run exercises this.
- The bed rejection probe initially supplied a bed argument while the player
  held another item. The fixture now equips the actual bed before use; the
  placement policy needed no production correction for that setup error.
- Native 26.1.2 Fabric floating-structure capture completed with a fully
  generated Atlas, muted hidden window, normal save and clean process exit.
- Remaining five runtime cells and all-six final build/test checks are running.
  Do not claim supported-version parity or merge before verification.

Run `scripts/run_outside_building_test.py {261|262|263} {fabric|neoforge}`
with Java 25. It uses ignored disposable multiplayer worlds, then reopens
without resetting them. It requires the fixture EULA to have been accepted;
serialise runs. Native windows stay hidden and muted. Evidence lives under
`logs/outside-building-255/<version>-<loader>/`, including fresh/reopen server
and client logs and native screenshots. This targeted development gate does
not replace full frozen-candidate release qualification.

## Floating exterior structure study

See [native captures](media/floating-exterior-structure/index.html).
The corrected disposable world `run/saves/RingWorld Floating Structure Study (1)` contains a
64 × 32 × 32 glass, iron, deepslate and sea-lantern building at X=480..543,
Y=132..163, Z=76..107 on a 2,048 × 128 ring. Its nearest side is separated
from the positive rim (Z=63) by twelve empty block columns. Existing user
worlds were not changed.

The first study accidentally used wallHeightBlocks=96 (wall top Y=32, below
reference terrain). It is recaptured with the normal wallHeightBlocks=160
(wall top Y=96). Wall height is measured from the world minimum Y=-64;
changing bootstrap configuration does not alter an existing saved world.

The structure appears as nearby real blocks and disappears in the distant
Atlas and opposite-ring views. The current Atlas represents only the finite
ring band; this is coverage evidence, not an exterior-object LOD implementation.
The capture fixture is opt-in with `ringworld.captureFloatingStructure=true`;
normal clients do not enter it. `ringworld.backgroundTestWindow=true` hides
the native test window before activation.

## Open visual defect: wall/terrain gap

Owner identified the sky-coloured void between the positive wall and distant
terrain on the left of the corrected nearby capture (2026-10-08). It also
appears in the distant capture. It is separate from the accidentally low wall
height: the defect persists at the normal top Y=96.

Current mesh terminates elevated terrain at the rim's inner Z boundary while
its rim faces stop at the fixed wall top, leaving no vertical terrain face
between higher terrain and that top. This is the suspected missing geometry;
verify with a controlled high terrain edge before implementing a fix. The
real-block cliff is visible nearby, while this gap is in the distant Atlas.
Do not increase the saved wall height or stretch the industrial wall above its
authored top to conceal it. No fix has been applied yet.

`ringworld.floatingStructureReview=true` opens the corrected existing sample at
the nearby capture viewpoint, enables Creative flight, leaves ordinary player
control active, and keeps the game loaded and muted. It does not rebuild the
structure or rerun the capture sequence.

### Additional observation: holes in the wall placeholder

The owner also identified rectangular sky-coloured openings inside the distant
wall itself while looking along the rim. Captured the actual running Java
window without focusing it: [live evidence](media/floating-exterior-structure/wall-placeholder-gaps-live.png).
The game remains open and muted for review.

The wall texture uses `RingWallPattern.blockPresent` to write transparent
texels for decayed top columns. `ring_surface.fsh` discards those texels.
`RingSurfaceMesh` emits inner/outer curtains and a flat top at the original
wall height, without stepped caps at the collapsed column heights. Thus the
proxy can expose sky without modelling the surviving thickness or underlying
terrain edge. Decay cutouts are intentional, but whether each observed hole
matches real blocks needs a controlled comparison; do not simply suppress all
alpha cutouts or remove decay.

Treat this as a second geometry/coverage case in the wall-gap investigation:
compare zero-decay and normal-decay samples, verify exposed terrain side faces
and caps at the actual remaining wall height, then test both inner/outer views.
No fix or diagnosis of every individual opening is claimed yet.
