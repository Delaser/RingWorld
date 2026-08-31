# Repository audit — 2026-08-31

## Outcome

The optional-feature branch is structurally healthy and remains suitable for
continued development. The audit found no tracked credentials, generated game
state, release archives, unexpected large files, or licence regressions. Both
loader builds pass the complete shared Java suite.

This is a source-state audit. It is not fresh frozen-candidate, multiplayer,
packaged-client, host-publication, or live-server evidence.

## Scope

The audit covered:

- tracked source, build logic, workflows, deployment templates, and docs;
- Fabric/NeoForge source boundaries and duplicate compiled output;
- ignored build, qualification, launcher, save, and capture directories;
- local Git worktrees, object integrity, and remotes;
- likely credential markers, unexpected binaries, large tracked files, and
  relative Markdown links;
- Java and Python regression suites.

The live demo server and its world were not contacted or changed. Existing
ignored saves and the user's current launcher state were preserved.

## Correctness repairs

Three concrete defects were fixed in focused commits before this audit record:

| Commit | Repair |
| --- | --- |
| `9852430` | Clear server-owned wall and sky appearance when a client session ends, preventing state leaking into the next world. |
| `b384211` | Make wall-pattern noise periodic even when circumference is not divisible by the sampling scale. |
| `87e41bf` | Generate seed previews through an isolated generator snapshot and reject cancelled or stale asynchronous results. |

Each repair has focused Fabric and NeoForge unit coverage. The complete shared
suite then passed **377 tests per loader**, with zero failures, errors, or
skips.

## Repository hygiene changes

- Added a Java 25 GitHub Actions build for Fabric and NeoForge source changes.
- Made the quick Windows packaging contract run on every pull request so a
  brittle path allow-list cannot omit a transitive helper change.
- Pinned GitHub Actions to reviewed commit SHAs.
- Added common Python, IDE, coverage, and patch-scratch paths to `.gitignore`.
- Made the example RCON sender read a caller-selected `server.properties`
  rather than embedding one host path.
- Removed host paths, world seeds, control-panel references, and backup
  locations from the current-state record. The public alpha bootstrap endpoint
  remains only where compatibility with previously downloaded installers
  requires it.
- Corrected the sun-rendering snapshot's moved source link and reconciled
  current Atlas format, supported-version, artifact-name, and test-count copy.

## Validation

| Check | Result |
| --- | --- |
| Fabric Java 25 default 26.1.2 `test build` | 377 passed |
| NeoForge Java 25 default 26.1.2 `test build` | 377 passed |
| Qualification-static Python set | 344 passed |
| Windows/package/prototype contract set | 41 passed; 2 expected non-Windows skips on macOS |
| Full Python discovery | 428 passed; 2 expected non-Windows skips on macOS |
| Workflow YAML parse | 3/3 passed |
| `git diff --check` | passed |
| RCON helper compile and focused tests | passed; 4 tests |
| Relative Markdown targets | 155 checked; 0 missing |
| Tracked files at least 1 MiB | none |
| Tracked binary audit | only the expected 48,462-byte Gradle wrapper JAR |
| Git object audit | no corruption |

The credential scan found only the deliberately malformed private-key marker
inside `scripts/test_stage_modrinth_release.py`, where it proves the release
stager rejects embedded private keys. It is not a usable credential.

## Local disk cleanup

Disposable qualification `gradle-home`, dependency-cache, and build trees were
removed only after their retained reports, runtime logs, captures, candidates,
and terminal evidence had been separated. This reclaimed approximately
**39 GiB**. Twenty-one clean, inactive temporary Git worktrees were also
removed through `git worktree remove`; their branches and commits were not
deleted.

The repository directory remains large because ignored qualification evidence,
packaged-client review material, fixture saves, and the active development save
are deliberately retained. They are not in Git. Future cleanup should use the
same evidence-aware rule rather than deleting `dist/qualification` wholesale.

Other active, agent-owned worktrees and their local-only remote configuration
were left intact. Worktree and remote configuration under `.git` is not
published with the repository; removing it during this audit could have
destroyed or disrupted work outside the branch's scope.

## Remaining development boundaries

- The three focused repairs still require a fresh frozen-candidate and the
  normal runtime gates before they can be released.
- The retained creation and Atlas UI captures predate those repairs; they are
  useful visual evidence but not evidence for the new candidate bytes.
- The medium Industrial lighting gallery is an interactive retained review
  world, not an isolated qualification task.
- Some optional visual-gallery helpers remain Fabric-oriented. Public feature
  claims must continue to come from the shared dual-loader fixtures.
- A deterministic unified Fabric/NeoForge prototype and both isolated 26.1.2
  dedicated-server boots now pass. Client, complete runtime, packaging, and
  host behavior remain untested, so this is not a supported artifact. See
  [`UNIFIED_JAR_FEASIBILITY.md`](UNIFIED_JAR_FEASIBILITY.md).
