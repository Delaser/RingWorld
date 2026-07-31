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

On every start, each launcher copies the packaged RingWorld and Fabric API jars
plus `mmc-pack.json` into the existing `RingWorld-Test` Prism instance. It
removes superseded jars with those two managed filename prefixes. It does not
replace Prism accounts, saves, options, screenshots, resource packs, an
existing RingWorld config, or user-edited `instance.cfg`.

This makes an in-place bundle update safe:

1. close Minecraft;
2. extract a newly downloaded bundle over the existing bundle directory;
3. run the platform launcher again.

The refreshed launcher source then updates the private `.prism-data` instance
without requiring another Microsoft login or deleting user state.

Keep the three templates synchronized with their copies in the generated
bundles. Validate both a fresh directory and an existing instance containing
sentinel account/save/config files before publishing.
