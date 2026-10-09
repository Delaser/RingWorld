# Release test setup and capture consolidation

The 9 October 2026 change keeps all supported versions, both loaders, ten nightly
fixtures per cell, isolated worlds, frozen candidates and evidence requirements.

- GitHub Java builds now cache Gradle dependencies and wrapper downloads using
  the existing pinned setup-java action. Cache keys include both builds, settings,
  properties, wrapper pins and qualification manifests. All six jobs remain parallel.
- All eight Gradle nightly runners accept the same optional `--gradle-loom-cache`
  seed. Each runner revalidates official metadata, client/server bytes and indexed
  assets before copying into its disposable home. No shared writable game/cache state.
- Production projection captures noon, dusk, night and rain in one client session.
  Each change runs through the integrated server, rechecks the centered pose and
  rendered sections, then records fresh per-view frame metrics and screenshots.
  The runner requires all four environment completion markers, twelve screenshots
  and every environment/view metric. It waits for screenshot callbacks before
  advancing; incomplete callbacks have a bounded failure timeout.
- Production seam/rim visual parity remains a separate fresh world/session.
  Recovery, lifecycle, restart and multiplayer gates retain their independent runs.
- Multiplayer/raid settling delays remain unchanged because earlier host-load
  failures justified them. Heavy native fixtures remain serial on this laptop.

Across ten runtime cells, weather batching removes thirty client startups and
thirty production-world copies. This is an operation-count reduction, not a
measured percentage reduction in total qualification time. Existing source-world
immutability checks and hash-bound retention remain active.

Development may select affected fixtures for feedback; partial selections remain
INCOMPLETE and cannot replace complete final-candidate release qualification.

Validation before integration: 452 Python tests (two platform skips on macOS),
Fabric/NeoForge Java build checks and the GitHub six-cell build matrix. Fresh full
release qualification and native batching evidence are recorded separately in
`deploy/qualified/1.4/README.md`; do not infer a release PASS from this document.

The first full 1.4 run exposed an obsolete Atlas UI revision marker: a floating
gold block was correctly omitted by the new manufactured-platform selector, so
the fixture could never observe its expected height. The revision proof now
places and removes stone, a natural material which must remain represented.
Its revision, height, removal, handshake and normal-disconnect assertions remain
required. The stopped run and logs remain under `logs/release-1.4`; qualification
must restart with freshly frozen candidates from the corrected source.
