# Full release qualification on macOS and Windows

Use the same release workflow from macOS Terminal or native Windows PowerShell
in a clean, pushed checkout. Both hosts use the same Python coordinators,
manifests, frozen-candidate rules and test gates; launcher and process cleanup
adapt to the host. Shared examples use `python`; use `python3` on macOS if that
is the installed Python 3 command.
It prepares six JARs and their evidence; **do not upload, publish, tag a release,
or update the public server**. The earlier staged 1.4 JARs predate the latest fixes.
Use fresh quick runs and test their exact frozen candidates throughout.

## Machine and source

- Git, Python 3.13, and a JDK 25 installation. Put the JDK's `bin` first on PATH
  and set `JAVA_HOME` to that same installation.
- Use a local checkout with adequate free space. On Windows, prefer a short path
  such as `C:\rw`, with long-path support available. Avoid cloud-synced folders,
  network shares and junction/symlink checkouts.
- Keep an interactive desktop session available for graphical tests.
  Clients run hidden and muted, but still need a working graphics driver/context.
- Allow space for Gradle dependencies, copied worlds and retained screenshots/logs.
  Run the groups serially; do not run multiple release coordinators at once.
- Begin from the merged cross-platform preparation commit or a later approved main
  commit. Record the full source SHA before starting; do not edit or pull midway.

Windows PowerShell:

```powershell
Set-Location C:\rw
git fetch origin
git switch main
git pull --ff-only
git status --porcelain
git rev-parse HEAD
git rev-parse '@{upstream}'
java -version
python --version
$env:PYTHONPATH = 'scripts'
python -m unittest scripts/test_qualification_host.py scripts/test_minecraft_qualification_executor.py
if ($LASTEXITCODE -ne 0) { throw 'Windows host preflight failed' }
```

macOS Terminal (substitute your checkout path):

```sh
cd /path/to/RingWorld
git fetch origin
git switch main
git pull --ff-only
git status --porcelain
git rev-parse HEAD
git rev-parse '@{upstream}'
java -version
python3 --version
export PYTHONPATH=scripts
python3 -m unittest scripts/test_qualification_host.py scripts/test_minecraft_qualification_executor.py
```

Require empty Git status, equal HEAD/upstream SHAs, Java 25, and passing preflight.
The preflight includes a real timed-out parent/child process test: verify that
neither process survives. On macOS, qualification uses `gradlew`, NeoForge's
generated `run.sh` and POSIX process groups. On Windows, it calls the reviewed
Gradle wrapper JAR directly through Java, avoiding batch quoting, and uses the
pinned NeoForge installer's `user_jvm_args.txt` and `win_args.txt` through Java.
Normal manual `gradlew.bat` commands remain available on Windows.

Nested child runners retain Windows home-directory variables while still
filtering secrets. Disposable cleanup uses native extended paths even when
Windows long-path policy is disabled; evidence paths keep their ordinary form.
Cleanup must preserve retained screenshots/reports and the validated worldgen
source. A cleanup failure still fails the fixture. Some static symlink-security
tests require Windows symlink privileges; report an unavailable privilege
separately and retain native CI evidence, rather than removing those checks or
claiming a complete local static PASS.

Atlas recovery accepts the exact expected report path in native relative form
(including `.\\world\\...` on Windows), POSIX relative form, or native absolute
form. Other roots and traversal spellings remain invalid; do not rewrite captured
reports to pass validation. Native regression coverage exercises both relative
spellings through both loaders' interrupted and resumed recovery stages.

## Prepare the production test world

The production fixtures need a stopped, fully pregenerated world saved in **26.1**,
so it can be opened forward on every target. The Large 26.3 multiplayer world is
not a replacement: older targets cannot open a newer save. Source worlds and
caches are ignored by Git and will not appear in a fresh clone.

Either copy an inventoried complete 26.1 test world below `dist/qualification`
and verify its transferred hashes, or generate a new disposable one as below.
This is test-input preparation, not release-candidate qualification. The example
matches the earlier production fixture's 16,384 × 256 size and seed.
Review the Minecraft EULA before setting `eula=true`.

Windows PowerShell:

```powershell
$repo = (Get-Location).Path
$sourceRoot = Join-Path $repo 'dist/qualification/windows-production-source'
$sourceRun = Join-Path $sourceRoot '26.1-fabric/run/run-headless-prewarm'
New-Item -ItemType Directory -Force $sourceRun | Out-Null
Set-Content -Encoding ascii (Join-Path $sourceRun 'eula.txt') 'eula=true'
Set-Content -Encoding ascii (Join-Path $sourceRun 'server.properties') @(
    'server-ip=127.0.0.1'
    'level-seed=2939526885284649652'
    'view-distance=2'
    'simulation-distance=2'
)
$sourceArgs = @(
    ':runHeadlessPrewarmServer', '--max-workers=1', '--console=plain'
    '-Pminecraft_version=26.1', '-Ploader_version=0.19.3'
    '-Ploom_version=1.17.19', '-Pfabric_api_version=0.145.1+26.1'
    '-Pneoforge_version=26.1.0.19-beta', '-Pmoddevgradle_version=2.0.143'
    '-Pmod_version=0.0.0-qualification+mc26.1', '-Prelease_label=qualification'
    "-PringQualificationRoot=$sourceRoot", '-PringQualificationCell=26.1-fabric'
    '-PringHeadlessPrewarmCircumference=16384', '-PringHeadlessPrewarmWidth=256'
)
& .\gradlew.bat @sourceArgs
if ($LASTEXITCODE -ne 0) { throw 'Production fixture generation failed' }
$world = Join-Path $sourceRun 'world'
$report = Get-Content -Raw (Join-Path $world 'ringworld-prewarm/result.json') | ConvertFrom-Json
if ($report.status -ne 'COMPLETE' -or $report.completedCells -ne $report.totalCells) {
    throw 'Production fixture is incomplete'
}
```

macOS Terminal:

```sh
source_root="$PWD/dist/qualification/macos-production-source"
source_run="$source_root/26.1-fabric/run/run-headless-prewarm"
mkdir -p "$source_run"
printf '%s\n' 'eula=true' > "$source_run/eula.txt"
printf '%s\n' 'server-ip=127.0.0.1' 'level-seed=2939526885284649652' \
  'view-distance=2' 'simulation-distance=2' > "$source_run/server.properties"
./gradlew :runHeadlessPrewarmServer --max-workers=1 --console=plain \
  -Pminecraft_version=26.1 -Ploader_version=0.19.3 \
  -Ploom_version=1.17.19 -Pfabric_api_version=0.145.1+26.1 \
  -Pneoforge_version=26.1.0.19-beta -Pmoddevgradle_version=2.0.143 \
  -Pmod_version=0.0.0-qualification+mc26.1 -Prelease_label=qualification \
  "-PringQualificationRoot=$source_root" -PringQualificationCell=26.1-fabric \
  -PringHeadlessPrewarmCircumference=16384 -PringHeadlessPrewarmWidth=256
```

Require exit code 0 and `status: COMPLETE` with equal `completedCells` and
`totalCells` in `$source_run/world/ringworld-prewarm/result.json`.
On either host, record the absolute path to the resulting `world` directory
for the shared full-suite commands below.

Keep this source stopped and unchanged. The coordinator independently inventories
and checks its version, settings and Atlas before copying it into disposable tests.
If a regenerated fixture has an obstructed projection viewpoint, inspect it and
use the documented `--projection-camera-x` override; retain that choice in evidence.
Do not copy the old value blindly or relax image/geometry checks to obtain PASS.

## Fresh quick qualification

Run each command separately, requiring exit code 0 and a complete PASS report.
Record each printed run ID with its manifest. Reports are retained below
`dist/qualification/matrix/<run-id>`; do not select an old run by directory order.

```text
python scripts/run_minecraft_qualification.py --tier quick --manifest config/minecraft-version-matrix.json --all
python scripts/run_minecraft_qualification.py --tier quick --manifest config/minecraft-version-matrix-26.2.json --all
python scripts/run_minecraft_qualification.py --tier quick --manifest config/minecraft-version-matrix-26.3.json --all
```

This creates six frozen candidates and tests ten runtime cells. The 26.1.x pair
must run unchanged on 26.1, 26.1.1 and 26.1.2. Use manifest pins, not the checkout's
ordinary development defaults. Optional verified caches may be supplied using
the existing cache flags; Mac caches and absolute paths are not prerequisites.

## Full release suite

Replace the run-ID and world-path placeholders below with the **new PASS quick
run IDs** and prepared world's absolute path. Run each command separately,
requiring exit code 0 and complete PASS before proceeding. Run all ten
fixtures per cell without `--cell` or `--fixture` filters: 60 + 20 + 20 = 100.

```text
python scripts/run_minecraft_nightly_matrix.py --manifest config/minecraft-version-matrix.json --quick-run-id NEW_26_1_X_QUICK_RUN_ID --production-world "PATH_TO_COMPLETE_26_1_WORLD" --execute
python scripts/run_minecraft_nightly_matrix.py --manifest config/minecraft-version-matrix-26.2.json --quick-run-id NEW_26_2_QUICK_RUN_ID --production-world "PATH_TO_COMPLETE_26_1_WORLD" --execute
python scripts/run_minecraft_nightly_matrix.py --manifest config/minecraft-version-matrix-26.3.json --quick-run-id NEW_26_3_QUICK_RUN_ID --production-world "PATH_TO_COMPLETE_26_1_WORLD" --execute
```

The ten fixtures are creation UI, world generation, Atlas recovery, Atlas UI,
multiplayer, raids, map/compass, production lifecycle, curved objects, and
production rendering. Preserve the default settle times and four weather/time
conditions. A dry run or filtered run is INCOMPLETE, not full-release PASS.
Saved-world upgrades and optional launcher bundles remain outside this release's scope.

On failure, retain the logs, terminal reports, source SHA and frozen hashes.
Identify the cause before retrying; do not overwrite evidence or quietly call a
set of repaired runs a monolithic PASS. Code fixes require fresh candidates.
Windows timeout cleanup uses `taskkill /T /F` only for the owned test process tree;
an already-exited PID is accepted only after confirming the exit. Other cleanup
failures still fail the fixture. Do not kill unrelated Java processes. Never
reuse a retained cell while its processes still run.

## Autonomous recovery during an authorized release

The release agent owns diagnosis and routine harness/tooling repairs through
qualification and staging. A failed fixture is evidence to investigate, not a
reason to request another generic permission to continue. The owner's standing
release scope authorizes reversible development, focused tests, a reviewed PR,
and merge of a verified repair. It does not authorize publication or weaker gates.

1. Retain the failed run ID, source SHA, frozen hashes, terminal report, relevant
   logs/screenshots and exact failure stage. Stop only the owned coordinator and
   its descendants; confirm cleanup before reusing a runtime. Keep the stopped
   production input world and prior reports unchanged.
2. Classify the cause from evidence: host/dependency failure, harness failure,
   or product regression. Inspect the first actual error and player/screen state,
   rather than treating the final timeout as the cause. Do not repeat a known
   failure unchanged or increase timeouts to hide it.
3. For host failures, restore the documented prerequisite and verify it with a
   small real probe before rerunning. Reuse hash-verified dependency, Loom and
   Gradle-distribution caches through supported flags. Do not change dependency
   pins or product code to compensate for a missing display or download timeout.
4. For harness/product defects, use a clean branch from current main, make the
   smallest correction and add a regression for the observed failure. Keep both
   Windows and macOS launch/path/process behavior and every supported 26.x loader
   equivalent. Run focused static checks and the actual failing fixture as a
   diagnostic. Do not change the checkout during an active qualification run.
5. Review the diff and native CI results, merge the verified PR within the
   authorized repair scope, then start from clean pushed main. Any source change,
   including test harness changes, requires fresh quick runs, frozen candidates
   and all 100 full fixtures. Development probes and previous-source PASS results
   cannot qualify those replacements. An unchanged-source host repair may retain
   its quick candidates, but still needs a complete fresh unfiltered full run.
6. Continue the next suite automatically after a complete PASS. Stage all six
   JARs only after the full gates pass; hand back hashes, checklists and screenshots
   for owner visual review. No upload, release tag or public-server update.

Stop for a specific action outside the authorized scope, an unavailable OS
security interaction, the owner's usage floor, or a cause that remains unresolved
after evidence-driven investigation. Report the exact boundary and smallest
required intervention. Do not invent an approval checkpoint merely because a
repair changes the source SHA. If cross-host messaging is unavailable, write a
self-contained blocker/continuation report in the build thread and retained logs;
continue independent authorized repairs instead of waiting for another chat.

Graphical prerequisites apply on both hosts: an active desktop and genuine
hardware OpenGL, even for hidden clients. On headless Windows, a standalone
persistent virtual display is a host prerequisite; TeamViewer presence or an
installed GPU alone is insufficient. After driver installation, verify active
resolution/refresh, SDR, capture, NVIDIA hardware OpenGL and separate client
launches with TeamViewer disconnected. Do not disconnect a human session or
install a driver without authorization. macOS uses its native desktop/graphics
context and process groups; do not apply Windows display commands there. Driver
installation and security prompts remain host setup, not release-suite steps.

Production lifecycle trials must keep their opt-in test player safe during the
entire close/reopen sequence, including after dimension transfers finish. Flight
abilities can reset on reopen. Protection must be test-scoped, server-owned and
must not suppress dimension, complete-Atlas, geometry or retained-world assertions.
A dead player or death screen is a fixture failure, never a reason to dismiss the
screen and declare success. The Windows run on `61e409db` passed seven full
fixtures before its reopened survival player fell from `(0.5, 200, 0.5)` and the
lifecycle timed out. Keep that failure as historical evidence; it is separate
from the resolved standalone-display/OpenGL prerequisite.

Production projection and visual-parity captures must switch only their copied
integrated-server player to Spectator as soon as the world is ready, before
waiting for Atlas completeness or dismissing a pause screen. Waiting until
camera/environment setup leaves a survival player exposed during the full Atlas
stream. On Windows source `75a2f642`, 79 full fixtures passed before the 26.2
NeoForge projection player was slain by a Zombie while its Atlas was at 58%.
The death screen prevented further progress. Preserve that failed run separately;
never dismiss a death screen or respawn to make a capture pass. Both capture
clients must fail explicitly on death, including the version-owned 26.3 parity
implementation. Keep all Atlas, camera, environment, screenshot and fresh-session
parity assertions intact after establishing player safety.

## Stage and hand back six JARs

After all three full suites pass, create new release inputs below an ignored
directory such as `dist/release-inputs/windows-1.4`. Copy the three historical
`deploy/qualified/1.4/*-release.json` descriptors there for review, and use the
current grouped patch notes as each new changelog. Retain exactly one
`{{RINGWORLD_CORRESPONDING_SOURCE_URL}}` placeholder in each changelog. Do not edit
the historical staged changelogs or treat their hashes as current qualification.

For each group, call `scripts/stage_qualified_release.py` with its matching
`--manifest`, new `--quick-run-id`, `--from-frozen`, new `--config`, and new
`--changelog`. Follow [JAR export](JAR_EXPORT.md) for byte-equivalence and source
checks. Review all six files together: same frozen source commit, correct game
ranges/loaders, six SHA-256 hashes, complete quick/full reports and screenshot bank.

Return the six staged JAR paths, hashes/sizes, the ten-cell/100-fixture checklist,
source SHA, any failures or composite repairs, and representative screenshots.
Owner visual confirmation comes before upload. No publication credentials are
needed for this handoff.

## Validation boundary

All 465 local Python tests run on macOS (461 pass, four Windows-only tests
skip). Windows/Linux static CI passes, including the actual parent/child timeout
test on Windows. This preparation is not a claim that replacement Minecraft
release candidates have completed their full suite on either host. Whichever
machine runs the release must run its native preflight, fresh quick cells and
full fixtures above and report their real outcomes.

The first native Windows cycle on source `fdabc511` passed all ten quick cells,
but its full coordinators reported ten FAIL and ninety INCOMPLETE fixtures.
Missing child-home variables and long-path cleanup were confirmed harness bugs;
dependency timeouts also affected some children. Two creation-UI children saved
52 screenshots, but cleanup failure prevented coordinator PASS. Preserve those
reports as failed evidence. After the runner fixes, first verify the native
regressions and a targeted development fixture, then use fresh quick candidates
and all 100 full fixtures from the approved pushed source. Do not combine the
earlier quick runs with a changed-source full run or claim the old candidates
are staged releases.

The next Windows cycle on `eb414a55` passed all ten fresh quick cells and the
creation-UI development slice. Its full run passed creation UI and world
generation, then failed Atlas recovery on the native relative report spelling.
The owned coordinator was stopped, leaving 97 fixtures incomplete or unstarted.
After the exact-path parser fix, verify its native regression and a targeted
Atlas recovery development fixture, then run fresh quick and complete full
qualification from the approved merged source. Historical reports and frozen
candidates keep their original source identity.
