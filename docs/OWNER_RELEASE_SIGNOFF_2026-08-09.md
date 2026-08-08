# RingWorld alpha 3 owner sign-off

Use this checklist against the hosted Modrinth files before approving promotion
or any live-server deployment. A failure leaves the release as alpha and must
be recorded before a replacement is built.

## Exact files

| Loader | Modrinth version | SHA-256 |
| --- | --- | --- |
| Fabric | `lnY3EC8t` / `0.2.0-alpha.3-fabric+mc26.1.2` | `9ec25789e1418fd3b1877c3c23d8388cbb880a0ed562ef5f0608498df0605097` |
| NeoForge | `D19TF1Qj` / `0.2.0-alpha.3-neoforge+mc26.1.2` | `ac8b8776d85038512bb85dab8967a32a53e8d33128a4ccae17b51b65b214938a` |

Both correspond to source commit
`94c8c9eb8a1a0e3d399ffd08a87af5c70b60b9b7`. Test Fabric and NeoForge in
separate clean instances. Do not mix their mods or worlds during this review.

## Record first

- Operating system and hardware:
- Java version (`25` required):
- Minecraft version (`26.1.2` required):
- Fabric Loader and Fabric API versions:
- NeoForge version:
- Tested jar SHA-256 values:

## Clean launch and world creation

- [ ] Fabric reaches the title screen and opens a world without errors.
- [ ] NeoForge reaches the title screen and opens a world without errors.
- [ ] The creation editor remains readable at the normal GUI scale and when
  the window is made narrow.
- [ ] Small shows 2,048×128 and its experimental/monument limitations.
- [ ] Medium shows 16,384×256 and is used for the main test world.
- [ ] Large shows 32,768×512 with its generation warning.
- [ ] Live lap time, radius/diameter, opposite angle, chunk, playable-area,
  Atlas, generation, and disk estimates update when dimensions change.
- [ ] Confirmation applies the chosen layout and the Create World footer
  reflects it.

## Visual and motion pass

- [ ] Real terrain bends smoothly into the distant Atlas without a severe
  colour, fog, geometry, or height discontinuity.
- [ ] Looking tangent to the ring and straight up shows a continuous full
  loop with no cutoff, triangle tear, black band, or far-plane clipping.
- [ ] Both cobble/mossy-cobble rims render and remain ordinary breakable
  blocks where the player can reach them.
- [ ] Clouds, sun, dusk, night, rain, and water/translucent terrain remain
  centred on the ring and do not visibly follow the camera.
- [ ] Walking, sprinting, flying, looking around, and crossing the seam feel
  smooth, with no camera snap or corrective teleport.
- [ ] Chests, lecterns/books, beds, banners, shulker boxes, boats, items,
  animals, and hostile mobs sit on the curved surface correctly.

## Gameplay pass

- [ ] Mine, place blocks, craft, use inventories, eat, take damage, collect
  drops, die, and respawn normally.
- [ ] Cross the seam on foot and by boat; blocks, fluids, redstone, mobs,
  projectiles, and combat behave locally across it.
- [ ] Sleep and wake normally. Reconnect after using a bed near the seam and
  confirm the player is not moved into the void.
- [ ] Enter and return from the Nether and End.
- [ ] Find/use the guaranteed stronghold portal; inspect a representative
  structure and its loot for incorrect height or inaccessible placement.
- [ ] Maps, banners, compasses, raids, weather, lightning, and Atlas controls
  behave normally.
- [ ] In multiplayer, two exact-version clients can see, fight, build, travel,
  and ride near and through the seam without disconnects.

## Windows requirement

- [ ] A clean Windows Fabric instance reaches the title screen and joins or
  opens a RingWorld world.
- [ ] A separate clean Windows NeoForge instance does the same.
- [ ] Save `latest.log`, the loader/Java versions, and one screenshot from each
  successful Windows run.

## Decision

Record one line for each loader: `PASS`, `PASS WITH NON-BLOCKING ISSUES`, or
`FAIL`. Link every failure to a GitHub issue with logs, screenshots, world
dimensions, coordinates, and reproduction steps.

Final approval should be explicit:

```text
Fabric alpha 3: PASS/FAIL
NeoForge alpha 3: PASS/FAIL
Visual and motion: PASS/FAIL
Windows clean install: PASS/FAIL
Promotion/live deployment: GO/NO-GO
```

Do not promote or deploy on any `FAIL`. A non-blocking issue must be accepted
explicitly in the go/no-go record.
