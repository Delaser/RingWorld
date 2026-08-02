# Seam gameplay regression — 2026-08-01

This is the issue-#71 evidence boundary for Minecraft 26.1.2/Java 25. It
records what the automated clients actually proved and keeps manual or unknown
behavior separate. The runtime directories and logs are ignored local state.

## Passing automated coverage

The reused 2,048×416 dedicated fixture ran one server and two independent real
clients. Both clients reported a fully loaded world before setup. The terminal
server line was:

```text
[multiplayer] full scenario result=true (baseline=true, fixture=true, damageWake=true, bedDestroyed=true, deathRespawn=true, netherPortal=true, endPortal=true, clientFixture=true, clientLifecycle=true, canonicalPlayers=true)
```

The run passed:

- natural player and boat seam folds, mutual tracking/query, melee, block
  interaction, long teleport, periodic return, and client-B reconnect;
- chest and book-bearing lectern block-entity state on each nearest client
  image;
- a redstone-block neighbour update from canonical X=`C-1` powering a lamp at
  X=`0`;
- synchronized seam-side water source state and a destructive explosion. The
  existing integrated harness separately proves scheduled water flow across
  the seam;
- a survival bed at canonical X=`0`/`1`, used by a player beside `C`, with a
  canonical server bed position, nearest-image client pose, damage wake, and
  bed destruction;
- a real client death screen, respawn request, replacement server player, and
  canonical Overworld respawn;
- a `PortalForcer`-created Nether portal, vanilla linked exit, return near the
  source's periodic image, and cleared/restored client RingWorld state;
- End portal-block travel and return with the same client-state checks. The
  stronghold harness separately proves a generated bounded portal room, all 12
  frames, activation, locate, and folded Eye-of-Ender motion.

The final logs contained no `moved too quickly` or `moved wrongly` warning and
no RingWorld exception or crash. Offline test accounts still produce expected
Mojang/Realms HTTP 401 noise; that is unrelated to the local offline server.

## Post-evidence fixture strengthening

The current fixture seals a two-cell trough, clears canonical X=`0`, places
its only water source at `C-1`, and requires both clients plus the server to
observe water at X=`0`.
The passing run documented above predates this assertion and checked only the
source state. It must not be described as evidence that the strengthened
destination-flow check has passed; a new dedicated run is required.

The current server fixture also clears and bounds a short ground lane, removes
only stale entities carrying its dedicated navigator tag, then asks a
persistent Zombie near canonical C-5 to navigate normally toward X=2. The
server now requires that Zombie to fold naturally into low canonical X before
finishing its path within target tolerance. This assertion also postdates the
run documented above and remains pending a new dedicated execution.

## Defects fixed by this gate

Vanilla's private bed-reach test treated canonical X=`C-1.5` and bed X=`1.5`
as one circumference apart. `ServerPlayerSleepMixin` now retains vanilla's
axis-aligned Y/Z limits while using the nearest periodic X delta. Starting the
sleep pose also realigns the connection's movement baselines; the normal move
packet path defensively aligns stale baselines to the authoritative nearest
image before anti-cheat validation.

The test itself had two reuse defects. It now waits for both clients to report
`isGameLoadFinished()` before fixture teleports, and advances Minecraft 26.1's
monotonic `WorldClock` to the next 13,000-tick night phase instead of trying to
rewind a reused world to absolute day zero. Direct portal-transition calls set
the cooldown normally supplied by the inside-block processor so the linked
Nether exit cannot return the player before the harness observes it.

## Manual or narrower coverage

- Rejoin while the player is still sleeping remains manual. Ordinary
  reconnect and the complete sleep/wake/destruction lifecycle pass separately.
- Maps, raids, command families, complex redstone/fluid networks, and more
  projectile/vehicle types remain manual sampling rather than exhaustive
  automation.
- The portal gate invokes the real portal blocks, destination calculation,
  linking, and transition, but skips the normal player wait inside the portal.
- Arbitrary structure seeds, placements, and loot belong to #72.
- The multi-size runtime matrix is covered by #24 and worldgen/structures by
  #72. Dynamic map/locator semantics and broader mod compatibility remain
  outside this gameplay gate.

## Unsupported claims

This gate does not certify arbitrary Fabric mods, shader packs, NeoForge,
third-party positional packets, or every vanilla subsystem. It demonstrates
the listed fixtures only. Real chunks remain authoritative and Nether/End stay
flat vanilla dimensions.
