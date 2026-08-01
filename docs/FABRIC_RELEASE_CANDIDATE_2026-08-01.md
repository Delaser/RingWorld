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
- Exact public source: `729537d711b6e793d7ba7df877d4cbf768e68455`
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
| macOS universal client ZIP | `bd7e029cbc79cef191084efe963179ad75b4769e07c0212b0950f9956eb1e0ab` |
| Windows client ZIP | `34547bab3b4285d25c7601910975ed1dfef54bf92b289d828e8242a78426597f` |
| dedicated-server overlay ZIP | `dfa5bac7c2208409197843fcddfe065f8c973a0a24bafb69981f50fef02cb3fb` |

The builder verified the three archives and their root checksums. Their
manifests identify the same frozen source revision. The Windows launcher and
PowerShell migration path previously passed on GitHub's real Windows runner
for the same package code; changing the frozen revision changes only generated
manifest/checksum bytes, not the launcher.

## Runtime evidence

The exact server overlay was extracted into an empty ignored directory and
combined with the official Fabric server bootstrap. A local accepted EULA and
offline test properties were test-fixture state, not distributable content.
The candidate jar hash matched the staged jar. Java 25 created a fresh survival
world with the production 16,384×256 layout, reached `Done (5.811s)`, and
saved all three dimensions before a clean `stop`. Background atlas generation
was disabled only in this bounded smoke fixture.

The exact macOS ZIP was overlaid onto an isolated existing Prism data tree.
The launcher preserved that tree, selected its Prism-managed Java 25 runtime,
installed the exact candidate jar, authenticated, and loaded Minecraft,
RingWorld, resources, shaders, sound, and all texture atlases without a crash.
It was then terminated without opening or modifying a world.

## Remaining issue-#12 gates

- Launch the actual ZIP in a graphical Windows Minecraft session and confirm
  Java 25, authentication, resources/shaders, and title-screen stability.
- Complete the clean, empty macOS-data launch interactively. The package
  downloaded Prism and installed the instance, but the locked automation
  session could not complete Prism's first-run Java selection. The verified
  in-place path had a Prism-managed Java 25 runtime already available.

Do not close #12 or call these convenience packages release-ready until those
two platform gates are recorded. Issue #13 then performs the independent
review of this frozen source and artifact set.
