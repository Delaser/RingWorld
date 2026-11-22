package dev.ringworld.world;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

/** Client chat validation metadata; actual execution stays in the loader's client dispatcher. */
public final class RingLodCommandSuggestions {
    private RingLodCommandSuggestions() {}

    public static <S> LiteralArgumentBuilder<S> band(Command<S> show, Command<S> set) {
        return LiteralArgumentBuilder.<S>literal("band").executes(show)
                .then(RequiredArgumentBuilder.<S, Float>argument("width",
                        FloatArgumentType.floatArg(RingLayerBand.MIN_BAND, RingLayerBand.MAX_BAND)).executes(set));
    }

    public static <S> LiteralArgumentBuilder<S> shaderSupport(Command<S> show, Command<S> on, Command<S> off) {
        return LiteralArgumentBuilder.<S>literal("shadersupport").executes(show)
                .then(LiteralArgumentBuilder.<S>literal("on").executes(on))
                .then(LiteralArgumentBuilder.<S>literal("off").executes(off));
    }

    public static <S> void ensurePresent(CommandDispatcher<S> dispatcher) {
        var root = dispatcher.getRoot().getChild("ringworld");
        if (root == null || root.getChild("shadersupport") == null)
            dispatcher.register(LiteralArgumentBuilder.<S>literal("ringworld").then(
                    shaderSupport(context -> 0, context -> 0, context -> 0)));
        if (root == null || root.getChild("distortion") == null) {
            var distortion = LiteralArgumentBuilder.<S>literal("distortion").executes(context -> 0);
            for (String action : new String[]{"on", "off", "show"})
                distortion.then(LiteralArgumentBuilder.<S>literal(action).executes(context -> 0));
            distortion.then(LiteralArgumentBuilder.<S>literal("distance").then(
                    com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, Integer>argument("chunks",
                            com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
                            .executes(context -> 0)));
            dispatcher.register(LiteralArgumentBuilder.<S>literal("ringworld").then(distortion));
        }
        if (root != null && root.getChild("lod") != null && root.getChild("lod").getChild("band") != null) return;
        var lod = LiteralArgumentBuilder.<S>literal("lod").executes(context -> 0);
        lod.then(band(context -> 0, context -> 0));
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
