# 26.2 runtime-fixture registry port

Minecraft 26.2 removed generated `Blocks`, `Items`, and `EntityType` constants
used by RingWorld's opt-in server and client runtime smoke fixtures, including
coloured beds/concrete/wool, fixture blocks and items, lightning bolts, boats,
armour stands, mobs, and copper golems. Those fixtures now resolve the same
vanilla identifiers through `BuiltInRegistries`, fail closed if an entry is
absent, and validate the entity factory's actual created instance. In 26.2,
`oak_boat` intentionally reports the widened `Entity` base class even though
its factory creates a `Boat`, so descriptor-class checking would incorrectly
reject the unchanged fixture. This works with both 26.1.2 and 26.2 without
changing fixture materials or entity behavior.

26.2 also removed the individual coal, redstone, lapis, diamond, and emerald
ore block tags.  The stronghold fixture therefore names each corresponding
overworld and deepslate vanilla ore block explicitly.  It does not treat a
missing tag as an empty assertion.

The threshold-noise and coordinate-density marker mixins are source-ABI-owned:
26.1 retains its original threshold condition and `WeirdScaledSampler` target,
while 26.2 maps the cached `SurfaceRules.Context` 2D/3D suppliers and omits
the removed density class.

`WorldGenRegion` preserves the same seam-local post-processing redirect and
`getChunk(BlockPos)` call in 26.2, but renames the enclosing method to
`markPosForPostProcessing`; the shared mixin lists both ABI spellings.
