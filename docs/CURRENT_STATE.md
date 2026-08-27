# Current state

2026-08-27 current qualification checkpoint: the complete static workflow now
passes 332 tests. The package-pin correction `49c0d53` passes 20 executed
tests (22 total, with two expected Windows-only skips). Metadata-only 26.1.x
stages with the recorded replacement hashes assembled Fabric and NeoForge macOS
and Windows client bundles plus server overlays. This is package assembly only,
not an operating-system or graphical-package smoke. Four dry publication plans
validate without reading a token or calling a host API. Publication remains held.

Current 26.2 quick run `20260827T094338Z-ceae3f67c0d7` on clean, pushed
`078b96d` **passes both loaders**, including frozen and diagnostic builds/unit
suites, artifact inspection and strict dedicated-server startup/clean stop.
Fabric's frozen build took 3m08s; NeoForge's took 4m59s. This is quick evidence,
not complete nightly, upgrade or packaged-launcher approval. Exact retained
candidate hashes are in `TESTING.md`.

Earlier corrected 26.2 attempts remain non-passing: `20260827T083411Z` was
cancelled during NeoForge asset download (`EXIT_143`) before any game launched;
`20260827T085115Z` failed both build paths while retrieving a Mojang library
POM, so its matrix remains Fabric `FAIL` and NeoForge `INCOMPLETE`. Quick
`20260827T090236Z-fdb8f0cf5e65` on `7fae756` passed both frozen builds and
338-test suites (Fabric 7m35s, NeoForge 16m40s), then failed Fabric's independent
diagnostic build on Maven POM `No route to host` errors. NeoForge diagnostics
were left incomplete and no dedicated runtime launched. This is not a
quick-matrix PASS. The separate source-world recreation attempt
`20260827T093000Z-f153f86ae12b` also stopped before a game launched: Fabric's
installer timed out retrieving Mojang's 26.1 version JSON.

`--gradle-loom-cache` is optional acceleration only: its shared verifier
rehashes the Mojang version metadata, client/server jars, asset index, and
objects before copying them into each isolated Gradle home. The separate
13-entry `RINGWORLD_QUALIFICATION_DOWNLOAD_CACHE` similarly seeds exact
external-runtime bytes. Neither enables offline mode, weakens checks, or
substitutes for a network/runtime qualification result.

The NeoForge seed now includes the same independently verified Mojang manifest,
version JSON and client/server jars as Fabric, in NeoForm's artifact layout.
All six assembled 26.1.x review archives also pass independent checksum and
nested licence checks. These changes do not turn failed runtime attempts into
passing evidence. Further qualification needs functioning upstream dependency
retrieval; avoid repeatedly rerunning the same unchanged failing matrix.

The shared external-runtime assemblers now copy and rehash the pinned Mojang
server before either official installer runs. Fabric's invocation omits only
the duplicate `-downloadMinecraft` step and verifies the launcher's root
`server.jar`; NeoForge still verifies its separate installed copy. The 32
focused smoke/assembly tests pass, including the installer-only Fabric layout
and corrupted-input rejection. The subsequent current quick run exercises this
correction on both real dedicated servers; it is not upgrade evidence.

Source fixture `20260827T094339Z-636659deb3ad` passes 26.1 Fabric and retains
its production world. The 26.1.1 Fabric attempt `20260827T094626Z-b5265759bec4`
passed production fresh/reload but hit the 60-second watchdog during the
seam fixture's synchronous block scan, while NeoForge compilation overlapped.
Retain that failed record; host contention is only a hypothesis until a quiet
rerun. The controlled, otherwise unchanged serial rerun
`20260827T095814Z-05ec2a7057b2` passes all 26.1.1 Fabric stages, supporting
contention as the explanation. Serialize heavy builds and runtime fixtures,
not only graphical clients; do not relax the watchdog.

Local 26.2 review jars are staged from the passing quick candidates, with only
approved public metadata changed: Fabric SHA-256
`abc21c93e31b7f6b9cec3be1b333b7d06aa653ffe78279e854771a2bebab827b` and NeoForge
`e26f93ade647284a185681830a4e22c63c6063df5ac8dedb4c9c505ca71e4062`.
Both loaders' macOS/Windows client archives and server overlays assembled and
pass SHA-256/nested MPL checks under
`dist/qualified-package-review/1.1.0-mc26.2`. Four local host/loader publication
plans validate without tokens or API calls. These remain review packages,
not packaged-runtime or release approval; nothing was uploaded.

Remaining before any publication decision: complete the 20-cell-result 26.2
nightly matrix; finish recreating the other four source worlds and run the six
26.1.x final-candidate upgrades plus the two 26.1.2→26.2 upgrades; perform real
staged-package OS/client/server smokes; obtain independent owner review. Keep
publication held.

The copied-world 26.2 stall is confirmed to be
Minecraft's new file-fix backup/join confirmation sequence, not a terrain-load
failure. Opt-in projection, visual-parity, and lifecycle fixtures now handle
only those exact prompts; ordinary player warnings remain untouched. The
clean-source `0ca305b` Fabric diagnostic opens a copied complete 16,384x256
26.1 save, retains all 65,536 Atlas cells, captures tangent/handoff/radial-up,
and stops cleanly. Captures were visually reviewed against the retained 26.1.2
baseline. This is source-ABI diagnostic evidence, not frozen-jar qualification.
The complete 26.2 nightly matrix and copied-world upgrade gates remain
required after the passing current quick run. See
[COPIED_WORLD_FILE_FIXTURE_UPGRADE.md](COPIED_WORLD_FILE_FIXTURE_UPGRADE.md).

The current 26.1.x local review jars were regenerated by the metadata-only
stager from quick run `20260823T130347Z-a493af8d7261` (build source `3e94b04`),
replacing the stale August 13 candidates. Their code/resources are byte-equal
to the retained qualified jars, apart from approved release metadata. Fabric
SHA-256 is `cdf564d260a0c2405dafeeede6ec4abd14ae48cb4ab44ed233c6d380355d5663`;
NeoForge is `0c2353032bc6bf9b308c6be58ada45a343ecb5ad838e393f3f4bc3526ef065e1`.
This is local preparation only: final-candidate forward upgrades, packaged
runtime review, and owner approval remain open. Publication is explicitly held.

Corrected-candidate quick run `20260827T073004Z-4c80e38c9d6b` on `97654ab`
passes both loader builds/unit suites and Fabric's external dedicated smoke.
NeoForge's external smoke failed before launching the game while fetching its
pinned installer (`STRICT_RUNTIME_EXECUTION_FAILED:URLError`). This is not a
NeoForge gameplay result or a passing two-cell matrix. Preserve the completed
Fabric evidence and candidate hashes; graphical and upgrade qualification
remain pending. The release-prep package bridge now derives explicit runtime
pins from the reviewed matrix, with positive assembly checks for both 26.2
loaders. Nothing has been published or deployed.

2026-08-27 depth-port correction: the pre-publication audit found a retained
26.1 depth comparison in the 26.2 distant-ring pipeline. The 26.2 adapter now
uses reversed depth and a backend-aware far clamp; 26.1 keeps its original
depth convention. Earlier `8048871` quick jars remain valid historical
server evidence but predate this rendering correction. Nightly attempt
`20260827T072408Z-1cd4f98ad748` was deliberately interrupted during its first
pre-game Gradle setup to avoid qualifying that superseded candidate. No game
or live-world state was changed. Fresh candidate/render evidence is required.

2026-08-27 work-in-progress: 26.2 is pinned in its own pending manifest. Quick
and nightly operators consume manifest-derived candidate groups, removing the
fixed three-patch assumption. Both 26.2 loaders compile/package and pass 338
unit cases each; the 26.1.2 dual-loader source regression also passes 338 each.
These exploratory builds are not frozen-candidate qualification. Both loader
26.2 development clients now pass thirteen creation/settings captures and
the eleven-capture Atlas UI fixture, including complete generation, live
revisions, normal disconnect, and session teardown. Clean quick run
`20260827T054844Z-eab4ee8cebfb` on pushed `8048871` passes both exact-candidate
dedicated-server cells. Multiplayer, production-render, and upgrade gates
remain pending; no 26.2
publication is claimed. See [VERSION_QUALIFICATION.md](VERSION_QUALIFICATION.md).

Initial clean quick attempt `20260827T052449Z-59cbd51ae3cd` was deliberately
stopped during NeoForge preparation after bytecode review found two invalid
26.2 mixin targets (removed `WeirdScaledSampler` and relocated surface-noise
suppliers). Its recorded `EXIT_143` is operator cancellation, not a game crash
or compatibility verdict. No dedicated runtime ran in that attempt. The next
candidate included the version-owned replacement hooks and passed the fresh
quick run above; the cancelled record remains unchanged.

Last audited: 2026-08-13 against public `main` and the 1.0 release branch.
The final
Minecraft 1.21.11 implementation remains historical provenance at
`mc-1.21.11-final` / `2c98650`.

The Minecraft 26.1.2 port is integrated on `main`; see
[`MINECRAFT_26_1_PORT_PLAN.md`](MINECRAFT_26_1_PORT_PLAN.md) and the
[`final baseline`](MINECRAFT_1_21_11_FINAL_BASELINE.md).

Minecraft 26.1 is now the approved development compatibility floor. The
current verified and published files still target 26.1.2 exactly; neither
26.1 nor 26.1.1 is a public compatibility claim until the complete dual-loader
qualification matrix passes. The rolling stable-release policy, automation
tiers, evidence contract, and intake order are defined in
[`MINECRAFT_VERSION_SUPPORT_PLAN.md`](MINECRAFT_VERSION_SUPPORT_PLAN.md).
Existing 26.1.2 release evidence remains exact historical evidence and is not
silently generalized to the rest of the patch line.

The Phase 5 forward-upgrade implementation now accepts independently pinned
source and target manifests and permits only copied, same-loader, stable
forward-version paths. Its historically qualified paths are
`26.1 -> 26.1.1`, `26.1 -> 26.1.2`, and `26.1.1 -> 26.1.2`,
revalidating a passed source-worldgen record and target quick record before it
can create a new contained target fixture. All six historical real forward paths
pass from clean pushed commit `7983b8a`; their run IDs are recorded under
Phase 5 in `MINECRAFT_VERSION_SUPPORT_PLAN.md` and GitHub issue #173.
Historical August 13 checkpoint (superseded by the local staging above):
Phase 6 added a static-tested local equivalence guard that permits a
proposed public jar to differ from the frozen candidate only in approved public
metadata/version fields. Local `1.1.0+mc26.1` / label `1.1` candidates were
built from clean public `main` on 2026-08-13 against the oldest 26.1 ABI. Both
passed all 338 tests and byte-level equivalence against the exact refreshed
frozen candidates: Fabric release SHA-256
`082cb6977252da3e454c028e5a11e62f15c9be0697ebb47aa05635cda6e5aef4`
matches frozen `8b919c42...e7e31`, and NeoForge release SHA-256
`1b81d62142d06a52f96d4a7416f62bd15bb1fd0198d35d4abc4fa771ab9941aa`
matches frozen `77389181...6f50`. Only the reviewed loader descriptor and
`ringworld-build.properties` version/label bytes differ. These ignored local
candidates are not staged, uploaded, published, or yet a broadened support
claim.

Phase 7's local staging bridge is now implemented and pure/static tested. It
requires all six strict quick records, both exact frozen hashes, and successful
metadata-only equivalence before it can create ignored Fabric and NeoForge
review folders. Each folder carries one candidate jar, both hashes, complete
archive inventory, exact source URL, three proven game-version tags, generated
Modrinth/CurseForge metadata, loader-correct Fabric API relations, changelog,
and rollback identity. The CLI contains no host client or credential input; a
real staging run was subsequently completed as recorded at the top of this page.

The Phase 8/9 host bridge is also implemented and no-network tested. It reads
only the qualified local stage, defaults to a printed dry run, requests an
unlisted Modrinth version or manually held CurseForge file, and has no route to
edit/delete old releases. Execution is fail-closed behind an exact short-lived
owner authorization, clean pushed source equality, and a host-specific token
environment variable. No token was read and no submission was made.

On 2026-08-13, local current-version Gradle development regressions passed on
both loaders: the safe-small mapping-4 map/compass and curved-rigid-object
fixtures, safe-small production visual parity, and the copied 16,384x256
production lifecycle and projection gates. The lifecycle completed
Overworld/Nether/Overworld/End/Overworld, normal save/disconnect, client-state
clear, and reopen with the complete mapping-4 Atlas on both loaders. Its first
Fabric attempt exposed a fixture timing error: a fixed 20-tick reopen sample
ran after only the first streamed Atlas tiles. The corrected gate retains its
existing timeout but waits for the complete matching Atlas identity; fresh
Fabric and NeoForge reruns pass. Production tangent, 16-chunk handoff, and
radial-up captures also pass on both loaders. Visual-parity seam windows
recorded Fabric 707 frames (10.086 ms average, 41.917 ms maximum, zero over
50 ms) and NeoForge 681 frames (10.554 ms average, 28.958 ms maximum, zero
over 50 ms). These are local Gradle dev-runtime regressions, not frozen-jar
six-cell qualification or release evidence.

Phase 0 is tracked by GitHub epic #168 and its Phase 1–10 child issues. Phase 1
now has a fail-closed six-cell manifest and eighteen pure validator tests. The
26.1.2 Fabric and NeoForge cells contain their immutable published hashes;
26.1 and 26.1.1 remain pending. Exact Fabric inputs are available, while the
only official NeoForge runtimes for those patches are pinned beta trial
builds whose ModDevGradle/build/runtime compatibility is not yet proven.
Every cell also pins the official runtime installer jar and SHA-256: Fabric
Installer 1.1.1 for Fabric, and the exact matching NeoForge installer for each
NeoForge runtime. Loader or universal jars are never treated as substitutes
for a production server installer.
Phase 2 now has its first opt-in Gradle isolation slice. Supplying the paired
`ringQualificationRoot` and `ringQualificationCell` properties redirects both
loaders' build output, declared game directories, fixture preparers, and
verifiers below one disposable cell root. Qualification-specific port
properties cover multiplayer, raid, and dedicated-smoke profiles. Normal
developer paths remain unchanged when those properties are absent. Seven
static guardrail tests and real Java 25 default/isolated Gradle configuration
checks pass. The raid and worldgen scripts now keep their fixture-managed
state below an explicitly selected qualification cell and reject traversal or
paths outside `dist/qualification`. The destructive raid preparer additionally
rejects every existing symlink component on its managed paths before fixture
creation or deletion.
The first source-build diagnostics also pass unchanged on all four earlier
patch cells: Fabric 26.1/26.1.1 and NeoForge's pinned 26.1/26.1.1 beta trials
each compile, package, and pass all 338 tests. Their generated loader metadata
names the selected Minecraft/runtime versions. These are source-build ABI
results only—not server/client runtime qualification or same-jar evidence—so
the four manifest cells remain `pending`.

Phase 3 has a fail-closed orchestration core. It validates and selects
manifest cells, plans contained paths, pinned downloads, locks, ports, Gradle
argument vectors, and deterministic JSON/Markdown reports. Dry-run output
remains write-free and `INCOMPLETE`; the serial non-dry path records immutable
evidence for reviewed build/unit, per-cell diagnostic-artifact, and complete
loader-triplet frozen-candidate preparation. Runtime qualification remains
pending until its real external cells complete.
The executor foundation now has passing focused coverage for UTC run
IDs, held operating-system cell locks, contained directories, process-group
timeouts, bounded credential-pattern-redacted logs, immutable terminal
reports, pinned-file hashing, and strict loader-aware diagnostic jar
inspection. Its build and diagnostic-artifact primitives are wired to the
serial runner; the external-runtime planner models the exact official
installer, mods inventory, safe-small configuration, launch command, markers,
and clean stop contract for all six cells without writing or launching
anything.
The external executor can now, as a separately tested adapter, fetch exact
pinned inputs without following redirects, refuse symlinked or reused runtime
paths, run the official installer, enforce an exact mod inventory, preflight
the loopback port, verify the installer-owned Mojang server against the
manifest pin, wait for loader/RingWorld/readiness markers, send `stop`, reject
fatal/crash output, and record an ordered stop/save/clean-exit ledger. A pure
adapter now binds this result to the strict terminal schema but deliberately
rejects `PASS` without the separate provenance, log hashes, runtime inventory,
frozen-candidate identity, and same-file group evidence. The runner now
installs this phase only after clean provenance and a complete frozen loader
triplet, lending its live exact cell lock to avoid re-acquisition. It persists
a separately validated, immutable raw strict-terminal-evidence JSON record
alongside the scheduler report. The current-main Fabric triplet passed end to
end on 2026-08-23 in combined run `20260823T130347Z-a493af8d7261`. Each of 26.1,
26.1.1, and 26.1.2 passed all 338 tests, strict diagnostic-jar inspection,
official installer/runtime assembly, a fresh 2,048x416 mapping-4 server, and
strict stop/save evidence. All three loaded the same frozen wide-range jar
with SHA-256
`1fc017289ebcb102d9894ccb16a30a697e03104b5c8165b6799a1496c4486216`.
The strict-evidence SHA-256 values are `13ca41f6...b1be` (26.1),
`ea3f70f6...8ae` (26.1.1), and `213d3a79...78c1` (26.1.2). The current-main
NeoForge triplet passed in the same run; all three
cells passed the same gates with frozen jar SHA-256
`5fd60d12db03386866cc153b7921b180cd1e4a96d4f443443364d04357b56823`.
Its strict-evidence SHA-256 values are `5078cb72...a8fc` (26.1),
`def57821...0086` (26.1.1), and `7d86c2b1...9e6b` (26.1.2). The combined run
also proves that one invocation safely retains distinct Fabric and NeoForge
frozen roots. It runs from clean, pushed merge commit `3e94b04` after PR #230
made the NeoForge runtime verifier distinguish the reviewed root seed from the
installer-owned verified server copy. Phase 3 quick qualification is complete
for both loaders on current `main`; the remaining Phase 4 client/gameplay/
rendering fixtures are pending.
Phase 4 now also has a pure, fail-closed Atlas recovery evidence contract. It
requires a genuine partial schema-2 `INTERRUPTED` checkpoint and a later
schema-2 `COMPLETE` report from the same disposable runtime/world and report
target. Independent saved-settings and Atlas-file observations must match the
mapping-4 2,048x416 identity, wall height, persistent Atlas path, hashes,
totals, clean exits, and globally ordered stage ledgers. A concrete external
plan, executor, and bounded process runner now implement this first nightly
slice. They re-inspect the frozen candidate, semantically validate and rehash
the selected cell's strict quick record before any download, use the official
installer in a new contained runtime, and hash every capture and process log.
The first process stops only after a durable independently parsed partial
Atlas exists; the second receives those exact bytes, must show growth, and
must halt itself with `COMPLETE`. Static tests use local fake children only.
The Atlas interruption/recovery slice now passes on all six 26.1.x cells with
the exact refreshed frozen candidate for each loader. Fabric runs are 26.1
`20260813T091340Z-0f6a75a06e36` (`53441db0...6dfb`), 26.1.1
`20260813T084030Z-a3030342d49c` (`9e847882...d04`), and 26.1.2
`20260813T084918Z-2a61b8523682` (`c826f4b1...f6ab`). NeoForge runs are 26.1
`20260813T092207Z-56b8d1593d37` (`1eca3d90...4892`), 26.1.1
`20260813T085803Z-abc3ee37973d` (`5a9e5213...6033`), and 26.1.2
`20260813T090427Z-21cef9b5920b` (`858aedc7...60f3`). Parenthesized values are
terminal-evidence SHA-256 prefixes/suffixes. Every run retained mapping 4,
world hash `8665210144080158345`, layout fingerprint `4064118068185880929`,
an exact byte-identical partial restart checkpoint, clean stage exits, and
self-halted only after schema-2 `COMPLETE`. This completes the six-cell Atlas
recovery slice, not the broader Phase 4 nightly matrix.
The second Phase 4 slice is also runtime-qualified on all six cells. The
external worldgen plan/executor/process runner consumes the retained quick jar,
installs three official disposable runtimes, and drives the existing four-pass
production reload/seam/terminal structure matrix. Its independent settings and
log contract requires mapping 4, format 3, all major biome families, caves,
ores, vegetation, structures, seam-crossing starts, references, loot, both
monument outcomes, spawn overrides, and clean self-halt. Pure and fake-runtime
tests pass on both loader shapes. Refreshed Fabric quick run
`20260813T072608Z-b7c68e555818` supplied frozen jar
`d7a66942...c8296`; its 26.1 worldgen run
`20260813T073235Z-1e16c008e584` passed with terminal SHA-256
`782a9bc3...110f1`. Its 26.1.1 and 26.1.2 runs are
`20260813T083349Z-0cdcffa76005` (`fafd6e83...f482`) and
`20260813T083518Z-b942314e7e0d` (`bc9c4cea...73cd`). Refreshed NeoForge quick run
`20260813T080722Z-377cfb994c93` supplied frozen jar
`53558ed5...d7314`; its 26.1 worldgen run
`20260813T082128Z-c2fae65dec2c` passed with terminal SHA-256
`4c0c1ef1...6aea3`. Its 26.1.1 and 26.1.2 runs are
`20260813T083644Z-e7ed932a1499` (`193b2aa5...5578`) and
`20260813T083822Z-03549862d588` (`f4896d60...1585`). All six prove the
four-stage production reload, seam-crossing, stronghold/portal, biome/cave/
ore/vegetation/loot, and monument-policy contract using one unchanged jar per
loader. Lifecycle and rendering nightlies remain pending.

The Phase 4 multiplayer path now has an exact-frozen-jar runner. It prevents
Loom and ModDevGradle from silently loading checkout classes, verifies the
retained jar hash in the dedicated server and both real-client `mods/`
directories, serializes asset warmup, drives the existing complete seam/
gameplay fixture, stops the server normally, and emits hash-bound fixture-06
evidence. The formal six-cell fixture-06 matrix passes from clean pushed
commit `351056c`: Fabric run IDs are
`20260825T214555Z-944190e851f2`, `20260825T214848Z-6b762555368c`, and
`20260825T215133Z-7c1ee4314c33`; NeoForge run IDs are
`20260825T205441Z-67ab6c18f772`, `20260825T213540Z-1d2e6b1e6b26`, and
`20260825T214008Z-ea4e5e2fd2c6`. Fabric retained candidate SHA-256 is
`1fc017289ebcb102d9894ccb16a30a697e03104b5c8165b6799a1496c4486216`;
NeoForge is
`5fd60d12db03386866cc153b7921b180cd1e4a96d4f443443364d04357b56823`.
All six cells passed exact patch identity, the full server/two-client matrix,
graceful RCON save/shutdown, and immutable log/capture hashing.
The following frozen-candidate raid slice also passes all six cells from clean
pushed commit `daaa1da`. Fabric run IDs are
`20260825T221028Z-466a9d85a0a8`, `20260825T221240Z-0f1e832eeeb0`, and
`20260825T221522Z-a58ea89ecbf1`; NeoForge run IDs are
`20260825T221751Z-ccdb777c556a`, `20260825T222236Z-d1d14d2036ff`, and
`20260825T222743Z-3614fad43309`. The same Fabric and NeoForge candidate hashes
as fixture 06 were retained across their respective patch triplets. Every
cell passed real two-client arm/save, bossbar persistence, exact-world reload,
canonical seam-side raider handling, victory, patch identity, and immutable
fixture-07 evidence.
The exact-frozen production lifecycle slice now passes all six cells from
clean pushed runner commit `d9ae051` using one independently inventoried
Minecraft 26.1 production world. Fabric run IDs are
`20260825T232710Z-952a555c46c0`, `20260825T232829Z-b0aae351351b`, and
`20260825T232936Z-087e4043ed96`; NeoForge run IDs are
`20260825T233044Z-dba4e089a8ea`, `20260825T233410Z-a95bc86f19f9`, and
`20260825T233831Z-7747e410ff19`. Every cell passed Overworld/Nether/End
transfer, normal save/disconnect, raw client-state teardown, and same-world
reopen with the unchanged frozen jar for its loader. The shared source is a
16,384x256 format-3/mapping-4 world last saved by 26.1 with a complete
65,536-cell Atlas. One earlier 26.1.2 NeoForge attempt failed before launch
when its disposable Loom cache exhausted local disk; the preserved failure is
not counted, and the fresh rerun passed after only disposable caches were
removed.
The matching exact-frozen production Atlas/render slice also passes all six
cells from clean pushed runner commit `425cbcf`. Fabric run IDs are
`20260825T234304Z-5ab300e78cc3`, `20260825T234729Z-80e30d8296fc`, and
`20260825T235159Z-fe8322982a43`; NeoForge run IDs are
`20260825T235644Z-7c6a194b372d`, `20260826T000338Z-d0fd305a1bde`, and
`20260826T001058Z-433b22bc0ff0`. Every cell opened fresh copies of the same
reviewed production source and passed noon, dusk, night, rain, tangent,
handoff, radial-up, natural seam, seam join, both textured rims, capture
hashing, and frame-log validation with its loader's unchanged frozen jar.
The first complete unattended coordinator attempt
`20260826T002122Z-76d2f96bc705` correctly failed closed rather than claiming
the matrix. It recorded 48 PASS, two FAIL, and ten downstream INCOMPLETE
results; aggregate terminal SHA-256 is
`ff91c891dabfd3c9c64950f0eb4abb5f67137e55e8465dacab6ec3618db46f81`.
Both failures were the strict pre-seam multiplayer readiness barrier on the
last Fabric and NeoForge cells after several hours of continuous graphical
work, before any topology assertion. Fabric observed 691 ticks and reached 69
consecutive on-time ticks; NeoForge observed 485 and reached one. Their
servers stopped normally, but disconnected clients required bounded operator
termination rather than waiting for the outer 30-minute timeout. The
coordinator originally took and recorded a 120-second quiescence window before each
multiplayer fixture and discarded validated child runtime/caches while
retaining logs, captures, and immutable evidence. A fresh full rerun is still
required; this failed aggregate is regression evidence, not support evidence.

The clean follow-up aggregate `20260826T035240Z-55d20f097651` also failed
closed. It recorded 44 PASS, three FAIL, and thirteen downstream INCOMPLETE
results; aggregate terminal SHA-256 is
`fb261365004d507d29d8f4c8a48c7c239aca1a4c92791d8c79388ad43aa7e025`.
NeoForge 26.1.1 reached the exact-target compass random-spin assertion, but
the fixture's two samples differed by `0.008853078` against an arbitrary
`>0.01` threshold. The assertion now reuses one wobble state across four
fixed seeds and measures their maximum circular separation. The last Fabric
and NeoForge multiplayer cells then exposed why the coordinator-level pause
was insufficient: each child performed heavy isolated Gradle/NeoForm
preparation after that pause and immediately launched its runtime. Fabric's
client B stopped rendering for roughly two minutes and invalidated dependent
interaction stages; NeoForge's server failed the strict readiness barrier
before topology began. The recorded settle interval is now forwarded into
the multiplayer runner and applied after preparation/assets, immediately
before server/client launch. This aggregate remains diagnostic evidence, not
support evidence; a clean targeted rerun and final full aggregate are still
required.

Corrected targeted evidence on pushed commit `d953b02` now passes: NeoForge
26.1.1 map/compass run `20260826T071728Z-3cd837095d5b`, Fabric 26.1.2
multiplayer run `20260826T072112Z-ea3503a09b11`, and NeoForge 26.1.2
multiplayer run `20260826T072608Z-26e04e212f30`. The multiplayer records bind
the post-preparation 120-second settle; their terminal SHA-256 values are
`251f8cf5092d312cfec9a76a20fe77613dd6ca53fae598ead3fddae358d066a4` and
`2363a8a3972d5bd5cf7191b8e58fd14604fc6614af8a6690ff3645c2648701cd`.

A subsequent complete-selection diagnostic started under coordinator run
`20260826T073421Z-f6d5d2fe9980`. Its first five child commands produced six
PASS records for 26.1 Fabric: creation/settings, worldgen, Atlas recovery,
Atlas UI, client handshake, and multiplayer. The raid arm phase also saved
normally, but its immediate reload launched into host pressure: one client
connected, the other retried a refused connection, and the server stopped
advancing after 23.377 seconds of accumulated tick delay. The operator stopped
the still-bounded but unproductive 30-minute wait and preserved the three
stalled reload logs. This interrupted diagnostic is not an aggregate report
or support evidence. The raid runner now applies and records the same bounded
120-second host settle before arm and again between arm and reload. Targeted
26.1 Fabric raid run `20260826T080924Z-6dfcb2ee422f` passes both phases with
five saved raiders, both boss bars, reload folding, Hero of the Village, and
canonical raider ownership. Its terminal SHA-256 is
`3fe71c211254df06d0e660da1bcdd62f8ab58f798e80d61cbf7abaa8934d535e`.
A clean full aggregate remains required.

The next complete-selection run, coordinator ID
`20260826T081615Z-42881cab0db9`, passed every 26.1 Fabric record and the first
five 26.1 NeoForge records through Atlas UI/handshake. It then stopped
fail-closed on `ENOSPC`, not a RingWorld assertion. The cause was a coordinator
cleanup mismatch: external worldgen and Atlas runners return a canonical
`terminal_evidence` path but no top-level `run_id`, so their verified
disposable runtime trees accumulated. The coordinator now derives a run ID
only from a contained terminal path with the exact version/loader/cell/evidence
layout and strict run-ID syntax, then uses the existing containment-checked
cleanup. Tests cover that external shape and preserve terminal evidence. This
interrupted run has no aggregate terminal and is not support evidence.

The clean rerun from `3aba403` then passed Fabric 26.1 creation/settings,
worldgen, Atlas recovery, Atlas UI, and format-3 handshake before its
multiplayer readiness gate observed a 30.755-second tick hitch and no stable
100-tick window. Both real clients had connected; the server halted the
disposable harness before topology assertions. On this 16 GiB host the three
concurrent game JVMs had no qualification heap ceiling and could each inherit
the JVM's roughly 4 GiB default maximum. The already-failed clients also
remained on their disconnect screens because the Python operator waited for
them before checking the exited server. The operator was deliberately stopped
after child FAIL evidence `20260826T094142Z-b23586bdcf3a` was written (terminal
SHA-256 `18b684a5c5b91f668ba66dda0e584953b88c3b5d74787d5b97b8dda26cb82b68`);
there is no aggregate terminal and no support claim. Fabric and NeoForge now
cap concurrent multiplayer/raid game JVMs at 2 GiB, and multiplayer detects an
exited server immediately. The coordinator also preserves terminal-hash-bound
PNG/log artifacts under immutable evidence before deleting heavy child run
trees. A clean full aggregate remains required.

The first capped-heap targeted rerun, Fabric 26.1
`20260826T100752Z-3428554196a4`, confirmed all three game processes received
`-Xms256m -Xmx2g` and that server exit now terminates the child immediately.
It also showed heap reservation was not the only pressure source: both clients
loaded with `renderDistance:2`, but their disposable options retained
VSync-off `maxFps:120`; the readiness window accumulated another 37.207-second
server delay and halted before gameplay. Terminal SHA-256 is
`e5a9a63f4ba2a6a1c49936bef7dba2e37af8925e5b3e5caee47e80a5789cc70d`.
The operator now writes `maxFps:30` for both disposable clients before launch,
preserving any other existing options. This qualification-only cap does not
change a distributed jar or normal player settings. Corrected targeted Fabric
26.1 run `20260826T101522Z-6ec8d904431b` then passed the complete two-client
seam, combat, interaction, placement, vehicle, reconnect, bed/death,
Nether/End, navigation, weather, and alias block-entity recovery matrix. Its
terminal SHA-256 is
`d29533d8dfaf28b19e0a8f135c8e84fd755e0fd24ecf4cb518ec5655d1c6cde3`.
Complete aggregate `20260826T102251Z-9ebec5b424d2` then ran all independent
cells to terminal evidence and recorded 55 PASS, one FAIL, and four downstream
INCOMPLETE results. Aggregate terminal SHA-256 is
`4a1f1e3baa103c1a73d38fc004fd4aad3691a25f2689a92474bf82068b949ae0`.
Only NeoForge 26.1 raid failed: its arm server loaded NeoForge, RingWorld, and
MixinExtras but never emitted Minecraft's `Done (` marker within the bounded
startup wait. It produced no crash or gameplay assertion, while the same cell
has three earlier raid passes and both later NeoForge patches passed in this
aggregate. The four later NeoForge 26.1 fixtures were therefore skipped. The
coordinator now gives every command exactly one automatic retry only for that
exact pre-game timeout when every recorded claim remains false; it cleans the
failed child, waits 120 seconds, and records both attempts. No assertion,
crash, evidence failure, generic timeout, or second failure is retried. A
targeted five-fixture follow-up on pushed commit `aa64b3f`, coordinator run
`20260826T145215Z-b593bba25512`, then passed the failed NeoForge 26.1 raid and
all four formerly skipped fixtures on their first attempts. The child run IDs
and terminal SHA-256 values were:

- raid `20260826T145215Z-9652fee324b2`,
  `c3c9c8fcb92b92d7551a6b7ef89dcf405908410fc5e16e362435906e669c5b06`;
- map/compass `20260826T145948Z-be0d10f5b156`,
  `cb9e5885cf508cc5ff77818fa444f9453224e5aa5a20c0b0eedd4abf00aea371`;
- production lifecycle `20260826T150329Z-47e9bc914f01`,
  `f998e84ad1a319b3c2e3cc1ece021ff3a3c472158f645b468ed9b23df722ad1a`;
- curved objects `20260826T150619Z-0c233074d365`,
  `436d4c26a0b8b89c4a5f097e74405754e50b12885c5b45000ff49c3c87899cf8`;
- production rendering `20260826T150920Z-6cb83986ee05`,
  `8924c1f0f81d08e4f11e96a16cae2ce90ed3beff180159490aab8302073d7b0b`.

The partial coordinator correctly remains `INCOMPLETE`; its terminal SHA-256
is `2a65eb626faf2b1f8496d361b814cb48ab3eb6a69bd6c0ad9b6218b14ff2d1ef`.
No automatic retry was consumed. Current evidence therefore covers every
individual command, but one clean unified 60-command aggregate is still
required before the broad-support claim.

A subsequent complete attempt from pushed commit `bf0ae27` passed the first 13
commands, covering the full Fabric 26.1 cell and NeoForge 26.1 creation,
worldgen, and Atlas recovery. The Mac then lost all outbound HTTPS while its
local gateway remained reachable. NeoForge Atlas UI child
`20260826T212553Z-95a9f246b50a` failed during Gradle setup with Loom's exact
dependency `DownloadException`, before Minecraft launched and without a
positive fixture claim. The coordinator correctly blocked that cell's later
fixtures and continued independently; the already-doomed aggregate was then
stopped and has no aggregate terminal. This is infrastructure diagnostic
evidence, not a RingWorld regression or support evidence. The coordinator now
also gives that exact exit-1, pre-launch, log-bound failure one cleanup and
120-second retry. Compilation failures, arbitrary network/Gradle errors, and
all gameplay/evidence failures remain non-retryable.

Complete aggregate `20260826T215217Z-68410e5f8e85` then executed all 60
commands against the unchanged Fabric and NeoForge frozen candidates. It
retained 55 PASS results; NeoForge 26.1 raid failed during reload because the
operator observed the arm phase's stale `Done (` marker in the persistent game
`latest.log`, launched client A before the new server bound its socket, and
therefore left that fixture plus four downstream results non-passing. Every
26.1.1 and 26.1.2 command passed. The raid operator now waits on the fresh
per-phase process log instead. Owner-directed targeted repair run
`20260827T040412Z-ee7ba84a5b3b` on pushed commit `f27a180` passed the exact
NeoForge 26.1 arm/reload fixture with two real clients, persisted raid state,
boss bars, seam folding, victory, Hero of the Village, and canonical raider
ownership. Its terminal SHA-256 is
`300a0f7a92cb210f46224141c9076dd3f3ce8ed3882ff139328140eb5271a0da`.
The owner explicitly chose this targeted repair rather than repeating the
other 59 commands; final reporting must preserve that provenance and must not
mislabel it as one monolithic all-PASS invocation.

The first Fabric production run exposed a fixture false positive before it
could claim PASS: one isolated 12-block natural step across the full width was
being rejected as though it were a broad seam wall. The audit now follows its
documented intent by retaining the broad-cliff gate and a two-block average
delta limit while allowing isolated relief. The refreshed real dual-loader
26.1 runs above pass that corrected audit.
The next Phase 4 slice has a production-style graphical-client executor
and operator CLI for the existing menu-only creation-settings fixture. It
verifies a pinned Prism 11.0.3 macOS archive, Java 25, the retained frozen jar,
the selected loader component and Fabric API where required; launches an
account-free disposable Prism root; and requires all thirteen valid PNGs,
bounded logs, the existing PASS marker, clean self-halt, exact mod hashes, and
no created world. Pure/fake-process tests pass. A fresh account-free Prism
profile cannot reach Minecraft because official Prism requires a valid
Microsoft account during first-run setup, even for its offline launch option.
This remains an authenticated-disposable-profile or owner release gate;
qualification never copies a user's normal Prism account data.

The source-ABI graphical alternative passes all six 26.1.x cells from clean
pushed commit `077615493e0f8a7b58e92aec51e9ec83535cb08f`. It launches the
actual Minecraft client with each cell's exact Minecraft/loader/API
dependencies, captures all thirteen creation/settings UI states, creates no
world, and uses disposable Gradle user, project-cache, build, and game roots.
Fabric runs are 26.1 `20260813T101541Z-e87eced07877`
(`b7cbe6f9...5710`), 26.1.1 `20260813T101904Z-f32dbc8917e9`
(`e4c513b3...a675`), and 26.1.2 `20260813T102213Z-d33b1a707c5b`
(`9bd5f40c...c2b3`). NeoForge runs are 26.1
`20260813T102535Z-618362c64a62` (`2c147161...9e79`), 26.1.1
`20260813T105844Z-fdefa2c044f5` (`22eabdb5...270d`), and 26.1.2
`20260813T110726Z-2e96621d7486` (`0f4255e1...dda6`). Parenthesized values
are terminal-evidence SHA-256 prefixes/suffixes. One earlier NeoForge 26.1.1
attempt failed closed when Mojang's library host was temporarily unreachable;
the successful fresh retry used the reviewed external read-only dependency
cache. This completes the settings UI/source-ABI slice, not
frozen-candidate packaged-client proof or the broader nightly matrix.
The matching source-ABI Atlas UI runner and pure contract are now implemented.
It selects exact cell dependencies, isolates both Gradle caches and game
state, and fails closed unless the existing integrated fixture creates one
disposable world, completes all eleven captures, and reaches its revisioned
placement/removal PASS. Its first 26.1 Fabric run exposed a fixture-only race:
after the Atlas fixture invoked Create World but before its player existed,
the legacy `testMode` launcher created a second world and Fabric networking
disconnected. The Fabric client now keeps the Atlas fixture exclusive through
that interval and the fixture records bounded menu-screen diagnostics. No
cross-version runtime PASS is claimed until the corrected run completes. The
first corrected clean-profile attempt then stopped at Minecraft's accessibility
onboarding screen. Both loader preparers now explicitly disable only that
first-run screen and set the fixture's intended GUI scale, matching the
already-qualified creation UI preparer. The next clean run proved one further
startup ordering requirement: Minecraft's temporary `GenericMessageScreen`
can supersede an editor opened before the final title screen. The shared
fixture now waits for `TitleScreen` before invoking `openFresh`.
The next run reached the Atlas screen and correctly failed its former
hard-coded published-build assertion: exact qualification cells intentionally
embed `qualification-<cell> · 0.0.0-qualification+mc<version>`. Both loader
run definitions now pass that independently constructed expected label into
the client fixture. The screen must match the selected cell's embedded
identity; the ordinary 26.1.2 development run continues to expect
`1.0 · 1.0.0+mc26.1.2`.
That corrected run then completed the Atlas and all eleven captures before
failing closed at the live revision probe. The unattended window still had
`pauseOnLostFocus:true`; closing the final map screen paused the integrated
server immediately after `/setblock`, so no revision could be emitted. The
shared Atlas fixture now disables lost-focus pausing itself, independent of
the legacy generic test launcher.
To keep this six-client GUI matrix proportionate, its disposable world now
uses the supported Small 2,048×128 preset (4,096 Atlas cells). Broader
2,048×416 coverage remains in the recovery, worldgen, and gameplay fixtures;
the Atlas UI fixture still exercises every control, renderer transition, full
completion, and live cell-revision assertion.
The corrected source-ABI Atlas UI gate now passes all six 26.1.x cells from
clean pushed commit `7a7c0449277dbb0be464b1fb972d77044132a1e6`. Fabric
runs are 26.1 `20260813T123311Z-a3ad7b5b5a58`
(`f3f9cc08...aace`), 26.1.1 `20260813T124040Z-0d2aec6c29f2`
(`40c9a245...add1`), and 26.1.2 `20260813T124816Z-bd3475d1bf21`
(`7305feb1...6fe0`). NeoForge runs are 26.1
`20260813T125559Z-789f027ffe4b` (`035c2c69...4a30`), 26.1.1
`20260813T131059Z-721b4de15471` (`5b9248de...55d9`), and 26.1.2
`20260813T132720Z-47169a2c9167` (`3b450310...079e`). Parenthesized values
are terminal-evidence SHA-256 prefixes/suffixes. Every cell launched the
actual patch-specific client and integrated server, created one disposable
world, produced all eleven captures, completed all 4,096 cells, and passed
pause/resume/cancel/retry plus ordered placement/removal revisions. This
closes nightly fixture 04 as source-ABI graphical evidence; it is not a
production-launcher or frozen-client-package claim.
The same runner now has a bounded fixture-05 handshake/disconnect tail. It
requires the loader-specific server log to accept `settings_ack_v3`, binds a
rendered client to mapping 4 and a nonzero layout fingerprint, performs
Minecraft's normal integrated-server disconnect, and waits for
`RingWorldClientSession.isCleared()` before passing. One atomic run writes
separate fixture-04 and fixture-05 terminal records. The expanded contract now
passes all six cells. Fabric runs are 26.1
`20260813T174054Z-bdeeafa3a751`, 26.1.1
`20260813T174418Z-b23550ef9a22`, and 26.1.2
`20260813T174740Z-1995f7cf8b48` from clean commit `a037308`. NeoForge runs are
26.1 `20260813T181123Z-5baa2ec6644c`, 26.1.1
`20260813T181648Z-b2342a09e2e7`, and 26.1.2
`20260813T182206Z-14d425cfb781` from clean commit `aa94e9a`. NeoForge's first
attempt exposed only its optional early splash trying to open against a
sleeping macOS display; the Atlas fixture now disables that splash exactly as
the other automated NeoForge graphical fixtures do, while retaining the real
Minecraft window. This closes fixture 05 as source-ABI client/session evidence;
it remains distinct from frozen-jar packaged-client qualification.
The next source-ABI gameplay wrapper now targets the existing map/compass
fixture. It requires both seam directions, map pixels and markers, banner and
item-frame state, all vanilla compass targets, save/disconnect/session clear,
reopen, and persisted state in one disposable world. The slice now passes all
six cells from clean commit `9015857`: Fabric runs are
`20260813T184333Z-82e19d55389b`, `20260813T184752Z-6181972f0b5e`, and
`20260813T185208Z-746d5447411a`; NeoForge runs are
`20260813T185449Z-26282a7c51fb`, `20260813T185906Z-d67da65d189d`, and
`20260813T190435Z-9f27985cd623`. This closes fixture 08 as source-ABI
graphical/gameplay evidence; it is not frozen-jar packaged-client evidence.
The fixture-10 curved-object wrapper is also implemented and pure-tested. It
reuses the existing real renderer fixture, requires one disposable world plus
the verified far/near captures, and applies the same fresh-title/onboarding/
NeoForge-splash safeguards. It now passes all six cells from clean commit
`f9cb4c2`: Fabric runs are `20260813T191305Z-12bda076ad23`,
`20260813T191530Z-a9b6435145d8`, and `20260813T191738Z-34182794aaf3`;
NeoForge runs are `20260813T191940Z-187e70e94c0c`,
`20260813T192307Z-3acb0e5bbc29`, and `20260813T192619Z-7787ffdc1eee`.
This closes fixture 10 as source-ABI graphical evidence.
The companion bounded persistence parser has been checked against an actual
NeoForge qualification world. It independently decodes the dimension-owned
gzip NBT settings and Atlas-v6 header/presence map, reproduces the Java
unsigned layout fingerprint and Atlas world hash, and rejects malformed,
truncated, trailing, or invalid-presence data. Recovery evidence additionally
requires an absent fresh runtime/world/Atlas before assembly and an exact
byte-identical interrupted Atlas observation immediately before restart.
The accepted same-jar proof architecture builds one frozen candidate per
loader against the oldest supported ABI and runs that unchanged file in
external production-style profiles for every patch. Per-cell Gradle builds are
ABI diagnostics, not compatibility proof.
Qualification-only Fabric and NeoForge candidates now also compile from the
26.1 source ABI with reviewed closed metadata covering 26.1 through 26.1.2.
Normal builds were separately checked to retain their exact published 26.1.2
metadata. Strict pure range checks prove that the six manifest targets are
inside those declarations. Both loader candidates now pass all three quick
external runtimes; this does not substitute for the Phase 4 nightly matrix.
The runner now has a serial injected-phase state machine, isolated
`GRADLE_USER_HOME`, `--no-daemon`, held cell locks, immutable per-cell/matrix
reports, and a clean pushed-source/Java-25 provenance preflight. Build/unit and
per-cell diagnostic-artifact adapters are enabled by default. The latter
accepts exactly one isolated runtime jar plus its canonical Gradle sources
sibling and records strict loader/MPL/build-identity inspection plus SHA-256.
A complete three-version loader selection additionally builds one candidate
from the 26.1 ABI with the reviewed closed metadata ranges, retains and
re-inspects it under the immutable run, and makes every loader cell cite the
same path and hash. Partial loader selections do not synthesize that proof.
The external-runtime phase bridge and standalone executor share one exact live
cell lock safely. Default runner wiring enables it only when clean provenance
and a complete loader triplet exist; a partial selection remains `INCOMPLETE`
without runtime I/O. Strict terminal evidence is written only after schema
validation with exclusive, non-symlink-safe creation.
The first clean pushed execution of this boundary selected Fabric 26.1 at
commit `51e7a95d56617e0af7b575dbc9c076727f5e65e2`: Java 25/Fabric Loom
1.17.19 completed all 337 tests and its isolated build in 2m59s, while the
cell correctly remained `INCOMPLETE` at the then-unwired later phases.
A clean repeat at `954bc7c` also passes strict inspection of the real Gradle
runtime jar (SHA-256 `7669a10461801bd0e24db60fbb3cab925d5177905e698377e65eb1e69b82a43f`)
and remains deliberately `INCOMPLETE` without a full loader triplet; no
external runtime was launched.
Publication is host-scoped: the current records describe Modrinth as published
and independently hash-verified, while CurseForge remains Under Review for
Fabric and Baking for NeoForge. One aggregate status never implies both hosts
are downloadable.

Owner Windows, gameplay, visual, and final-review gates #12, #13, #95, and
#96 are complete. The active release metadata targets shared runtime version
`1.0.0+mc26.1.2`, with loader-specific public identifiers
`1.0.0-fabric+mc26.1.2` and `1.0.0-neoforge+mc26.1.2`. Issue #97 is complete:
exact source commit `f3a5ce12a3d72a7e2253e893ca385e27f3fe7448` is tagged
`v1.0.0+mc26.1.2`, both Modrinth Release files are published, and both
CurseForge Release files are submitted for host review.

The exact 1.0 Fabric jar SHA-256 is
`ec06f6dbf81a6ac1c662f87f2fdb6a3d30297222e61da7094212b125a568c421`;
the NeoForge jar SHA-256 is
`ad818e6aec7aaf64f4d0618975c667d8b9163965e83e32408d8fc98797e700d5`.
The public showcase now links only to Modrinth, CurseForge, and GitHub; old
direct downloads were moved outside the document root. The unlisted alpha page
serves checksum-verified 1.0 Windows packages and manifest-following installers.
GitHub, host-listing, showcase, and operator copy now describe the same
progressive Atlas experience: real chunks are playable first, a fogged
biome-flavoured placeholder bridges the incomplete distant ring and both rims,
streamed revisions cross-fade as work is checkpointed, and verified completion
removes the placeholder and upgrades the detailed mesh. The copy explicitly
warns that generation time and disk cost vary by layout and machine.

The live demo was migrated from Fabric to NeoForge 26.1.2.87 and RingWorld 1.0.
Its previous world is preserved intact at
`/opt/ringworld-server-archives/20260810T204137Z-fabric-world`; the replacement
world uses seed `-7809050111168616191` with 16,384×256 geometry. The server,
Meridian, and Meridian regeneration API are active after migration.

The historical dual-loader alpha-3 candidate's validated code baseline is commit
`967759be872080a72e48bd26f7a97df9ee0a0302`. Its Fabric SHA-256 is
`9ec25789e1418fd3b1877c3c23d8388cbb880a0ed562ef5f0608498df0605097`;
its NeoForge SHA-256 is
`ac8b8776d85038512bb85dab8967a32a53e8d33128a4ccae17b51b65b214938a`.
The clean dual alpha build/stage, 291 tests per loader, focused distribution suite,
dual-loader safe-small multiplayer gates, and production NeoForge
Atlas-concurrency matrix pass. The final staging manifest supplies the exact
post-documentation corresponding-source revision. Machine evidence and the
remaining human gates are in
[`DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md`](DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md).

On 2026-08-08, owner-authorized matched alpha 3 files were uploaded to the
still-**Under review** Modrinth project. Fabric version `lnY3EC8t` has SHA-256
`9ec25789e1418fd3b1877c3c23d8388cbb880a0ed562ef5f0608498df0605097`;
NeoForge version `D19TF1Qj` has SHA-256
`ac8b8776d85038512bb85dab8967a32a53e8d33128a4ccae17b51b65b214938a`.
Both hosted files point to exact public source revision `94c8c9e`; a fresh CDN
download matched each frozen hash and passed the loader-specific MPL
distribution verifier. These remain alpha test builds. Upload does not close
the owner gameplay/visual, real Windows, independent-review, or compatibility
gates; use `OWNER_RELEASE_SIGNOFF_2026-08-09.md` for the final review.

On 2026-08-09, the same exact Fabric and NeoForge jars were submitted as Alpha
files to new CurseForge Minecraft Mod project `1645598` under MPL-2.0. Both
files target Client and Server, Java 25, and Minecraft 26.1.2. Fabric alone
declares Fabric API project `306612` as a required relation. The project Source
tab points to the public RingWorld GitHub repository. Both files reached
CurseForge's `Under Review` state, so this is distribution
staging rather than release promotion. See `CURSEFORGE_RELEASE.md`.
The CurseForge media gallery also contains the six approved in-game images
from the RingWorld showcase page rather than automated diagnostic captures.

Earlier on 2026-08-10, the unlisted showcase alpha directory was refreshed from merged
public source commit `622fb76`, including the mapping-4 terrain-seam
correction, biome-flavoured incomplete-Atlas placeholder, 750 ms revision
morph, completion-driven haze, neutral cobble/moss rim returns, finite clouds,
and the in-menu alpha/worldgen identity. It also displays the compact top-left
Atlas percentage until generation completes. The exact Windows package
SHA-256 values are Fabric
`b5e9eff725623f6e716d5d9f7d4c0366532350f683ed732ebcc9f1d6618c7729`
and NeoForge
`e2130a6d7db766ac857b69d6b4e92564e118b258b33f5e661a5d413848df4a4d`.
`https://andwhatnotstudio.com/ringworld/alpha/` provides separate reusable
manifest-following one-click installers and manual ZIPs for both loaders, the
MPL licence, and exact source manifests. The historical installer and manifest
names remain Fabric aliases for existing testers. Every installer validates
loader/source identity and the selected artifact SHA-256 on each run, so one
downloaded BAT follows later matching alpha builds without embedding their
checksums.
The packaged server entry is pre-added but is not joined automatically. This
is a test convenience build, not a Modrinth/CurseForge promotion or 1.0 gate.
The previous directory is retained outside the document root at
`/root/ringworld-alpha-backups/alpha-20260810T192327Z`. At that checkpoint no
live server or world was changed; the later 1.0 publication and migration
recorded at the top of this document supersede that distribution state.

Issue #33 now supplies a local fail-closed staging workflow for any later
manual Fabric upload. It builds and validates exactly one runtime jar, records
SHA-256/SHA-512 and the clean pushed public-source revision, and rejects stale
licensing, inconsistent Fabric metadata, source/development jars, unsafe
archive paths, and common credential/runtime-state names. It has no upload or
listing mutation path. The clean dedicated-server smoke is documented. On
2026-08-01, clean and in-place-modified graphical Fabric fixtures loaded the
staged jar through complete resource/shader initialization without a crash;
the latter retained a disposable companion mod across the staged-jar install.
The ignored local evidence and exact procedures are recorded in
`MODRINTH_RELEASE.md`.

This document separates demonstrated implementation from planned or incomplete
work. It should be updated after every substantial milestone.

Issue #149 corrects the alpha terrain-banding defect without silently changing
existing worlds. The legacy axial mapping ignored intrinsic Z in one noise
axis and its coordinate Jacobian collapsed at quarter-ring longitudes. Fresh
worlds created after the seam regression use complete annular mapping v2 (4)
`((R+Z)sin(theta),(R+Z)cos(theta))`; formats 1 and 2 upgrade with the exact
legacy mapping retained. Mapping identity is persisted, handshaken on new
`settings_v3`/`settings_ack_v3` channels, attached to every Overworld noise
router, fingerprinted, and included in the atlas world hash. Both loader test
suites pass 338 cases. Mapping 4 also transforms vanilla's direct
`BlendedNoise` leaf; mappings 1-3 remain preserved historical identities. Both
fresh 16,384×256 stronghold/worldgen gates previously passed
the five-longitude, three-width-position terrain/height/alias matrix plus the
existing biome, seam structure, rim, monument, and portal checks. The uploaded
alpha-3 jars remain format-2 historical test artifacts; 1.0 supersedes them.
Fresh dual-loader production/reload plus both
safe-small policy-seed matrices now pass. Fresh production headless Atlas
generation and complete-atlas resume/load also pass on both loaders. Fresh
format-3 production projection, natural seam/both-rim visual parity, and the
Overworld/Nether/End save-disconnect-reopen lifecycle now pass on both loaders;
owner visual review is complete for promotion.

The 1.0 metadata uses distinct loader-specific public identifiers while
retaining one shared `1.0.0+mc26.1.2` runtime version. The staging gate rejects
the generic artifact version as a hosted identifier and remains a local-only
verification step. Owner authorization for publication and the subsequent
website/server deployment is recorded in issue #97.

The 2026-08-10 fresh-candidate graphical runs used the independently generated
production worlds rather than an alpha save. Fabric and NeoForge each loaded
all 65,536 Atlas cells, acknowledged format 3, and verified tangent, 12-chunk
handoff, and radial captures. Their natural seam traversals retained 0.25-block
steps and sampled 702 frames at 11.99 ms average on Fabric and 753 frames at
9.60 ms average on NeoForge before capturing both textured rims. Both lifecycle
runs restored the exact format-3 fingerprint and Atlas after Nether and End
transfers, normal save/disconnect, full client-state clear, and same-process
reopen. These automated captures do not replace owner judgement of perceived
terrain banding and colour/LOD quality.

Headless prewarm evidence schema 2 now records the supported terrain-noise
mapping explicitly in progress and terminal JSON; both loader finalizers reject
an absent/unknown mapping in addition to stale identity or incomplete totals.

On 2026-08-02, Phase 4 began with issue #90. The eleven Fabric-owned
entrypoint, lifecycle, networking, environment-path, and automated-client
classes moved into explicit `src/platform/fabric` and
`src/platform/fabricClient` source trees without changing their packages or
runtime identity. Shared main/client trees now contain no Fabric or NeoForge
API references, enforced by `verifyLoaderBoundary` as part of `check`. The
Java 25 test/build gate passed all 235 cases, the rebuilt jar retained its
entrypoints and licence, a fresh Fabric dedicated server reached `Done`, and a
Fabric client completed resource/shader initialization.

Issue #91 adds the NeoForge 26.1.2.87 / ModDevGradle 2.0.143 Java 25 module,
metadata, bootstrap, lifecycle, command, payload-transport, and atlas
adapters. The Fabric and NeoForge builds each pass all 235 unit/parameterized
cases. Fresh dedicated servers for both loaders reach `Done`; the NeoForge
server also starts and progresses its terrain atlas.

Issue #92 reaches the first NeoForge graphical-client checkpoint. Shared
client payload transport and session teardown now serve both loaders; NeoForge
includes the shared client sources, client mixins, shaders, and resources while
its adapter owns payload-handler, client lifecycle, cache-path, and render
pipeline registration. A real NeoForge client completed resource/shader
loading, opened a copied production 16,384×256 world through its integrated
server, acknowledged settings format 2, streamed atlas metadata and tiles, and
rendered a progressive textured ring surface. The replacement
`:neoforge:runProductionProjectionClient` task copies a named source save into
an isolated run directory, waits for atlas completion, captures tangent,
handoff, and radial views, verifies them, and exits. Its production 16,384×256
noon run passes; the settled stages averaged 10.7, 8.4, and 8.4 ms per frame,
respectively. Dusk, night, and rain projection variants also pass. The
disposable visual-parity gate captures a continuous seam and both correctly
textured rims. Same-process switching between 16,384×256 and 15,552×256 clears
the old geometry/atlas, and the production lifecycle passes Nether, End, both
Overworld returns, save/disconnect, and reopen with the original complete
atlas and fingerprint. This completes #92; #93 below completes the server and
multiplayer runtime gates, leaving packaging and full standalone parity open.
Use `:runServer`
for Fabric and `:neoforge:runServer` for NeoForge rather than an ambiguous
unqualified task.

The copied-world Fabric projection, same-process layout-switch, and production
lifecycle runtime tasks now match the NeoForge fail-closed gate shape: each
clears old ignored evidence before launch and runs a verifier finalizer.
Projection requires and decodes its three environment-specific PNGs; layout
and lifecycle require their exact successful terminal marker and copied
`level.dat`. The build-level verifier contract fixture proves missing, failed,
and corrupt evidence is rejected as part of `check`.

The subsequent networking audit makes the NeoForge payload-thread boundary
explicit: every serverbound and clientbound payload handler delegates its
stateful body through `IPayloadContext.enqueueWork`. This keeps the handshake
tracker, server atlas stream, client session/cache/GPU state, disconnects, and
outbound acknowledgements on the corresponding game thread while preserving
the immediate post-play-login settings send, exact rejection messages, and
idempotent acknowledgement behavior.

The follow-up headless admission audit moves NeoForge's denial to a
cancellable `PlayerList.placeNewPlayer` method-head injection before vanilla
creates the play listener or initial packet buffer. One platform helper is shared by that early gate
and the later event fallback. An active prewarm therefore sends no RingWorld
settings, starts no handshake timeout, and exposes no atlas metadata; ordinary
login still receives settings directly behind the play-login packet. The
dual-loader suite includes a compiled-bytecode platform-boundary assertion for the
decision side effects and exact injection order.

The matching Fabric audit closes a different loader boundary: its array-backed
JOIN event continues invoking listeners after the lifecycle adapter disconnects
a headless-prewarm join. The later networking listener now rechecks the same
coordinator state and returns before `sendSettings`, leaving rejection/message
ownership unchanged and preventing handshake or atlas work. A platform-isolated
compiled-bytecode test fixes that guard-before-settings ordering.

Issue #93 completes NeoForge server/runtime parity. The shared headless prewarm
coordinator now owns scheduling, terminal JSON evidence, join gating,
checkpointing, save, and shutdown while thin Fabric and NeoForge adapters own
loader lifecycle events. A fresh NeoForge 2,048×416 unattended run completed
all 3,328 chunks/13,312 atlas cells and shut down with verified `COMPLETE`
evidence. The loader-selectable worldgen runner passed fresh/reload production
16,384×256 plus both 2,048×416 policy cases, covering all 14 biome families,
caves, ores, trees, loot, seam-crossing structures, satisfied/unsatisfied
monument policy, strongholds, and complete End portals. The dedicated
two-client NeoForge fixture passes natural 0.25-block seam travel, periodic
visibility and combat, stateful blocks, explosions, bed/death lifecycle,
physical Nether and End portals, boats/passengers, teleports, reconnect, and
canonical storage. Runtime review also fixed NeoForge's changed explosion
method target, a same-tick bed-rule cache difference, and a client network-
thread chunk reset during portal travel.

Issue #94 completes local standalone packaging parity. Fabric and NeoForge
runtime jars are validated against their own loader metadata and MPL-2.0
contents, then compared before staging for matching mod/Minecraft versions and
byte-identical shared mixins, settings/geometry, compatibility API, protocol
models, and shaders. `--loader both --build` refuses to write either Modrinth
stage unless both candidates come from the same clean pushed public commit and
the shared contract matches. The optional package builder now creates
loader-labelled, reproducible macOS, Windows, and server archives. Fabric and
NeoForge use separate Prism instance IDs, preserving the other loader's
instance while refreshing managed jars only inside the selected instance;
NeoForge contains no Fabric API. Accounts, saves, options, config, resource
packs, unrelated mods, and instance settings are preserved. All 37 focused
Python tests pass locally (two Windows-only cases skipped), all six actual package
archives pass licence/hygiene verification, and a packaged NeoForge client on
macOS loaded NeoForge 26.1.2.87, RingWorld, resources, and shaders without a
crash. The Windows launcher path runs in GitHub Actions; a real graphical
Windows Minecraft run remains #12. Nothing in this gate uploads, publishes,
deploys, or changes a hosted listing.

Public `main` commit `3015532` is the latest merged headless checkpoint. Its clean
Java 25 build passes all 241 unit/parameterized cases independently for Fabric
and NeoForge, and explicit dedicated-server launches for both loaders reach
`Done`. The Fabric development runtime jar has SHA-256
`5804931222db74590835f978084e6987da88f5bf40eecfe4d2289d9365c441ff`;
the NeoForge jar has SHA-256
`53c5786dea95f75f46350ce6e4d77aa5a8ee0f9c75f49cdccc615b309accc277`.
They are not frozen release candidates: graphical and strengthened two-client
acceptance remains open.

Issue #111 tracks the raid/POI seam defect. Its loader-neutral
foundation defines periodic raid distance/selection, three-image POI query
origins, nearest-image village-centre averaging with canonical persistence,
and split canonical block/chunk readiness windows for wave spawning. Pure
tests cover the `C-1/0` cases. `ServerWorldMixin` now applies that distance to
the saved active-raid lookup used by omen extension, bossbars, villager state,
commands, and reconnect. The integration branch additionally applies periodic
POI discovery/averaging, canonical centre creation and relocation, canonical
wave-spawn readiness and result positions, periodic village/retention probes,
and nearest-image raider path targets. Both loader builds and dedicated-server
startup pass with those mixins applied. The branch now contains an opt-in,
two-phase Fabric/NeoForge fixture: arm creates occupied seam POIs and saves a
real first wave; reload proves persisted occupied POIs, restored
membership/bossbar state, natural
raider folding, victory, and Hero of the Village. On 2026-08-02 both loaders
completed both phases with canonical centre `X=1`, both seam-side players on
the bossbar, a natural raider fold, and `[raid-seam] PASS`. Issue #111 is
complete subject to final merge and tracker closure.

Issue #24 expands the loader-neutral dimension matrix to 200
unit/parameterized cases. It covers the safe-small, aligned playable-minimum,
production, former-wide, long/narrow, wide/medium, and custom-wall layouts;
the corresponding topology/spawn/worldgen seam-and-rim/atlas/render/UI and
settings-handshake identities are derived from the selected immutable layout.
The only documented bootstrap exception is first-world spawn selection, which
runs before saved data exists and delegates its finite-Z policy to
`RingSpawnBounds`; saved worlds do not use bootstrap geometry. The matrix also
exercises structural-only and unsafe-curvature rejection, invalid alignment,
and the maximum technical warning envelope. This improves pure coverage; it
does not replace the isolated client, copied-world, or dedicated runtime gates.
Its two new isolated stronghold/worldgen runtime cases passed at the aligned
2,016×256 playable minimum and 4,096×2,048 wide/custom-wall-192 layout,
including periodic base-height/base-column agreement, canonical structure
bounds, folded Eye continuity, both rims through their saved height, their
generated exterior void; atlas pregeneration was disabled.

Issue #51 adds a new-world-only ocean-monument guarantee. Policy format 2
persists the request and one deterministic terminal result before generation;
legacy/missing policy stays monument-disabled. The satisfied production-seed
fixture generated one valid canonical monument at chunk `(606, 3)`, bounded at
X=9667..9724 and Z=19..76, then located it from the adjacent presentation
chart without creating an alias chunk. A second dedicated-server process
loaded the same candidate/start and correctly treated its prior unexplored
reference as used.

The atlas-priority phase is complete through #70. #66
records the production and safe-small 6/12/28 visual/performance baseline. #67
is superseded by #148: zero-cell and partial Atlases now render an opaque,
world-hash-seeded fallback, smooth generated-terrain palette influence, and
temporary curved returns at both inner rim faces. Each published revision
cross-fades on the GPU for 750 ms. A completion-driven 0.88-strength haze
clears to zero as the Atlas fills, and temporary returns use neutral
cobble/moss shading instead of green terrain samples; completion still performs one exact
full-detail transition. #68 introduces disk format
6, bounded post-edit cell recapture, monotonic durable revisions, persistent
complete-client tile subscriptions, ordered revision commits, and exact-
revision reconnect reuse. The real safe-small atlas UI fixture completed all
13,312 cells, committed revision 1, then placed and removed a sampled high
surface block and observed revisions 2 and 3 plus matching client heights.
The active suite passes 338 unit/parameterized cases per loader.
Fresh production visual-parity runs on 2026-08-10 exercised multiple partial
Atlas revisions and the two-texture shader on both loaders before completing
the natural seam and both rim captures. Fabric recorded 831 seam-motion frames
at 8.65 ms average; NeoForge recorded 855 at 8.41 ms average. Both exited
cleanly.
The visual-parity fixture now also requires a look-back capture from X=2 toward
the C-1 side. Earlier seam captures faced along travel after crossing and were
not evidence that generated terrain visually joined behind the player.
The F3 RingWorld group now reports the persisted terrain mapping name and
number so legacy-world evidence cannot be mistaken for a current
`annular-complete-v2 (4)` world.
The RingWorld Map now embeds the same diagnostic in its normal UI, beneath an
`Alpha 4 · 0.2.0+mc26.1.2` build label generated into both loader jars. Fabric
passed the complete GUI-scale-4 atlas UI capture/revision fixture with the new
header on 2026-08-10; both runtime jars carry the same validated identity.
Fresh production mapping-3 runs passed that seam-join capture on both loaders
on 2026-08-10. Their real chunk terrain crosses X=16383/0 without a flat
height wall; Fabric recorded 847 seam-motion frames at 8.49 ms average and
NeoForge recorded 846 at 8.44 ms average, with one frame over 50 ms each.
The follow-up progress haze and neutral cobble/moss return shading also passed
fresh production visual-parity runs on both loaders: Fabric rendered 12.5% and
71.9% partial states and NeoForge exercised the partial path through 71.9%,
then both completed their seam and rim captures with one frame over 50 ms.
Owner review of the deliberately incomplete appearance remains.

Issue #157 clips curved vanilla cloud fragments to `RingCloudBounds` at the
inner faces of the two five-block rims. The clip is fragment-accurate for
straddling cells and remains inactive outside a negotiated RingWorld
Overworld. Issue #158 adds complete annular mapping 3 for fresh worlds: the
saved density transform is also applied to surface rules, clay/badlands,
frozen-ocean features, and carver seed identity. Existing mapping-1/2 saves
remain unchanged. A fresh 2,048×256 Fabric seam-strip matrix reported zero
height delta for every playable-Z seam column; fresh Fabric and NeoForge
mapping-3 stronghold/cardinal gates pass.

The uploaded exact seed `-4558730636853595596` then exposed a remaining
mapping-3 gap: vanilla's direct `BlendedNoise` density leaf still used flat
X/Z, and the old twelve-block cliff threshold missed its broad nine-block
join wall. Fresh mapping 4 (`annular-complete-v2`) transforms that leaf too,
while the strengthened gate rejects average join mismatch above one block.
Fabric and NeoForge exact-seed 16,384x256 runs pass at average delta
`0.35365853658536583` instead of the uploaded world's `3.2276`. Existing
mapping-3 worlds keep their saved generator and are not rewritten.

Issue #147's directional seam-placement loss is fixed at the outbound packet
ownership boundary. Block-use packets now canonicalize the clicked block and
translate the hit vector by the same whole-chart offset, so the valid east face
of canonical `X=C-1` remains locally at `X=C` instead of wrapping to a point a
full circumference away. Pure coverage includes both seam directions and
positive/negative presentation aliases. The real two-client Survival fixture
then passed both directions on Fabric and NeoForge: each placement preserved
the support, created one canonical target block, consumed exactly one of two
items, and appeared on both clients. Fabric continued through the full matrix.
NeoForge's original placement run passed that baseline before a later known
#134 cold stall. A serialized 2026-08-10 Atlas-disabled rerun then completed
sleeping reconnect, death/respawn, physical Nether/End travel, the post-End
stability window, weather, both client terminal results, and the strict full-
matrix verifier. The placement fix therefore has complete dual-loader runtime
qualification; cold resource profiling remains separate.

Issue #145 now has a loader-neutral portal destination policy at the
`PortalForcer` ownership boundary. After vanilla's Nether-to-Overworld 8:1
scale, X is canonicalized, Z is clamped far enough inside the rims for the
complete vanilla creation sweep, and portal POI lookup unions both adjacent X
images before selecting by periodic distance. Fresh Fabric and NeoForge
two-client fixtures both pass positive/negative multi-lap lookup, portal
creation beyond both Z rims, a real four-lap player return, ordinary 80-tick
survival portal delay, reconnect, and subsequent End travel. The live demo
world was not opened or regenerated. The same complete matrix also passes at
the Medium 16,384x256 geometry on Fabric and on a warmed NeoForge retry; the
first cold NeoForge production attempt stopped earlier at the known fixture
resource-pressure boundary tracked by #134, before portal routing ran.

The combined alpha-4 integration branch was then built and exercised as one
candidate rather than relying only on the four issue branches independently.
Its clean Fabric and NeoForge builds each pass 337 tests. Fresh Atlas-disabled
2,048×416 two-client runs on both loaders pass the strict full-matrix verifier
with settings format 3, both seam-placement directions, the shared 54-slot
double chest and lossless alias recovery, the four-lap/out-of-width Nether
portal cases, sleeping reconnect, death/respawn, End travel, and seam weather
all enabled together. This is automated integration evidence; it does not
replace the remaining owner visual/gameplay and real-Windows review.

#69 compares production atlas steps 8/4/2/1 with a checked cost matrix and a
repeatable format-6 save/load/tile/CPU-texture benchmark. Finer candidates use
4×/16×/64× the source cells and cold tile bytes while the current 4,096×256
GPU texture and 393,216-vertex mesh stay fixed. The release therefore retains
the single eight-block profile and makes no saved-format or protocol change.

#70 completes the production regression gate. A 16,384×256 format-6 atlas
resumed after interruption and finished all 16,384 chunks/65,536 cells in a
combined 13m39.7s; a complete-cache rerun performed no generation. The real
GUI pause/resume/cancel/retry and revisioned-edit path, safe-small-to-production
layout switch, Overworld/Nether/End/save/reopen lifecycle, dedicated two-client
seam/gameplay/cache scenario, and safe-small plus production 6/12/28 projection
matrix all pass. Complete texture preparation now runs from an immutable
snapshot off the render thread, and already-complete revision bursts publish
once after a three-second quiet period or ten-second maximum delay. Exact
resource numbers, hashes, commands, frame metrics, and residual cold-start
spikes are recorded in `ATLAS_RELEASE_GATE_2026-08-01.md`.

Issue #128 replaces the final atlas mesh's per-quad corner resampling with a
shared pure `RingSurfaceMesh` lattice. The unindexed GPU triangle list still
repeats vertices, but every interior adjacent band and segment now receives
the exact same sampled float position and UV at its common boundary. The two
periodic seam columns share an exact physical X=0/C position while deliberately
retaining U=0/1 for texture repeat. Focused production and safe-small tests
cover band continuity, segment continuity, the periodic seam, and the
progressive reference-height path. Verified completion performs one detailed
mesh upgrade; later complete-atlas revisions rebuild relief only when their
surface-height fingerprint changes, so colour-only texture updates retain the
existing mesh. Texture pixels, height fingerprint, and any replacement mesh
share one immutable atlas snapshot, preventing asynchronous work from mixing
one revision's colour with another revision's relief. The exact production
visual projection/parity gates now pass on both loaders and show the formerly
reported triangle absent.

Issues #130 and #131 now have their local runtime evidence as well. Fabric's
exact production 16,384×256 headless prewarm completed on 2026-08-06. Issue
#149 then supplied fresh format-3 annular production prewarms on both loaders on
2026-08-10: Fabric completed in 38m16s and NeoForge in about 41m, each with
16,384/16,384 chunks, 65,536/65,536 cells, schema 2, mapping 2, normal save,
and a separate complete-atlas resume/load pass. `/ringworld atlas start` starts an idle durable partial
atlas and `/ringworld atlas resume` reattaches that saved partial work after an
`IDLE` restart without replacing active or release-pending work. On 2026-08-08,
fresh Fabric and cold NeoForge two-client Atlas-concurrency fixtures passed the
full matrix after both clients had been ready through 100 consecutive server
intervals at or below 100 ms (60 seconds/1,200 observations fail closed). The
gate retains the original 100-tick Creative-to-Survival dwell and the strict
`maxRemoteStep <= 1.25` requirement; each automated client exits after its
terminal result.

The ignored Fabric and NeoForge Atlas-concurrency harnesses now expose the
same validated circumference, width, and wall-height Gradle properties while
retaining their 2,048×416×160 safe-small defaults. The Atlas opt-in verifier
checks each logged total against the selected `(C / 8) * (W / 8)` count. On
2026-08-08 the NeoForge harness created the production 16,384×256×160 layout,
two ordinary graphical clients completed the full multiplayer matrix while
its 65,536-cell Atlas advanced, and the verifier passed. The server then saved
7,544 cells (11.5%), restarted from that exact partial state without preparing
or deleting the fixture, resumed monotonically, completed all 65,536 cells,
and stopped through a normal save. The gameplay run logged two isolated cold
server-behind warnings (7.464 and 8.296 seconds) but no managed-block deadlock;
the Atlas stayed monotonic and the strict client movement result remained
within the qualified bound. Cold-stall profiling is tracked separately in #134 and
does not invalidate the completed Atlas-concurrency proof.

A cold safe-small NeoForge release-regression attempt with Atlas generation
disabled passed through End return, then watchdog-terminated before its final
weather result. The sample found the server thread runnable in
`ServerEntity.sendChanges` with an unusually heavy disposable-world inventory
while the host was under substantial swap pressure. That remains profiling
evidence for #134, not a proven cause or an Atlas deadlock. A fresh
warmed/staggered rerun then passed the complete server and both-client matrix,
including the new sleeping reconnect/resleep path, normal Nether delay, End
return, weather/lightning, and the strict terminal verifier.

The disposable fixture now bounds that diagnosis directly. Its cross-seam
BLOCK explosion runs inside a seam-wrapped no-drop glass cell instead of
breaking arbitrary cold terrain. Read-only phase telemetry records chunk,
scheduled-tick, entity/item/falling-block, heap, and Atlas state around the
fixture and portal transitions. After verified End return, a new independent
100-on-time-tick stability window must pass before weather is armed; timeout
fails the matrix. Both loader verifiers require the ordered telemetry and
stability-ready markers. These are fixture-only safeguards and do not alter
live server scheduling, chunks, entities, or watchdog policy.

Fresh patched Fabric and NeoForge safe-small runs on 2026-08-08 passed the
complete server/client matrix and their strict verifiers. The synthetic
explosion changed the NeoForge fixture inventory from 167 items/86 falling
blocks before to exactly the same counts afterwards, and Fabric likewise held
158/86, ruling out the former terrain-dependent blast as a source of further
growth. NeoForge recovered from isolated 4.073-second Nether and 5.870-second
post-weather-arm server-behind warnings; Fabric recovered from a 3.988-second
Nether warning. Neither produced a watchdog or crash report. This is
dual-loader functional evidence for the fixture hardening, not a claim that
cold host/resource stalls are eliminated.

The exact patched production NeoForge profile then passed at
16,384×256×160 with two graphical clients and Atlas generation enabled. Atlas
advanced monotonically from 596 to 3,824 of 65,536 cells at roughly 28–32
cells/s while the complete gameplay matrix ran; its strict verifier passed.
The largest server-behind warning was 3.219 seconds during cold Nether
generation, with no watchdog, crash report, Atlas failure, or progress
regression. This satisfies #134's reproducible production attribution: the
remaining bounded spike coincides with vanilla cold dimension work rather
than the ticket-backed Atlas scheduler.

#71 completes the expanded safe-small seam gameplay gate. The dedicated
server now waits for two fully loaded clients, then passes the original
movement/combat/block/boat/teleport/reconnect matrix plus synchronized chest
and lectern block entities, a real cross-seam redstone neighbour update,
fluid-source state, a destructive explosion, survival bed sleep/reconnect/
resleep/damage wake/destruction, death/client respawn, linked Nether portal travel and return, and
End portal travel and return. Canonical server ownership and nearest client
images remain intact, Nether/End client RingWorld state clears and restores,
and the final run emitted neither flat-distance movement warning. The fixes
are a nearest-periodic server bed-reach box, movement-baseline realignment
after server-owned sleep poses, monotonic 26.1 clock setup, and explicit
client-ready gating. Exact automated, manual, and unsupported coverage is in
`SEAM_GAMEPLAY_REGRESSION_2026-08-01.md`.

The 2026-08-08 Fabric and warmed/staggered NeoForge reruns extend that gate
through a real disconnect while
the player is asleep at the seam. Vanilla intentionally reloads a saved player
awake; the replacement player remained canonical beside the same bed, the
client verified the Overworld RingWorld session, loaded bed, and X/Y/Z
proximity rather than only X, a second sleep restored the nearest-image
pose, and the subsequent damage wake and full remaining matrix passed. The
shared readiness sender now retries until the loader transport can actually
send its payload instead of latching a pre-channel-ready attempt.

The current extended fixture seals
a two-cell trough, clears canonical X=0, places its sole water source at C-1,
and requires the server and both clients to observe water at X=0. The
2026-08-02 fresh Fabric and NeoForge 2,048x416 runs both passed this stricter
destination assertion as part of the complete two-client matrix. The
2026-08-01 source-only record remains historical evidence only.

The current fixture also includes a server-only hostile navigation probe: a tagged,
persistent Zombie starts near canonical C-5 in a bounded clear lane, receives a
vanilla navigation target near X=2, and must naturally fold into low canonical
X and finish the path within target tolerance before the fixture can advance.
It removes only prior tagged fixture Zombies from reused worlds. This is
covered by the same fresh 2026-08-02 Fabric and NeoForge passes and is not
retroactive evidence for the 2026-08-01 matrix.

#72 completes the multi-seed worldgen and structure seam matrix. Three fresh
dedicated worlds plus one exact production-save reload cover all 14 defined
major Overworld biome families, 544 fresh fully generated seam-strip chunks,
caves, ores, trees, loot containers, canonical starts/references, six
deliberately seam-crossing mineshafts, and both satisfied and bounded-
unsatisfied saved monument outcomes. The production reload preserves the exact
sampled biome, terrain, structure, loot, and policy record. Every case also
passes generated stronghold frames, activation, periodic locate, and folded-
Eye checks; the separate #71 client gate supplies physical End travel. Exact
counts and honest limits are in `WORLDGEN_STRUCTURE_MATRIX_2026-08-01.md`.

#73 hardens the mandatory play-phase protocol. Each join now has a 300-tick
acknowledgement deadline, exact required-channel capability checks on both
peers, idempotent duplicate handling, request gating, and disconnect cleanup.
The 26.1 positional audit adds minecart-step, damage-source, `/look`, sign,
pick-block, and block-tag-query conversions. Filled-map pixels plus
player/banner/frame decorations now use their map centre's nearest image, and
spawn/lodestone/recovery compasses use the holder's nearest target image. The
locator bar, operator debug packets, and opaque mod payloads remain
unsupported. The complete two-client seam/gameplay/reconnect matrix still
passes. See `PROTOCOL_HARDENING_2026-08-01.md`.

The #95 navigation slice routes filled-map sampling and decorations plus
spawn/lodestone/recovery compass bearings through the nearest periodic image.
Its two-direction pure rules pass. Fresh 2026-08-06 Fabric and NeoForge runs of
the expanded disposable real-client fixture also pass and each produced all
eight labelled screenshots. Both seam directions verify filled-map pixels,
player and white-banner decorations, and decorations backed by real
world-added item frames; the high-centred map then passes scale one, banner
removal/restoration, and locking. The fixture exercises spawn, lodestone, and
recovery targets in both directions. Its seam-equivalent exact-target check
reuses one compass wobble state, making the two seeded random-spin samples a
deterministic assertion instead of comparing independent random offsets.

The same runs use Minecraft's normal save/disconnect path, wait for raw
RingWorld session teardown, and reopen the saved world in the same process.
The raw gate covers geometry and presentation-camera state, atlas identity and
cache ownership, atlas-control status, and complete-ring GPU resources without
depending on `client.level` already being absent. After reopen, the fixture
rechecks the locked map's pixel/decorations and server centre/scale/lock,
loads the same persistent live item frame and framed map, verifies persisted
spawn/lodestone/recovery targets, and repeats the nearest-image compass check.

The strengthened general #95 two-client acceptance matrix now passes from
fresh 2,048x416 fixtures on both Fabric and NeoForge. Both runs reached
`full scenario result=true`; NeoForge also passed its evidence verifier. This
freshly covers natural seam travel, tracking, combat, blocks, boat/passenger,
teleport, reconnect, chest/lectern, cross-seam redstone and destination water,
hostile Zombie path completion, beds, death/respawn, and physical Nether/End
round trips on each loader. A subsequent 2026-08-02 dual-loader run retained
that matrix while requiring the normal 80-tick survival Nether-portal wait
(83–84 ticks on Fabric, 83 on NeoForge), full rain/thunder state, and a real lightning
entity visible to both clients across the seam. Client interaction now waits
for both target chunks before the server places its block, and fixture startup
clears saved weather so reused worlds cannot satisfy the new evidence early.

Issue #146 identified a missing runtime block-entity ownership boundary.
Chunk acquisition already selected the canonical holder, but vanilla
`LevelChunk` still keyed its block-entity maps by the full raw `BlockPos`.
Consequently, a double chest spanning `C-1`/`0` could resolve its partner via
X=`C` or X=`-1` and expose two independent 27-slot inventories. The shared,
server-Overworld-only `LevelChunkMixin` now canonicalizes block-state and
block-entity map positions before creation, lookup, removal, and save lookup.
The 2026-08-10 Fabric disposable two-client run passed the complete matrix and
recorded one shared 54-slot container from both halves, cross-view items, and
canonical alias identity. The matching NeoForge run passed the same explicit
container/client checks; its serialized pending-NBT recovery marker also
passes, including save lookup before the alias and canonical packed entries
are otherwise read. A serialized Atlas-disabled NeoForge rerun on 2026-08-10 completed the
entire matrix and strict verifier after the double-chest and recovery markers,
including sleeping reconnect, death/respawn, Nether/End, post-End stability,
weather, and both client terminal results. An earlier attempt failed before
the chest fixture when client B did not arm the baseline seam stage; the
client harness now records its local pose, game mode, remote position, and
expected nearest image while waiting so this startup condition is diagnosable.
No automatic destructive merge is attempted if an already-corrupted save
contains two distinct alias-backed inventories. Saved-chunk post-load retains
the raw alias through vanilla NBT position decoding: a lone alias repairs to
its canonical owner, while a canonical/alias collision keeps both payloads
independently addressable and logs recovery guidance. The runtime regression
uses serialized chest NBT for both cases rather than injecting only live map
entries. It deliberately promotes/loads the alias before the canonical entry
through the packed-pending path and the same direct-entry reconciliation
policy used by post-load, proving the ownership decision itself is independent
of iteration order. The production `runPostLoad` redirect resolves on both
loader servers, but a future fixture should still drive an alias-first region
file through that exact vanilla callback rather than invoking its two shared
components separately.

The same cold dual-loader rerun exposed a fixture-only sleep/reconnect race:
the client acknowledgement could arrive after a cold server had already
processed the disconnect, and disconnect/login could also finish between two
slow server ticks. The harness now captures the old `ServerPlayer` when the
server successfully starts sleep and treats its later replacement as
definitive reconnect evidence, while retaining the bounded timeout and all
awake/session/bed/proximity assertions. Gameplay behavior is unchanged.
The corrected timing path completed the final Fabric matrix; the code is
shared and dual-build green, while a fresh terminal NeoForge replay remains
part of #134's warmed/staggered harness work rather than issue #146.

The #96 refresh completed Fabric and NeoForge safe-small 6/12/28 tangent,
handoff, and radial-up captures. Fabric average frame times were
8.636/8.646/11.915 ms with 0/1/2 frames over 50 ms. NeoForge's tangent
averages were 8.446/9.904/11.253 ms, its handoff and radial averages remained
below 9.4 ms, and all nine measured views had zero frames over 50 ms.
Complete-ring alignment, the broad live/LOD handoff, sky phases, and the
inspected rim remained intact. The exact production 16,384×256 tangent,
handoff, radial-up, natural-seam, and both-rim gate now also passes on both
loaders. The refreshed noon projection captures contain no tutorial overlay;
Fabric averaged 9.021/8.599/8.356 ms for tangent/handoff/radial-up and
NeoForge averaged 8.903/8.694/8.356 ms. A fresh-world curved-object fixture visually passes for chest,
lectern/book, sign, bed, ender chest, shulker box, banner, copper golem, item,
boat, cow, and zombie on both loaders.
That run also fixed NeoForge's initial login ordering: immutable settings are
now queued after the play-login packet but before initial position/chunk data,
and the fixture rejects missing client blocks instead of accepting empty sky.
Exposure, close-cloud, real-player proximity, and motion review stay open. See
`VISUAL_POLISH_CHECKPOINT_2026-08-02.md`.

The refreshed #96 checkpoint includes an exact production 16,384×256
dual-loader pass.
The projection runner now uses a centered server-authoritative spectator pose
instead of inheriting the save's stale rim-adjacent player position. Fabric
and NeoForge passed loader-identical tangent/handoff/radial captures at 12
chunks, and their shared production visual-parity runner passed a natural seam
plus both textured rims. Object/block-entity, broader exposure/weather, and
motion review remain. The refreshed shared visual-parity gate recorded the
natural seam-motion interval: Fabric sampled 426 frames at 16.742 ms average,
51.018 ms maximum, and one frame over 50 ms; NeoForge sampled 428 frames at
16.661 ms average, 21.858 ms maximum, and zero frames over 50 ms. Both strict
loader verifiers passed.

The refreshed 2026-08-10 same-size/different-seed layout-switch regression
also passes both loaders at production 16,384×256. Each process opened two
complete format-3/annular Atlas worlds with equal
geometry but distinct settings fingerprints, Atlas world hashes, and terrain
content fingerprints; disconnect cleared every RingWorld-owned client session
and static GPU resource before the second world was accepted.

#74 completes the stability configuration and compatibility contract. The
creation preview now scales the measured 16,384-chunk production run into a
checked reference estimate for full-generation time and generated-world
growth, while atlas wire bytes and minimum transfer ticks remain exact. API
version 1 adds read-only canonical, nearest-presentation, physical-position,
and physical-pose conversion. Compatibility contract version 1 publishes the
supported Fabric/vanilla-renderer baseline and eleven high-confidence
unsupported renderer, LOD, topology, gravity, and chunk-pipeline mod IDs. A
narrow Fabric adapter logs those conflicts at initialization; unlisted mods
remain untested rather than implicitly supported. See `COMPATIBILITY.md`.

The P1–P4 architecture parents (#5–#8) are now closed after final review of
the integrated 26.1 topology, worldgen, protocol, renderer, lifecycle, and
runtime evidence. This records that the ported architecture meets those work
packages; it does not erase the broader release-hardening and compatibility
limits documented below.

Issue #12 retains the frozen Fabric-only candidate at public source revision
`9b77326d1ec7fba7e2e12e06d89adfceae0ffeb5` and its exact evidence in
`FABRIC_RELEASE_CANDIDATE_2026-08-01.md`. Issue #94 generalizes that builder,
installer-upgrade path, and Windows launcher CI to both Fabric and NeoForge.
That historical release boundary is closed: real graphical Windows launches
and independent review passed before the 1.0 publication.

Atlas-pregeneration Phases 1b and 2 are landed through #55, #56, and #59:
the loader-neutral job-model foundation
plus `RingAtlasPregenerationService`, the sole world-owned server atlas writer.
It owns cursor/ticket-backed request/retry/control/save/completion state; Fabric commands,
lifecycle hooks, and client tile streaming delegate through
`RingTerrainAtlasServer`. One in-flight chunk, the 64-task player-work guard,
200-tick checkpoints, 20-tick tile publication, format-6 bytes, partial
resume, and verified-final-save completion are retained. The shared pause-menu
map, confirmation/progress controls, cancel lifecycle, versioned payloads,
permissions, completion toast, and disconnect cleanup share that same handle
on Fabric and NeoForge. Both isolated GUI-scale-4 fixtures pass the complete
11-capture flow through start, progressive view, pause/resume, cancel/retry,
completion, and revisioned block edit; NeoForge uses
`:neoforge:runAtlasUiClient`.
The shutdown lifecycle now cancels/releases an outstanding ticket-backed request
without resolving its loaded-result supplier. A normal Fabric `stop` showed
that level unload can follow chunk-cache eviction even when the load future is
complete; consuming it there produced a false missing-chunk retry. Teardown now
checkpoints only previously captured cells and leaves the cursor selection
unadvanced for safe resume, while ordinary service ticks retain the existing
consume/capture behavior.
The follow-up ownership audit also closes the terminal-replacement leak: a
consume-side ticket-release exception leaves the terminal job attached for an
idempotent close retry, and `pregenerate` refuses to replace it until the
request is gone. Both operator command and map-control paths report that
temporary release-pending state instead of claiming a new job started.
The Fabric headless prewarm adapter is now implemented as that thin
launch/report/save/stop coordinator: a fresh safe-small run checkpointed on
SIGTERM at 1,200 durable chunks/4,800 cells, resumed to 3,328/3,328 chunks and
13,312/13,312 cells, then verified, saved, emitted atomic JSON evidence, and
stopped cleanly. Its complete-cache fast path also passed. A copied 1.21.11
legacy-open-proof fixture also upgraded only in the ignored destination,
migrated settings, rejected its incompatible legacy atlas, regenerated and
verified 2,000/2,000 chunks (8,000 cells), and left the 66-file/47,931,005-byte
source fingerprint unchanged. The later #70 gate supplies the production-scale
benchmark and recovery evidence. An exact production Fabric prewarm also
completed successfully on 2026-08-06. Fresh format-3 unattended production
prewarms subsequently passed on both loaders on 2026-08-10, as recorded above.
The independent copied ordinary-world fixture now also reaches the
pre-`ServerLevelEvents.LOAD` constructor-tail rejection seam: it writes an
atomic `REJECTED` report with unavailable identity sentinels and the original
immutable-settings reason, then preserves the original startup failure rather
than manufacturing a RingWorld identity or continuing the invalid world.

Port Phase 1 is complete: the project moved to official Mojang mappings while
remaining on Minecraft 1.21.11. All 73 tests, the destructive
safe-small harness, same-process layout switch, dedicated two-client scenario,
and production tangent/radial projection capture passed without changing the
wire protocol, saved formats, or topology behavior. At that checkpoint the only
intermediary-looking source identifier was Mojang's still-unnamed
`ServerLevel.method_31420` synthetic entity-tick lambda, documented in
`MIXIN_MAP.md`.

Phase 2 and the first integrated source/runtime gate are established. The
active branch resolves unobfuscated Minecraft 26.1.2 and Fabric API 0.155.2
under Java 25 and Gradle 9.5.1. Common and client compilation passes without
temporary shims, with 337 unit/parameterized cases passing per loader, and Loom produces
`ringworld-1.0.0+mc26.1.2.jar`.

The S2 storage migration is integrated. RingWorld settings and the server
terrain atlas now live under the Overworld's 26.1 dimension-owned data
directory. An isolated fresh-world dedicated server created that layout and
stopped cleanly. A disposable copy of an actual 1.21.11 RingWorld completed
Mojang's world upgrade, copied the legacy immutable settings byte-for-byte,
left both source and copied legacy files unchanged, rejected an invalid legacy
atlas, rebuilt it at the new authoritative path, reached `Done`, and stopped
cleanly.

The first dedicated-server launch exposed a strict runtime mixin failure that
compilation could not detect: entity tick eligibility moved into
`ServerLevel.lambda$tick$0`. The redirect now names the exact 26.1 synthetic
descriptor, retains its required injection count, and the fresh and copied
server launches pass with it.

The first real client launch similarly caught a callback-descriptor mismatch:
`GlobalSettingsUniform.update` receives extracted camera `Vec3`, not `Camera`.
After that strict fix, the 2,048×416 integrated creative harness completed
resource/shader loading, terrain generation, a 100% 13,312-cell atlas, a
2,048×416 GPU surface with 79,872 vertices, and tangent plus radial-up
complete-ring captures. Two natural seam crossings retained yaw/pitch with
zero correction packets and no non-canonical chunk-holder requests. Block,
entity, projectile, vehicle, AI, fluid, explosion, collision, late tracking,
rim, shortened-wall, and exterior-void probes passed.

The first topology run averaged 8.37 ms at the seam and 8.41 ms by the rim
with no frames above 50 ms. The full-atlas run averaged 8.41/8.37 ms and
recorded one isolated frame above 50 ms in each measured phase while
generation/upload work was active. The later safe-small 6/12/28-chunk visual
matrix retained full-loop tangent/up coverage and measured seam averages of
12.389/8.673/9.015 ms respectively. A final 28-chunk weather run added a
rainy-noon lightmap capture and waited for clear weather before the handoff
frames. Profile 4 was retained after an alternate pixel hash produced worse
salt-and-pepper transition noise. The complete review is in
`VISUAL_HANDOFF_REVIEW_2026-08-01.md`.
The current 16,384×256 default has now resumed a real copied 26.1 atlas from
32,900 to 65,536 cells without a player lap, taking about 13 minutes 22 seconds
for the remaining 32,636 cells (about 41 cells/sec), then emitted non-empty
tangent and radial-up captures with a clean projection result. This establishes
the earlier production atlas/projection checkpoint; #70 subsequently adds the
production lifecycle, multiplayer synchronization, resource, and full
safe-small/production 6/12/28 frame-pacing matrix.
The projection harness now also normalizes clear noon and a requested 2–32
chunk view distance, captures the geometry-derived live/proxy handoff between
the tangent and radial views, and logs per-view frame metrics. Its production
6/12/28 comparison exposed the old 512-segment mesh cap at six chunks. Visual
profile 5 raises that cap to 2,048, retaining the default atlas's eight-block
height spacing at 393,216 vertices/9,437,184 estimated mesh bytes. Settled
handoff averages remained 8.590/8.513/10.954 ms at 6/12/28 chunks. See
`ATLAS_VISUAL_BASELINE_2026-08-01.md` for the scoped evidence and remaining
historical issue-66 tuning context.

The same production harness exposed an intermittent post-fold simulation
failure: a `PersistentEntitySectionManager` seam load request routed through
its visibility updater could lower an already-ticking seam chunk to tracked.
Entity reads now queue directly without changing visibility. In addition, a
folded mob now shifts its active navigation path/target and stuck/timeout caches
by the exact canonical delta. Two consecutive 16,384×256 runs then passed the
projectile hit, moving-item (X≈6), navigation (X≈1.29), and both-seam-chunks-
ticking probes.

An isolated dedicated 2,048×416 server plus two 26.1 clients also completed the
full multiplayer harness on its first run. Both clients acknowledged the
layout; a natural seam crossing stayed canonical with 0.25-block maximum
packet/tick samples; mutual visibility/query/distance passed; real melee,
block interaction/update, shared boat visibility, long teleport and periodic
return, disconnect, and reconnect all passed. The server reported
`full scenario result=true` and stopped cleanly.

The current Fabric atlas-concurrency/full-matrix rerun passed from a fresh
fixture on 2026-08-06. The former NeoForge cold-start `1.333`-block remote-step
result was startup timing noise, not accepted evidence: the corrected cold
concurrent gate passed on 2026-08-08 after the bounded readiness interval,
including its `<= 1.25` remote-step limit.

The same dedicated two-client scenario has now passed on the production
16,384×256 layout after warming the saved world and resource state. The first
cold run completed the server gameplay and reconnect probes, but its aggregate
result was false solely because client B observed a 1.333-block remote step
while the server still received 0.25-block packets but accumulated a 4-block
per-tick sample under cold resource pressure. The warmed repeat reported server
`maxTickSample=0.25`, client B `maxRemoteStep=0.2498857`, no missing ticks, lag
warnings, or crashes.

A later fresh-process cold run against a copied complete atlas passed the full
scenario in about 2 minutes 51 seconds: `maxPacketStep=0.25`,
`maxTickSample=2.75`, client A/B `maxRemoteStep=0.0/1.25`, zero missing client
ticks, and true seam, combat, block, vehicle, teleport, and reconnect probes.
It also logged 3.816-second initial-connect and 39.402-second reconnect
server-behind warnings. This is a repeatable functional cold-start pass, but
not acceptable showcase frame/tick performance; cold resource-pressure
diagnosis and the remaining resource benchmark matrix stay open.

The cold trace then exposed duplicate atlas completion work: identical dirty
tiles arriving after the first complete snapshot repeatedly forced a full
4,096×256 texture build, 98,304-vertex mesh build, and cache save. Tile apply
now reports actual cell changes, ignores identical repeats, and forces an
immediate publish and save only on the real incomplete-to-complete transition.
An equally cold A/B
comparison reduced completion notices from seven to one and mesh builds from
three to two per client, total scenario time from about 171 to 69 seconds,
server `maxTickSample` from 2.75 to 0.75, and client B `maxRemoteStep` from
1.25 to 0.4167. Reconnect passed without its former warning; the run had one
remaining 2.020-second/40-tick initial-connect warning and no crash.

An instrumented third cold run also passed. After both clients were armed, the
full seam-to-reconnect scenario took about 18 seconds with server
`maxPacketStep=0.25`/`maxTickSample=0.25`, client A/B
`maxRemoteStep=0.0/0.2499238`, zero missing ticks, no overload warning, and no
crash. Each client logged one completion; client A built one complete mesh and
client B built two. One-second sampling observed lower bounds of about 591 MiB
server RSS, 871 MiB client A, 941 MiB client B, and 2.15 GiB combined. Existing
system swap stayed flat during retained samples. Full process start-to-result
was about 2 minutes 22 seconds, dominated by offline Mojang/Realms timeouts.
The second Client B build occurred one second after its first completed atlas,
while newly loaded server chunks were still reconciling the atlas, and before
Client A requested the already-updated snapshot. That is a legitimate changed
surface revision rather than the duplicate-packet churn fixed above. The
full atlas-generation/disk benchmark is recorded below.

A clean-atlas run on another disposable copy completed 65,536/65,536 cells in
13 minutes 37 seconds, averaging about 80.2 cells per second. The final gzip
atlas was 76 KiB; the copied world grew by about 169.3 MiB, chiefly from chunk
generation. Fifteen-second sampling observed a server RSS peak of about
1.06 GiB and no swap growth. The run logged no server-behind warning,
generation error, RingWorld exception, or crash, stopped cleanly, and left the
source save hashes unchanged.

An isolated copy of a complete 16,384×256 production world also passes the
single-client lifecycle gate. The server used real 26.1 `TeleportTransition`
moves through Overworld → Nether → Overworld → End → Overworld. The client
proved RingWorld state inactive in Nether and End, restored the exact geometry,
layout fingerprint, and 65,536-cell atlas on both Overworld returns, then used
Minecraft's normal save/disconnect path. Static client state cleared before an
in-process reopen, which restored the same layout and complete atlas. The final
marker was `result=true`; this is transfer-path evidence, not proof of vanilla
portal construction or linking.

Broader gameplay and compatibility gates remain. Repeatable
Fabric staging is complete, but optional convenience packages and independent
release-candidate review are still pending, so the active 26.1.2 alpha is not
yet a stable release. The frozen `mc-1.21.11-final` tag remains the rollback
baseline.

New RingWorlds now persist a mandatory structure policy and replace vanilla's
unbounded stronghold rings with one deterministic canonical start centred
across the finite width. If vanilla's completed terrain-adjusted graph reaches
past a boundary, every piece receives one minimal X/Z translation before the
start is built. A dedicated Java 25 gate passed eight production seeds,
including a regression whose unadjusted bounds reached Z=-132, plus 2,048×416
  and 15,552×4,096 layouts: each complete piece graph and
portal room stayed inside the band, generated all 12 End portal frames, matched vanilla's
activation pattern, and returned the nearest periodic locator from the
opposite chart. A saved-world rerun passed with the same start and portal.
Older RingWorlds with no structure-policy state retain their old placement;
the guarantee is not silently retrofitted.

The “Implemented” sections below describe the reviewed 26.1.2 architecture on
the active public integration line. Historical 1.21.11 evidence remains in its
frozen baseline document; the known limitations below still constrain release
and compatibility claims.

## Implemented

### Topology and storage

- One canonical Overworld X plane.
- Periodic block/chunk coordinate helpers.
- Periodic chunk holder, ticket, watch, and simulation propagation, plus a
  configured-distance nearest-player eligibility fallback for transient or
  stale seam-side entity graph state.
- Continuous client presentation charts.
- Natural player and vehicle seam folding without a corrective teleport.
- Explicit same-world teleports project their canonical X target into the
  nearest client presentation image, avoiding seam-adjacent chart eviction.
- Canonical entity indexing, save/load, and tick eligibility; seam entity
  reads queue without mutating an already-ticking chunk's visibility, and
  folded mobs shift active paths and raw-coordinate navigation caches.
- Periodic entity queries, distances, tracking, reach, projectiles,
  explosions, AI targets, and proximity effects.
- Canonical block/fluid scheduled ticks.

### World generation

- Cylindrical coordinate sampling for terrain and vanilla structure
  base-height/base-column queries, with query X canonicalized before vanilla
  cell/cache interpolation.
- Vanilla sampler/cache/aquifer identity preserved.
- Canonical seam-crossing worldgen writes and neighbour aliases.
- Finite Z band with exterior void.
- Five-block breakable cobblestone/mossy-cobblestone rims.
- Shortened wall height and gradual migration from legacy stone-brick rims.
- Boundary section meshing that tolerates only intentionally absent exterior
  neighbours, keeping genuine rim blocks visible as well as collidable.
- Spawn constrained to the safe width interior.

### Rendering

- Curved terrain shader using a synchronized format-3 layout.
- Named extended Globals UBO fields for circumference, width, surface
  reference, saved wall height, wall/cloud elevations, physical centre, view
  distance, handoff, detail, and haze.
- Sub-block-stable camera reconstruction.
- Curved CPU section culling for upward views.
- RingWorld-only non-occluding section-graph traversal so flat mountains cannot
  suppress chunks that curvature bends back into view; curved frustum and
  render-distance culling remain active.
- Curved/tangent-aligned entity models.
- Exact-cylinder local cloud deck derived from saved wall top plus eight
  blocks.
- Fixed ring-centred dimming Minecraft sun at about 0.9 degrees apparent
  diameter and hidden moon.
- Smooth global noon/dawn/dusk/midnight sun intensity and colour tone driven
  by vanilla time; the former shadow-panel mesh is removed.
- Persistent periodic format-6 atlas of exposed top-face height and
  texture-corrected biome colour sampled from the actual highest surface block.
  Dedicated servers fall back to the sampled block's map colour when their
  unloaded client-only grass/foliage colormaps return zero.
- Relief-shaded, mipmapped progressive/complete-ring GPU texture and bounded
  mesh at normal real-chunk render distance. Partial atlases expose only known
  cells through alpha and use one reference-height mesh. Completion performs
  one upgrade to the expanded terrain-height surface; later colour-only
  revisions reuse that mesh and changed height fingerprints rebuild it.
- A compact top-left `Ring Atlas Generating: X%` indicator reports whole-percent
  client Atlas coverage while generation is incomplete and disappears at 100%.
- Live RGB lightmap exposure for the distant surface using the
  full-skylight/no-block-light texel, matching client day/night, weather,
  gamma, lightning, darkness, and night-vision state.
- A broad live/LOD alpha cross-fade with reduced, terrain-hugging fog colour:
  proxy opacity grows from 68% to 98% of effective view distance while a
  RingWorld-only terrain fragment dither spans 78% to 102%. This makes the
  proxy effectively opaque before disappearing live terrain can expose sky.
- Canonical F3 coordinates and atlas state.
- Dimension-aware `RingRenderProfile` with half-circumference clamping and
  bounded texture/mesh resources.

### Multiplayer and tooling

- Required full-layout server/client settings handshake.
- Independently verified layout fingerprint and format mismatch rejection.
- Dedicated test clients wait for the initial resource reload before joining,
  preventing an early-connect particle-sprite initialization race.
- Tiled atlas transfer and world-hash client cache.
- Checked atlas allocation, typed tile coordinates, long pregeneration
  counters, transfer estimates, progress rate, ETA, and a loader-neutral
  pregeneration model/cursor.
- Gamemaster-level atlas status/start/pause/resume commands. Start and resume
  can continue a durable partial atlas from process-local `IDLE`.
- Local destructive smoke harness.
- Same-JVM saved-layout switch harness that verifies disconnect clearing and
  second-world geometry/atlas replacement.
- Isolated copied-production lifecycle harness that drives 26.1
  `TeleportTransition` transfers through Nether, Overworld, End, and Overworld, then
  saves/disconnects/reopens the copy while independently checking inactive
  non-Overworld rendering and exact geometry/fingerprint/complete-atlas
  restoration. The complete 16,384×256 runtime gate passes.
- The automated upward live/LOD capture waits for the current world's atlas to
  be complete before reducing view distance for seam traversal. With
  pregeneration explicitly disabled and no cache, it records a skipped LOD
  capture after 600 ticks and continues topology/rim coverage.
- First-seam gameplay fixtures remain resident for a complete 240-tick
  observation window before the accelerated circuit starts. The server
  identifies that circuit from high-X then low-X canonical poses rather than
  counting repeated packets from one presentation chart.
- Dedicated two-client seam/combat/block/vehicle/teleport/reconnect harness.
- The reusable multiplayer fixture removes stale automated boats and their
  passengers, waits for both clients to acquire the new vehicle identities,
  detects canonical folds across overloaded server ticks, and treats periodic
  teleport targets as equivalent client-chart images. Its vehicle gate rejects
  a missing/replaced root or passenger, lost mount, rotation jump, excess
  motion, or non-canonical post-fold ownership.
- Gradle wrapper, parameterized pure dimension tests, server deployment templates, and public
  MPL-2.0 GitHub source repository.
- Latest profile-3 safe-small runtime (2,048×416 at 28-chunk capture) passed two
  natural wraps with zero camera delta/correction packets and passed block,
  entity, projectile, vehicle, AI, fluid, explosion, collision, rim, and
  exterior-void probes. The harness measured 15.99 ms average across the first
  seam interval and 11.84 ms beside the rim.
- The derived-pitch 6-chunk case also passed the complete harness, with the
  capture aimed at the actual 96-block handoff and 15.9/13.3 ms first-seam/rim
  averages.
- Derived-pitch 12- and 28-chunk comparison captures are complete. A
  2,048×256 no-cache width stress passed both wraps, gameplay, AI, rim, and
  exterior checks; its LOD capture was deliberately skipped because neither
  pregeneration nor a complete cache was available.
- A reused-world dedicated 2,048×416 server plus two real clients passed the
  complete seam, combat, block, vehicle, long-teleport, return-chart, and
  reconnect matrix. Its largest sampled movement packet was 0.25 blocks.
- The repository subsequently adopted MPL-2.0 for versions released under the
  new licence. Binary releases must identify their exact corresponding source
  revision and pass the `MPL-2.0` package metadata gate.
- A fresh dedicated-server atlas exposed a server-only colour failure:
  10,192 of 13,312 cells were black because common-code grass/foliage lookup
  arrays remain zero-filled without a client resource reload. Format 5
  invalidates that cache and uses block map colour only when the biome lookup
  is unavailable; ordinary integrated-server biome tint remains unchanged.
  The rebuilt atlas contains zero black cells (median luminance 72.7 instead
  of 0).
- Layout wire generation 3 uses versioned `settings_v3` and
  `settings_ack_v3` channels. Stale earlier clients are rejected with a
  package-update message instead of crashing Netty after leaving ten unread
  settings bytes. Shareable launchers now refresh managed mod files in an
  existing Prism instance on every start without touching accounts, saves, or
  user settings. The full isolated two-client harness passed after this change:
  both clients acknowledged the new geometry channel, crossed and interacted
  through the seam, completed combat and vehicle checks, and passed the
  deliberate disconnect/reconnect sequence.
- Visual profile 4 prevents Minecraft's chunk-derived far plane from clipping
  the complete-ring proxy. A synthetic complete 15,552×4,096 atlas produced a
  4,096×1,024 texture and 393,216-vertex mesh; separate tangent/along-ring and
  radial-up framebuffer captures both showed continuous proxy coverage. The
  runtime diagnostic measured a 1,024-block level far plane versus about
  4,893 blocks through the opposite reference surface and 5,305 blocks to the
  far width edge at the test camera. Real trees and terrain remained visibly
  in front of the sky-stage proxy in both captures.
- The visual-profile-4 client packages were published after credential and
  archive-integrity checks. The withdrawn MIT-labelled SHA-256 values were
  `92026aa66ff062ed44e0074ec4502f25b702b4b43c655421e3bcefeeac04ff29`
  for the universal package and
  `380b1cfc2fc112dc16487f773d5ddf543d8eb09b003ec9fd911ab7a1dc66adc5`
  for the Windows package. They remain rollback evidence only and must not be
  restored to a public path. The public server was not restarted for that
  historical visual-only update.

## Deliberate design decisions

- Gravity remains vanilla in intrinsic coordinates.
- Nether and End remain vanilla.
- The server stores no duplicate circumference laps.
- Distant visibility is a texture/LOD problem, not a forced whole-ring chunk
  render-distance problem.
- Walls are finite-height, textured, thick, and breakable; players may leave
  the ring.
- Current day/night is global. Position-aware darkness will not be faked
  visually without matching server simulation.

## Known limitations and risks

### Distant surface

- The active far ring appears at zero cells as a deliberately subdued,
  world-specific opaque fallback and converges toward received Atlas data.
- Disconnect and settings-reception paths clear the previous world's GPU mesh
  and texture. The renderer independently rejects absent, corrupt, and
  wrong-world Atlases while accepting a current zero-cell Atlas as the
  fallback identity, preventing stale terrain during a new world.
- Source resolution is one height/surface-colour sample per eight blocks.
- The client expands colour data but cannot recreate blocks, transparent
  layers, trees, buildings, mobs, or weather volumes.
- Texture-luminance-corrected biome tint, relief shading, periodic mip
  filtering, live full-skylight exposure, partial terrain visibility through
  the transition, and local proxy exclusion are implemented. Their visual
  tuning still needs captured comparisons across weather, time, and water.
- The lightmap match represents exposed terrain globally. Dynamic local block
  lights are not encoded in the static atlas.
- The atlas is refreshed when chunks are captured/loaded, not immediately on
  every block edit. Player construction can remain stale until recapture.
- The LOD retains limited terrain contrast at the nominal chunk edge to avoid a
  visible flat-colour belt. Translucent live surfaces still require dedicated
  visual regression and cannot be reproduced faithfully by the opaque atlas.

### Rendering maintenance

- The extended Globals UBO and custom shader include are version-sensitive.
- `RingRenderProfile` visual-policy version 5 owns live/proxy/detail distances,
  near/far reveal, haze endpoints/exponent, local cloud fade, and the higher
  production mesh cap. Safe-small and 16,384×256 6/12/28 captures plus
  production dusk/night/rain comparisons pass. Broader adaptive fidelity and
  custom-size resource tiers remain issue #69.
- Custom shaders replace vanilla assets and can conflict with renderer/shader
  mods.
- Boundary rendering redirects a private
  `SectionRenderDispatcher.RenderSection` readiness check and must be
  re-audited on Minecraft or mappings upgrades.

### Worldgen

- The mandatory stronghold is deterministic and validated across several
  seeds and supported layouts. Its post-generation fit uses the complete
  terrain-adjusted bounds and is gated by immutable saved policy. Other
  structures, carvers, and features still lack broad seam coverage.
- Ocean monuments are now an opt-in new-world guarantee. Candidate selection
  is bounded, canonical, registry-bound, and validated with the same periodic
  climate sampler as generated biomes; invalid or exhausted searches persist
  a typed unsatisfied result. Forced placement and locate/reference handling
  are restricted to the saved candidate, and fresh/reload dedicated-server
  gates pass. Other random-spread scarce structures remain unsupported; their
  source/registry audit and required state-design gate are in
  [`SCARCE_STRUCTURE_GUARANTEE_AUDIT.md`](SCARCE_STRUCTURE_GUARANTEE_AUDIT.md).
- Vanilla Overworld structure height queries canonicalize X before their
  internal cell/cache interpolation and use the same cylindrical sampler as
  generated terrain. The stronghold gate compares periodic aliases at X=0 and
  two remote positions, then compares those remote canonical queries against a
  noise-complete `WORLD_SURFACE_WG` terrain height. X=0 is intentionally not a
  terrain-height comparison because spawn preparation can advance that chunk
  beyond noise generation. The runtime gate rejects either selected remote
  chunk if it was already fully loaded. This prevents raw-alias or flat-noise
  Y mismatches that could float villages and other heightmap-projected
  structures. It fixes height sampling only; it does not certify every
  structure's seam placement, footprint, loot, mob, or reload behavior.
- Periodic density noise does not guarantee every vanilla structure placement
  seed or third-party generator treats X=0/C as adjacent.
- The new 16,384×256 production default requires 16,384 canonical chunks and
  65,536 atlas cells. One copied-world atlas resume completed its remaining
  32,636 cells in about 13 minutes 22 seconds. A clean-atlas copied-world run
  completed all cells in 13 minutes 37 seconds at about 80.2 cells per second,
  with a 76 KiB compressed atlas, about 169.3 MiB copied-world growth, and a
  1.06 GiB sampled server RSS peak. Multi-size visual and repeated
  frame-pacing review remain open.
- Existing Overworld region files without RingWorld saved settings are
  explicitly rejected; no conversion tool exists.
- Decorative wall-height changes can produce mixed old/new boundary chunks.

### Gameplay coverage

- Representative arrows, a boat, one navigator, water, explosions, effects,
  blocks, block entities, redstone, melee, beds, death/respawn, physical
  Nether/End portal transfers, and map/compass nearest-image rules are tested.
  Arbitrary redstone/fluid networks, additional projectile/vehicle variants,
  full map-mode playthroughs, command families, and modded systems are not.
- Explicit teleport and reconnect have harness coverage. The isolated
  copied-production lifecycle runner covers Nether → Overworld → End →
  Overworld, normal save/disconnect, and same-process reopen at 16,384×256.
  The safe-small two-client runner adds actual `PortalForcer`-created Nether
  blocks/linking/return, positive and negative multi-lap X lookup, safe
  destinations for targets beyond both Z rims, and End portal block travel. Normal stand-in-portal
  delays and full map-mode playthroughs remain manual coverage. The bed gate
  disconnects while asleep, requires vanilla's rejoined-awake state beside the
  canonical seam bed with valid Overworld geometry and loaded-bed proximity,
  then sleeps again before its
  damage-wake and destruction probes. A dedicated two-phase Fabric/NeoForge fixture now covers
  raid creation, saved POIs/raid/raider reload, natural raider seam folding,
  victory, and Hero rewards.
- Minecraft 26.1.2's gameplay positional packets have an explicit audit, but
  the 26.1 locator bar, operator debug packets, future Minecraft packets, and
  opaque third-party payloads are not globally caught.

### Protocol and compatibility

- The handshake uses an exact settings format plus the complete required
  channel generations. It deliberately does not negotiate partial features.
- Vanilla clients are intentionally unsupported.
- Compatibility contract/API version 1 defines the tested vanilla-renderer
  Fabric baseline, read-only coordinate/pose helpers, and eleven explicit
  unsupported mod IDs. The Fabric probe logs a clear error for a detected
  match; unlisted combinations remain untested.

### Configuration/user experience

- The Create World screen now uses one loader-neutral configuration model for
  both Fabric and NeoForge. It offers **Small** (2,048×128×160), **Medium**
  (16,384×256×160), and **Large** (32,768×512×160) presets plus concise live
  validation and equations for lap time, physical geometry, chunk/playable
  area, atlas, height, and measured generation/disk cost.
- Its entry is a third member of Minecraft 26.1's managed Create/Cancel footer
  row. The centered bordered editor remains complete at both 480×270 and
  320×270 logical scale-4 views.
- The layout editor relies on Minecraft's framework-managed background pass;
  it does not request a second menu blur while rendered over Create World.
- Field parsing and basic structural validation aggregate applicable field
  messages; once minimum/alignment checks pass, cross-field report errors are
  shown together. The apply action stays disabled until all errors are
  resolved. New-world admission is 2,048 blocks around by 128 across, while
  legacy 1,024-circumference settings remain readable.
- Applying a valid creation layout requires a second explicit confirmation
  that repeats the immutable dimensions, wall height, and monument choice.
- The editor explains that values affect only the next new Overworld. First
  Overworld load saves the layout and monument result permanently; existing
  worlds are never changed. Enabling the monument option asks that new world
  to search for one valid ocean-monument location and records either its
  result or unavailability.
- Dedicated servers use equivalent first-world bootstrap properties.
- The pure UI model is shared by Fabric and NeoForge. The dual-loader,
  menu-only GUI-scale creation-UI gate passes on both loaders: it opens no
  world, captures all thirteen scale-1-to-4 and narrow states in a 1,920-pixel-wide
  framebuffer at least 1,080 pixels tall, validates the final 4,096×640×192
  monument request in the bootstrap config, and rejects any created save.
- There is no supported in-place resize or conversion tool.
- Width 128 cannot satisfy the optional monument margins, so Small disables
  that toggle. Its guaranteed stronghold fits the portal room and terrain
  envelope within the band; optional graph bounds may extend into suppressed
  exterior space and players may need to mine to the room, so the creation
  editor visibly labels Small experimental. The exact 2,048×128
  seed `ringworld-small-128` passes portal frames/activation, periodic locate,
  and Eye behavior on both Fabric and NeoForge.
- New-world dimension validation now checks the full-height radial clearance,
  finite-rim interior, wall/build bounds, axis limits, and atlas allocation
  budget before settings are created. The active 2,048-by-416 development
  preset passes with about 70 radial blocks above Y=320; the retired
  1,600-block circumference is a required validation failure.
- The preview scales the measured production generation into a reference time
  and generated-world size, and warns above 30 minutes or 512 MiB. These are
  planning figures rather than performance guarantees; atlas transport bounds
  are calculated exactly.
- Saved format-3 settings win before generation; formats 1 and 2 migrate
  explicitly while retaining the legacy terrain-noise mapping.
- The full immutable layout is sent to clients and used for walls, clouds,
  shaders, and atlas identity.
- The 26.1 F3 position group reports presentation and canonical Ring
  coordinates, canonical block/chunk/region positions, loop index, and atlas
  state without feeding any debug value back into storage or chart state.
- Sleeping positions remain canonical on the server and in saves. The client
  maps only its own replicated bed position to the nearest presentation image
  before vanilla applies the sleeping pose, so sleeping, waking, bed
  orientation, and bed lookup cannot snap a seam-side player to the raw
  canonical copy or into the void. The automated integrated harness exercises
  that client getter across the seam, and the dedicated two-client harness now
  exercises real night sleep, canonical server state, damage interruption,
  destruction, and death/respawn. Rejoining while still asleep remains in the
  manual multiplayer matrix.
- Block entities and interaction overlays now use the same exact cylindrical
  anchor and tangent frame as ordinary entity models. An isolated safe-small
  client capture shows a chest, lectern book, ender chest, copper golem, item,
  and boat seated on the curved live-terrain strip from both X=0.5 and X=32.5,
  with strict `LevelRenderer` mixin application and no distance-dependent
  vertical slide.
- The source-audited variable registry and correction plan are maintained in
  `DIMENSION_SCALING_PLAN.md`.

## Removed/rejected approaches

### Seam teleport

An early implementation snapped the player from one edge to the other. It
could not keep other players, entities, chunks, and interactions continuous.
The current canonical-server/presentation-client architecture replaced it.

### Multiple stored laps

Keeping server entities thousands of blocks apart in different logical laps
broke distance, combat, tracking, and save semantics. The server now owns one
canonical plane only.

### Forced 100-chunk render distance

Rendering the complete development circumference as real chunks looked good
but consumed unacceptable CPU, memory, chunk meshing, and GPU resources. The
normal-distance real terrain plus atlas-backed GPU ring replaced it.

### Radial physics rewrite

Literal vector gravity would require pervasive movement, fluid, AI, projectile,
vehicle, and mod compatibility work. Intrinsic coordinates already make
vanilla `-Y` the correct local outward gravity after visual embedding.

### Shadow-panel sky

The first fixed-sun design rendered twenty moving slabs around the star. Their
scale and silhouette dominated the sky and did not fit the desired visual.
The active cycle keeps the sun fixed and uses a continuous global
dimming/colour shift instead. The removed implementation remains documented in
`SUN_RENDERING_SNAPSHOT_2026-07-26.md`.

### Artificial containment-array sun

A custom cyan, amber, and white 32×32 machine-like sun was tested after the
shadow panels. At the intended small angular size it looked busy and visually
odd, so the active renderer returned to Minecraft's original sun sprite while
retaining the fixed pose and continuous dimming/colour cycle.

## Recommended next work

The owner-approved order is recorded in
`DUAL_LOADER_STANDALONE_PLAN.md` and GitHub epic #4.

1. **NeoForge standalone packaging (#34, #94)**
   - runtime parity is complete through #93;
   - freeze matching Fabric/NeoForge candidates and pass clean macOS/Windows
     package gates.
2. **Standalone gameplay and visual polish (#95–#96)**
   - fix remaining first-party playability defects;
   - sign off the production ring, live/atlas handoff, sky, weather, curved
     objects, supported sizes, and performance budgets on both loaders.
   - use the exact dual-loader manual checklist and candidate evidence record
     in `TESTING.md`; the strengthened Fabric and NeoForge two-client and raid
     fixtures plus the direct map/compass gate pass, while structures,
     ordinary play sampling, block entities, and final-candidate
     atlas controls still need inspection. Automated seam weather/lightning
     and ordinary portal-delay coverage now pass on both loaders.
3. **Exact-candidate release (#12, #13, #97)**
   - complete remaining Windows evidence and independent review;
   - freeze hashes, stage loader-specific Modrinth versions, and keep a final
     owner go/no-go before publication.
4. **Third-party compatibility later (#98)**
   - return to modpack compatibility only after standalone owner sign-off;
   - start with Create without changing shared topology or file/wire formats.

## Evidence required before calling the mod stable and broadly compatible

- Longer manual map playthroughs beyond the passing automated bed reconnect,
  death, map, and portal-delay gates.
- Manual gamma, night-vision, and close cloud-height visual checks; automated
  seam thunder/lightning passes but is not final visual sign-off.
- Optional package fresh/upgrade launch checks and an independent final review.
- Real compatibility testing beyond the published baseline and explicit
  unsupported list.
