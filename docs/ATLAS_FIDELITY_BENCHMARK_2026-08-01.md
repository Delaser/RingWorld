# Atlas fidelity and production resource decision — 2026-08-01

This is the acceptance record for issue #69. It compares the shipped
eight-block atlas sample with finer four-, two-, and one-block candidates for
the 16,384-by-256 production layout.

## Decision

Keep the fixed **eight-block sample step**. Do not introduce adaptive or
selectable fidelity profiles in the current Fabric release candidate.

The profile-5 visual baseline already removed the visible 32-block mesh facets
by matching the mesh to the eight-block source heights. Finer source atlases do
not change the current 4,096-by-256 GPU texture, 393,216-vertex mesh, GPU
memory, or steady rendering work. They mainly increase server/client atlas
memory, generation sampling, save/load cost, tile count, initial transfer time,
and cache churn. There is no matched visual evidence that the remaining
four-block colour/detail opportunity is worth those costs.

This decision introduces no new saved setting, atlas format, payload field, or
world-hash change. Format 6 and `SAMPLE_STEP_BLOCKS=8` remain authoritative.

## Reproducible synthetic benchmark

Run under Java 25:

```sh
./gradlew runAtlasFidelityBenchmark --console=plain
```

The task fills a deterministic terrain-like complete atlas for each candidate,
performs the real format-6 gzip save/load and tile encoder, and runs the same
4,096-by-256 bilinear sampling, relief shading, and mip filtering used before
GPU upload. It writes ignored evidence to
`build/reports/ringworld/atlas-fidelity.md`.

One development-Mac run produced:

| Step | Cells | Samples/chunk | Raw atlas per copy | Full tile stream | Synthetic gzip | Save | Load | CPU texture/mips |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 8 | 65,536 | 4 | 458,752 B | 459,264 B | 224,548 B | 111.0 ms | 75.1 ms | 42.8 ms |
| 4 | 262,144 | 16 | 1,835,008 B | 1,837,056 B | 884,717 B | 411.1 ms | 230.5 ms | 45.9 ms |
| 2 | 1,048,576 | 64 | 7,340,032 B | 7,348,224 B | 3,554,806 B | 1,775.8 ms | 909.0 ms | 37.6 ms |
| 1 | 4,194,304 | 256 | 29,360,128 B | 29,392,896 B | 14,081,949 B | 7,129.2 ms | 3,640.8 ms | 38.9 ms |

Wall-clock figures are a local diagnostic sample, not release performance
guarantees. Exact cells, primitive-array bytes, encoded tile bytes, and
samples-per-chunk are deterministic. Synthetic gzip deliberately contains
more colour entropy than the measured real world and is useful for relative
scaling, not predicting its absolute file size. CPU texture/mip time remains
roughly flat because every candidate expands into the same final output.

At the fixed eight-tiles-per-tick stream limit, a cold full transfer requires
at least 32, 128, 512, or 2,048 server ticks respectively: approximately 1.6,
6.4, 25.6, or 102.4 seconds at 20 TPS before network latency and client work.

## Real production evidence for the retained profile

The complete production atlas benchmark generated all 16,384 canonical chunks
and 65,536 cells in 13 minutes 37 seconds at about 80.2 cells per second. Its
real gzip file was 76 KiB. Server RSS peaked near 1.06 GiB with no swap growth.
The copied world grew about 169.3 MiB, showing that real chunk generation and
storage dominate the atlas arrays.

The production client profile owns fixed costs of:

- 4,096-by-256 RGBA texture and mips: 5,592,384 bytes;
- 393,216 mesh vertices: 9,437,184 bytes;
- peak texture-build arrays/upload scratch: 12,582,912 bytes;
- one observed active client RSS sample: about 1.56 GiB.

Settled profile-5 frame evidence remains:

| View distance | Tangent average | Handoff average | Radial average | Handoff frames over 50 ms |
| ---: | ---: | ---: | ---: | ---: |
| 6 chunks | 8.862 ms | 8.590 ms | 8.639 ms | 0 |
| 12 chunks | 9.608 ms | 8.513 ms | 8.603 ms | 0 |
| 28 chunks | 11.643 ms | 10.954 ms | 8.658 ms | 0 |

Finer source steps would not reduce these fixed GPU/render costs under the
current renderer. A future four-block experiment should proceed only with a
matched real-world A/B capture showing a material transition improvement and
should deliberately version/persist the selected step before it becomes a
supported world property.

## Validation

The production cost matrix covers steps 8, 4, 2, and 1 through checked
`RingDimensionReport` arithmetic. All fit the production layout's hard
16-million-cell allocation limit; block-level sampling crosses the four-million
warning threshold. Larger custom dimensions continue to fail before
allocation when a candidate exceeds the hard budget.

The shipped supported profile remains only step 8, which already passes the
safe-small and production dimension matrices, 6/12/28 visual captures,
production resource budgets, complete atlas generation, revisioned live edits,
and reconnect/cache invalidation rules.
