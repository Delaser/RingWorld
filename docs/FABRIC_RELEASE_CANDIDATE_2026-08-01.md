# Fabric release-candidate evidence — 2026-08-01

This is the issue-[#12](https://github.com/Delaser/RingWorld/issues/12)
candidate checkpoint. It records local validation only. Nothing in this gate
uploads a file, changes Modrinth, deploys the showcase server, or publishes a
client bundle.

## Frozen candidate

- Version: `0.2.0+mc26.1.2`
- Minecraft: 26.1.2
- Fabric Loader: 0.19.3
- Fabric API: 0.155.2+26.1.2
- Java: 25
- Exact public source: `9b77326d1ec7fba7e2e12e06d89adfceae0ffeb5`
- Runtime jar SHA-256:
  `b1cc0fb9bdbf335e7711f4355e900ac3f69bbd496716fdfd9754eebb4572ec80`

The clean Java 25 `test build` gate passed all 233 Java
unit/parameterized cases. The 22 distribution, package, and staging cases also
passed locally, with the Windows-only launcher case skipped on macOS. The
staging manifest names the exact public source commit above and has no upload
capability.

## Optional package hashes

| Artifact | SHA-256 |
| --- | --- |
| macOS universal client ZIP | `9ff881373fe0aa2c6b3cd93a5dfc255c6c7a06cfaac843dec775853a35ea731b` |
| Windows client ZIP | `ae52c4cb13b3362a5633616ef9c144ebe5e87bd524232d1789fd00fa92530c36` |
| dedicated-server overlay ZIP | `1fb0090c1277816800836abcbdb1db3fbd2a079e8fad317e8546562c33ba1e5b` |

The builder verified the three archives and their root checksums. Their
manifests identify the same frozen source revision. The Windows launcher and
PowerShell migration path passed on GitHub's real Windows runner for this
package code in pull request #87. This remains a launcher/update gate rather
than a graphical Minecraft claim.

## Runtime evidence

The exact server overlay was extracted into an empty ignored directory and
combined with the official Fabric server bootstrap. A local accepted EULA and
offline test properties were test-fixture state, not distributable content.
The candidate jar hash matched the staged jar. Java 25 created a fresh survival
world with the production 16,384×256 layout, reached `Done (6.224s)`, and
saved all three dimensions before a clean `stop`. Background atlas generation
was disabled only in this bounded smoke fixture.

The exact macOS ZIP was overlaid onto an isolated existing Prism data tree.
The launcher preserved that tree, validated and selected its existing Java 25
runtime, installed the exact candidate jar, authenticated, and loaded
Minecraft, RingWorld, resources, shaders, sound, and all texture atlases
without a crash. It was then terminated without opening or modifying a world.

## Remaining issue-#12 gates

- Launch the actual ZIP in a graphical Windows Minecraft session and confirm
  Java 25, authentication, resources/shaders, and title-screen stability.
- Complete the clean, empty macOS-data launch interactively. The package
  installed the instance and correctly selected the detected Java 25 runtime,
  but Prism retained a first-run graphical setup step while the Mac desktop was
  locked. This is no longer a Java-discovery failure.

Do not close #12 or call these convenience packages release-ready until those
two platform gates are recorded. Issue #13 then performs the independent
review of this frozen source and artifact set.
