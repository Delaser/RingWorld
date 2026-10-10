# RingWorld documentation

- [Large-server multiplayer load trial](SERVER_MULTIPLAYER_LOAD_2026_10_10.md):
  three real clients, completion-status scan fix, measurements and remaining stalls.
- [Server Atlas checkpoint persistence](SERVER_ATLAS_CHECKPOINTS.md): bounded
  snapshot preparation, worker saves, recovery and Large-server measurements.

- [Bounded Atlas concurrency trial (#253)](ATLAS_CONCURRENCY_253.md): shared
  request slots, lifecycle safety, JVM trial control and measured evidence.


These files describe the implementation currently in the repository. They are
maintained alongside code and should be updated whenever an invariant, packet
path, mixin, configuration field, or operational procedure changes.

Start here:

- [JAR export](JAR_EXPORT.md): current six-file qualification, staging and host
  publication route; [export audit](JAR_EXPORT_AUDIT_2026-10-09.md) records cleanup
  and remaining manual checks.

- [Floating-build Atlas sampling (#257)](FLOATING_ATLAS_257.md): conservative
  server capture/live updates, cache invalidation and native checks.

- [Nearby block normalisation (#256)](NEARBY_DISTORTION_TRIAL.md): client controls,
  fixed-height projection, integration checks and remaining limitations.

- [Compressed wall previews (#260)](WALL_PREVIEW_COMPRESSION_260.md): shared PNG
  packaging, regeneration, decoder checks and six-JAR size measurements.

- [Dynamic cloud height and curved faces (#268)](CLOUD_ALTITUDE_268.md):
  shared CPU/GPU saved-wall altitude and Fancy cap retention.

- [Altitude-aware terrain handoff (#266)](ATLAS_ALTITUDE_HANDOFF_266.md):
  high-camera coverage gap, coordinated live/Atlas distance and native checks.

- [Atlas wall closure (#264)](ATLAS_WALL_CLOSURE_264.md): terrain edge faces,
  closed decayed crests, saved-height anchoring and native comparisons.

- [Atlas wall gap diagnosis](ATLAS_WALL_GAPS_2026_10_09.md): missing terrain
  edge faces and decayed-wall caps, with a controlled geometry/material probe.

- [Outside building (#255)](OUTSIDE_BUILDING_255.md): default-on saved policy,
  server commands, World-page control, exterior delivery and native samples.

- [Overworld horizon removal (#259)](HORIZON_259.md): scoped atmospheric change,
  matched captures and cross-version development checks.
- [Exterior entity visibility #254](OFF_RING_ENTITY_VISIBILITY_254.md): bounded
  tracking over the void, client render selection and targeted regression.

- [1.3 supported-version parity and release hold](RELEASE_1_3_PARITY.md):
  required 26.x/Fabric/NeoForge behaviour, port status and remaining checks.

- [Implemented RingWorld UI captures](media/ring-generation-ui-implemented/index.html):
  actual 26.3 Fabric creation and in-world screens for the approved 1.3 redesign.
- [Ring generation UI proposal](design/ring-generation-ui-proposal/index.html):
  the reviewed design report and interactive mockup that preceded implementation.
- [`TRANSITION_IMPROVEMENT_PLAN_1_3.md`](TRANSITION_IMPROVEMENT_PLAN_1_3.md): staged
  material, water, geometry and blending improvements from the accepted 1.3 checkpoint.

- [`../AGENTS.md`](../AGENTS.md): concise operating rules for future coding
  agents.
- [`../LICENSE`](../LICENSE): authoritative Mozilla Public License 2.0 terms.
- [`LICENSING.md`](LICENSING.md): practical source, binary, modpack, fork, and
  historical-version licensing guidance.
- [`../CONTRIBUTING.md`](../CONTRIBUTING.md): contribution requirements and
  inbound MPL-2.0 terms.
- [`../SECURITY.md`](../SECURITY.md): private vulnerability-reporting and
  supported-version policy.
- [`ARCHITECTURE.md`](ARCHITECTURE.md): coordinate model and end-to-end system
  design.
- [`OPTIONAL_WORLD_GENERATION.md`](OPTIONAL_WORLD_GENERATION.md): saved Atlas
  fidelity, Archipelago, continuous-river and increased-structure options,
  including their shared implementation and repeatable test controls.
- [`DIMENSION_SCALING_PLAN.md`](DIMENSION_SCALING_PLAN.md): source-audited
  registry of dimension-sensitive variables, safety limits, and the staged
  custom-size implementation plan.
- [`ATLAS_PREGENERATION_PLAN.md`](ATLAS_PREGENERATION_PLAN.md): one-click
  complete-map UI and extraction plan for a resumable atlas-generation service.
- [`MINECRAFT_26_1_PORT_PLAN.md`](MINECRAFT_26_1_PORT_PLAN.md): gated
  Minecraft 26.1.2 port plan, primary/secondary agent ownership, integration
  order, validation gates, and deployment criteria.
- [`MINECRAFT_VERSION_SUPPORT_PLAN.md`](MINECRAFT_VERSION_SUPPORT_PLAN.md):
  approved Minecraft 26.1 compatibility floor, initial patch matrix, rolling
  stable-version intake, automation tiers, evidence contract, and complete
  Modrinth/CurseForge delivery plan.
- [`VERSION_QUALIFICATION.md`](VERSION_QUALIFICATION.md): repeatable manifest-driven
  version intake, commands, and the qualified 26.2 checkpoint.
- [`RELEASE_1_1_OWNER_HANDOFF.md`](RELEASE_1_1_OWNER_HANDOFF.md): exact staged
  candidates, packaged-client checks, and owner approval.
- [`RELEASE_1_1_PUBLICATION_2026-08-27.md`](RELEASE_1_1_PUBLICATION_2026-08-27.md):
  1.1 host IDs, exact sources, downloaded hashes and remaining host review limits.
- [`VERSION_FORWARD_UPGRADE.md`](VERSION_FORWARD_UPGRADE.md): copied-world
  qualification across independently pinned stable version lines.
- [`DUAL_LOADER_STANDALONE_PLAN.md`](DUAL_LOADER_STANDALONE_PLAN.md): approved
  NeoForge-first implementation, standalone gameplay/visual polish, release,
  and deferred third-party compatibility sequence.
- [`UNIFIED_JAR_FEASIBILITY.md`](UNIFIED_JAR_FEASIBILITY.md): current
  entry-level artifact audit, risks, deterministic merge design, and acceptance
  path for an optional single Fabric/NeoForge download.
- [`PORTING_26_1_AUDIT.md`](PORTING_26_1_AUDIT.md): official-source audit of
  the 26.1.2 toolchain, Fabric API changes, and all 35 candidate mixin targets.
- [`MINECRAFT_1_21_11_FINAL_BASELINE.md`](MINECRAFT_1_21_11_FINAL_BASELINE.md):
  immutable pre-port tag, test results, artifact and screenshot hashes, and
  protected rollback inventory.
- [`MINECRAFT_26_1_COMPILER_BASELINE.md`](MINECRAFT_26_1_COMPILER_BASELINE.md):
  historical Java 25/26.1.2 95-error inventory and the subsequent green
  build/dedicated-server checkpoint.
- [`NETWORK_PROTOCOL.md`](NETWORK_PROTOCOL.md): login handshake, atlas
  transport, and canonical/presentation packet mapping.
- [`COMPATIBILITY.md`](COMPATIBILITY.md): versioned read-only API, supported
  baseline, known unsupported mods/shaders, and loader boundary.
- [`RENDERING.md`](RENDERING.md): terrain curvature, culling, distant texture,
  fog, clouds, and the small fixed tone-shifting sun.
- [`VISUAL_HANDOFF_REVIEW_2026-08-01.md`](VISUAL_HANDOFF_REVIEW_2026-08-01.md):
  approved 6/12/28-chunk and production-size live/proxy comparison evidence.
- [`VISUAL_POLISH_CHECKPOINT_2026-08-02.md`](VISUAL_POLISH_CHECKPOINT_2026-08-02.md):
  exact post-gameplay dual-loader safe-small/production terrain, seam/rim,
  and curved-object refresh.
- [`ATLAS_VISUAL_BASELINE_2026-08-01.md`](ATLAS_VISUAL_BASELINE_2026-08-01.md):
  production 6/12/28-chunk handoff automation, profile-4 baseline, and the
  profile-5 mesh-fidelity comparison.
- [`PROGRESSIVE_ATLAS_RENDERING_2026-08-01.md`](PROGRESSIVE_ATLAS_RENDERING_2026-08-01.md):
  partial-atlas transparency, bounded GPU update policy, and the fresh-client
  partial-to-complete runtime gate.
- [`ATLAS_REVISIONED_UPDATES_2026-08-01.md`](ATLAS_REVISIONED_UPDATES_2026-08-01.md):
  bounded terrain invalidation, durable revisions, ordered tile commits, and
  exact reconnect-cache reuse.
- [`ATLAS_FIDELITY_BENCHMARK_2026-08-01.md`](ATLAS_FIDELITY_BENCHMARK_2026-08-01.md):
  production step 8/4/2/1 resource comparison and the retained fixed-profile
  decision.
- [`ATLAS_RELEASE_GATE_2026-08-01.md`](ATLAS_RELEASE_GATE_2026-08-01.md):
  complete production generation/recovery, GUI, lifecycle, multiplayer,
  visual, resource, hash, and frame-pacing evidence.
- [`FABRIC_RELEASE_CANDIDATE_2026-08-01.md`](FABRIC_RELEASE_CANDIDATE_2026-08-01.md):
  frozen Fabric candidate hashes, package/server/macOS runtime evidence, and
  the remaining graphical platform gates.
- [`DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md`](DUAL_LOADER_RELEASE_CANDIDATE_2026-08-08.md):
  current Fabric/NeoForge source and artifact hashes, machine validation,
  optional packages, and remaining human release gates.
- [`SEAM_GAMEPLAY_REGRESSION_2026-08-01.md`](SEAM_GAMEPLAY_REGRESSION_2026-08-01.md):
  expanded dedicated-client stateful-block, bed/death, and physical-portal
  seam evidence plus the remaining manual/unsupported matrix.
- [`WORLDGEN_STRUCTURE_MATRIX_2026-08-01.md`](WORLDGEN_STRUCTURE_MATRIX_2026-08-01.md):
  multi-seed biome, carver, feature, loot, structure-seam, saved-policy, and
  reload evidence.
- [`PROTOCOL_HARDENING_2026-08-01.md`](PROTOCOL_HARDENING_2026-08-01.md):
  acknowledgement deadline, exact-version capability policy, positional
  packet audit, runtime evidence, and explicit unsupported boundary.
- [`SUN_RENDERING_SNAPSHOT_2026-07-26.md`](SUN_RENDERING_SNAPSHOT_2026-07-26.md):
  frozen rollback description of the removed ring-centred sun and panel array.
- [`MIXIN_MAP.md`](MIXIN_MAP.md): ownership and risk map for every mixin.
- [`SCARCE_STRUCTURE_GUARANTEE_AUDIT.md`](SCARCE_STRUCTURE_GUARANTEE_AUDIT.md):
  Minecraft 26.1.2 monument guarantee design/evidence and the approval boundary
  for any additional scarce finite-ring structure.
- [`TESTING.md`](TESTING.md): unit, local smoke, visual, copied-world
  lifecycle, and two-client multiplayer procedures.
- [`OPERATIONS.md`](OPERATIONS.md): configuration, persistence, build,
  installation, packaging, and deployment.
- [`MODRINTH_RELEASE.md`](MODRINTH_RELEASE.md): fail-closed dual-loader local
  staging, source provenance, and manual release gates.
- [`CURSEFORGE_RELEASE.md`](CURSEFORGE_RELEASE.md): official CurseForge
  project metadata, current alpha uploads, loader relations, and manual upload
  checklist.
- [`OWNER_ALPHA4_SIGNOFF_2026-08-10.md`](OWNER_ALPHA4_SIGNOFF_2026-08-10.md):
  exact prepared alpha-4 gameplay, visual, seam-regression, Windows, and
  go/no-go checklist.
- [`OWNER_RELEASE_SIGNOFF_2026-08-09.md`](OWNER_RELEASE_SIGNOFF_2026-08-09.md):
  historical exact alpha-3 owner checklist.
- [`CURRENT_STATE.md`](CURRENT_STATE.md): implemented features, deliberate
  boundaries, known defects, and recommended next work.
- [`PROJECT_AUDIT_2026-08-31.md`](PROJECT_AUDIT_2026-08-31.md): repository
  hygiene, correctness repairs, validation, disk cleanup, and retained-risk
  record for the optional-feature branch.

- [`CODE_REVIEW_DEBLOAT_2026-10-09.md`](CODE_REVIEW_DEBLOAT_2026-10-09.md):
  focused correctness review, repaired 26.3 saved-wall inputs, and shared
  seed-preview/override cleanup.

The top-level [`README.md`](../README.md) remains the user-facing overview.
When it conflicts with these files, verify the source and correct both.
