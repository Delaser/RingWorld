# 1.4 implementation ledger

The owner authorized implementation on 15 October 2026: finish 1.21.1 first,
then 1.20.1 Fabric/Forge, diagnosing and fixing failures until the complete
[acceptance plan](../../docs/BACKPORT_1_4_DUAL_VERSION_PLAN_2026-10-15.md) is met.
This authorizes development/testing, not publication or public-server changes.

Pinned upstream runtime source: `1533878fe3a6eab7562830b3f39cbd7caae6d46a`.
Baseline product: `8f1fe43`; approved plan checkpoint: `695d02c`.
Work branch: `codex/backport-1.4`. Existing saves and prior evidence are preserved.

## P1: owner-thread chunk admission

The service now invokes the audited private `getChunkFutureMainThread` admission
through a required shared Mixin invoker. Its canonical cursor and non-expiring
region ticket remain unchanged. Vanilla ticket propagation and dependency
scheduling remain intact; only the public wrapper's future-completion
`managedBlock` is bypassed. Captures still happen on later owner-thread ticks
after readiness, identity and coverage checks. Cancellation/unload keeps its
existing lease ownership and never resolves a discarded result.

ASM regressions inspect the pinned Minecraft implementation and forbid the
public blocking acquisition from the service. The opt-in
`ringworld.atlasRequestTimings` logs completion-at-return and owner span for a
real cold-runtime proof. It is off by default. This is not a hard tick-time
guarantee: ticket propagation itself remains an indivisible vanilla operation.

P1 validation passes on both loaders: Fabric 533 and NeoForge 556 cases,
including the actual pinned-bytecode admission checks. Fresh official-loader
packaged dedicated runtimes each returned all 982 admitted generation requests
before completion, captured all 262,144 cells, saved all dimensions, then reopened
with no new requests and byte-identical complete Atlas files. Independent disk
parsers validated immutable settings, format 10, counts, revision and hashes.
Evidence: `.codex-tmp/b14p1/results.json`, per-loader fresh/reopen logs and disk
receipts, plus `.codex-tmp/backport-1.4-p1-build.log`. These are local diagnostic
jars with the baseline 1.3 label, not frozen 1.4 release evidence. Admission spans
are functional traces, not the final paired performance/soak matrix.

No 1.4 candidate or full acceptance PASS is claimed. The development identity
advances explicitly with the integrated feature port.

## Remaining stages

P2 Atlas 11/sampler/coherent persistence; P3 adaptive capture/concurrency and
chunk/light work; P4 exterior gameplay; P5 rendering/UI; full 1.21.1 acceptance;
then the independent Java-17 1.20.1 Fabric/Forge graphs and their complete gates.

## P2/P3: integrated Atlas engine (in progress)

The native Java-21 implementation now carries Atlas format 11, the bounded
manufactured-column selector, deferred loaded-chunk capture, eight retained
request slots, adaptive 4/2/1 backoff, fixed 1/2/4/8 operator controls, and local
integrated-client frame feedback on both loaders. The format bump deliberately
rebuilds visual caches; saved dimensions, topology and terrain remain unchanged.
The P1 private admission path is retained for every slot. Unloaded recapture
cells remain droppable and exact/overflow fairness remains the backport policy.

Checkpoint allocation, compression, replacement and verification run on a
single worker. Owner-thread copies advance in bounded slices and repair slices
edited during copying. Newer mutations remain dirty after an older write, and
normal completion/cancellation waits for its durable checkpoint without blocking
normal gameplay ticks. Blocking drain is confined to teardown. The foliage
settlement observation now includes queued loaded-chunk captures.

The development identity is `1.4.0-dev.1+mc1.21.1`. The historical format-10
reader and tests are preserved; a separate strict format-11 reader binds the new
semantic identity. Final native runtime, upgrade/recovery and production-scale
performance evidence for this engine is still pending. No full acceptance PASS
is claimed.

### Interrupted-run defect found and repaired

The first Atlas-11 packaged Fabric fresh generation and complete reopen pass,
but the eight-request interrupted run failed. Cancelling a private vanilla
admission future poisoned vanilla chunk-save dependencies, logged `Failed to
save chunk` and stalled shutdown. The attempt remains under
`.codex-tmp/b14a2/fabric/recovery-interrupt/` with an explicit `FAILED.json`;
the owned JVM required forced termination after shutdown stopped progressing.
It is not acceptance evidence.

Atlas requests now retain a dependent completion future that they exclusively
own. Cancelling it releases the Atlas ticket without cancelling vanilla's
future. A regression models a vanilla save dependent on the original future
and requires successful completion after Atlas cancellation. Native repeated
interruption/resume and both-loader validation are required before crediting
this repair. The earlier completed-world-only P1 proof did not exercise this
pending-future cancellation case.

## P3: scheduled maintenance and lighting (in progress)

The required native hooks now scope scheduled autosaves in 1.21.1 `tickServer`,
queue coordinate-only save sweeps, and apply normal/catch-up/heap-pressure
budgets to ordinary unload/eager work. Explicit/flush saves and shutdown retain
vanilla drains. The native `chunkSaveCooldowns` and one-argument
`saveChunkIfNeeded` are used. A backport-owned asynchronous-write counter
preserves original futures and exceptions because 1.21.1 lacks mainline's
counter. Location advancements defer once after movement and cannot starve
under continuous travel.

Server Overworld light maps use mainline's partitioned lookup snapshots. The
native covariant copy methods have separate required Sky/Block mixins; the
client, Nether and End remain vanilla. An opt-in `ringworld.lightStorageProbe`
checks real transformed cache invalidation, DataLayer identity/isolation,
branch/removal and concurrent published reads on detached copies only. Normal
play does not run it. All new injections remain required.

Build/unit checks pass (Fabric 579 / NeoForge 602; zero failures, errors or
skips); native packaged, real scheduled-durability, allocation/GC and controlled
Large-world acceptance remain pending. These counts should be reconciled with
the XML receipts if more cases are added before final qualification.

## P4: exterior building and visibility (in progress)

Player block/bucket placement now uses a separately persisted default-on policy,
with both bed halves checked before mutation. The native SavedData factory keeps
terrain settings and layout hashes unchanged. Both loaders register the identical
building-settings/control wire records, reject control before acknowledgement,
validate the current fingerprint and owner/operator authority, broadcast policy,
and refresh permission snapshots with observed Atlas status. Client prediction
uses the matching layout's policy and resets raw policy/permission state on
teardown. The map gains a World page and the operator command is
`/ringworld building outside on|off|show`.

Periodic chunk/entity watching now includes nearby exterior builds within the
ordinary distance window. Terrain generation and Atlas coverage retain their
finite-Z guards. The legacy traversal seed no longer clamps an exterior camera
to the rim because its actual chunk is streamed. The private legacy underlay
coverage proof still excludes the exterior; it must not gain exterior Atlas LOD.
Build, UI and real multiplayer placement/visibility acceptance are pending.

### P2/P3 packaged functional checkpoint

All fourteen official-loader dedicated phases pass at source `44cf94a`:
fresh generation/reopen, fixed-eight-request clean interruption/resume/reopen,
and copied format-10 visual-cache rebuild/reopen, on Fabric and NeoForge.
Each complete phase has all 262,144 durable cells; interrupted phases have a
strictly partial durable checkpoint and normal all-dimension shutdown. Complete
reopens admit no requests and preserve cache bytes. Upgrade checks preserve the
saved-settings values and layout fingerprint while rebuilding Atlas identity.
These checks do not assert byte-identical region files or exhaustive block-state
migration coverage. The complete gameplay/upgrade suite remains required.

Every launch also passes the real light-storage probe: Overworld block/sky maps
use partitioned snapshots and Nether/End block maps remain vanilla. Logs have
no chunk-save failures. Evidence is `.codex-tmp/b14a4/results.json`, fourteen
hash-bound process logs, retained Atlas/settings files and independent readers.
The compiled source archive was compared against `44cf94a` (normalizing text
line endings); jars are retained alongside their hashes. This is diagnostic
functional evidence, not the final clean/pushed frozen acceptance matrix.

The earlier `.codex-tmp/b14a3` first launch completed normally but its harness
expected six light stores rather than vanilla's four; `FAILED.json` records
that harness error. The corrected fourteen-phase run is fresh, not a relabelled
PASS over the failed cancellation attempt.

P4 build/unit checks pass on both loaders (Fabric 585 / NeoForge 608). The
expanded real-client fixture adds World On/Off/restored captures using real
widgets and policy payloads, waits for a fresh authority snapshot after a stale
fingerprint request, and retains all older generation/handoff/revision/teardown
assertions. Its revision marker is now stone: Atlas 11 intentionally omits a
qualifying floating manufactured gold block. Both Gradle verifiers require the
new policy marker and three decodable captures. Real-client execution is next.
