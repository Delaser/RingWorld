# Independent review: 1.21.1 optional-visual in-world smoke

Date: 2026-09-02

Verdict: **PASS**

Findings: no remaining findings; one genuine product defect and two fixture
readiness defects were corrected before the retained runs.

## Reviewed checkpoint

- documented base: `18674e3464425dd1b45857dc991e5ceacbce81be`;
- opt-in fixture: `dfc55655af5044b5433b84edc4343dddc914f424`;
- command-routing product fix:
  `d893f0171a7c5293756c6998900bca8e1965f48b`;
- phase-specific fixture readiness:
  `94544ea6a2d80bdb7cefdb16f0ac36f46ffaba68`;
- evidence/documentation:
  `b660151167e8d6319dd54240d29780c90ceb4457`.

The chain is linear and clean, and `git diff --check` passes. The implementation
touches one existing loader-neutral Atlas client, the two loader run adapters,
one narrow command-routing helper, shared Gradle verification, source/unit
contracts, and the disposable-run ignore entry. It adds no qualification
matrix or runner script and does not touch Create, packaging, multiplayer,
world-generation algorithms, production dimensions, or release metadata.

## Fixture and source review

The smoke is an explicit opt-in phase over the existing integrated safe-small
Atlas client. The ordinary Atlas-UI path is unchanged unless
`ringworld.optionalVisualSmoke` is true. Fabric and NeoForge use the same seed,
2,048x128 bootstrap dimensions, nine-block Industrial/Hybrid/37% custom wall,
Atmosphere/Small initial profile, eight capture names, and shared verifier.

The partial capture is fail-closed. It requires an incomplete Atlas, a staged
preview, real renderer ownership, an active shader draw, all live sections
rendered, and sixty settled frames. A review finding against the initial
optional seven-capture path was corrected before the fixture commit: Atlas
completion can no longer replace the mandatory staged visual with a log-only
claim. The verifier requires exactly all eight non-uniform PNGs.

The later complete-Atlas, rim, profile, and lighting poses require the complete
Atlas, an active proxy draw, and sixty settled frames. They deliberately do not
require `hasRenderedAllSections()`: that live-section condition is relevant to
the incomplete live/Atlas handoff but can remain false after a long teleport
or for an exterior finite-band wall view. Commit `94544ea` makes only that
phase-specific distinction and does not weaken the partial evidence.

The custom-wall captures use the saved style and paired inner/top and
outer/top poses adjacent to canonical seam X=2. The profile steps issue the
real `/ringworld sky` and `/ringworld sun` commands, await the client-visible
authoritative profiles, and preserve those settings through normal save,
disconnect, raw session teardown, and same-world reopen. The authored-light
probe selects one zero-baseline Atlas cell, observes ordered Atlas revisions
for a lit redstone lamp (15) and its unlit state (0), and captures both at the
same Night/Large pose.

## Product defect and command-routing fix

The first in-world Fabric flow exposed a genuine shared defect: registering
the local `/ringworld ringlights` branch caused the client dispatcher to own
the whole `ringworld` root, so real `/ringworld sky` and `/ringworld sun`
commands were rejected before reaching the server.

Commit `d893f01` retains the literal local `ringlights` branch and adds one
greedy fallback whose Brigadier unknown-command signal tells both loader
dispatchers to send every other original `ringworld` command to the server.
Focused tests prove local `ringlights`, and forwarding signals for root, sky,
sun, and unknown children. Both retained clients independently prove actual
server receipt and chat acknowledgement of Night/Large and Void/None. The fix
does not rename the public command, introduce a new packet, or intercept
unrelated roots.

## Retained evidence audit

| Loader | Captures | Worlds | Log SHA-256 | Contact-sheet SHA-256 |
| --- | ---: | ---: | --- | --- |
| Fabric | 8/8 | 1 | `92249e90fed83ddb1ea18fa53a779666c466fa4471567a6d9755a9c1cbc81b04` | `f24ddce179eafd3c8ebd95ab8627e2be6fcfd6936b7d42e4957fc1ae93581faf` |
| NeoForge | 8/8 | 1 | `a3dba6a2d177c79d2c526436b00f315ddc304ce5734dee439641f363f4ca7629` | `f1f71f77422c317236125f65012db19dc178645ddb94eef5eee4623382059bec` |

The retained evidence roots are:

- Fabric:
  `C:\Users\Admin\.codex\worktrees\f862\RingWorld\run-optional-visual-smoke`;
- NeoForge:
  `C:\Users\Admin\.codex\worktrees\f862\RingWorld\neoforge\run-optional-visual-smoke`.

An independent file walk found exactly eight named captures and exactly one
`level.dat` per loader. All sixteen screenshot hashes in the evidence document
were recomputed and match. Both log and contact-sheet byte sizes and SHA-256
values also match the documentation.

Fabric captured the mandatory current preview at 560/4,096 cells; NeoForge at
652/4,096. Both advanced to a high preview and then 4,096/4,096 terrain-height
Atlas rendering. Both server logs and client chat confirm Night/Large and
Void/None commands. Both Atlas light probes record baseline 0, lit 15 at
revision 6, and restored 0 at revision 7. Both logs record two complete raw
session/renderer/texture clears, reopen of the same named world with the
complete Atlas, custom wall and Void/None state, and terminal PASS with eight
captures. No retained log contains a command syntax error or fixture FAIL.

## Visual review

Both contact sheets were inspected at original detail and are materially
loader-matched. Capture 01 is visibly blurred/incomplete and capture 02 is a
crisp completed ring surface. The paired custom-wall views show the textured
prism closing through the seam without a visible break. The Night/Large
lighting pair has an obvious authored bright cell when on and no such cell
when off. Void/None before disconnect and after reopen are coherent on each
loader. Small transient chunk, foliage, and entity differences are consistent
with separately timed same-seed clients and do not change the reviewed state.

## Excluded attempts and limits

Three Fabric attempts are correctly excluded: the first was consumed at the
menu by a fixture lifecycle predicate; the second exposed the genuine command
root defect; the third stalled because the partial-only section-readiness
condition was applied to complete/exterior poses. Preparation deletes each
prior run root, so only the corrected replacement is retained. NeoForge needed
no replacement.

This PASS is bounded Windows Java 21 source/development-client evidence. It is
not a broad renderer matrix, production-size world, multiplayer or dedicated
server gate, third-party compatibility result, packaged/frozen artifact,
launcher, Linux, macOS, or release claim.
