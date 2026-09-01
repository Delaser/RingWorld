# 1.21.1 optional-visual in-world smoke — 2026-09-02

## Result

The bounded integrated-client smoke passes on both 1.21.1 loaders from final
fixture checkpoint `94544ea6a2d80bdb7cefdb16f0ac36f46ffaba68`, based on the
documented optional-visual creation checkpoint
`18674e3464425dd1b45857dc991e5ceacbce81be`.

| Loader | Result | Captures | Worlds (`level.dat`) | Contact-sheet SHA-256 |
| --- | --- | ---: | ---: | --- |
| Fabric | PASS | 8/8 | 1 | `f24ddce179eafd3c8ebd95ab8627e2be6fcfd6936b7d42e4957fc1ae93581faf` |
| NeoForge | PASS | 8/8 | 1 | `f1f71f77422c317236125f65012db19dc178645ddb94eef5eee4623382059bec` |

Each successful loader run created and reopened one disposable world with seed
`-2162056627494116761`, circumference 2,048, width 128, and the same saved
custom wall and sky state. The PASS is source/development-client evidence, not
a packaged-release or cross-platform claim.

## Fixture and product checkpoints

The work is deliberately separated into three commits:

- `dfc55655af5044b5433b84edc4343dddc914f424` adds the opt-in fixture,
  isolated run roots, exact eight-capture verifier, and source contracts;
- `d893f0171a7c5293756c6998900bca8e1965f48b` fixes a genuine shared product
  defect by preserving local `/ringworld ringlights` while forwarding root,
  sky, sun, and unknown child commands to the server; and
- `94544ea6a2d80bdb7cefdb16f0ac36f46ffaba68` limits the fixture's
  `hasRenderedAllSections` requirement to the mandatory incomplete-Atlas
  capture. Complete-Atlas poses instead require a complete Atlas, a valid
  legacy-proxy draw, and sixty settled frames.

The ordinary Atlas UI gate remains unchanged unless
`ringworld.optionalVisualSmoke` is enabled. Focused command-routing and source
contracts, both client compile graphs, and `git diff --check` passed before the
final graphical runs. Dependency-verification metadata was not changed.

## Invocation and covered flow

The successful Windows Java 21 commands were:

```powershell
.\gradlew.bat :runOptionalVisualSmokeClient --no-daemon --max-workers=1
.\gradlew.bat :neoforge:runOptionalVisualSmokeClient --no-daemon --max-workers=1
```

The corrected Fabric replacement completed in about 108 seconds. NeoForge
completed in 1m45s with `BUILD SUCCESSFUL`; its verifier reported all eight
captures and wrote the contact sheet.

Both runs proved, in one saved world per loader:

- real client resource, Mixin, renderer, and GLSL loading;
- a mandatory visible `current` staged preview while the Atlas was incomplete
  (Fabric 560/4,096 cells; NeoForge 652/4,096 cells), with both the render Mixin
  and shader draw observed;
- completion at 4,096/4,096 cells and terrain-height Atlas rendering;
- a persisted nine-block `INDUSTRIAL` / `HYBRID` / 37% wall prism, with
  reviewed inner/top and outer/top seam-continuity views;
- initial `ATMOSPHERE+SMALL`, live `/ringworld sky night` plus
  `/ringworld sun large`, and live `VOID+NONE`;
- an authored redstone-lamp Atlas cell changing from baseline 0 to block light
  15 and back to 0 across ordered live revisions;
- normal save and disconnect, raw client-session/renderer/texture teardown,
  and reopen of the same world; and
- persisted complete Atlas, custom wall, and `VOID+NONE` state after reopen,
  followed by a second normal disconnect and teardown.

## Evidence roots

Fabric evidence is under
`C:\Users\Admin\.codex\worktrees\f862\RingWorld\run-optional-visual-smoke`:

- `logs\latest.log`: 45,041 bytes, SHA-256
  `92249e90fed83ddb1ea18fa53a779666c466fa4471567a6d9755a9c1cbc81b04`;
- `evidence\optional-visual-contact-sheet.png`: 370,026 bytes, SHA-256
  `f24ddce179eafd3c8ebd95ab8627e2be6fcfd6936b7d42e4957fc1ae93581faf`.

NeoForge evidence is under
`C:\Users\Admin\.codex\worktrees\f862\RingWorld\neoforge\run-optional-visual-smoke`:

- `logs\latest.log`: 56,542 bytes, SHA-256
  `a3dba6a2d177c79d2c526436b00f315ddc304ce5734dee439641f363f4ca7629`;
- `evidence\optional-visual-contact-sheet.png`: 398,674 bytes, SHA-256
  `f1f71f77422c317236125f65012db19dc178645ddb94eef5eee4623382059bec`.

These disposable run roots are ignored. The hashes, rather than generated
screenshots committed to Git, bind this checkpoint to the reviewed evidence.

## Capture manifest

The ordered filenames and meanings are identical on both loaders.

| # | Capture | Fabric SHA-256 | NeoForge SHA-256 |
| ---: | --- | --- | --- |
| 1 | `optional-visual-01-partial-preview.png` | `601141ddf66504692a40c7feb02d400bb49cbb4c07ec8dc4b92332b65c692ef3` | `450cc266a6a709ae002d177725ada1b3dbce9b3e4f3c4d40ddc5c518c3ec6def` |
| 2 | `optional-visual-02-complete-atmosphere-small.png` | `e59c04abf1182bdaeb794f2b8bf7f40eecb1094eb2aebd8258ea245569089d15` | `082785e8537522c5a9308e218c21b9bcfd7dbd7f52abb27403d1b568ea8719d8` |
| 3 | `optional-visual-03-custom-wall-inner-top-seam.png` | `9dbbd475ed9c6769084fcb806e34c8c1ca23cdd3bd556a62859b5be21bbac5fb` | `623d17c41f11fa14f7be4046f6787bd823105d7a3fd76bfb07d4249a669d9610` |
| 4 | `optional-visual-04-custom-wall-outer-top-seam.png` | `79ef37bc8b0df3a5baedee66ba6c03a58a358172f7a802ab1a99d422a6917868` | `805814c08610fcf1766dcc002e8be16b4beaa2b9049a56aae20a61962ce81e5f` |
| 5 | `optional-visual-05-night-large-light-on.png` | `2898a0036309d86966b90231544e9709fdbc234fae94e75610e1e205b1dec8c3` | `457499e779ff31e8f766acc2d6724aa09f4939536a8c9079da6d9810b7152590` |
| 6 | `optional-visual-06-night-large-light-off.png` | `f65f0db66a42efb4bda7e9bfd8af59f070ba1e7ec7c89cbcc682201ce322dd69` | `e7a5322bddeb055a86be55fef33fb63568e69d0e188c8a1b4b585db144e8de1d` |
| 7 | `optional-visual-07-void-none.png` | `a2978f4963061069516f8c3760ca5bfd0875dd6f544f9b1c6c0355008edeb1df` | `acb7890e36c89c640be4b2eb788cab7ba934eda5b6dd055404ed0947032c06f4` |
| 8 | `optional-visual-08-reopen-void-none.png` | `042a6301c845e7e0cbdc4f103c4c5c170331f687e9ab2de51afed217603a5aa4` | `a5ca5ef1eb839305f58f75405906c43be1cfbafe680e344f4a6da0df44d2696b` |

## Visual review

Both contact sheets contain the complete ordered set and are materially
loader-matched. Capture 01 visibly shows a real staged preview replacing only
part of the placeholder surface. Capture 02 shows completed Atlas terrain.
Captures 03-04 show the textured custom prism on both rim faces without a seam
break. Captures 05-06 show the night/large profile and a visibly distinct
authored light state. Captures 07-08 show the same Void/None presentation
before disconnect and after reopening. Transient nearby chunks and entities
differ slightly between loaders, as expected for separately timed clients.

## Discarded attempts and limitations

Three earlier Fabric attempts were excluded before the final replacement:

1. an opt-in fixture lifecycle error consumed the menu tick before the
   existing Fabric start-world fallback;
2. the first in-world flow exposed the genuine client-command root-shadowing
   product defect fixed by `d893f01`; and
3. the first post-fix run reached complete Atlas but stalled because a
   partial-only section-readiness check had been applied to all poses.

The controller authorized the corrected Fabric replacement after each
classification. NeoForge required no replacement. Only the final Fabric and
single NeoForge PASS roots are retained as qualification evidence.

Both clients logged nonfatal Mojang profile/public-key connection timeouts.
Fabric also warns that its literal local `ringlights` child overlaps the
greedy server-fallback child; Brigadier literal precedence is retained and the
focused routing test proves local `ringlights` plus forwarding of sky, sun,
root, and unknown children. The warning is a cosmetic integration limitation,
not a failed command route.

This is intentionally a lean smoke. It does not cover a broad visual matrix,
Create or other third-party mods, multiplayer, dedicated servers, production
world size, packaging, frozen jars, launchers, Linux, macOS, or release
metadata.
