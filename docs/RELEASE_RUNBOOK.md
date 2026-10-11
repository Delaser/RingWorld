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

All 461 local Python tests run on macOS (459 pass, two Windows-only launcher tests
skip). Windows/Linux static CI passes, including the actual parent/child timeout
test on Windows. This preparation is not a claim that replacement Minecraft
release candidates have completed their full suite on either host. Whichever
machine runs the release must run its native preflight, fresh quick cells and
full fixtures above and report their real outcomes.
