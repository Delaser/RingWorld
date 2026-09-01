# Minecraft 1.21.1 optional-visual protocol-model review

Reviewed source/test commit `97dd53ca5c4dce7cb348eb5b369bd3ec1bc70d05` and documentation commit `d7f4f411fa8ec529dcb562ce5295d52be4458f3c` against parent `c56c0cd62ee038aaccf58ed8e58a6b35a78275d6`, the 26.x implementation through `e058c69`, and the optional-visual technical datasheet. Scope was limited to common protocol models and tests. No builds, fixtures, or production edits were performed by the reviewer.

## Disposition

**Pass; no actionable findings.**

## Review checklist

- [x] `ringworld:settings_v5` writes and reads the exact documented order: width, circumference, seed, wall height, surface reference Y, terrain mapping, five wall-style fields, three sky-profile fields, settings format, and fingerprint.
- [x] Settings decoding validates geometry, terrain mapping, wall/style IDs and formats, and sky IDs/formats before client state can receive the payload. Fingerprint recomputation includes the immutable wall style and excludes the cosmetic sky profile as required.
- [x] `ringworld:settings_ack_v3` is unchanged: its source blob is identical to the parent, its channel identity remains unchanged, and its wire test still proves VarInt format followed by long fingerprint with no trailing bytes.
- [x] `ringworld:sky_profile_v1` uses stable backdrop/light-source IDs and profile format, round-trips valid profiles, and rejects unknown IDs, unsupported format, null input, and truncated data.
- [x] `ringworld:terrain_preview_v2` preserves stable stage values 0–3, caps the compressed byte array at 2 MiB, rejects empty/malformed/truncated/oversized bodies and unknown stages, validates the embedded world hash, and delegates to the bounded preview decoder for format, dimensions, cell count, and trailing-data checks. Payload bytes are defensively copied.
- [x] `RingProtocolCapabilities` requires all three exact clientbound identities—`settings_v5`, `sky_profile_v1`, and `terrain_preview_v2`—before handshake; omitting any one returns false, so old or partial capability sets fail closed for the later loader adapter.
- [x] The commit range contains no Fabric/NeoForge registration, capability query, send/receive handler, client-session mutation, renderer, shader, resource, Atlas service, UI, or command change. Compatibility disconnect presentation remains deliberately deferred to the loader-transport batch.

The implementer reports Fabric/common and NeoForge compilation plus all 39 focused cases passing on each loader. This review confirms the committed model contract and scope, not runtime transport registration.
