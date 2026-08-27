# macOS packaged-runtime review — 2026-08-27

**Partial: three PASS, one blocked before Minecraft starts. No publication.**

| Packaged runtime | Result |
| --- | --- |
| Minecraft 26.1.2 / Fabric 0.19.3 | PASS |
| Minecraft 26.1.2 / NeoForge 26.1.2.87 | PASS |
| Minecraft 26.2 / Fabric 0.19.3 | PASS |
| Minecraft 26.2 / NeoForge 26.2.0.69 | BLOCKED: Prism metadata HTTP 404 |

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

## Remaining blocker

Prism's official URL
`https://meta.prismlauncher.org/v1/net.neoforged/26.2.0.69.json`
returned HTTP 404. Its catalog's latest 26.2 loader was 26.2.0.67; Minecraft's
own 26.2 metadata returned HTTP 200. The last case never started Minecraft.
This is a convenience-package provisioning failure, not a mod crash or a
failure of the retained exact-jar NeoForge qualification.

[#234](https://github.com/Delaser/RingWorld/issues/234) tracks the repair.
Retain qualified loader 26.2.0.69 and the frozen jar. Wait for the official entry
or implement a reviewed, pinned installer/instance-metadata path; do not silently
select .67. Recheck affected package assembly/hashes and rerun only the blocked
authenticated smoke, then confirm the matching Windows import path. The owner's
general Windows sign-off does not independently prove this exact fresh install.

## Retained evidence and privacy

Private root: `dist/macos-package-review-20260827/`.
Immutable report: `packaged-capture-results-partial.json`, status `INCOMPLETE`,
SHA-256 `fe838ac36f92b2f0f6bd73e70b004a658628d27b6a653f8f8d14add53809c09f`.
It binds archive/jar hashes, captured PNGs, game logs and official metadata
snapshots. The three result directories are below `evidence/`.

**Never upload or archive the whole review root:** it now includes authenticated
Prism account data. Share only individually reviewed non-secret evidence. No
candidate jar, normal player world or live server was changed by this review.
