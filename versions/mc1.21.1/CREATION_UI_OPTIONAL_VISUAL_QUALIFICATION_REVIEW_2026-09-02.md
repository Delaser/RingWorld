# Independent review: 1.21.1 optional-visual creation UI qualification

Date: 2026-09-02

Verdict: **PASS**

Findings: none remaining

## Reviewed checkpoint

- documented parent: `154509f4dd8cc6f8f199f3c4bf4770fcd0c2b86d`;
- fixture/source commit: `16202ed560504d51586f7778076a88405ca886a1`;
- evidence/documentation commit:
  `18674e3464425dd1b45857dc991e5ceacbce81be`.

The commit chain is linear, both implementation commits are clean, and
`git diff --check` reports no whitespace errors. The fixture commit changes ten
bounded creation-UI, verifier, runner-contract, and test files. It does not add
an in-world fixture, a protocol or server path, world generation, Create
integration, packaging, or release metadata.

## Source and fixture review

The existing menu-only fixture is expanded from thirteen to seventeen ordered
captures. The additions exercise two seed previews, the existing wall editor,
and independent sky/sun selection while retaining the previous footer,
GUI-scale, compact-window, validation, preset-size, custom, confirmation, and
applied-footer coverage.

The seed flow deliberately starts one preview, edits its seed while the worker
is active, and requires the cancellation counter to advance. It then waits for
ready results for seeds `12345` and `67890`, requires their preview identities
to differ, logs exact `64:1` aspect and centered-seam assertions, closes by the
normal UI path, and requires the dynamic texture plus worker to be released.
The preview worker continues to use an immutable client-thread snapshot and a
detached generator; no save or chunk is created.

The wall flow captures the default editor, applies `OVERGROWN_RUIN`, resizes to
a 320-logical-pixel view, verifies the selection through the resize, and
returns it to the parent. The later reset intentionally restores the saved
bootstrap wall style before the final custom layout. The custom capture proves
`4096x640x192`, monument enabled, Night backdrop, and Large sun as independent
choices. Confirmation updates the real Create World footer and then stops the
client; there is no world-creation call.

The verifier requires exactly one non-uniform PNG for every ordered prefix,
distinct seed PNG bytes and logged identities, the exact cancellation/teardown
marker, final bootstrap values, zero `level.dat` files, and a non-uniform
contact sheet. Updating the two existing external/Gradle runner capture tuples
to the same seventeen names does not expand their qualification claims.

Two stale thirteen-capture assertions in the external-runner test were found
during review and corrected before the final fixture commit. No issue remains
in the reviewed source.

## Retained evidence audit

| Loader | Captures | `level.dat` | Log SHA-256 | Contact-sheet SHA-256 |
| --- | ---: | ---: | --- | --- |
| Fabric | 17/17 | 0 | `c021fe37c129a6af821172eb289629950bcf40bd264fd21593932a9fba77007b` | `7f57e26f1403b72cbfa36810a73d2f02b603c8c60062f909c6dd19d9f4ff0181` |
| NeoForge | 17/17 | 0 | `034ff930e0188d2edc2b191950669a46b4b7fa740356330fa45bfd9cd645ca27` | `163b9f5eac04d66b07465625fe1d75df19f97ed89dfb9ba64ec025d99f9f6d3d` |

The retained roots are:

- Fabric: `C:\Users\Admin\.codex\worktrees\f862\RingWorld\run-creation-ui`;
- NeoForge:
  `C:\Users\Admin\.codex\worktrees\f862\RingWorld\neoforge\run-creation-ui`.

An independent file walk found exactly seventeen named captures per loader and
no `level.dat`. All 34 screenshot hashes in the evidence document were
recomputed and match. The log sizes/hashes and contact-sheet sizes/hashes also
match the document. Both logs contain the same seed identities,
`a910bdd6d2eda84e` and `b47942971fbe2941`, the centered `64:1` assertions, the
exact successful cancellation/texture-release/normal-close marker, and the
seventeen-capture terminal PASS. Neither contains a fixture FAIL.

Both final `ringworld.properties` files contain `4096`, `640`, `192`,
`NIGHT`, `LARGE`, monument enabled, test mode disabled, and Atlas
pregeneration disabled. Their independently recomputed SHA-256 values are
`30c23e2170483ba0312b34a206077ecca411a393a8afa3ed618b8cfdaeb6935b`
for Fabric and
`d72a122f6ac0c139c9bf41425808db20203413dc25227fe40f80f82ff124462a`
for NeoForge.

## Visual review

Both contact sheets were inspected at original detail. They contain the same
seventeen ordered states and are materially loader-matched. The two seed
strips are visibly different and remain thin, centered ring-aspect previews.
The default and Overgrown wall editors fit at their required sizes; the
320-wide main editor and five-error view remain readable; and the custom,
confirmation, and applied-footer states do not show clipping, overlap, or a
stale preview texture. The custom view visibly presents Night and Large.

## Evidence classification and limits

The first attempted graphical command failed in Loom `downloadAssets` before
Minecraft launched and produced no captures. The clean retry ran one Fabric
and one NeoForge client and is the evidence reviewed here. Nonfatal offline
Mojang authentication timeouts do not affect the menu-only result. One broad
external macOS-runner unit suite remains blocked by a pre-existing Windows path
length before capture validation; the focused external capture-contract test
passed.

This verdict is limited to Windows Java 21 source/development-client,
menu-only creation/settings evidence. It does not qualify packaged release,
production launcher, in-world rendering, Atlas lighting, live commands,
worldgen, lifecycle, multiplayer, Linux, or macOS behavior.
