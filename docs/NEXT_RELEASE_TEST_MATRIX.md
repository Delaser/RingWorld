# Next-release qualification plan

Approved by the owner on 14 October 2026. This is a plan for the next fresh
release campaign, not a change to the active 1.4 qualification on `1533878`.
That campaign keeps its ten quick cells and 100 full fixtures, including its
same-source resume rules. Do not restart it or reinterpret its evidence.

## Coverage

Keep all five supported Minecraft versions, both loaders and six release JARs.
The 26.1.x JAR for each loader is still built against 26.1 and tested unchanged
on all three patches. Keep all ten quick cells, source/ABI builds, unit tests,
package checks and exact-candidate server startup checks.

| Minecraft | Fabric | NeoForge |
| --- | --- | --- |
| 26.1 | Focused patch compatibility | Focused patch compatibility |
| 26.1.1 | Focused patch compatibility | Focused patch compatibility |
| 26.1.2 | Full ten-scenario suite | Full ten-scenario suite |
| 26.2 | Full ten-scenario suite | Full ten-scenario suite |
| 26.3 | Full ten-scenario suite | Full ten-scenario suite |

This replaces 100 full fixture invocations with **60 full fixture invocations
and four focused patch-compatibility profiles**. Each profile contains several
assertions; it is not equivalent to one existing full fixture.

The full scenarios remain creation UI, world generation, Atlas recovery, Atlas
UI, multiplayer, raids, map/compass, production lifecycle, curved objects and
production rendering. Preserve their existing assertions, timeouts, settling
delays and four time/weather conditions.

## Focused patch compatibility

Use the exact frozen 26.1.x release candidate on each older patch with that
patch's pinned loader/API dependencies. In isolated disposable worlds, verify:

- Client startup and world entry without mixin, linkage or shader errors.
- Real terrain curvature, detailed Atlas rendering, their transition and walls;
  retain screenshots at a fixed viewpoint after the scene is ready.
- Resource reload completes and rendering recovers.
- Normal save/disconnect/reopen preserves the world and restores rendering.
- A client connects to a dedicated server running the same candidate; verify
  settings/Atlas handshake and actual world readiness, then disconnect cleanly.

Reuse existing assertion and capture helpers. Combine compatible checks within
one session where possible, but retain the real reload, reopen and multiplayer
boundaries. Do not substitute startup alone for graphical compatibility.
Do not repeat unrelated raids or full four-condition production captures on
every patch unless a change or failure requires them.

## Escalation and implementation

1. Add an explicit, versioned coverage policy to the portable coordinator and
   release evidence validation. Keep the current policy available for old runs.
2. Add the focused profile using shared helpers on Windows and macOS. Preserve
   hidden/muted clients, player safety, isolated writable state and source-world
   immutability. Retain hash-bound logs, screenshots and failures.
3. Record required checks per cell, candidate/source identity and policy digest.
   Resume only matching-policy evidence; staging must reject missing coverage.
   A filtered run under the old 100-fixture policy remains INCOMPLETE.
4. Expand testing on an older patch when code changes affect its version-specific
   mixins, shaders, loader/API integration or behaviour. Run the relevant full
   scenarios on that patch/loader; use its full suite when the impact is unclear.
   A focused-profile failure blocks qualification until investigated and fixed.
5. Add regression coverage for selection, escalation, resume and staging gates;
   validate native focused profiles on both hosts before using the new policy.
   Update the runbook's executable commands and validators together.

The owner has approved the coverage reduction. Implementing it must not simply
remove cells from manifests, accept incomplete old-policy reports, weaken
assertions or claim that representative tests ran on every supported patch.

## Expected duration

Current Windows medians are about 44 minutes per Fabric full cell and 42 per
NeoForge full cell. Removing four full cells saves roughly 172 minutes before
adding focused checks. Budgeting 5–10 minutes for each focused profile suggests
about **2–2.5 hours saved**, or **5.5–6 hours overall** with existing quick checks.
These are planning estimates, not measured results for the new policy. Measure
the first completed run, including setup, cleanup, retries and final staging.
