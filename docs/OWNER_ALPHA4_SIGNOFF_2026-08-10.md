# RingWorld alpha 4 owner sign-off

Use this checklist against the exact locally staged alpha-4 files before
authorizing a merge, upload, or live-server update. Fabric and NeoForge must be
tested in separate clean instances. A failure remains a release blocker unless
the owner explicitly records it as accepted and non-blocking.

## Exact files

| Loader | Prepared public version | SHA-256 |
| --- | --- | --- |
| Fabric | `0.2.0-alpha.4-fabric+mc26.1.2` | `517b9b0ace9c87b7225242f09d4aceda7ecf95d5fd9d1506c571b8b2774ab6f1` |
| NeoForge | `0.2.0-alpha.4-neoforge+mc26.1.2` | `2fe2cfb04060a740ec450e62247eadd42e7f52ac859aa5c416bf62f3fd4d3dbd` |

The staged manifest beside each jar records the exact pushed source revision
and must match the final draft PR #154 head. The shared jar metadata version is
`0.2.0+mc26.1.2`; that is intentional and is distinct from the hosted public
version identifiers above. No alpha-4 upload is authorized by this document.

## Record first

- Operating system and hardware:
- Java version (`25` required):
- Minecraft version (`26.1.2` required):
- Fabric Loader and Fabric API versions:
- NeoForge version:
- Tested jar SHA-256 values:
- Draft PR #154 head and staged-manifest source revision:

## Clean install and creation UI

- [ ] Fabric reaches the title screen and creates a fresh world.
- [ ] NeoForge reaches the title screen and creates a separate fresh world.
- [ ] The editor remains readable at normal scale, GUI scale 4, and a narrow
  window.
- [ ] Small is 2,048×128, Medium is 16,384×256, and Large is 32,768×512.
- [ ] Live lap time, radius/diameter, opposite angle, chunks, playable area,
  Atlas, generation, and disk estimates update when dimensions change.
- [ ] Use Medium for the main review; confirm the footer shows the selected
  immutable layout before creating the world.

## Fresh terrain and Atlas

- [ ] The new world reports settings format 3 and uses the annular terrain
  mapping. Do not reuse an alpha-3 world for this check.
- [ ] Generate the entire Atlas and wait for completion; incomplete cells may
  use the normal placeholder but must be replaced by the generated terrain.
- [ ] Inspect approximately X=0, 4,096, 8,192, 12,288, and the 16,383→0 seam.
  Terrain must not develop longitude-dependent bands or stretched strips.
- [ ] Looking tangent and straight up shows a continuous full loop without a
  cutoff, triangle tear, black band, severe colour mismatch, or obvious LOD
  seam.
- [ ] Both rims, clouds, sun, dusk, night, rain, water, and translucent terrain
  remain visually coherent and centred on the ring.
- [ ] Walking, sprinting, flying, turning, and crossing X=16,383→0 remain
  smooth without camera snaps or corrective teleports.

## Alpha-4 seam regressions

- [ ] Place from an existing block at X=16,383 onto X=0; the new block remains.
- [ ] Place from an existing block at X=0 onto X=16,383; the new block remains.
- [ ] Build a double chest across X=16,383/0. Both halves open one 54-slot
  inventory; put different items in it, save, quit, reconnect, and recheck.
- [ ] Hoppers and comparators interact with either half of that chest.
- [ ] Enter and return through a Nether portal after travelling far enough in
  Nether X to exceed one Overworld circumference. The return is canonical and
  local, not a multi-lap coordinate.
- [ ] Repeat a Nether return with an out-of-width Z target. The Overworld
  portal appears inside the safe playable band, not beyond a rim.

## Ordinary gameplay

- [ ] Mine, build, craft, use inventories, eat, take damage, collect drops,
  sleep/wake, die, and respawn normally.
- [ ] Reconnect after sleeping near the seam; the player must not appear in the
  void.
- [ ] Cross the seam on foot and by boat; combat, projectiles, mobs, fluids,
  redstone, block entities, and passengers behave as local neighbours.
- [ ] Enter and return from the Nether and End; find and activate the guaranteed
  stronghold portal; inspect representative structure placement and loot.
- [ ] Maps, banners, compasses, raids, weather, lightning, Atlas controls, and
  save/reopen behave normally.

## Windows requirement

- [ ] A clean Windows Fabric instance with the exact jar reaches the title
  screen and opens or joins a RingWorld world.
- [ ] A separate clean Windows NeoForge instance with the exact jar does the
  same.
- [ ] Save `latest.log`, loader/Java versions, the tested SHA-256, and one
  in-world screenshot from each run.

## Decision

Record each line as `PASS`, `PASS WITH ACCEPTED NON-BLOCKING ISSUES`, or
`FAIL`. Link failures to GitHub issues with logs, screenshots, dimensions,
coordinates, seed, loader, and reproduction steps.

```text
Fabric exact candidate: PASS/FAIL
NeoForge exact candidate: PASS/FAIL
Fresh annular terrain and Atlas: PASS/FAIL
Seam placement/chest/portal regressions: PASS/FAIL
Ordinary gameplay and motion: PASS/FAIL
Windows clean installs: PASS/FAIL
Merge and hosted alpha-4 upload: GO/NO-GO
Live demo server update: GO/NO-GO (separate authorization)
```

Do not merge, upload, or update the live demo server on any unresolved `FAIL`.
