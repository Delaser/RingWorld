# Dual-loader release-candidate evidence — 2026-08-08

This checkpoint records local machine validation for the Fabric and NeoForge
`0.2.0+mc26.1.2` standalone candidates. The owner subsequently authorized the
matched Modrinth alpha uploads. It still does not authorize a demo-server
restart, live-world change, or promotion beyond alpha.

## Source and runtime artifacts

- Validated code baseline: `967759be872080a72e48bd26f7a97df9ee0a0302`
- Minecraft: 26.1.2
- Java: 25
- Fabric Loader: 0.19.3 or newer; Fabric API: 0.155.2+26.1.2
- NeoForge: 26.1.2.87 or newer
- Fabric jar SHA-256:
  `9ec25789e1418fd3b1877c3c23d8388cbb880a0ed562ef5f0608498df0605097`
- NeoForge jar SHA-256:
  `ac8b8776d85038512bb85dab8967a32a53e8d33128a4ccae17b51b65b214938a`

The fail-closed staging workflow rebuilt both loaders from the same clean,
pushed public commit. The final immutable corresponding-source URL is generated
into the external staging manifest, description, and changelog after this
evidence document is committed; source cannot embed its own future commit hash.
The gate passed 291 unit/parameterized tests per loader,
compared the byte-identical shared contract, verified MPL-2.0 metadata and the
embedded licence, and generated exact corresponding-source links. The 40
focused distribution/staging/package tests passed with two expected
platform-specific skips.

## Hosted alpha 3 files

- Fabric: `0.2.0-alpha.3-fabric+mc26.1.2`, Modrinth version `lnY3EC8t`
- NeoForge: `0.2.0-alpha.3-neoforge+mc26.1.2`, Modrinth version `D19TF1Qj`
- Corresponding source for both:
  `94c8c9eb8a1a0e3d399ffd08a87af5c70b60b9b7`

Both CDN downloads reproduce the SHA-256 values above and pass the
loader-specific distribution/licence verifier. The Modrinth project remains
under review. The uploads are testing alphas, not stable-release sign-off.

On 2026-08-09, those same exact jars were submitted to CurseForge project
`1645598` as separate Fabric and NeoForge Alpha files. Both are tagged Client,
Server, Java 25, and Minecraft 26.1.2; Fabric alone requires Fabric API project
`306612`. The CurseForge project and files are awaiting scanning/moderation.
See `CURSEFORGE_RELEASE.md`; no live-server or promotion authority was implied.

## Runtime evidence

- Fresh Fabric and NeoForge 2,048×416 two-client matrices passed seam movement,
  visibility, combat, block interaction, boats, reconnect, seam-bed sleep and
  reconnect, death/respawn, Nether/End travel, weather, and strict verifiers.
- A fresh NeoForge 16,384×256 production run passed the same matrix while its
  Atlas advanced monotonically from 596 to 3,824 of 65,536 cells at roughly
  28–32 cells/s. The largest warning was a 3.219-second cold Nether-generation
  stall; there was no watchdog, crash, Atlas failure, or progress regression.
- The deterministic seam explosion added no item or falling-block entities in
  either loader fixture.

## Optional packages

The reproducible builder passed for the Fabric and NeoForge macOS clients,
Windows clients, and server overlays. These are convenience packages, not
normal Modrinth upload files. Their exact checksums are deliberately emitted
to each ignored external `SHA256SUMS.txt`: package archives include the staging
manifest and corresponding-source revision, so embedding those package hashes
inside the source commit would create a circular, immediately stale record.

## Remaining release gates

- ordinary owner playability review on the exact jars for both loaders;
- owner visual and motion sign-off, including exposure/cloud/player views;
- real graphical Windows title-screen and world/server connection on both
  loaders;
- independent final cross-system review after those results;
- explicit owner go/no-go before promotion or any live-server change.

Until those gates pass, this remains an alpha candidate rather than a stable
release.
