# 1.1 pre-publication owner handoff

Status: **three macOS package smokes pass; final package blocked by #234; do not publish.** This handoff records local staging
inputs and owner review work. The 20-item nightly review and all eight bounded
copied-world forward-upgrade routes have completed with independently rehashed
local evidence. This does not establish public 26.2 support.

## Local staged inputs

Use only these exact stages for the remaining owner checks.
Rehash the nested runtime jar before any owner review.

| Line / loader | Stage manifest and runtime jar | Frozen source | SHA-256 |
| --- | --- | --- | --- |
| 26.1.x Fabric | `dist/qualified-release/1.1.0+mc26.1/fabric/STAGING-MANIFEST.json` / `ringworld-1.1.0+mc26.1.jar` | `3e94b04f4b42aa22dbe3d57f2ac169745226ec05` | `cdf564d260a0c2405dafeeede6ec4abd14ae48cb4ab44ed233c6d380355d5663` |
| 26.1.x NeoForge | `dist/qualified-release/1.1.0+mc26.1/neoforge/STAGING-MANIFEST.json` / `ringworld-neoforge-1.1.0+mc26.1.jar` | `3e94b04f4b42aa22dbe3d57f2ac169745226ec05` | `0c2353032bc6bf9b308c6be58ada45a343ecb5ad838e393f3f4bc3526ef065e1` |
| 26.2 Fabric | `dist/qualified-release/fixture-fix-20260827/1.1.0+mc26.2/fabric/STAGING-MANIFEST.json` / `ringworld-1.1.0+mc26.2.jar` | `1cfac9b10648054e46f4303f6e8b87df9b9bcdba` | `dbc4d0ff170a8b3850c85edf859865e8ce10a12a7296a6f83dd324a193138949` |
| 26.2 NeoForge | `dist/qualified-release/fixture-fix-20260827/1.1.0+mc26.2/neoforge/STAGING-MANIFEST.json` / `ringworld-neoforge-1.1.0+mc26.2.jar` | `1cfac9b10648054e46f4303f6e8b87df9b9bcdba` | `15e2d2c9e84ed9a421351bce34ad5f24dfafe2349772cbe42bf34b8cda1ce0a5` |

The similarly named older 26.2 staging directory is superseded; do not select
it. The fixture-fix package review archives are under
`dist/qualified-package-review/fixture-fix-20260827/`. All four local 26.1.2
and 26.2 server-overlay smokes passed only as disposable localhost
startup/normal-stop checks; they do not cover installer/network provisioning,
graphical clients, or publication.

## Owner review checklist

Windows owner sign-off received on 2026-08-27: "seems fine windows good",
following delivery of `RingWorld-1.1-Windows-Testing-Kit-20260827.zip`
(SHA-256 `3fb9663645f1a38a8b107179ca7f44f76399a8a83f5417b4c0d85afb4aa86073`).
This accepts the Windows package-review gate as an owner-reported result;
it is not a new machine-evidence report or a claim of individually recorded
passes for every checklist action. It does not prove a fresh install of the
specific NeoForge 26.2 package now blocked by #234. The final
release/publication decision remains pending.

macOS preparation (2026-08-27): all four archive checksums, nested runtime-jar
hashes, MPL licences and pinned Minecraft/loader components verified. Exact
archives were extracted into new isolated folders under
`dist/macos-package-review-20260827/`; no account, options or saves were copied.
The 26.2 Fabric outer launcher detected Java 25 and downloaded official Prism
11.0.3. Owner authentication succeeded through the device-code flow. Separate
fresh imported instances in that authenticated test root pass the packaged
Atlas capture smoke on 26.1.2 Fabric, 26.1.2 NeoForge and 26.2 Fabric.
NeoForge 26.2 cannot launch: Prism's metadata URL for pinned 26.2.0.69 returns
404; its latest listed 26.2 build is 26.2.0.67. Track the exact-pin packaging
repair in [#234](https://github.com/Delaser/RingWorld/issues/234), then rerun only
that blocked case and verify its Windows import path. Do not downgrade the
qualified loader silently. Full evidence and scope limits are in the
[macOS review](MACOS_PACKAGE_REVIEW_2026-08-27.md).
Keep the test directory private: Prism now stores account credentials there.

1. On macOS, start fresh authenticated Prism instances for each loader and both
   version lines (26.1.x packages use 26.1.2; the separate line uses 26.2). Do not copy an
   existing account, options, saves, or instance data into the review.
2. On a real Windows host, test both Fabric and NeoForge packages for both lines. Open a new
   world and a representative reviewed world; check natural seam travel, Atlas
   completion, Nether transfer, normal save/disconnect, and reopen.
3. Review the ordinary visual baseline. The visible water-proxy transition
   matches that baseline; record it as such, not as a new rendering regression.
4. Reinspect the final staged jars, package checksums, licences, source URL,
   loader and game-version metadata. Then make the explicit final release
   go/no-go decision.

## Pause before publication

Do not merge, deploy, mutate a live world/server, or place tokens in the
repository. For every version line, host, and loader (four host×loader actions
per line), create fresh owner authorization bound to the exact staged source
revision and jar SHA-256, expiring within one hour. `--execute` also requires
the current clean pushed checkout to equal that stage's frozen source revision;
a tree-identical later commit does not satisfy the boundary. Keep credentials
only in the approved environment/credential store and record returned host file
IDs after the owner-authorized action.
