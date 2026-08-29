# Create rotating-bearing C.1 qualification checkpoint (2026-08-29)

This checkpoint investigates the reported disappearance or flat placement of
glued Mechanical Bearing contraptions on the exact qualified adapter tuple:
Minecraft 1.21.1, NeoForge 21.1.239, Create 6.0.10, and Create's nested
Flywheel 1.0.6. It is local regression evidence, not published support or
release metadata. Fabric Create remains unqualified.

No production topology, tracking, protocol, persistence, renderer, shader, or
compatibility-mixin behavior changed. The exact adapter still applies four
server and four physical-client mixins. The dead
`EntityVisibilityTester`/`AbstractEntityVisual.isVisible` CPU target remains
absent, and this exact tuple still does not consume `RingPresentationBounds`.

## Real mechanism and lifecycle coverage

The disposable Mechanical Bearing fixture builds a 27-block asymmetric
contraption through Create's real bearing search/removal/entity path. Two
13-block branches are joined only by 26 real Super Glue edges. They include
opaque and translucent blocks, saturated target-only palette blocks, a moved
chest, a moved shulker box, and a moved chassis. An adjacent unglued copper
block is the negative control.

The fixture records exact pre-assembly block states and glue edges, proves the
captured set is removed into a real `BearingContraption`, proves the unglued
block remains authoritative in-world, and later requires exact block-state and
glue-edge restoration. Server source and ownership positions stay canonical;
only the client entity/camera uses a nearest presentation chart.

Three indirect routes cover a normal location, a high-chart positive seam,
and a fresh low-chart reverse presentation. The seam arms physically cross
the presentation seam while rotating. Separate explicit instancing high/low
and backend-OFF high/low runs use the same mechanism and deterministic pose
sequence. Every run proves:

- real bearing assembly and continuous live angle changes;
- six in-view sweep poses at materially separated angles, plus two deliberate
  off-screen poses;
- uninterrupted entity and visual identity inside each sweep generation;
- speed magnitude change and sign reversal through ordinary power control;
- aligned stop/disassembly and exact restoration;
- ordinary restart/reassembly as a second lifecycle generation;
- save/disconnect/full integrated-server reopen while assembled and rotating,
  followed by a visible third generation and final exact restoration.

The high/low/default and explicit instancing paths record finite live
`ContraptionVisual` embedding matrices and separated angle/matrix samples.
Backend OFF records zero `ContraptionVisual` embedding calls and visibly
renders the same target through the audited vanilla fallback. A separate real
Windmill Bearing control captures 13 glued blocks, proves wind-driven angle
progress, renders an enlarged four-color assembly, stops near its normal
alignment, disassembles, and restores all blocks and glue.

## Why checkpoint C was invalid

Checkpoint C's functional and lifecycle assertions were useful, but its seven
Mechanical contact sheets were not graphical qualification. Several cameras
were aimed with flat intrinsic/canonical deltas; the target was absent from
images labelled center, edge, re-entry, or reopened. Entity membership, a live
`ContraptionVisual`, finite embedding matrices, and zero deletes do not prove
that Flywheel emitted target pixels. All C screenshots were therefore rejected
as render evidence. They are not included in the C.1 accepted manifests.

## C.1 projection and pixel methodology

C.1 aims in physical ring space. It transforms all 216 rotated block corners
to camera-local curved coordinates, derives yaw/pitch from their curved bounds
center, and logs projected screen bounds, depth, viewport intersection, and
corner counts. A NeoForge-only pure test covers centered aiming, opposite yaw
offsets, behind-camera rejection, and partial-versus-outside viewport bounds.

The screenshot verifier uses the logged projected target rectangle, clipped to
the 1280x720 viewport and expanded by a documented 24-pixel tolerance for the
one-frame screenshot handoff and anti-aliasing. The broader pose region is only
a sanity ceiling; it is not the target oracle. Every visible capture requires
the clipped target region to remain within that ceiling.

Target pixels are assigned to at most one nearest reviewed prototype: gold,
magenta, lime, or purple. A pixel cannot satisfy two groups. A visible capture
requires at least three distinct groups and a bounded multi-pixel footprint
inside the projected target region. The two deliberate leave frames instead
scan a narrow upper-center in-frame sanity region and require no more than
eight total target-like pixels. All 14 accepted negative frames recorded zero.
The manifest schema separately records `expectedVisible`, `projectionRoi`,
`poseSanityRoi`, mutually exclusive `paletteCounts`, and
`paletteFootprint`, together with camera/angle/backend/identity proof.

This pixel oracle is independent of the functional lifecycle assertions. A
functional PASS cannot substitute for pixel evidence, and a single static
image cannot substitute for separated angle and transform samples.

## Result and limits

The suspected product defect was **not reproduced** in this exact tuple and
bounded mechanism. Manual review of all seven hardened Mechanical contact
sheets finds the colored assembly in all 56 expected-visible frames and both
lifecycle frames per run, absent in all 14 deliberate leave frames, and
materially consistent across indirect, explicit instancing, and OFF. The
enlarged Windmill is also inspectable. Automated projection, palette,
footprint, identity, angle, matrix, and lifecycle assertions all pass.

This does not qualify every Create contraption or every GPU/driver. It covers
the tested Mechanical/Windmill bearing shapes, the selected backends on this
Windows machine, the bounded distances/poses, and the exact artifact tuple.
The broader seam kinetic factory, fluid machine, and linear moving-machine
matrix remains separate future work. Since no production failure was
reproduced, this checkpoint introduces no speculative renderer or tracking
fix.

## Retained local evidence

Accepted logs, schema-2 manifests, and screenshots are copied under:

`C:\Users\Admin\AppData\Local\Temp\ringworld-create-bearing-reproducer-evidence\checkpoint-c1-hardened`

The seven manually reviewed contact sheets are under:

`C:\Users\Admin\AppData\Local\Temp\ringworld-create-bearing-reproducer-evidence\checkpoint-c1-hardened-contact-sheets`

Earlier C captures, setup failures, the first obscured Windmill image, and the
pre-hardening unlabelled contact sheets remain segregated outside those
accepted directories. They are diagnostic history, not qualification results.

Contact-sheet SHA-256 values:

| Route/backend | SHA-256 |
| --- | --- |
| indirect high | `3aaaa1c0e2d236d0b74389f9bf63b18212421d16421c8048a79c2a04d1ac9589` |
| indirect normal | `644d5828fc85bf12fd967065d2f5b769a678b4afafdfcc5d4f588e1e7c1a8750` |
| indirect low | `2355e13757c2cb259ff302f66298bd8199c195826c9eb06f646fa8f52a59a5ef` |
| instancing high | `dc9892b9379d3ed303da9cd441fd80bfdd0eadf5e984e6d24fe11542fdbb4d4c` |
| instancing low | `0fc6132f2746e81706e6fb035f10c9a5595742e0b38c0ef78ebac925f1390ff2` |
| OFF high | `7287feae2b224727103c5d5840116aa72019564bdbc75a4e057845b0d4072b9b` |
| OFF low | `6f28134a866ebd8eacf709f2216db31595e9a4427c03e1bbee308ee841a44342` |

Each manifest and its bound source log are independently hashed:

| Route/backend | Manifest SHA-256 | Source-log SHA-256 |
| --- | --- | --- |
| indirect high | `a5fe5bad223502c7112fdcce97e7631c8102e760109161400adc21c263329e65` | `6b683a459d7cbd8cb18e74894d76cba03d96c0120b6152fbb82b6d59aebc54e9` |
| indirect normal | `709118ae8d78d544ad7bd168a6c0c5559524b94723570855f5bcde614c2635f3` | `3608912cf795d9277af1fd845d07e4b245d2307abe20f19f2e325934db3324d3` |
| indirect low | `ad608a1c00a71b054bebc586d1f545bdf9f703906e518758bcd1792ef01cb126` | `847d46ce717cae7f623290a595fe2fae76a9ba5764e6be3f5c69ddf16ca6952a` |
| instancing high | `cd30272b76c65e3207ddee81b9b18012541749937c9d9b87dc218b47bb18a600` | `257b4c15b2c4e469bf7c9fd213fa2df69c6d0c1683cd5dd9783e2fd6b493fac7` |
| instancing low | `a76bc97c29cb45239cdf736746ab149b59f34a34cd5d52b696f0c90c71945e90` | `14f88ee7483728a2fb6ff568d15262779376bb93acc69a96f4af8797f22328d4` |
| OFF high | `8b670b46319c321910df1640faefdff032ddf11126e8f8e694d3b3cbcecc1514` | `2eeb63efd05b61169af6ca426275c3bdce0b292c7f8535f410959c2cfdae259f` |
| OFF low | `68cbd2886678073a1ce1d2c0cd7f88875a91e245e4ce91774e5825c4df9cb1f1` | `6d9518ad4f22a5303cad754f709d8e75882f20a1cefbeeb21ce090c20449393d` |
| Windmill indirect | `c417029b6da6b4080369269ba463a791d1f3ff3daddadeba83e47932305f5cdf` | `38cf065105591deae5659923095a19da8e91247f859f03c69900794d74039397` |

## Proportional source and absence gates

Java 21 full source gates pass with 368 tests in 62 suites for Fabric and 376
tests in 65 suites for NeoForge, with zero failures, errors, or skips. The
four additional NeoForge cases are the fixture projection tests. NeoForge
`build` also passes the mandatory 371-component / 752-artifact / 752-hash
inventory and the isolated exact Create/Flywheel dependency and shader gate.

The Create-absent dedicated boot applies 0 server / 0 client compatibility
mixins. The exact-tuple dedicated boot applies 4 server / 0 client mixins and
passes the phase-3A belt/tank server fixture. Every graphical bearing run
applies the unchanged 4 server / 4 client target set. The existing no-Create
`:neoforge:runCreationUiClient` gate passes all 13 captures with an empty
`mods/` directory, zero Create-compat applications, and no Create/Flywheel
linkage error; its final log SHA-256 is
`a92dadf9cc2421cc02293e314eb82650736440455f86ed302d7a53db51cf03cf`.

The final source commit is recorded in the control-task checkpoint after the
clean local commit. The manifest `sourceCommit` fields are regenerated against
that commit before handoff.
