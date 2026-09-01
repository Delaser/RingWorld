# Minecraft 1.21.1 optional-visual integration manifest — 2026-09-02

## Purpose and authority

This is the authoritative integration index for the post-1.1 optional-visual
backport on local branch `codex/mc1211-optional-visuals`. It joins the exact
mainline behavior sources, the linear 1.21.1 implementation and evidence
history through `b660151167e8d6319dd54240d29780c90ceb4457`, and the independent
documentation-only reviews consolidated after that checkpoint.

This manifest records source and local qualification state. It does not change
Minecraft support, release metadata, packaging, or published-artifact claims.
The optional-visual work is separate from the pre-existing experimental Create
compatibility records and does not qualify Fabric or NeoForge Create.

## Mainline behavior mapped to the backport

The behavioral authority remains mainline range
`7903311..27335816b8c7671fc54b8f1ddecd89669f94d295` from PR #244. It is not a
safe mechanical cherry-pick onto Minecraft 1.21.1.

| Mainline change | 1.21.1 realization |
| --- | --- |
| `b4044959a5dfe53f5e862c78ee1f6b8e07e6bb00` — measured mycelium Atlas colour | Atlas model/persistence foundation `57cc7fd63f81f3c21da95840b9b5ba9544e1c800` and service integration `61ee9ee0b8bb78bee6537aaf6c24026406a7110c` |
| `71a10983996d722be71a4868a8a94bc4d33dbbd3` — configurable wall and independent sky/sun profiles | model/storage `57cc7fd`, server/worldgen `d04f3e7`, fail-closed migration fix `c4a46d1`, protocol `97dd53c`/`a5d809a`, creation UI `dc4e025`, wall GPU `5b4956e`, sky rendering `8568f49`, and live commands `6030719` |
| `0ccd02e77efffaa4a64769227e00a8e101c0cbfe` — completed Atlas visual batch | Atlas service and previews `61ee9ee`/`7e95b6a`, placeholder/wall surface `98c0c9e`, wall/light/sky/fog GPU stages `5b4956e`/`d10ece2`/`8568f49`/`99c0bdc`, local light tuning `0dea7ba`, compact UI `341bdcd`, and the two retained graphical evidence stages |
| `27335816b8c7671fc54b8f1ddecd89669f94d295` — version-adapter GUI-capture contract | adapted rather than copied by the 1.21.1 creation fixture `16202ed` and optional in-world fixture `dfc5565`; it adds no separate player/runtime behavior |

Four later mainline corrections named by the technical datasheet are required
parts of the reproduced contract:

- seam sampling correction `b384211` is present in `57cc7fd`;
- isolated preview request gate `87e41bf` is present in `57cc7fd`;
- centred preview display seam `892fa43` is present in `57cc7fd`; and
- client preview/session teardown correction `9852430` is present in
  `a5d809a609e1da7331abc06d2608d5d6596d5c7f`.

Backport review found two additional required product corrections:

- `c4a46d1078dd8901b34645d3d1e4b2f703276072` disables unsafe automatic
  content-detected rim rewriting for every format-4 world; and
- `d893f0171a7c5293756c6998900bca8e1965f48b` preserves the client-local
  `/ringworld ringlights` literal while forwarding the shared root, sky, sun,
  and unknown children to the server on both loaders.

`94544ea6a2d80bdb7cefdb16f0ac36f46ffaba68` is a fixture-only correction: it
keeps full-section readiness mandatory for the incomplete-Atlas capture but
does not impose that irrelevant gate on complete-Atlas poses.

## Exact 1.21.1 implementation, documentation, and review map

Every source stage below is in the direct ancestry of `b660151`. Documentation
commits are separate from their source/test commits. Review commits in this
table are the documentation-only copies on the consolidation branch.

| Stage | Source, fixture, or fix commit(s) | Checkpoint/evidence commit | Consolidated independent review |
| --- | --- | --- | --- |
| Model, storage, formats, stable IDs | `57cc7fd63f81f3c21da95840b9b5ba9544e1c800` | `ed2e51dd998918241f718613dbbce4d3790def27` | No separate supplied review record; covered by the technical datasheet and downstream protocol/storage reviews |
| Server config, wall generation, bounds | `d04f3e72fa10bdfba458eedd0bb265872b123ad0`; migration fix `c4a46d1078dd8901b34645d3d1e4b2f703276072` | `6b9aff9767b23e37213e5ee250ad8cb08dfe36e7`; `c56c0cd62ee038aaccf58ed8e58a6b35a78275d6` | [`23a3a65606468947464b1b9e3b3331ea74c68559`](OPTIONAL_VISUAL_SERVER_BATCH_REVIEW_2026-09-01.md) |
| Common protocol models | `97dd53ca5c4dce7cb348eb5b369bd3ec1bc70d05` | `d7f4f411fa8ec529dcb562ce5295d52be4458f3c` | [`563361b00faf7596b8e2d2d8863c176c84ad953c`](OPTIONAL_VISUAL_PROTOCOL_MODEL_REVIEW_2026-09-01.md) |
| Fabric/NeoForge transport and session | `a5d809a609e1da7331abc06d2608d5d6596d5c7f` | `637eb60473bfd5cc98e2420947b917f0b945dc5d` | [`000a26a94af97f245120544c470421a0059e4a1c`](OPTIONAL_VISUAL_PROTOCOL_TRANSPORT_REVIEW_2026-09-01.md) |
| Atlas format-8 service and light capture | `61ee9ee0b8bb78bee6537aaf6c24026406a7110c` | `a2ee13ab060c2926993449d511a89f4498f96006` | [`ef57216323e1a3394b1fcc656ad8b36a269aaad4`](OPTIONAL_VISUAL_ATLAS_FORMAT8_SERVICE_REVIEW_2026-09-01.md) |
| Staged server terrain previews | `7e95b6ac3e1733d864bd96a4b35c7addf842ab65` | `4295d0dfe795e6c98e7b34b1a80a7f82fa26c723` | [`4dbb973ed4051413e89a4887b3f018e22e80bf65`](OPTIONAL_VISUAL_SERVER_TERRAIN_PREVIEW_REVIEW_2026-09-01.md) |
| Creation UI and chunk-free seed preview | `dc4e025db1900c966133461feda30dba8b314949` | `0c16fd6cf84cfce631d0747637d1b8840cd46bf3` | [`b94d3fcfe8554a743fd93e103752c5e7eef5706e`](OPTIONAL_VISUAL_CREATION_UI_SEED_PREVIEW_REVIEW_2026-09-02.md) |
| CPU placeholder and closed wall mesh | `98c0c9e60f816010f5eefbf4e1ecd11e303a7e5f` | `90f57ab24a4dd445286667b26d94ccc2808cfddb` | [`ab5be11f118480178da32750d818f801d1a8ca7d`](OPTIONAL_VISUAL_CPU_SURFACE_WALL_MESH_REVIEW_2026-09-02.md) |
| Wall-appearance GPU adapter | `5b4956e75d6df4869b1b8be4906e5d53b1d6a900` | `b4cc14548514f06bc689a52231820a7409d60228` | [`0d8fdf2cd51c0afd82c0b5bcf8af915934647022`](OPTIONAL_VISUAL_WALL_APPEARANCE_GPU_REVIEW_2026-09-02.md) |
| Atlas block-light GPU | `d10ece21b4cde1f8491f09f84cc50ab84b5db1d4` | `8816cd0e6e2063557f6faf060ee3b7a7df0f7f39` | [`eb3680334cbc74bc450f47f3f32787bbdaf58225`](OPTIONAL_VISUAL_ATLAS_BLOCK_LIGHT_RENDERER_REVIEW_2026-09-02.md) |
| Sky-profile rendering | `8568f49b56f996e4eea02788db6d8903c39c3556` | `a85b0c880a52a94ec242b1c37df1181c6401663e` | [`f97a040ac64447a037b9663a7a8290c3d3b8e4a5`](OPTIONAL_VISUAL_SKY_PROFILE_RENDERING_REVIEW_2026-09-02.md) |
| Fog and dark ring-edge matching | `99c0bdcdcf53e96ba841c245b8c0fc496014d723` | `dfd57c7457d31301607f21c764d91e81f1d172d0` | [`85dbafe28f9382ed844d0c2fad78b6e543b722d7`](OPTIONAL_VISUAL_FOG_RING_EDGE_REVIEW_2026-09-02.md) |
| Server-owned live sky/sun command | `60307195257d400ea8a3b97915dc970f79f1b06b` | `ce32ac26c668d8248fcf62c28e1447ba511506ea` | [`763f39cbf887a16ea3be83e9c24b22231e25c1ff`](OPTIONAL_VISUAL_LIVE_SKY_SUN_COMMAND_REVIEW_2026-09-02.md) |
| Process-local ring-light command | `0dea7bae9ab56558a6725341fd35c21aadf3f945` | `54f32f40b8320d37379e8afe4108e74d7c212aff` | [`32c00a84c9f82ed53983b8cb1afecd64f5f183ae`](OPTIONAL_VISUAL_CLIENT_RINGLIGHTS_COMMAND_REVIEW_2026-09-02.md) |
| Compact Atlas/preview UI | `341bdcd38d9bd6da003f950f34e2b155f9af6ae6` | `154509f4dd8cc6f8f199f3c4bf4770fcd0c2b86d` | [`2980216b92ecaecf32469d70a1b466f1643dc0ee`](OPTIONAL_VISUAL_COMPACT_ATLAS_PREVIEW_UI_REVIEW_2026-09-02.md) |
| Menu-only graphical qualification | fixture `16202ed560504d51586f7778076a88405ca886a1` | evidence `18674e3464425dd1b45857dc991e5ceacbce81be` | [`3ef2b8fc54a975f465f9b6a63f8f8e303468c11d`](CREATION_UI_OPTIONAL_VISUAL_QUALIFICATION_REVIEW_2026-09-02.md) |
| Integrated in-world smoke | fixture `dfc55655af5044b5433b84edc4343dddc914f424`; product fix `d893f0171a7c5293756c6998900bca8e1965f48b`; fixture fix `94544ea6a2d80bdb7cefdb16f0ac36f46ffaba68` | evidence `b660151167e8d6319dd54240d29780c90ceb4457` | [`cc703a35c13559151b7cce260839ca1b8bf901fe`](OPTIONAL_VISUAL_IN_WORLD_SMOKE_REVIEW_2026-09-02.md) |

## Review-object provenance

The supplied independent reviews formed their own linear history. They were
verified as commits and documentation-only path changes before consolidation.
This table preserves original object identity alongside the new branch commit:

| Original review object | Consolidated commit |
| --- | --- |
| `1412763c082a11b23bd2403c18716fb45cec34db` | `23a3a65606468947464b1b9e3b3331ea74c68559` |
| `40b9e6861633201dec7f85cb72608d323edee8ca` | `563361b00faf7596b8e2d2d8863c176c84ad953c` |
| `7eac60b10294fba1f3094001442e3442e2339fc3` | `000a26a94af97f245120544c470421a0059e4a1c` |
| `d22bf688096eedcfe1b285bbc5bcb722e7a0b17a` | `ef57216323e1a3394b1fcc656ad8b36a269aaad4` |
| `8fa5dd20621f72589808a14acea32efa316a8a82` | `4dbb973ed4051413e89a4887b3f018e22e80bf65` |
| `693296c75804f58bd1660895d14928a579e53c17` | `b94d3fcfe8554a743fd93e103752c5e7eef5706e` |
| `afba946bc391557302f92d72062d5107519045b6` | `ab5be11f118480178da32750d818f801d1a8ca7d` |
| `4a31e1f3ebe5839d33b1e0e283cfe9763fe6a78c` | `0d8fdf2cd51c0afd82c0b5bcf8af915934647022` |
| `af1b2fd6c6a4b88b56aa07b8f6addb5a8b729368` | `eb3680334cbc74bc450f47f3f32787bbdaf58225` |
| `2986852eb56c0889b02b46a49bd13d10a7346130` | `f97a040ac64447a037b9663a7a8290c3d3b8e4a5` |
| `33168fcae87becb54f51ecdf86652074de162d1f` | `85dbafe28f9382ed844d0c2fad78b6e543b722d7` |
| `2dc55fed40616cd3d5c5427b7fb7a65e5b155209` | `763f39cbf887a16ea3be83e9c24b22231e25c1ff` |
| `7fe2cce93e26d9eec8aaac9a947eee2ec614039a` | `32c00a84c9f82ed53983b8cb1afecd64f5f183ae` |
| `a87b42896fc189ec74716a70676dc8837dd914c3` | `2980216b92ecaecf32469d70a1b466f1643dc0ee` |
| `66f3be9a2ba6a0d004d812577c095dd8aea9fed0` | `3ef2b8fc54a975f465f9b6a63f8f8e303468c11d` |
| `8a5dbe24f413159b7e8780a03b00e0be39293e53` | `cc703a35c13559151b7cce260839ca1b8bf901fe` |

The first cherry-pick had one documentation-only modify/delete conflict because
the review file's earlier base version was absent at `b660151`. Consolidation
retained the reviewed final document. The other fifteen reviews applied
without conflict.

## What is runtime-qualified

Two bounded Windows Java 21 development-client results are retained and
independently reviewed on both Fabric and NeoForge:

1. The menu-only creation qualification at `18674e3` passes 17/17 captures per
   loader, two seed-preview identities, exact 64:1 aspect/seam placement,
   cancellation and texture cleanup, all wall controls, independent Night and
   Large choices, and zero `level.dat` files. See
   [`CREATION_UI_OPTIONAL_VISUAL_QUALIFICATION_2026-09-02.md`](CREATION_UI_OPTIONAL_VISUAL_QUALIFICATION_2026-09-02.md).
2. The integrated safe-small in-world smoke at `b660151` passes 8/8 captures
   and one saved/reopened world per loader: real staged incomplete preview,
   4,096/4,096 Atlas terrain, both custom-wall rim faces at the seam,
   Atmosphere/Small, live Night/Large, block light 15 then 0, live Void/None,
   normal disconnect/session teardown, and persisted reopen. See
   [`OPTIONAL_VISUAL_IN_WORLD_SMOKE_2026-09-02.md`](OPTIONAL_VISUAL_IN_WORLD_SMOKE_2026-09-02.md).

The implementation stages also have the focused dual-loader compile, unit,
source-contract, codec, descriptor, and shader-contract results recorded in the
technical datasheet and individual reviews. Those are static/source evidence,
not additional runtime or release qualification.

## What remains unqualified

Do not infer any of the following from the two retained graphical results:

- dedicated-server or two-client optional-visual behavior, including live
  delivery, capability mismatch, reconnect synchronization, or old-peer
  rejection at runtime;
- a live X=0-crossing 15-block Atlas-light invalidation footprint;
- the full 3x3 sky/sun matrix, half-lap physical star inversion, dusk rim-top
  horizon, weather/accessibility variants, or proof that vanilla gameplay
  clock and light mechanics remain unchanged through every profile;
- all ten wall presets and boundary thickness extremes in real generation,
  portal placement, reload, or explicit legacy-block migration tooling;
- production-size performance and rendering, a broad visual matrix, shader
  packs, renderer mods, or general third-party compatibility;
- packaged or frozen Fabric/NeoForge jars, licence/archive staging, public
  release artifacts, Linux, or macOS; or
- Create compatibility. Existing Create documents remain separate bounded WIP
  records and are neither changed nor incorporated by this branch.

No support, publication, or release metadata should change until the required
remaining gates pass on exact artifacts.

## How to resume

1. Start from `codex/mc1211-optional-visuals`, confirm a clean tree, record the
   exact current head, use Java 21, and retain the reviewed dependency cache and
   strict verification metadata. Never regenerate verification metadata for an
   ordinary build.
2. Read this manifest, the technical datasheet, and the review record for the
   subsystem being changed. Preserve settings format 4, fingerprint v3, Atlas
   format 8, `settings_v5`, `settings_ack_v3`, `sky_profile_v1`, and
   `terrain_preview_v2` exactly.
3. Select one remaining qualification boundary. Prefer dedicated/two-client
   transport and lifecycle, seam-crossing light invalidation, then the full
   sky/sun visual matrix before production/package work. Reuse the smallest
   existing fixture; do not create a broad replacement framework.
4. Treat a reproduced source/product defect separately from fixture-only
   corrections. Keep source, fixture, and documentation/evidence commits
   distinct and record every discarded attempt.
5. Re-run dual-loader compile/tests and the runtime gate proportional to the
   changed boundary. Record exact command, clean source head, hashes, world
   count, captures, teardown, warnings, and exclusions.
6. Keep Create and other compatibility work outside this integration lane.
   Do not update support/release metadata, package, or push until the owner
   explicitly authorizes the still-required gates.
