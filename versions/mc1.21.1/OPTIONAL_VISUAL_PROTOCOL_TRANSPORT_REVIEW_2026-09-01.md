# Minecraft 1.21.1 optional-visual protocol-transport review

Reviewed source/test commit `a5d809a609e1da7331abc06d2608d5d6596d5c7f`
and documentation commit `637eb60473bfd5cc98e2420947b917f0b945dc5d`
against parent `d7f4f411fa8ec529dcb562ce5295d52be4458f3c` in the
implementer worktree. Scope was limited to loader registration and capability
gates, login and client-handler ordering, acknowledgement stability, client
session installation/teardown, reconnect freshness, tests, and batch
boundaries. No builds, runtime fixtures, or production edits were performed by
the reviewer.

## Disposition

**Pass; no actionable findings or blockers.**

## Review checklist

- [x] Fabric registers the exact required clientbound protocol set
  `settings_v5`, `sky_profile_v1`, and `terrain_preview_v2` in its shared play
  registry and installs a client receiver for each. NeoForge registers the
  same three through `playToClient` and routes each through its client payload
  dispatcher.
- [x] Both server send paths call the shared
  `RingProtocolCapabilities.supportsRequiredClientbound` all-match gate before
  starting the acknowledgement tracker. That shared list contains exactly the
  three reviewed identities, so absence of any one disconnects before the
  settings handshake begins.
- [x] Immutable settings remain at the established early boundary. The Fabric
  and NeoForge `PlayerList` mixin blobs are unchanged from the parent and still
  inject immediately after vanilla's first play-login send, before position or
  chunk packets. The send methods now source the authoritative saved sky
  profile but do not move the injection or introduce a later lifecycle event.
- [x] Fabric's settings, live-sky, and terrain-preview receiver bodies mutate
  client state directly. No extra `Minecraft.execute` hop was added, preserving
  the render-thread arrival order guaranteed by Fabric play payload delivery.
  The pre-existing executor hop in the disconnect lifecycle callback is
  unrelated and remains required for safe teardown.
- [x] NeoForge routes all three corresponding client mutations through
  `IPayloadContext.enqueueWork`; settings clear/install/acknowledgement remains
  one queued unit, and sky and preview use the same ordered client-thread
  mechanism.
- [x] `settings_ack_v3` is unchanged. Its source blob is identical in the
  parent and reviewed documentation commit, and settings acknowledgement is
  still emitted only after fingerprint validation, server-channel checks,
  previous-session clear, and complete settings installation.
- [x] Both settings handlers install the transmitted wall style, initial sky
  profile, generator seed, settings format, geometry, mapping, and fingerprint.
  The live-sky handler installs the validated profile. Preview installation
  decodes the bounded payload and accepts it only when both the current Atlas
  metadata and embedded preview identify the same world hash.
- [x] The mainline teardown correction represented by `9852430` is present:
  `ClientRingState.clear` restores legacy/default wall and sky appearance,
  zeros seed and format, and discards preview/stage in addition to the existing
  topology and Atlas state. `sessionCleared` asserts all of those fields.
- [x] Reconnect cannot reuse old appearance or preview state. The unchanged
  shared disconnect/level-clear hooks call `RingWorldClientSession.clear`, both
  loader lifecycle paths clear it, and receipt of a new settings payload clears
  once more before install. A new settings install also drops Atlas and preview
  state, while the world-hash check rejects a preview belonging to another
  session.
- [x] The committed source/test range is confined to the five expected
  transport/client-state files and two focused tests. The follow-up commit
  changes only the technical datasheet. There is no renderer, shader, resource,
  creation UI, server preview worker/publication, Atlas service, worldgen,
  command, compatibility, packaging, support metadata, or verification-metadata
  scope creep.

The implementer reports Java 21 Fabric/common and NeoForge compilation and all
20 focused transport, capability, handshake, order, client-state, preview, and
teardown cases passing on each loader. Per assignment, this independent review
did not rerun builds or tests; it verifies the committed source, test surface,
documentation claim, and diff boundaries.
