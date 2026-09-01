# Optional visual live sky/sun command review — 2026-09-02

## Verdict

**PASS with no findings** for the bounded Minecraft 1.21.1 live server sky and
sun command batch.

Reviewed commits:

- parent: `dfd57c7457d31301607f21c764d91e81f1d172d0`;
- source and tests: `60307195257d400ea8a3b97915dc970f79f1b06b`;
- implementation documentation:
  `ce32ac26c668d8248fcf62c28e1447ba511506ea`.

The source commit is based directly on the stated parent, and the documentation
commit is based directly on the source commit. The exact range changes five
expected files and passes `git diff --check`.

## Command ownership and permissions

The new `sky` and `sun` branches are attached to the existing shared
`/ringworld` command tree, whose unchanged `source.hasPermission(2)` requirement
is Minecraft 1.21.1's gamemaster-level gate. Fabric registers that shared tree
through its existing command callback; NeoForge registers the same tree from
its existing command-registration event. No client command or loader-specific
command implementation was added.

`/ringworld sky` and `/ringworld sun` query the saved backdrop and visible
light-source labels without replacing the profile. Their set forms accept only
the documented case-insensitive values, provide matching suggestions, return
clear failures for invalid values, and use the ordinary command result contract
of zero for failure and one for success.

## Persistence and independent updates

Every query and mutation resolves the server's owning Overworld rather than the
source entity's current dimension. A missing Overworld, unreadable saved data,
or unwritable saved data produces a command failure rather than an exception
escaping the command.

The loader-neutral command model delegates sky changes to
`RingSkyProfile.withBackdrop` and sun changes to
`RingSkyProfile.withLightSource`. Focused cases prove that each operation
retains the other profile field. Successful mutations then replace the owning
Overworld's `RingSkySettings` through the established `setProfile` path, which
marks the replacement dirty before installing it in dimension-owned storage.
No terrain or layout identity is changed.

## Live delivery

After persistence succeeds, the command creates the existing
`RingSkyProfilePayload` and iterates the server-wide connected-player list, so
players temporarily outside the Overworld are included. Each player is checked
through the established loader-owned `PayloadTransport.canSend` seam before
delivery on the existing `sky_profile_v1` type.

Fabric therefore retains its real per-player channel-capability query, while
NeoForge retains the already-established non-optional negotiated-channel
contract. A missing Fabric capability skips that player. A capability-query or
send failure for one player is caught and logged without undoing persistence or
preventing delivery attempts to the remaining players.

## Scope and evidence

The exact range contains only the shared command coordinator, a small pure
parse/update model, focused model and bytecode transport-contract tests, and the
technical-datasheet checkpoint. It does not change client commands,
renderer/shader/fog/sky drawing, UI/HUD, payload codecs, channel identity,
settings formats, server handshake ordering, world generation, Create
integration, packaging, dependencies, or release metadata.

The implementer reports clean Java 21 compilation of Fabric/common and
NeoForge, followed by the same six focused command/model/transport cases on
each graph: 12 executions with no failures, errors, or skips. This independent
review did not repeat builds or launch Minecraft.

## Remaining qualification

The bounded PASS establishes source, persistence, registration, and delivery
wiring. It does not prove commands on a live dedicated or integrated server,
multiplayer clients visibly updating in every dimension, save/reopen behavior,
or compatibility with third-party permission and command-tree modifications.
Those remain focused runtime and broad compatibility qualification, not
blockers for this checkpoint.
