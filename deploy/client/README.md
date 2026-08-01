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

Use `scripts/prepare_release_packages.py` with an explicit final RingWorld jar,
matching Fabric API jar, clean Prism instance template, and full 40-character
public source revision. It creates reproducible macOS/universal and Windows
ZIPs plus a separately labelled server overlay. It never downloads a launcher,
opens Prism, creates website files, uploads, deploys, or changes a live service.

The clean instance template must contain `mmc-pack.json`, `instance.cfg`, and
`.minecraft/config/ringworld.properties`. It must not contain managed mod jars,
accounts, saves, options, screenshots, resource packs, logs, source artifacts,
or other runtime state. The generated `PACKAGE-MANIFEST.json` points to the
exact MPL-covered public source revision, and root `SHA256SUMS.txt` covers every
archive.

On every start, each launcher copies the packaged RingWorld and Fabric API jars
plus `mmc-pack.json` into the existing `RingWorld-Test` Prism instance. It
removes superseded jars with those two managed filename prefixes. It does not
replace Prism accounts, saves, options, screenshots, resource packs, an
existing RingWorld config, or unrelated `instance.cfg` values. On macOS it
validates the existing Java path plus common system, Homebrew, SDK, and
user-local locations. A detected Java 25 runtime is selected explicitly; an
older override is never reused. If no Java 25 runtime is found, it sets
`AutomaticJava=true` and `OverrideJavaLocation=false` so Prism can install or
select one. The Windows launcher retains that automatic Prism path.

This makes an in-place bundle update safe:

1. close Minecraft;
2. extract a newly downloaded bundle over the existing bundle directory;
3. run the platform launcher again.

The refreshed launcher source then updates the private `.prism-data` instance
without requiring another Microsoft login or deleting user state.

Keep the three templates synchronized with their copies in the generated
bundles. Validate both a fresh directory and an existing instance containing
sentinel account/save/config files before publishing.

`package-windows.yml` executes the Windows batch/PowerShell update path on a
Windows runner whenever package inputs change. It uses a local harmless Prism
stand-in and makes no account, network-login, or Minecraft launch claim; an
actual graphical Windows client remains a release-candidate runtime gate.
