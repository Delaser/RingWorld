# RingWorld 1.3 release preparation

Scope: six mod jars for Minecraft 26.1–26.1.2, 26.2 and 26.3 on Fabric and
NeoForge, published to CurseForge. Modrinth, optional launcher bundles,
saved-world upgrades and optional launcher bundles remain outside scope.
The owner authorized a full release suite, six completed local jars, and
visual confirmation before upload on 29 September.

**Current gate:** the earlier test run was cancelled after the 26.3-only
rendering gap was identified. All 1.3 fixes, performance improvements, and
features must have equivalent behaviour on every supported 26.x line and both
loaders. The port is implemented. Build and run fresh complete qualification
on all six jars from one fixed source revision; the interrupted run is not
evidence. Stage the files for owner visual confirmation. No 1.3 files have
been uploaded.
The [parity matrix](../../../docs/RELEASE_1_3_PARITY.md) tracks implementation
and remaining runtime checks.

Run fresh quick artifact/startup and nightly runtime qualification for every
supported line and both loaders. Test the staged NeoForge jars with newer loader
builds; accepted loader ranges are minimum-only while Minecraft remains bounded.
Inspect staged metadata, source links and hashes before upload, then verify
hosted downloads. Do not substitute the older development evidence for final
candidate checks.

The changelogs contain only changes since the published 1.2 source. The 26.3
rendering fixes must appear in every version's changelog once parity is verified;
loader-specific implementation notes can remain version-specific. Final qualification,
staging and upload results will be recorded here and in the publication record.
