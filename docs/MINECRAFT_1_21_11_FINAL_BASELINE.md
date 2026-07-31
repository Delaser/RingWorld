# Final Minecraft 1.21.11 baseline

Status: frozen and validated

Date: 2026-07-28

Git tag: `mc-1.21.11-final`

Commit: `2c98650e850064428c50667ba0809736294e549e`

This is the behavioral and rollback baseline for the Minecraft 26.1.2 port.
The tag contains the final Yarn-mapped Minecraft 1.21.11 implementation and
the complete port plan. Runtime worlds, logs, screenshots, and authenticated
launcher state remain outside Git.

## Build

`./gradlew test build` passed all 73 unit and parameterized cases.

```text
0c615ddb70700b666d8e72d5db7aac7d787eae1340bb6e0290accbd4f511e662  ringworld-0.1.0.jar
704a3c49b5aeca2f005325a64e074c6a233a53b44d9a829008c023441e177996  ringworld-0.1.0-sources.jar
```

## Safe-small integrated harness

The fresh creative harness used C=2,048, W=416, wall height 160, and a
28-chunk live/LOD capture before reducing to six chunks for traversal. It
completed:

- ordinary terrain and circumference coordinate wrap;
- complete 13,312-cell terrain atlas generation;
- tangent and radial-up complete-ring captures;
- two natural seam crossings with zero corrective position packets and zero
  yaw or pitch change;
- canonical block interaction and chunk-holder ownership;
- entity query/tracking, projectile collision, vehicle motion, AI navigation,
  fluid scheduling, explosion reach, and block collision across the seam;
- exterior void, textured rim presence, and shortened rim top;
- fixed-sun noon, dusk, and night tone captures.

Frame pacing:

```text
main traversal: 5110 samples, 18.604 ms average, 252.607 ms maximum,
                5 samples over 50 ms
rim capture:    1169 samples, 12.772 ms average, 21.736 ms maximum,
                0 samples over 50 ms
```

Atlas pregeneration produced temporary integrated-server `Can't keep up`
warnings while generating missing chunks. Traversal and rim frame pacing were
collected after the relevant resources had settled.

## Saved-layout switch

`./gradlew runLayoutSwitchClient` reported:

```text
[layout-switch] result=true
```

The client opened a 4,096×2,048 save, disconnected and cleared geometry/atlas
state, then opened a 32,768×512 save in the same JVM with a different layout
fingerprint and atlas shape. Old atlas-format fixtures were safely rejected
and rebuilt.

## Dedicated multiplayer

The dedicated server and two independent clients reported:

```text
[multiplayer] full scenario result=true
```

Natural seam motion and visibility, shortest-periodic queries, melee combat,
block interaction, boat tracking, explicit teleport/return, disconnect,
reconnect, and the complete client phase matrix passed.

## Production projection

The production capture used:

```text
circumference: 15552
width:          4096
radius:         2475.1776749651563
diameter:       4950.3553499303125
atlas:          995328/995328 cells
GPU texture:    4096x1024
mesh:           393216 vertices
```

Both tangent/along-ring and radial-up captures completed. The production
geometry selected proxy far-depth compression because the vanilla 1,792-block
level far plane is shorter than the opposite ring surface.

## Screenshot hashes

The images are local evidence and are intentionally ignored by Git:

```text
d85bd4a0f8d7369f3d5451547c4f6616c14511adf1a7e2836e4c832c0a619c08  ringworld-automated.png
43167f6e31113d0e44f7fd50c03094bf90e09a7e79f3fc3084c2cd67ee2cb5bc  ringworld-visible-arch.png
72e03fb71bf658739ed9c5aff7f73f37f3328c959ef2f436ff3f88c2181006f7  ringworld-visible-up.png
527c8e6fc6fb1c514e3fedbec3747442022f8de90062736edcef609398a9b8c6  ringworld-seam.png
f06a0a77005513139a06a2c50cabeb33c78dacea725a417da6c2cd6b6891bff2  ringworld-second-wrap.png
19ae934d27e351b124665816539e344ce885db6ada87e76c810f34bfeef1dac5  ringworld-boundary.png
642abcdc8feaf16598300447481f9b1aca0b8dde47b96e97037364fcbc209de1  ringworld-projection-tangent.png
28dec39c638d40f6c03d41ed4c0e69d755462df52fcadf83fecd2fc0799eca81  ringworld-projection-up.png
3d307083efb4378a4ba04f2c571240cae9687872069fe8cea8b27c662d63ee43  ringworld-multiplayer-a.png
4825b3759e31ec4ecff71f52eb0621fed255bac462385f6401b8692fe3c97a49  ringworld-multiplayer-b.png
```

Deployment-specific backups and external-service configuration are maintained
outside this source repository.
