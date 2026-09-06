# Exterior visibility and lighting

The owner reported disappearance of nearby real chunks immediately outside the finite Z walls and black hands/entities. The before capture at Z−129.411 shows the Atlas, floating entities, no live chunks, and a black hand. This was a newly created world (`New World (1)`), not the earlier wall study save; its Atlas was still generating.

Vanilla 26.2's section graph gates traversal on its loaded-column set. RingWorld deliberately does not send exterior columns, so starting at the exterior camera column leaves traversal waiting for a column that will never arrive. The existing non-occluding traversal does not bypass this readiness check. Queue initialization now clamps only its seed Z to the nearest finite-band column. Rendering, camera position and run-update distance checks still use the actual camera. The same queue hook exists in 26.1.2.

The client sky-light engine has no data for that unsent exterior. Its read-only light lookup now returns open-sky level 15 for exterior Z positions in a RingWorld ClientLevel. Block light, interior positions and server lighting are untouched. The normal lightmap still applies time of day and weather; this is not fullbright rendering. LevelLightEngine.getRawBrightness also delegates through this lookup, covering the darkness vignette.

Both loader builds and their existing 434 JVM tests passed on 26.1.2 and 26.2. Runtime validation follows below; these checks do not qualify the complete multiplayer/topology release matrix.

The optional `ringworld.fidelityGalleryWorld` JVM property lets the existing resume fixture reopen a specifically selected save without resetting the fixture or changing the default world. It is used here to preserve the owner's active new world rather than reopen the earlier gallery save.

Runtime Fabric 26.2 reopened the exact active save and retained its exterior position. The after screenshot shows live desert terrain and the wall restored, a normally lit hand, and no black vignette. Before/after screenshots: `logs/industrial-wall-study/run/screenshots/outside-dark-before.png` and `outside-dark-after.png`. No weather/time commands or terrain edits were used for this comparison. The new world Atlas remains in progressive generation, which explains its unfinished far-distance texture. Client left open. Runtime on 26.1.2 and NeoForge was not repeated.
