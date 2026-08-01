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
- [`RENDERING.md`](RENDERING.md): terrain curvature, culling, distant texture,
  fog, clouds, and the small fixed tone-shifting sun.
- [`SUN_RENDERING_SNAPSHOT_2026-07-26.md`](SUN_RENDERING_SNAPSHOT_2026-07-26.md):
  frozen rollback description of the removed ring-centred sun and panel array.
- [`MIXIN_MAP.md`](MIXIN_MAP.md): ownership and risk map for every mixin.
- [`TESTING.md`](TESTING.md): unit, local smoke, visual, copied-world
  lifecycle, and two-client multiplayer procedures.
- [`OPERATIONS.md`](OPERATIONS.md): configuration, persistence, build,
  installation, packaging, and deployment.
- [`CURRENT_STATE.md`](CURRENT_STATE.md): implemented features, deliberate
  boundaries, known defects, and recommended next work.

The top-level [`README.md`](../README.md) remains the user-facing overview.
When it conflicts with these files, verify the source and correct both.
