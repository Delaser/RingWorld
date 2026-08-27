# macOS packaged-runtime review — 2026-08-27

**All four packaged-runtime smokes PASS after the targeted #234 repair. No publication.**

| Packaged runtime | Result |
| --- | --- |
| Minecraft 26.1.2 / Fabric 0.19.3 | PASS |
| Minecraft 26.1.2 / NeoForge 26.1.2.87 | PASS |
| Minecraft 26.2 / Fabric 0.19.3 | PASS |
| Minecraft 26.2 / NeoForge 26.2.0.69 | PASS after package-only metadata repair |

## What ran

All four fresh archive hashes, nested runtime-jar identities, MPL licences and
Minecraft/loader pins were verified against the [owner handoff](RELEASE_1_1_OWNER_HANDOFF.md).
The 26.2 Fabric outer launcher bootstrapped official Prism 11.0.3 and Java 25.
The owner authenticated the new test root; each candidate used a separate fresh
instance imported from its package's `RingWorld-Prism-Instance.zip`. Normal
Prism accounts, saves and instances were not copied or modified. This is three
packaged-runtime/import passes, not four independent outer-launcher first runs.

Each passing instance ran the existing opt-in `AtlasPregenerationUiTestClient`
from its exact, unchanged packaged jar under real authenticated Prism, not a
Gradle development launch. Only disposable options/configuration and diagnostic
JVM flags changed. Fresh creative 2,048×128 worlds exercised progressive Atlas
generation, pause/resume, cancel/retry, 100% completion, live block-edit/removal
revisions, normal save/disconnect and client-session teardown. Expected embedded
1.1 build labels were checked. Each pass retains a game log and ten PNGs.
Initial confirmation capture 03 was skipped because normal packaged startup
already begins generation; retry confirmation 10 was exercised.

Captured menus and representative world views were inspected. This bounded
smoke is not a new production-size performance, same-world reopen, multiplayer,
Nether-travel or owner visual-parity claim. Those existing qualification records
remain separate. The first 26.1.2 Fabric attempt was interrupted by an operator
launcher timeout; it is excluded, preserved, and replaced only by a fresh
passing instance, not labeled a game defect.

Two installed Prism copies share a macOS bundle ID, so the browser login
callback reached the older launcher. Device-code/QR authentication resolved
this without copying account files. Future tests should target the full test
application path and reuse only this explicitly authenticated test root.

## Original blocker and targeted repair

Prism's official URL
`https://meta.prismlauncher.org/v1/net.neoforged/26.2.0.69.json`
returned HTTP 404. Its catalog's latest 26.2 loader was 26.2.0.67; Minecraft's
own 26.2 metadata returned HTTP 200. The last case never started Minecraft.
This is a convenience-package provisioning failure, not a mod crash or a
failure of the retained exact-jar NeoForge qualification.

[#234](https://github.com/Delaser/RingWorld/issues/234) is repaired by the optional
`--neoforge-installer` assembly path. It verifies the official installer against
the selected qualification cell's SHA-256, checks Minecraft/NeoForge identities,
and generates Prism's native custom component with official hashed downloads.
The loader remains 26.2.0.69; the RingWorld jar is byte-identical to the original
stage. No Minecraft or installer binary is included in the client archive.
See [assembly instructions](../deploy/client/README.md).

The new `Review-26.2-NeoForge-Metadata-Fix` instance in the authenticated test
root passed the same fresh 2,048×128 Atlas fixture, including all ten applicable
captures, complete generation, live edits and normal save/session teardown.
Its game log identifies Minecraft 26.2 / NeoForge 26.2.0.69 / RingWorld
1.1.0+mc26.2. Shutdown completed and no game JVM remained. The original blocked
record is preserved; this is a targeted replacement, not a four-case rerun.

Corrected archives under `dist/prism-neoforge-repair-20260827/final-packages/`:

| Archive | SHA-256 |
| --- | --- |
| `RingWorld-1.1.0+mc26.2-NeoForge-Client-macOS-universal.zip` | `46f81e65c330e5e62960acd66024e425be8091a267caba2493b8e070dcc85134` |
| `RingWorld-1.1.0+mc26.2-NeoForge-Client-Windows.zip` | `ad3bc4e10862a40a13e0c36a9d4cab3b017f022d1c10be85adf60c14e97d0435` |

Both OS import ZIPs contain the identical component and unchanged staged jar.
The local package/installer/launcher suite passes 30 executed cases (32 total,
two Windows-only skips); hosted Windows launcher/update regression remains the
platform-specific check. This does not claim a new graphical Windows run.
That [Windows launcher/update check](https://github.com/Delaser/RingWorld/actions/runs/33107075427)
passes on `b27b44e`. The first run failed only while cleaning up its still-running
stub; the test-only fix waits for the stub without changing shipped launchers.
Corrected packages are local only; the earlier public test kit is not rewritten
by this repair. Release, merge and live deployment remain unauthorized.

Implementation references: Prism's
[NeoForge metadata generator](https://github.com/PrismLauncher/meta/blob/main/meta/run/generate_neoforge.py)
and [native custom component handling](https://github.com/PrismLauncher/PrismLauncher/blob/11.0.3/launcher/minecraft/Component.cpp).

## Retained evidence and privacy

Private root: `dist/macos-package-review-20260827/`.
Immutable report: `packaged-capture-results-partial.json`, status `INCOMPLETE`,
SHA-256 `fe838ac36f92b2f0f6bd73e70b004a658628d27b6a653f8f8d14add53809c09f`.
It binds archive/jar hashes, captured PNGs, game logs and official metadata
snapshots. The three result directories are below `evidence/`.

The targeted repair has independent retained evidence below
`dist/prism-neoforge-repair-20260827/`:

- `packaged-smoke-result.json`, SHA-256
  `e910df9ab63800d22c541eff5bb161d737dd366f9335636da20fb9cecef7c56e`.
- `repair-verification.json`, SHA-256
  `5586489525bd42e2f6c27a1724b8b19166af656b7e75183a16cab52845b217c9`,
  independently rehashing every capture/log and both OS archive/component/jar/licence identities.

**Never upload or archive the whole review root:** it now includes authenticated
Prism account data. Share only individually reviewed non-secret evidence. No
candidate jar, normal player world or live server was changed by this review.
