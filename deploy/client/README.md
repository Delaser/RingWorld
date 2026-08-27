# Shareable client launcher templates

These files are the versioned source for the launchers placed in the
credential-free bundles under `dist/`.

Every outer bundle and nested Prism instance must include the current
top-level `LICENSE`. The managed RingWorld jar must declare
`MPL-2.0` and embed `LICENSE-RINGWORLD.txt`. Packaging must fail if the jar
declares MIT, the retired evaluation identifier, or omits the embedded
licence. Every published build must identify a timely, reasonable way for
recipients to obtain the corresponding MPL-covered source at the exact release
revision. See [`docs/LICENSING.md`](../../docs/LICENSING.md).

Validate generated artifacts with
`scripts/verify_distribution_license.py` before distribution.

## Optional 26.1.2 package assembly

Use `scripts/prepare_release_packages.py` with an explicit `--loader fabric`
or `--loader neoforge`, final matching RingWorld jar, clean loader-specific
Prism template, and the matching manifest produced by the fail-closed Modrinth
staging gate. Fabric requires the matching Fabric API jar; NeoForge must not be
given Fabric API. It creates
reproducible macOS/universal and Windows ZIPs plus a separately labelled server
overlay. It never downloads a launcher, opens Prism, creates website files,
uploads, deploys, or changes a live service.

The clean instance template must contain `mmc-pack.json`, `instance.cfg`, and
`.minecraft/config/ringworld.properties`. It must not contain managed mod jars,
accounts, saves, options, screenshots, resource packs, logs, source artifacts,
or other runtime state. The generated `PACKAGE-MANIFEST.json` points to the
exact MPL-covered public source revision, and root `SHA256SUMS.txt` covers every
archive.

## Qualified NeoForge packages when Prism metadata lags

For a format-1 qualified stage, the optional `--neoforge-installer PATH` accepts
only the exact official installer SHA-256 pinned by `--runtime-cell` in the
selected qualification manifest. The builder reads its client/profile metadata
and generates Prism's native `patches/net.neoforged.json`, including official
download URLs, sizes and hashes plus the reviewed Prism ForgeWrapper. It does
not execute or bundle the installer, NeoForge libraries, or Minecraft jars.
Prism downloads/verifies those files at launch. No loader version is substituted.
An unpinned, wrong-version or malformed installer fails package assembly.

Example for the staged 26.2 candidate:

```sh
python3 scripts/prepare_release_packages.py --loader neoforge \
  --stage-manifest dist/qualified-release/fixture-fix-20260827/1.1.0+mc26.2/neoforge/STAGING-MANIFEST.json \
  --qualification-manifest config/minecraft-version-matrix-26.2.json \
  --runtime-cell 26.2-neoforge \
  --neoforge-installer /path/to/official/neoforge-26.2.0.69-installer.jar \
  --output dist/new-neoforge-review
```

The identical component is included in the nested Prism import ZIP and both OS
bundles, and its SHA-256 is recorded in `PACKAGE-MANIFEST.json`. Each launcher
updates only this owned component and its ownership marker. A later package
without the fallback removes only a previously RingWorld-owned loader patch;
unrelated custom components and player data are preserved. Do not place a
hand-written loader patch in the clean instance template to bypass the pin.
This is launcher metadata compatibility, not a new mod-runtime qualification.

Client packages generate a minimal `servers.dat` containing only **RingWorld
Test Server** at `andwhatnotstudio.com:25565`. It is a public convenience entry,
not client state, and the launcher writes it only while creating its managed
instance; existing server lists are preserved. The bundle never auto-joins it.

On every start, each launcher copies its packaged RingWorld jar plus
`mmc-pack.json` into its loader-specific Prism instance: `RingWorld-Test` for
Fabric and `RingWorld-NeoForge` for NeoForge. The separate instance IDs prevent
a loader change from carrying arbitrary Fabric-only or NeoForge-only mods into
the other runtime. The package's loader marker uses fixed LF bytes on every
assembly platform. Fabric launchers additionally refresh only their managed
Fabric API jar; NeoForge does not bundle or manage Fabric API. Neither launcher
replaces Prism accounts, saves, options, screenshots, resource packs, an
existing RingWorld config, unrelated mods, or unrelated `instance.cfg` values
inside its own instance. On macOS it
validates the existing Java path plus common system, Homebrew, SDK, and
user-local locations. A detected Java 25 runtime is selected explicitly; an
older override is never reused. If no Java 25 runtime is found, it sets
`AutomaticJava=true` and `OverrideJavaLocation=false` so Prism can install or
select one. The Windows launcher retains that automatic Prism path.

This makes an in-place update of the same loader safe:

1. close Minecraft;
2. extract a newly downloaded bundle over the existing bundle directory;
3. run the platform launcher again.

The refreshed launcher source then updates that loader's private `.prism-data`
instance without requiring another Microsoft login or deleting user state. A
different-loader bundle creates or updates its separate instance instead.

Keep the three templates synchronized with their copies in the generated
bundles. Validate both a fresh directory and an existing instance containing
sentinel account/save/config files before publishing.

`package-windows.yml` executes the Windows batch/PowerShell update path on a
Windows runner whenever package inputs change. It uses a local harmless Prism
stand-in and makes no account, network-login, or Minecraft launch claim; an
actual graphical Windows client remains a release-candidate runtime gate.
The fixture adds `/wait` to its temporary launcher's final stub launch so
Windows can release the executable before cleanup; shipped launchers retain
their normal detached Prism launch.
