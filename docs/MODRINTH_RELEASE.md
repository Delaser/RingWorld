# Modrinth release workflow

Last updated: 2026-07-31. Issue [#33](https://github.com/Delaser/RingWorld/issues/33)
tracks this workflow.

RingWorld is distributed as a normal Fabric runtime jar, not as a Prism
instance or client bundle. The first alpha is `0.2.0+mc26.1.2` for Minecraft
26.1.2, Java 25, Fabric Loader 0.19.3 or newer, and Fabric API
0.155.2+26.1.2. The same RingWorld version is required on both a dedicated
server and every connecting client. A future NeoForge artifact belongs on the
same Modrinth project as a separately validated loader-specific version.

## Stage locally

Run the following single command at the repository root:

```sh
python3 scripts/stage_modrinth_release.py --build
```

It runs `./gradlew clean test build --console=plain`, validates the expected
runtime jar, and creates the ignored review directory
`dist/modrinth/0.2.0+mc26.1.2/fabric/`. The script has no upload implementation,
does not make network requests, has no token option, and must never change a
Modrinth listing. To validate an already-built exact file, use
`--jar path/to/ringworld-0.2.0+mc26.1.2.jar` instead.

The generated directory contains exactly one jar. That jar alone is the
potential upload file; `STAGING-MANIFEST.json`, `SHA256SUMS.txt`, the project
description, and the changelog are operator-review material. The manifest
records the jar SHA-256 and SHA-512 plus the exact accessible public source
revision and commit URL. Confirm the revision is the source corresponding to
the selected binary before a separately authorized manual upload.

## Fail-closed checks

Staging rejects sources, development, and Javadoc archives; unexpected names;
missing compiled classes or mixin descriptors; archive source files; account,
save, option, server, log, or runtime-state files; private-key material;
unsafe or duplicate archive paths; and replacement of an unrecognized stage.
It also rejects missing or stale MPL-2.0 metadata, a missing or different
embedded `LICENSE-RINGWORLD.txt`, inconsistent version, Minecraft, Fabric
Loader, Java, Fabric API, author/contact, environment, or Modrinth dependency
metadata. The source revision must be a full SHA and its canonical public
GitHub commit URL.

Run focused checks with:

```sh
python3 -m unittest scripts/test_verify_distribution_license.py scripts/test_stage_modrinth_release.py
```

## Manual release gates

The manual alpha upload already submitted for moderation is not modified by
this workflow. Any later upload or listing change needs explicit owner
authorization and must use the reviewed staged jar only. First inspect the
archive and checksum; install into a clean Fabric client; test installation in
an existing modded instance; launch a clean dedicated server; then run the
two-client handshake, seam, combat, block, boat, teleport, reconnect, atlas,
and production-geometry validation gates. State untested renderer, shader,
gravity, world-generation, chunk, and networking combinations as compatibility
risks, not support claims.

The Modrinth page must give installation and world-creation guidance, exact
client/server requirements, the MPL-2.0 statement, a current alpha changelog,
and a public route to the corresponding source revision. Do not store a token
in the repository, generated stage, Gradle properties, documentation, shell
history, or client bundle.
