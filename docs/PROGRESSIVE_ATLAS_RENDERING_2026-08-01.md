# Progressive atlas rendering — 2026-08-01

Issue #67 removes the all-or-nothing distant-ring gate. A client now displays
trustworthy regions of its current world's terrain atlas as soon as at least
one cell arrives. Missing cells encode zero alpha and remain sky; bilinear
coverage softens only the boundary of known data. Alpha-weighted mip colours
prevent transparent neighbours from adding a dark fringe.

The partial path is deliberately bounded:

- tile packets still coalesce into at most one render revision per second;
- the existing GPU texture allocation is updated in place;
- incomplete atlases use at most source-atlas resolution;
- one reference-height mesh is retained throughout generation;
- verified completion performs one upgrade to the expanded texture and
  terrain-height mesh;
- later complete-atlas revisions update texture data without rebuilding the
  mesh.

This keeps real chunks authoritative and does not invent collision, entities,
structures, or ungenerated terrain. World hash, geometry, format validation,
monotonic cache application, and disconnect/settings teardown continue to
reject corrupt, wrong-world, and stale state.

## Validation

The Java 25 clean build passes all 208 unit/parameterized cases. New pure tests
cover missing/partial/complete alpha and transparent-neighbour mip filtering;
existing atlas tests cover partial disk/tile round trips, monotonic cache
merging, corrupt formats, and wrong world hashes.

The isolated `runAtlasUiClient` real-client gate starts with a zero-cell cache,
loads its first partial surface immediately, captures the gameplay view after
at least 25% is trustworthy, exercises pause/resume/cancel/retry, and advances
continuously to completion. Progressive
updates retained a 256×52 texture and one 79,872-vertex reference-height mesh.
At 13,312/13,312 cells the renderer made one transition to the 2,048×416
texture and terrain-height mesh. Minecraft loaded the RingWorld shader and the
fixture ended with `[atlas-ui-test] PASS` and eleven captures, including the
partial gameplay view.

The complete-atlas pixels remain opaque, so their alpha-weighted mip result and
fragment alpha are identical to the accepted safe-small and production visual
baseline in `ATLAS_VISUAL_BASELINE_2026-08-01.md`. The production partial path
is bounded to its 2,048×32 source cells and one capped 393,216-vertex mesh.
