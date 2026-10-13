# Release fixture stability and NeoForge setup follow-up

This work starts at main `4421b7c` after PR #293. It does not qualify new release
JARs or alter the original Windows 57efcb0 results.

## Commanded zombie navigation

The retained native 26.1.2 Fabric trace from the preceding speed pass shows a
reachable command to `(2050,120,15)` replaced at tick 11 by
`WaterAvoidingRandomStrollGoal`, with a new unreachable target `(2049,122,14)`.
The source, current and target chunks were loaded. That particular run still
arrived within the existing tolerance and passed; it does not reproduce either
older Windows failure.

The fixture issued `navigation.moveTo` outside the zombie's GoalSelector. That
does not reserve the selector's MOVE control, so ordinary AI can supersede the
test input. A regression against Minecraft's real GoalSelector demonstrates
that arbitration, then verifies a fixture-owned MOVE goal excludes competing
movement while unrelated LOOK goals keep running. The goal has no path, retry,
velocity, teleport or arrival implementation. It is installed only on the fresh
tagged test zombie and remains until that fixture entity is discarded.

The one original navigation command, ordinary path following/folding, lane,
reachability diagnostics, completed-path requirement, target tolerance and
1,200-tick timeout are unchanged. This is deterministic ownership of test input,
not a gameplay pathfinding fix or an assertion waiver. Native validation is
recorded below when complete.

## Automated player modes

The production lifecycle coordinator previously granted flight and
invulnerability while leaving copied Survival worlds in Survival mode. It now
sets actual Creative mode for its registered live player on the server thread,
and reapplies protection when reopen replaces ServerPlayer. Repeated ticks do
not resend unchanged mode/abilities. Dead players remain dead and the client
still fails on any unexpected death. Unregistered players and ordinary sessions
are untouched.

The two-save layout-switch test previously had no player protection during
handshake/Atlas waits. It now uses the existing server-thread Spectator safety
bridge before assertions in each loaded save, with a fresh acknowledgement for
the second integrated server. Its storage, identity, Atlas and disconnect checks
are unchanged.

Other full-suite visual fixtures already create Creative worlds or use Spectator
capture protection. Dedicated multiplayer and raid servers already default to
Creative. Explicit Survival phases remain for movement/combat, item-consumption
placement, sleeping/damage, deliberate death/respawn and ordinary portal delay.
Off-ring falling/collision phases retain their explicit physical setup. Creative
does not prevent void death, so those fixtures must keep their supports/flight
and actual death detection. No blanket god-mode, healing loop or auto-respawn is
introduced.

## NeoForm cache measurement

Use distinct isolated Gradle homes and project/build directories for cold and
seeded samples, identical pinned dependencies and authenticated Mojang assets,
and the existing indexed seed staging path. Inspect actual `Used cache of`
node records, rather than inferring reuse from copied files or UP-TO-DATE tasks.
Record seed-copy cost separately from Gradle/NFRt work. Cache inputs never qualify
a JAR or permit reuse across changed runtime source. Measurements and host scope
will be recorded after the native probe finishes.
