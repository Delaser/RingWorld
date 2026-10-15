# Minecraft 1.20.1 port intake

Status on 15 October 2026: **planned; no build, runtime support or release claimed**.

The target is RingWorld 1.4 functionality from public source
`1533878fe3a6eab7562830b3f39cbd7caae6d46a`, with the existing 1.21.1 backport's
version adaptations and retained correctness fixes. This is a new porting lane.

See the [complete audit and dual-version plan](../../docs/BACKPORT_1_4_DUAL_VERSION_PLAN_2026-10-15.md)
and [acceptance planning contract](../../config/backport-1.4-acceptance-plan.json).

## Intended target

| Item | Decision/status |
| --- | --- |
| Minecraft | Exactly 1.20.1 |
| Game Java / bytecode | Java 17 / class major 61 |
| Loaders | Fabric and Forge 47.x; owner confirmed 15 October 2026 |
| Build JVM, wrapper, Loom, Fabric API, Forge/plugin | Exact reviewed pins pending compiler spike |
| Build root | Proposed isolated `backports/mc1.20.1/`; not created yet |
| Atlas | Format 11, one-block source, bounded snapshots/worker writes |
| Immutable settings / mapping | Format 5 / mapping 4 contract through older serialization adapters |
| Existing newer saves | No downgrade from 1.21.1 or 26.x |
| Compatibility | Vanilla first; optional renderer combinations independently qualified |

NeoForge's own [user guide](https://docs.neoforged.net/user/docs/) recommends
Forge for this Minecraft version. The owner confirmed Fabric/Forge for this
line and Fabric/NeoForge for 1.21.1. Legacy NeoForge is outside the agreed
1.20.1 scope; a Forge result cannot advertise it implicitly.

## Build and testing procedure

1. Pin the official Minecraft client/server, Java runtime, wrapper, mappings,
   loader, APIs, plugin and resolved platform artifacts with hashes. Establish
   Fabric and Forge compile/test/reobfuscated runtime-jar checks. The legacy
   ModDevGradle plugin is a toolchain spike, not an already validated choice.
2. Port shared pure algorithms at Java 17, then older packet codecs, early-login
   ordering, saved data/item NBT, chunk/ticket/lighting internals, mixins, GLSL 150,
   object/sky/cloud rendering and UI through version-owned adapters.
3. Launch each exact jar using its official loader on a dedicated server and a
   real graphics client. Check required handshake/rejection, resource reload,
   canonical topology, partial Atlas and save/disconnect/reopen before advancing.
4. Generate a stopped inventoried **native 1.20.1** production input world;
   never reuse a newer Minecraft save by lowering a data-version tag.
5. For each loader run the complete ten-fixture release suite from the plan:
   creation, worldgen, recovery, Atlas UI/handshake, multiplayer, persisted raids,
   maps/compasses, production lifecycle, curved objects and production render.
   That is 20 core cell-family keys for this line, plus 14 required extension keys.
6. Run the specific 1.4 behavior, performance, crash/fault/recovery, migration/
   rejection, soaked multiplayer and exact packaged-jar checks. Version-specific
   vanilla content can use equivalent test subjects with the same assertion.
7. Freeze/rehash each candidate and retain per-phase immutable evidence,
   screenshots, frametimes, tick/GC/memory metrics and independent disk results.
   Fix defects and repeat affected diagnostics followed by complete replacement
   qualification; no missing or failed key may become accepted by relabelling it.
8. Stage runtime-only MPL-2.0 jars with immutable source links and exact target/
   dependency metadata only after acceptance. Keep platform/renderer claims
   bounded to completed native evidence.

There are no valid 1.20.1 build/run commands yet. Proposed coordinator commands
in the full plan are interfaces to implement. The available planning validator
explicitly reports `PLAN_VALID_NOT_QUALIFIED`; `--require-executable` fails closed.
