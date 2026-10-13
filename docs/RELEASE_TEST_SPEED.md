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

This preparation was held on `codex/next-release-flow` while the Windows
qualification ran. The owner authorized integration on 13 October; PR #289 is
merged. It did not replace that run or its frozen candidates. The later completed
`57efcb0` coverage and remaining improvements are recorded below.

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

## Windows 1.4 build review (13 October 2026)

The `1.4 Build` chat reports 10/10 quick checks, 100/100 reviewed full-fixture
coverage and six staged, verified JARs at frozen source `57efcb0`. This is
composite coverage: 98 passes were retained and two failed multiplayer keys
passed owner-authorized retries. Preserve the original failures and their
provenance; do not call this a fresh monolithic 100/100 pass. No upload occurred.
Those six JARs predate the shared rendering compatibility and submerged-camera
changes in PR #292. Updated patch notes do not update staged binaries: new
runtime source needs new candidates and qualification before publication.

The two intermittent failures were zombie-navigation arrival assertions on
26.1.2 NeoForge and 26.2 Fabric. One stopped before the seam; the other crossed
but stopped short of the target. Four diagnostic runs and the authorized retries
passed without a code repair. The cause is still unconfirmed. Do not disable AI,
widen arrival tolerances or automatically classify this as infrastructure.

The combined PR #289/#292 Python contract suite passes 401 tests (two expected
platform skips on macOS). PR #289 adds independent-fixture/group continuation,
immutable checkpoints, at most two retries for recognized infrastructure
failures, and optional private NeoForm cache seeding. It does not automatically
resume completed coverage or retry assertion failures. Native cache-hit timing
still needs measurement; source/runtime qualification gates remain unchanged.

Further improvements, in priority order (not implemented by this review):

1. Add a portable resume planner which derives missing/failed cell-fixture keys
   from existing strict evidence review instead of generating Windows-only
   scripts by hand. Require identical frozen source, manifest, quick-run identity
   and candidate hashes; preserve valid passes, immutable failure attempts and
   composite-review gates. Assertion retries still need explicit authorization.
2. Add bounded, logging-only navigation diagnostics to the shared multiplayer
   fixture: path reachability, current/next node, target, actual position,
   active goals and loaded-chunk state on route changes and assertion failure.
   Use the correct game APIs and keep existing timing, goals, motion and arrival
   assertions intact. Passing a later diagnostic run is not a bug fix.
3. Produce one portable timing summary per run: dependency/setup, game startup,
   Atlas transfer, required settles, fixture execution, retry/cooldown and
   evidence/staging. Compare matched cold/warm NeoForge cache runs on both hosts
   before claiming faster tests. This separates preparation cost from gameplay
   and avoids cutting deliberate stability waits to improve a headline time.

Keep all 100 full fixtures and ten quick cells. These improvements are for the
next qualified source; never relabel old PASS evidence after a runtime change.

## Resume, diagnostics and setup batching (13 October 2026 follow-up)

The three improvements from the Windows build review are implemented on
`codex/release-test-resume-speed`:

- The nightly planner accepts repeated `--resume-aggregate` inputs. The all-group
  wrapper forwards repeated `--resume-26-1`, `--resume-26-2`, `--resume-26-3`
  flags. Both dry-run selection and execution independently validate the same
  source, quick candidate/evidence, manifest SHA-256 and production inventory.
  Reused PASS terminals and relocated captures/logs pass the existing strict
  source-ABI/exact-candidate review. Only missing/INCOMPLETE keys run by default.
  `--retry-failed-fixtures` explicitly authorizes rerunning recorded FAIL keys;
  it does not waive assertions or erase attempts. Duplicate cumulative
  checkpoints with identical terminals are accepted; ambiguous replacements,
  changed evidence and incomplete key coverage cannot qualify. Reports lacking
  a manifest digest remain on the manual review route. Source changes cannot
  inherit old coverage.
- Resumed complete coverage is `COMPOSITE_COVERAGE_REVIEWED` with
  `monolithic_pass=false`. Fresh complete coverage keeps PASS. Partial selection
  stays INCOMPLETE and failures stay FAIL. All 100 keys and ten quick cells remain.
- Shared multiplayer diagnostics log the zombie's path/node/reachability,
  movement target, running goals and already-loaded source/current/target chunks.
  Normal traces are capped at 32 records, with terminal arrival/failure always
  recorded. The accessor only reads goals. No path recomputation, chunk loading,
  AI changes or altered timing/arrival tolerance is introduced.
- Each nightly and all-group execution writes immutable `timing.json` and
  `timing.md` beside its terminal. The report separates setup/assets,
  concurrent runtime intervals, verification, retries, configured settles,
  unclassified overhead and time from skipped prior commands. Recorded local
  offsets permit first-game-log observations independent of review-host timezone;
  bounded game logs provide observed initial Atlas-to-detailed intervals.
  Observations overlap other buckets and are not additive. Missing measurements
  are not invented. These reports do not change qualification verdicts.

The additional speed pass batches multiplayer and raid preparation plus asset
warmup into one Gradle command. Across ten cells this removes twenty Gradle JVM
startups/configurations. Each fixture still gets isolated writable caches/worlds,
unchanged post-prepare/raid settles, server/client ordering, frozen-JAR checks,
raid reload and verifiers. This is an operation-count improvement, not a measured
percentage reduction in full-release wall time. Do not reuse mutable caches or
combine unrelated native fixtures to claim a larger improvement.

Validation: 417 Python tests pass (two expected platform skips on macOS),
514 Java cases per loader pass locally, and all eleven CI checks at `701646d`
pass, including all six source/loader build groups and Windows/Linux contracts.
The final timing-only correction and preparation-order regressions are covered
by the Python suite; native Java/batching behaviour is unchanged.

A real 26.1.2 Fabric dedicated server plus two hidden, muted clients passes the
full multiplayer scenario, navigation, reconnects, shutdown and verifiers at
`701646d`. This was a disposable source-ABI development smoke using normal dev
caches, not a frozen release invocation. The initial probe failed because its
copied EULA was prepared too late; the corrected run retains that failed record.

The warm ABBA preparation comparison measures separate prepare/assets at
7.73 seconds on average versus 4.07 seconds combined (47.3% lower for this setup
step). The comparison uses the same disposable configuration after an unmeasured
warmup; it proves neither a full-suite percentage nor cold NeoForge/Windows
cache savings. First-game-log delays were 10.86 seconds for the server and
13.86/13.84 seconds for the two clients. The deliberate 120-second settle stayed.

The navigation trace records a reachable commanded target `(2050,120,15)` at
arming, then at tick 11 `WaterAvoidingRandomStrollGoal` replaces it with an
unreachable `(2049,122,14)` target while all observed chunks remain loaded.
The zombie still reaches the existing arrival tolerance and the fixture passes.
This confirms interference in this run, but does not reproduce or fix the earlier
Windows failures. No AI goal or assertion was changed. Preserve this lead for
separate diagnosis rather than claiming that retries fixed the navigation bug.

See [sanitized native/benchmark evidence](RELEASE_TEST_RESUME_SPEED_2026_10_13.json).
No new release candidates are qualified or uploaded by this tooling change.
