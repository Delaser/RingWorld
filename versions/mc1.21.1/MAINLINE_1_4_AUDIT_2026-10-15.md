# Mainline 1.4 intake audit — 15 October 2026

Full functionality, implementation order, support matrix and acceptance criteria:
[dual-version plan](../../docs/BACKPORT_1_4_DUAL_VERSION_PLAN_2026-10-15.md).

## Exact source

- Existing backport: `8f1fe43a3f706d98883b75fce478dc80c5c56817`.
- Published 1.4 runtime: `1533878fe3a6eab7562830b3f39cbd7caae6d46a`.
- Publication documentation: main `653b9b5a9fdbf05c900d1a3db7ddd48e40fc8c83`.
- Prior mainline comparison baseline: `a46965c547c07e91e5c1f02e999f376192fe5a94`.

Fetched public refs without resetting this checkout or other worktrees. Inspected
the immutable published-source snapshot, final notes/publication record, complete
release coordinator/runbook and source/test contracts for all 16 audited feature
areas. Earlier trial/proposal/superseded candidate notes are not current behavior.
The former 1.3 upload remains unperformed; no publication action is part of this audit.

## Fresh baseline validation

These checks exercise the unchanged local **1.3** product source. They establish
an intake baseline; they do not certify 1.4 or 1.20.1.

| Check | Result |
| --- | --- |
| Java 21 `build :neoforge:build`, strict dependencies/licence/archive/loader checks | PASS |
| Forced `:test :neoforge:test --rerun-tasks` | 531 Fabric/common + 554 NeoForge; zero failures/errors/skips |
| Hidden GLFW context | Creates a window/context; previous display failure is not reproduced |
| Fabric menu-only creation | PASS; 26 captures |
| NeoForge menu-only creation | PASS; 26 captures |
| Fabric integrated Atlas UI | PASS; 13 captures, ordered edits, handshake, normal disconnect/teardown |
| NeoForge integrated Atlas UI | PASS; 13 captures, ordered edits, handshake, normal disconnect/teardown |
| New planning-validator and workflow contracts | 14 local Python tests PASS; planning status stays NOT_QUALIFIED |

Java 21 is the reviewed Temurin `21.0.12.1+1` installation. Heavy graphical
fixtures ran serially. Their old ignored run folders were copied into retained
prior-evidence directories before preparers reset only disposable fixtures.
No original production source save was opened. The graphics probe establishes
basic context creation, not GPU performance qualification or a disconnected-
TeamViewer display-persistence test.

Visually inspected the fresh Fabric compact-Walls capture and NeoForge completed
Atlas menu capture. This is a two-image layout spot check, not owner visual
acceptance or a review of all 78 baseline captures.

The new planning regressions are wired into the Windows/Linux static workflow.
No hosted CI run or 1.4 runtime acceptance is claimed by that wiring.

Ignored local evidence:

- `.codex-tmp/backport-1.4-audit-baseline-build.log`
- `.codex-tmp/backport-1.4-audit-fresh-tests.log`
- `.codex-tmp/backport-1.4-audit-fresh-test-totals.json`
- `.codex-tmp/backport-1.4-audit-opengl.txt`
- `.codex-tmp/backport-1.4-baseline-ui/results.json` and per-loader logs/captures

## Confirmed implementation gaps and regression risks

The existing port retains the 1.3 unified editor, one-block format-10 Atlas,
water/light data, wall materials, depth/interaction fixes and local fair recapture
queue. It does not yet have the 1.4 multi-request/adaptive scheduler, incremental
checkpoint writer, scheduled chunk maintenance/light-map snapshots, outside-
building policy, nearby correction, new mesh closure, altitude handoff, private
shared rendering core/optional replacement-renderer adapter or camera-water policy.
Some cloud/wall/uniform concepts already exist, but their new semantics still
need the mapped port and runtime comparisons described in the plan.

Two source-confirmed blockers must be handled before accepting a port:

1. The local service invokes `ServerChunkCache.getChunkFuture` from its owner
   tick. The pinned named 1.21.1 method's main-thread branch calls
   `MainThreadExecutor.managedBlock`. Its current nonblocking comment is false.
   This was verified with `javap`, not inferred from the method name. A new
   bounded batch must use an audited nonblocking owner-thread boundary and
   retain ticket/FULL-result/cancel/unload guarantees. No Minecraft decompiled
   source is copied into the project.
2. Published 1.4's service re-enqueues unloaded recapture cells, and its queue
   prioritizes all exact cells before overflow. Our local real-lamp starvation
   correction is newer in this respect. Preserve it rather than replacing the
   service/queue wholesale. Extend the foliage idle observation for the new
   queued player-loaded captures as well as recaptures/dirty tiles/revisions.

The 26.x acceptance coordinator selects ten families, not every development
probe in the repository. Its renderer verifier records frametimes without a
fixed performance budget. The new plan therefore retains all ten families and
adds seven required feature/reliability/performance families, with explicit
measurement gates. Four proposed backport cells imply 40 + 28 required keys.
These counts describe planning coverage, not completed runtime tests.

## Current result

The existing backport baseline is buildable and passes the bounded fresh checks
above. Neither 1.4 backport has been implemented or accepted. 1.20.1 has no
compiler graph/artifact yet. The owner confirmed Fabric/Forge for 1.20.1 and
Fabric/NeoForge for 1.21.1 on 15 October 2026. No new Minecraft/loader,
platform, shader-pack, Sodium or Create support is advertised by this audit.
