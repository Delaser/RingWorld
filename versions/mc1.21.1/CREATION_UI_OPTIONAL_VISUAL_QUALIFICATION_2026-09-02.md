# 1.21.1 optional-visual creation UI qualification — 2026-09-02

## Result

The expanded menu-only creation/settings fixture passes on both 1.21.1
loaders from fixture commit
`16202ed560504d51586f7778076a88405ca886a1`, based on documented checkpoint
`154509f4dd8cc6f8f199f3c4bf4770fcd0c2b86d`.

| Loader | Result | Captures | `level.dat` files | Contact-sheet SHA-256 |
| --- | --- | ---: | ---: | --- |
| Fabric | PASS | 17/17 | 0 | `7f57e26f1403b72cbfa36810a73d2f02b603c8c60062f909c6dd19d9f4ff0181` |
| NeoForge | PASS | 17/17 | 0 | `163b9f5eac04d66b07465625fe1d75df19f97ed89dfb9ba64ec025d99f9f6d3d` |

No product defect was found and no replacement run was required. The fixture
does not call world creation or open a world.

## Invocation and scope

The successful Windows Java 21 command was:

```powershell
.\gradlew.bat runCreationUiClient --no-daemon --max-workers=1
```

Gradle's unqualified selector matched and ran one root Fabric task and one
NeoForge task in the same successful invocation. It completed in 2m28s with
21 actionable tasks and `BUILD SUCCESSFUL`.

The flow covers:

- the retained footer, default editor, five-error invalid state, Small,
  Medium, Large, custom, confirmation, and applied-footer views at the
  established GUI scales;
- default and Overgrown wall selections, including the 320-pixel-wide logical
  editor;
- independent sky and sun selection, finishing at Night and Large;
- seed previews for `12345` and `67890`, with distinct identities
  `a910bdd6d2eda84e` and `b47942971fbe2941`;
- a centered seam and exact `64:1` preview aspect;
- cancellation of an in-flight preview after an edit; and
- normal close, preview-worker cancellation, dynamic-texture release, and
  return to the parent screen.

Both loaders finished with the same applied values:

```text
circumference=4096
width=640
wallHeight=192
skyBackdrop=NIGHT
sunStyle=LARGE
requestOceanMonument=true
testMode=false
pregenerateTerrainAtlas=false
```

Focused Java creation-model/request-gate/source-contract tests, both client
compile graphs, the relevant Python Gradle-runner tests, the external capture
contract test, and Python bytecode compilation passed before the graphical
run.

## Evidence roots

Fabric evidence is under
`C:\Users\Admin\.codex\worktrees\f862\RingWorld\run-creation-ui`:

- `logs\latest.log`: 18,607 bytes, SHA-256
  `c021fe37c129a6af821172eb289629950bcf40bd264fd21593932a9fba77007b`;
- `evidence\creation-ui-contact-sheet.png`: 635,738 bytes, SHA-256
  `7f57e26f1403b72cbfa36810a73d2f02b603c8c60062f909c6dd19d9f4ff0181`.

NeoForge evidence is under
`C:\Users\Admin\.codex\worktrees\f862\RingWorld\neoforge\run-creation-ui`:

- `logs\latest.log`: 22,080 bytes, SHA-256
  `034ff930e0188d2edc2b191950669a46b4b7fa740356330fa45bfd9cd645ca27`;
- `evidence\creation-ui-contact-sheet.png`: 662,474 bytes, SHA-256
  `163b9f5eac04d66b07465625fe1d75df19f97ed89dfb9ba64ec025d99f9f6d3d`.

These run directories are disposable and ignored; the hashes, not committed
generated images, bind this checkpoint to the reviewed local evidence.

## Capture manifests

The filename order is identical on both loaders.

| # | Capture | Fabric SHA-256 | NeoForge SHA-256 |
| ---: | --- | --- | --- |
| 1 | `creation-ui-01-footer-scale1.png` | `d7cb8a47a2c8e7bdf115086e186a92c98d3c9f36ab35200539e530e805a95a2e` | `b49c2e7b8e4776a1031c3597da996d355b2b0e1ffdf5edc3ad47415e678da79f` |
| 2 | `creation-ui-02-default-scale1.png` | `35a70fc1b8d4e9c161d8b33f6341ee291cc9ff02d1bbbba46874604b07eb515b` | `d144e9603a237bf549217783e25557aaf857e9ae03a18365ba55a80c47ca0be3` |
| 3 | `creation-ui-03-default-scale2.png` | `4f55e46b832de89bb75386caee1a2d969013291df84d39faa1535b4a4f23f4c6` | `f1c8f57f927151c2ff11ffa888585176c578f0873e5ad00706a57075e1b0210d` |
| 4 | `creation-ui-04-default-scale3.png` | `57f48b7716c2f39bee5b898040c04c3b5dddec89491ca01646d2055753515c12` | `e264d07c5803799e0e61a8c3116d62fa8ef528a2112a64967458d596d9ab5af1` |
| 5 | `creation-ui-05-default-scale4.png` | `775d86568e81eb64c9f50047712ccaf73be594081d3ef619bc30b916f83e346d` | `6e388524421ed2cd05e4cdab2a03051c0d55db4ff5c3c15fc79924e7d8be7345` |
| 6 | `creation-ui-06-seed-preview-12345-scale4.png` | `f5901bf2225cc1437b2ec4c59d01d356f3d1fef3a6f21b8893fb3f1ac932cd83` | `e8da125bd90f5ba68fe5ba6852d6200cb82ed795b66f7de67a1ed5a9e346175a` |
| 7 | `creation-ui-07-seed-preview-67890-scale4.png` | `cd8d334a1fb3549c32b2913c43bd7d5cae159b04abe23cc0ef5206b9374b9fd7` | `0e8e395b9fbed6cdd0b569cddae577cdc2f15ee7fab4bbea5902e99970dc2bd4` |
| 8 | `creation-ui-08-rim-default-scale4.png` | `af754552e6d6c0c3db53f42f84ff2f5a10273d60c795f9bcc2c316887ba1c593` | `30a1c8f69ae47e08a2baaeb4ecfec412d579e72cf1940a23ed39af15167bee3f` |
| 9 | `creation-ui-09-rim-overgrown-narrow-scale4.png` | `46d7664dfd5f54d3670a6d5dea0c9f44c0cac1b0d59997f9d27db598a82c9b58` | `4c75e80965c4e051e725ea35964b819577e0b752c6a95a4686849e17921eef37` |
| 10 | `creation-ui-10-large-narrow-scale4.png` | `59870137f3f49b35139901214863486dfefb027d460aebfeb267de37e3ea7c6b` | `fe138187dd734f7318e371f7fe8e2ca16b3a0289e560b4b37a9ce7bd37fab789` |
| 11 | `creation-ui-11-invalid-five-errors-narrow-scale4.png` | `9923c2239ecbb691366ef705e04cb54b0d6ad182d64f7e1aa60993ae59e6b30b` | `53ec57ece33f0bec5a7f8a103a6dcf3d603e21fa57d25aa4cfdf248690e3a300` |
| 12 | `creation-ui-12-small-scale4.png` | `87448f65e276a4ee463b3db22a1d47c1f5ff95d0dfe25a42c5b95a05707cc001` | `95675e7762aae6cac11899f0a9a8967169f5e160f37a23963d46d9c67ae36391` |
| 13 | `creation-ui-13-medium-scale4.png` | `d27aadd722c3de3d3c35dc37808f198f61de1834641b6b0558553207b6a68e4f` | `60d47f06fbd10c88b44c9b9e66effe879b5b1e10fc2dccf7cc8cd701093ed16b` |
| 14 | `creation-ui-14-large-scale4.png` | `4101809362c8cc43e772802a1b330854b238664575cb1aa4266feec555c74634` | `e857c2034f654b7838e403faddebc9aafb134b2bc297c941c3d676f07b43e653` |
| 15 | `creation-ui-15-custom-monument-night-large-scale4.png` | `5219849876b8c04ae2a987af477a857ed9bda790e0b56437c7bf89eb6fcbbfdc` | `fee975083e648a9d446077e178d6830e770a54fd766ed791259edb5c3c85b2e4` |
| 16 | `creation-ui-16-confirm-layout-scale4.png` | `fc1aa5a0c2938eb08c30099f8d03e6cba86d7484fa6320dae4cf14c0b250f2d3` | `14dc7a28fb0fa9843a97ac26cc4fd9c5f177304b7bae6d3c6cfaaea25073c088` |
| 17 | `creation-ui-17-footer-applied-scale4.png` | `b00da260bd9ed2310b2d9d1f66cda5421e36220e6a992140682d6420ec186f21` | `c5eaee965e8bea788fe5f9be27ce30e3fdc0274946a94d27c9883590e586b2ed` |

## Visual review

Both contact sheets contain all seventeen ordered captures and are materially
loader-matched. The seed strips are visibly different, centered, and retain
the thin ring aspect. The default and Overgrown wall editors fit the narrow
layout; all five validation errors remain visible; preset, custom,
confirmation, and footer states are readable; and the custom capture visibly
selects Night and Large. No clipping, overlap, stale texture, or inconsistent
loader state was observed.

## Failures and limits

The first command attempt stopped before Minecraft launched because Loom's
`downloadAssets` task exhausted three download attempts. Re-running after the
partial asset cache was populated completed both loaders. That first attempt
is infrastructure/prelaunch failure evidence, not a fixture failure or PASS.
The successful clients also logged nonfatal Mojang authentication/profile-key
connection timeouts; these did not affect the offline menu fixture.

A broad macOS external-runner unit suite still has one Windows-host path-length
failure before it reaches capture validation. The focused external capture
contract passed, so that unrelated host limitation does not change this local
Gradle result.

This checkpoint is Windows Java 21 source/development-client evidence. It is
not a packaged-release, Linux, macOS, in-world rendering, Atlas-light, live
command, multiplayer, lifecycle, or production-launcher claim.
