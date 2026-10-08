# Exterior entity visibility — issue #254

## Status

Implemented on an isolated branch from `a46965c`; pending owner visual review
and integration. This change does not include the separate horizon PR #261 or
the outside-building option tracked by #255.

## Cause and fix

Exterior Z columns deliberately have no client terrain chunks. Vanilla rejects
entity render extraction when the entity's terrain section is not compiled and
visible. That hides even the local third-person model after it leaves the band.
The server also used the finite terrain watch filter when pairing remote
entities, so a multiplayer observer could lose the player entirely.

The client now bypasses **only the terrain-section check** for entities beyond
either width boundary. Frustum, entity distance, first-person self-hiding and
normal interior visibility checks remain active. Minecraft 26.1 owns this path
in `LevelRenderer`; 26.2 and 26.3 own it in `LevelExtractor`, with different
section-check signatures. Each supported source adapter targets its real path.

The server uses the same configured periodic watch distance for exterior
entities, without the terrain filter's Z clamp. This supports initial pairing
and existing pairings over the void. Tracking range and ordinary unpairing
still apply. Interior initial pairing continues to require delivered chunks;
the existing pending-chunk seam pairing protection is preserved.

No extra exterior terrain is sent, no terrain generation is expanded, and no
production chunk tickets or forced entity ticking are added. X remains
canonical and periodic, Z remains finite, gravity and void damage are unchanged.
Both paths are restricted to RingWorld Overworld geometry. No build restriction
or building-toggle state controls entity visibility.

## Automated evidence

The opt-in two-client fixture checks actual entity render extraction, not just
network presence. Every positive result requires ten consecutive client-tick
samples. It covers:

1. Local third-person player and remote observer beyond the positive Z wall;
   a cow and boat are also paired and extracted by the observer.
2. Return to the interior band.
3. The negative Z wall, including cow and boat.
4. Observer moved 300 blocks away: normal player unpairing.
5. Observer returned: fresh player/cow/boat pairing outside the positive wall.
6. Opposite X seam sides while outside the wall, including a boat whose setup
   position canonicalizes through X=0.
7. Falling outside the negative wall while both clients continue rendering.
8. Ordinary Survival void damage below the world; the fixture applies no damage.

Fixture setup teleports are explicit setup, not proof of natural player seam
crossing. The full release/seam suite is unchanged and has not been rerun for
this development fix. This is targeted development evidence, not frozen-JAR
release qualification. Owner gameplay review and merge are still pending.

| Minecraft runtime | Fabric build/unit | Fabric two-client | NeoForge build/unit | NeoForge two-client |
| --- | --- | --- | --- | --- |
| 26.1.2 (26.1.x source line) | PASS, 450 cases | PASS | PASS, 450 cases | PASS |
| 26.2 | PASS, 450 cases | PASS | PASS, 450 cases | PASS |
| 26.3 | PASS, 453 cases | PASS | PASS, 453 cases | PASS |

All six scenarios reached the seven two-client visibility gates and the
Survival void-damage gate, then all eighteen processes stopped normally with
zero exit codes. Older 26.1/26.1.1 patch runtimes were not separately rerun in
this targeted matrix; they share the 26.1.x source adapter and loader JAR.
[Machine summary](media/off-ring-entity-visibility/validation.json).

Raw build XML, process logs and native screenshots are retained locally under
`logs/off-ring-254/`. Representative native images are banked in
[the review gallery](media/off-ring-entity-visibility/index.html).

## Repeat the targeted fixture

Use Java 25. Run one version/loader at a time, with no other build or game using
this checkout. The existing multiplayer prepare tasks reset their ignored
worlds; they never touch ordinary saves. Both test clients are hidden and muted.

The appropriate disposable `run-multiplayer/server/eula.txt` (Fabric) or
`neoforge/run-multiplayer/server/eula.txt` (NeoForge) must already contain an
accepted EULA. The runner does not accept it on the user's behalf.

```sh
python3 scripts/run_off_ring_visibility_test.py 261 fabric
python3 scripts/run_off_ring_visibility_test.py 261 neoforge
python3 scripts/run_off_ring_visibility_test.py 262 fabric
python3 scripts/run_off_ring_visibility_test.py 262 neoforge
python3 scripts/run_off_ring_visibility_test.py 263 fabric
python3 scripts/run_off_ring_visibility_test.py 263 neoforge
```

Each run uses the standard dedicated server and two real client tasks, the
2048×128 disposable geometry and the `ringworld.offRingVisibilityTest` JVM flag.
A 2400-tick server watchdog and runner timeouts fail closed. The runner requires
the terminal server result and normal zero exits from all three processes.
Without the flag, the existing multiplayer suite and ordinary gameplay retain
their existing paths.

During fixture development, a 26.2 render-hook target failed startup and was
corrected against its actual `LevelExtractor` bytecode. An overlapping build
also invalidated a server's development classpath; that failed launch is not
validation. Final runs are serialized and require clean startup and shutdown.
