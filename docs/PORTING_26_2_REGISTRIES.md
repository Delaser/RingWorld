# 26.2 runtime-fixture registry port

Minecraft 26.2 removed generated `Blocks`, `Items`, and `EntityType` constants
used by RingWorld's opt-in server and client runtime smoke fixtures, including
coloured beds/concrete/wool, fixture blocks and items, lightning bolts, boats,
armour stands, mobs, and copper golems. Those fixtures now resolve the same
vanilla identifiers through `BuiltInRegistries`, fail closed if an entry is
absent, and validate an entity type's base class before making the required
generic cast. This works with both 26.1.2 and 26.2 without changing fixture
materials or entity behavior.

26.2 also removed the individual coal, redstone, lapis, diamond, and emerald
ore block tags.  The stronghold fixture therefore names each corresponding
overworld and deepslate vanilla ore block explicitly.  It does not treat a
missing tag as an empty assertion.
