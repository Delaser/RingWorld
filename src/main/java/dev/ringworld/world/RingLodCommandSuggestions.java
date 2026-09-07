package dev.ringworld.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/** Client chat validation metadata; actual execution stays in the loader's client dispatcher. */
public final class RingLodCommandSuggestions {
    private RingLodCommandSuggestions() {}

    public static <S> void ensurePresent(CommandDispatcher<S> dispatcher) {
        var root = dispatcher.getRoot().getChild("ringworld");
        if (root != null && root.getChild("lod") != null) return;
        var lod = LiteralArgumentBuilder.<S>literal("lod").executes(context -> 0);
        lod.then(LiteralArgumentBuilder.<S>literal("show").executes(context -> 0));
        lod.then(LiteralArgumentBuilder.<S>literal("reset").executes(context -> 0));
        for (var quality : RingLodQuality.values()) {
            lod.then(LiteralArgumentBuilder.<S>literal(quality.command()).executes(context -> 0));
        }
        // Build all descendants before merging: an existing server root keeps its
        // identity, so adding descendants to a detached clone afterwards loses them.
        dispatcher.register(LiteralArgumentBuilder.<S>literal("ringworld").then(lod));
    }
}
