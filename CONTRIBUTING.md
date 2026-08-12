# Contributing to RingWorld

Bug reports, reproduction steps, compatibility observations, performance
measurements, documentation improvements, and code contributions are welcome.
For substantial changes, open an issue before implementation so topology,
loader boundaries, protocol compatibility, and validation scope can be agreed.

## Contribution licence

RingWorld is licensed under the Mozilla Public License 2.0. By submitting a
copyrightable contribution for inclusion in RingWorld, you agree to license
that contribution under MPL-2.0 and represent that you have the right to do so.
You retain copyright in your contribution.

Do not submit decompiled or otherwise unauthorised Minecraft code, third-party
assets, generated code with incompatible terms, credentials, private runtime
data, or code copied from a project whose licence is incompatible with
MPL-2.0. Explain the origin and licence of any new dependency or bundled asset.

Opening an issue, discussing an idea, or providing non-copyrightable factual
information does not transfer ownership.

## Pull requests

Contributions should:

1. start from an issue or clearly explain their scope;
2. preserve the coordinate and storage invariants in `AGENTS.md`;
3. keep new gameplay and geometry logic loader-agnostic where practical;
4. include focused tests appropriate to the change;
5. update all affected documentation in the same change; and
6. avoid unrelated formatting or generated-runtime changes.

Maintainers may ask for additional runtime evidence for rendering, topology,
networking, world-generation, or multiplayer changes. A green compile alone is
not sufficient evidence for those areas.

## Forks, modpacks, and ports

MPL-2.0 permits forks, modpack inclusion, compatibility patches, and loader
ports without separate permission, provided its conditions are followed.
Modified RingWorld files remain MPL-2.0 when distributed. Separate files in a
larger work may use another licence.

Forks must not imply endorsement or official status. The MPL does not grant
rights to RingWorld trademarks, logos, or release branding.

See `docs/LICENSING.md` for practical source-availability and binary-
distribution guidance.

## Minecraft 1.21.1 backport

The coordinated 1.21.1 backport uses the temporary integration branch
`port/mc-1.21.1`. Contributors should begin with an assigned `mc:1.21.1`
issue, branch from that integration branch, and open their pull request back
against it. Do not target incomplete backport slices directly at `main`.

Read `versions/mc1.21.1/README.md` before changing build inputs, mappings,
mixins, world generation, networking, or rendering. Shared RingWorld behavior
must remain shared; version-specific code belongs behind the narrowest
practical adapter.
