# Protocol hardening and positional-packet audit — 2026-08-01

Issue #73 hardens the mandatory Fabric play-phase handshake and audits the
Minecraft 26.1.2 positional packet surface. It does not change a payload codec
or reuse an existing identifier with a different byte layout.

## Negotiation policy

RingWorld deliberately uses exact-version capability negotiation rather than
feature bits. A compatible peer must expose the current settings channel, the
complete revisioned-atlas channel set, and the atlas-map status/control set.
The immutable settings format must equal `RingWorldSettings.FORMAT_VERSION`
and the independently recomputed layout fingerprint must match.

This is intentionally all-or-nothing because topology, renderer geometry,
atlas revision semantics, and canonical/presentation packet mapping are not
independently optional. A breaking field-layout change gets a new channel
generation; it is never appended to an old identifier. No payload layout or
identifier changed in this pass.

## Acknowledgement state machine

`RingHandshakeTracker` is loader-neutral, server-thread-owned state. Each join
replaces any previous UUID state and starts a 300-tick (15-second at 20 TPS)
deadline. A valid first acknowledgement transitions the session to
acknowledged; a duplicate is idempotent and does not resend metadata. Missing,
expired, unexpected, format-mismatched, or fingerprint-mismatched
acknowledgements disconnect with distinct useful messages. Disconnect clears
all state, so reconnect cannot inherit an acknowledgement.

Atlas requests, map-status requests, controls, and automated test reports are
rejected until acknowledgement. Atlas metadata remains the first post-
acknowledgement payload. The server checks every required clientbound feature
channel before beginning; the client checks every required serverbound channel
before installing `ClientRingState`.

## Positional packet audit

The existing mixins already project chunks, light, biome refreshes, player and
entity absolute movement, vehicles, block updates/events, particles,
explosions, and coordinate sounds. This pass adds:

| Direction | Packet/path | Policy |
| --- | --- | --- |
| S2C | minecart interpolation steps | Project each absolute step sequentially into the minecart's current chart |
| S2C | position-only damage source | Project source X around the hurt entity so damage direction remains local |
| S2C | `/look` coordinate/entity target | Project the resolved target position |
| S2C | open sign editor | Project the block position used to resolve the client block entity |
| C2S | sign update | Canonicalize the edited sign position |
| C2S | pick block with data | Canonicalize the picked block position |
| C2S | block-entity tag query | Canonicalize the queried block position |

Relative entity movement, velocity, explosion knockback, structure-block
offsets, and entity-interaction hit vectors are deltas/local values and must
not be wrapped as absolute coordinates.

The following audited surfaces remain deliberately unsupported rather than
being incorrectly rewritten once at packet receipt:

- the 26.1 locator bar still needs its own dynamic nearest-image audit;
  filled-map pixels/decorations and spawn/lodestone/recovery compass targets are handled
  separately at their vanilla sampling/rendering paths;
- world-border centre packets do not describe RingWorld's finite-Z rims and
  are not used as a circumference boundary;
- GameTest/debug overlays and operator-only structure, jigsaw, command-block,
  and test-block editing packets are not gameplay compatibility promises;
- third-party custom payloads are opaque. Compatible mods must use the public
  geometry API and define their own canonical/presentation conversion.

## Evidence

- 224/224 Java unit and parameterized cases pass, including missing, deadline,
  duplicate, reconnect, correct-layout, wrong-format, and wrong-fingerprint
  handshake states.
- A real dedicated server plus two independent clients acknowledged format 2,
  completed a planned disconnect/reconnect, and ended with
  `[multiplayer] full scenario result=true`.
- Both clients loaded every new required mixin target. The run retained the
  entire #71 seam/gameplay/portal matrix and logged no handshake timeout,
  movement warning, RingWorld exception, or crash.

The packet audit is version-specific to Minecraft 26.1.2. Every Minecraft
upgrade must repeat it against the new packet classes and handler methods.
