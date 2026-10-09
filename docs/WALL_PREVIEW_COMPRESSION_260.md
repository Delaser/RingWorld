# Compressed wall selector previews (#260)

The 30 shared in-game wall samples dominated the download: approximately
12.65 MB of compressed entries in each JAR. Only their bundled copies are now
384x213 indexed PNGs with a 256-colour palette and no dithering. The original
crop and aspect ratio are preserved. Full-quality website previews and images
remain unchanged. PNG is retained because Minecraft NativeImage validates a
PNG header; WebP is not a drop-in replacement.

This integrates only banked commit `cedb7b3`, without nearby-normalisation,
floating-Atlas or unified-JAR experiments. `scripts/wall_samples/package.py`
now emits compact bundled images whenever captures are packaged. Existing
renderers sample normalized UVs, so no Java or shader changes are required.

To regenerate the 30 bundled images from website previews (requires Pillow):

```
python3 scripts/wall_samples/compress_previews.py
```

Regeneration reproduces the banked assets byte for byte. The source PNG total
falls from 12,684,720 to 2,370,843 bytes; their final JAR entries occupy
2,370,895 compressed bytes rather than 12,646,702.

## Measured development JAR sizes

These compare the current development builds before and after image replacement,
not newly published release files. MB below means 1,000,000 bytes. The build's
legacy default artifact filename is not a Minecraft-version identifier;
version columns refer to the pinned Gradle source/runtime cells.

| Minecraft source line | Loader | Before bytes | After bytes | After MB | Reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| 26.1.2 | fabric | 13,657,583 | 3,381,776 | 3.382 | 75.24% |
| 26.1.2 | neoforge | 13,638,584 | 3,362,777 | 3.363 | 75.34% |
| 26.2 | fabric | 13,657,431 | 3,381,624 | 3.382 | 75.24% |
| 26.2 | neoforge | 13,638,436 | 3,362,629 | 3.363 | 75.34% |
| 26.3 | fabric | 13,662,779 | 3,386,972 | 3.387 | 75.21% |
| 26.3 | neoforge | 13,643,737 | 3,367,930 | 3.368 | 75.32% |

Every JAR is 10,275,807 bytes smaller, about 75%. All six source builds and Java
suites pass: 458 cases per loader on 26.1.x and 26.2, 461 on 26.3 (2,754 total).
Every bundled image is present and byte-identical to its compact source in all
six JARs: 180 checked entries. All 30 PNGs decode to RGBA through both LWJGL/STB
3.4.1 and 3.4.3, the native decoder versions used by these Minecraft lines.

Native menu review uses the existing `runCreationUiClient` fixture on Fabric
26.1.2 and NeoForge 26.3, including old rim controls and the redesigned Walls
page at normal and compact GUI sizes. Final native review results are recorded
in the integration PR. This is focused packaging/UI validation; a future release
still needs its normal frozen-candidate qualification. Logs, hashes, exact
sizes and captures are generated under `logs/wall-preview-compression/`.

Future patch-note text: **Smaller downloads: compressed the in-game wall selector
previews, cutting JAR size from roughly 13.6 MB to 3.4 MB across every supported
Minecraft version and both loaders.** Website images retain their original quality.
