# Optional visual client ringlights command review — 2026-09-02

## Verdict

**PASS with no findings** for the bounded Minecraft 1.21.1 client-only
`/ringworld ringlights` command batch.

Reviewed commits:

- parent: `ce32ac26c668d8248fcf62c28e1447ba511506ea`;
- source and tests: `0dea7bae9ab56558a6725341fd35c21aadf3f945`;
- implementation documentation:
  `54f32f40b8320d37379e8afe4108e74d7c212aff`.

The source commit is based directly on the stated parent, and the documentation
commit is based directly on the source commit. The exact range changes seven
expected files and passes `git diff --check`.

## Client-only command ownership

The loader-neutral `RingAtlasLightCommand` model lives under the client source
set and depends only on `RingAtlasLightTuning` and the existing pure
`RingAtlasLightProfile`. It neither imports nor calls a command dispatcher,
network transport, payload, server, persistence, or rendering API.

Fabric's thin adapter registers through the pinned 1.21.1
`ClientCommandRegistrationCallback` and `ClientCommandManager` API. NeoForge's
thin adapter is a client-only event subscriber whose handler accepts
`RegisterClientCommandsEvent`. The source/bytecode contract requires exactly
one active loader adapter per test graph, its matching client registration API,
and the absence of `ringlights` from the shared server command tree. Inspection
of the exact additions also finds no payload, channel, command-send, or other
network delivery call.

## Forms, validation, and mutation

Both adapters expose the documented forms:

- `/ringworld ringlights` and `/ringworld ringlights show` report the current
  process-local profile;
- `/ringworld ringlights reset` selects the existing Midpoint profile; and
- `/ringworld ringlights <falloff> <peak>` requires both numeric arguments and
  selects Gamma.

Brigadier bounds falloff to 0.5–6.0 and peak to 0.1–3.0. The shared result model
then calls the existing profile constructor path, which independently rejects
non-finite and out-of-range values. Invalid direct-model input returns local
error feedback without mutating the previous tuning profile. Focused cases
cover both inclusive endpoints, below/above-range values, NaN, infinity,
show-without-mutation, reset, and exact summary text.

`RingAtlasLightTuning` remains a volatile process-local value: it is not saved,
sent, or attached to server state. The only production mutations in this batch
are its established `useGamma` and `reset` methods.

## Feedback and loader parity

The loader-neutral result contains only success state and nonblank display
text. Fabric maps it to `FabricClientCommandSource.sendFeedback` or
`sendError`; NeoForge maps it to the local client command source's
`sendSuccess` or `sendFailure`. Both return one for success and zero for a
rejected model result. No feedback is sent to the server.

The initial implementation used the newer reference line's Fabric class name;
the final commit correctly uses the pinned 1.21.1 `ClientCommandManager` API.
The pre-final review note also corrected the tuning class's stale claim that no
command was registered.

## Scope and evidence

The exact range contains only the pure client command/result model, the tuning
Javadoc correction, two thin loader client adapters, focused model and
bytecode/source-contract tests, and the technical-datasheet checkpoint. It
does not change server commands, payloads, channels, renderer/shader/globals,
sky/fog behavior, HUD/UI, persistence, world generation, Create integration,
packaging, dependencies, or release metadata.

The implementer reports final Java 21 compilation of both client graphs and
five focused command/tuning/registration cases on each graph: ten executions
with no failures, errors, or skips. This independent review did not repeat
builds or launch Minecraft.

## Remaining qualification

The bounded PASS proves source-set ownership, model behavior, loader client
registration wiring, and absence from server/network paths. It does not prove
in-game client-command coexistence with the server's `/ringworld` root or live
visual tuning on a running GPU renderer. Those remain bounded client runtime
qualification, not blockers for this checkpoint.
