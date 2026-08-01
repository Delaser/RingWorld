# RingWorld documentation

These files describe the implementation currently in the repository. They are
maintained alongside code and should be updated whenever an invariant, packet
path, mixin, configuration field, or operational procedure changes.

Start here:

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
- [`DIMENSION_SCALING_PLAN.md`](DIMENSION_SCALING_PLAN.md): source-audited
  registry of dimension-sensitive variables, safety limits, and the staged
  custom-size implementation plan.
- [`ATLAS_PREGENERATION_PLAN.md`](ATLAS_PREGENERATION_PLAN.md): one-click
  complete-map UI and extraction plan for a resumable atlas-generation service.
- [`MINECRAFT_26_1_PORT_PLAN.md`](MINECRAFT_26_1_PORT_PLAN.md): gated
  Minecraft 26.1.2 port plan, primary/secondary agent ownership, integration
  order, validation gates, and deployment criteria.
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
- [`MODRINTH_RELEASE.md`](MODRINTH_RELEASE.md): fail-closed local staging,
  source provenance, and manual-release gates for the Fabric alpha.
- [`CURRENT_STATE.md`](CURRENT_STATE.md): implemented features, deliberate
  boundaries, known defects, and recommended next work.

The top-level [`README.md`](../README.md) remains the user-facing overview.
When it conflicts with these files, verify the source and correct both.
