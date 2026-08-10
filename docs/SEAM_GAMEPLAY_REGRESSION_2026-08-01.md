# Seam gameplay regression — 2026-08-01

This is the issue-#71 evidence boundary for Minecraft 26.1.2/Java 25. It
records what the automated clients actually proved and keeps manual or unknown
behavior separate. The runtime directories and logs are ignored local state.

## Passing automated coverage

### Sleeping-player reconnect extension — 2026-08-08

The fresh Fabric and warmed/staggered NeoForge 2,048×416 gates now disconnect
client A while it is sleeping
in the canonical X=`0`/`1` seam bed. Minecraft intentionally loads a saved
player awake; the replacement player and client both remained beside the bed
in their correct canonical/nearest-image coordinates, with valid Overworld
geometry and loaded-bed X/Y/Z proximity. The server then started a second real sleep, waited out
post-login protection, applied ordinary survival damage, and completed the
existing wake, bed destruction, death, portal, and weather matrix. Its terminal
line included `sleepingReconnect=true` and `full scenario result=true`.

An earlier cold NeoForge attempt passed through End return but later
watchdog-terminated before weather/final result under the separate cold-stall
issue #134. The subsequent warmed/staggered run passed weather, both client
terminal results, the server's full result, and the strict NeoForge verifier.
Later instrumented fresh Fabric and NeoForge runs also passed. Their
deterministic glass blasts left the pre-existing item/falling-block counts
unchanged (158/86 on Fabric and 167/86 on NeoForge), their post-End stability
windows completed before weather, and neither produced a watchdog or crash
report. Isolated cold portal/weather server-behind warnings remain profiling
evidence rather than a topology or Atlas failure.

### Strengthened dual-loader checkpoint — 2026-08-02

Fresh-process Fabric and NeoForge 2,048×416 runs passed the current complete
matrix. In addition to the destination-water and hostile-navigation checks,
the player remained inside the real Nether portal for at least vanilla's
80-tick survival delay (84 ticks on Fabric, 83 on NeoForge). The final stage
then placed the clients on opposite seam charts, enabled full rain and thunder,
and required each to observe a real visual-only lightning entity. Both clients
acknowledged the storm and produced labelled screenshots. The fixture clears
saved weather on startup and waits for each client's target chunk before the
cross-seam block is placed, preventing reused-world and cold-start false
results. Both server logs ended with `weather=true` and
`full scenario result=true`; NeoForge's evidence verifier passed.

The issue-#147 branch repeated this full NeoForge fixture on 2026-08-10 with
Atlas generation disabled after both directional Survival placements passed.
It completed the subsequent sleeping reconnect, death/respawn, physical
Nether/End, post-End stability, and weather stages, and the strict verifier
passed. This separates placement correctness from the independently tracked
cold Atlas/performance profile.

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
- synchronized seam-side water source state and a destructive BLOCK explosion
  inside a deterministic seam-wrapped no-drop glass cell. The
  existing integrated harness separately proves scheduled water flow across
  the seam;
- a survival bed at canonical X=`0`/`1`, used by a player beside `C`, with a
  canonical server bed position and nearest-image client pose; disconnecting
  while asleep must rejoin awake beside that bed (vanilla behavior), rather
  than remain in a void state, after which a second sleep, damage wake, and bed
  destruction pass;
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

The 2026-08-10 dual-loader refresh extends the physical portal phase. It
creates and rediscovers safe canonical portals from positive and negative raw
targets several circumferences away and beyond both Z rims, then moves the
player to a four-lap Nether coordinate and performs the real return transition.
Fabric and NeoForge both returned at canonical X=1044.5 beside the expected
safe high-Z portal, retained the ordinary 80-tick survival delay, and completed
the following End/weather phases. The verifier now requires the ordered
`multi-lap Nether portal routing result=true` marker.

The complete matrix also passed at the Medium 16,384x256 geometry on Fabric
and on a warmed NeoForge retry, returning beside the normalized high-Z portal
at canonical X=8217.5. The first cold NeoForge production attempt stopped at
the already tracked #134 fixture/resource-pressure boundary before portal
routing began.

The current fixture seals a two-cell trough, clears canonical X=`0`, places
its only water source at `C-1`, and requires both clients plus the server to
observe water at X=`0`.
The historical source-only run predates this assertion. The strengthened
2026-08-02 Fabric and NeoForge runs both passed the destination-flow check.

The current server fixture also clears and bounds a short ground lane, removes
only stale entities carrying its dedicated navigator tag, then asks a
persistent Zombie near canonical C-5 to navigate normally toward X=2. The
server now requires that Zombie to fold naturally into low canonical X before
finishing its path within target tolerance. The strengthened 2026-08-02 Fabric
and NeoForge runs both passed this assertion.

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

- Both loaders now automate disconnect while sleeping, vanilla's
  rejoined-awake state, resleep, damage wake, and destruction.
- Maps, raids, command families, complex redstone/fluid networks, and more
  projectile/vehicle types remain manual sampling rather than exhaustive
  automation.
- The portal gate invokes real portal blocks, destination calculation and
  linking, and now requires the normal survival wait for outbound Nether
  travel. The automated return remains a direct real-portal destination
  transition after the outbound behavior has passed.
- Arbitrary structure seeds, placements, and loot belong to #72.
- The multi-size runtime matrix is covered by #24 and worldgen/structures by
  #72. Dynamic map/locator semantics and broader mod compatibility remain
  outside this gameplay gate.

## Unsupported claims

This gate does not certify arbitrary Fabric mods, shader packs, NeoForge,
third-party positional packets, or every vanilla subsystem. It demonstrates
the listed fixtures only. Real chunks remain authoritative and Nether/End stay
flat vanilla dimensions.
