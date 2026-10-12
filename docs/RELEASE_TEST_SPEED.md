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

## Next-run preparation (12 October 2026)

The current Windows qualification on `5b53acf` remains unchanged. These changes
are prepared on `codex/next-release-flow` for a later run; do not merge/pull them
into an active checkout.

Measured Windows timing from the retained `75a2f64` run: the 60-fixture 26.1.x
group took 4h 51m 31s, without infrastructure retries. In a matched creation UI
fixture, Fabric spent 87.63s before the first game log and 42.98s afterwards;
NeoForge spent 183.87s before the first game log and 41.00s afterwards. This
identifies preparation as a substantial contributor, but is not a measured speedup
for the next-run change. The 120-second settles remain intact.

ModDevGradle 2.0.147's `NeoFormRuntimeTask` places its compiled intermediate cache
at `GradleUserHome/caches/neoformruntime/intermediate_results`. Every isolated
fixture currently discards that directory. The existing seed supplies Mojang
downloads and assets, so repeated NeoForm compilation can remain despite it.

An optional acceleration snapshot now lives beside the existing external Loom
seed, without another runner flag. With the source worker stopped, use the same
Python command on Windows or macOS, substituting that host's absolute paths:

```text
python scripts/minecraft_neoform_seed.py --source-cache "PATH_TO_TRUSTED_GRADLE_HOME/caches/neoformruntime" --loom-seed "PATH_TO_EXISTING_EXTERNAL_LOOM_SEED"
```

Provision only from a trusted worker using the pinned Minecraft/NeoForge tools.
The SHA-256 inventory checks snapshot integrity; it is not vendor authentication
or qualification evidence. The provisioner refuses to overwrite a snapshot and
accepts only bounded plain intermediate JAR/ZIP/TXT files. Locks, worlds, credentials,
RingWorld build outputs and mutable runtime state are excluded. A failed snapshot
has no valid completed inventory and must not be used; provision a fresh seed.

All eight Gradle fixture runners and quick preparation consume the optional
inventory through `--gradle-loom-cache`. Before staging they validate its hashes,
then copy files into each NeoForge child's private Gradle home and check the copies
against the inventory. Fabric ignores these intermediate files. NeoForm still
checks its own cache keys and regenerates missing or incompatible artifacts.
The external snapshot is never writable/shared by running children. If the optional
inventory is absent, setup behaves as before; a present corrupt inventory fails
explicitly rather than silently becoming trusted. Keep the stopped source cache
and existing external seed unchanged while qualification runs.

No native cold/warm timing or Minecraft integration result is claimed for this
snapshot yet. On the next run, compare the same NeoForge fixture with/without the
optional snapshot, recording preparation time, actual cache hit/compile logs,
candidate hashes and fixture results. Repeat on Windows and macOS before treating
the acceleration as proven. Keep full release coverage and source-world isolation.

The next coordinator preserves per-fixture checkpoints, bounded infrastructure
retries and independent-group continuation. See `docs/RELEASE_RUNBOOK.md` for
failure gates and the all-group command. Extraction of the embedded Java test
harness and safe reuse after harness-only repairs remain a separate change; these
improvements do not authorize mixing previous-source PASS results into a new run.
