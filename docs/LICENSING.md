# RingWorld licensing guide

The authoritative licence is the top-level [`LICENSE`](../LICENSE): Mozilla
Public License Version 2.0 (`MPL-2.0`). This document is practical project
guidance, not a substitute for the licence text or legal advice.

## What MPL-2.0 covers

The licence applies to RingWorld source files carrying the project licence,
their executable form, and modifications of those files. A distributed
modified RingWorld file remains MPL-2.0. A new file that contains covered
RingWorld code is also a modification under the licence.

Separate files combined with RingWorld as a larger work may use other licence
terms. This allows ordinary modpacks, loader adapters, compatibility mods, and
unrelated mods to retain their own licences without relicensing the whole
collection.

MPL-2.0 permits private use, modification, redistribution, commercial use,
and charging for copies or services. It does not create a noncommercial or
free-of-charge restriction. Recipients retain the MPL rights in covered source
files, so a distributor cannot make its paid copy the exclusive lawful source
of those files.

## Distributing builds

When an MPL-covered executable is distributed, the distributor must:

1. keep the required copyright and licence notices;
2. make the corresponding MPL-covered Source Code Form available by reasonable
   means in a timely manner, at no more than the cost of distribution;
3. tell recipients how to obtain that source; and
4. avoid terms that restrict the recipients' MPL rights in the source.

An accessible public repository at the exact released revision is the planned
source-delivery method for official releases. Until that exists, do not publish
a newly built MPL executable unless its corresponding source is supplied by
another compliant method. A source link must identify the release revision or
tag rather than only the moving development branch.

Every RingWorld jar declares `MPL-2.0` and embeds the authoritative licence as
`LICENSE-RINGWORLD.txt`. Outer client and server packages also include the
licence beside their primary instructions. Packaging checks verify those
copies and reject the retired `MIT` and evaluation-licence metadata.

## Network use

Running a modified RingWorld build on a server without distributing the build
does not, by itself, trigger MPL source-distribution requirements. RingWorld is
not licensed under the GNU Affero General Public License.

## Contributions and relicensing

Contributions accepted through [`CONTRIBUTING.md`](../CONTRIBUTING.md) are
licensed under MPL-2.0. Contributors retain copyright in their contributions.
Changing the whole project to an incompatible licence later would ordinarily
require permission from all relevant copyright holders.

## Branding

MPL-2.0 does not grant trademark rights. Forks may accurately describe their
origin and licence but must not claim to be an official RingWorld release or
use official branding in a misleading way.

## Historical copies

Licences already granted for previously distributed copies are not revoked by
this change. The unintended MIT-labelled 0.1.0 test bundles and copies issued
under the RingWorld Evaluation License remain governed by the terms attached
to those copies. Repository versions explicitly released under MPL-2.0 use
MPL-2.0.
