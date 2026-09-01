package dev.ringworld.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * Adds a fail-through branch to a client-owned command root that is also
 * owned by the server. Both 1.21.1 loaders treat Brigadier's unknown-command
 * signal as permission to send the original command to the server.
 */
public final class RingClientCommandRouting {
    private static final String SERVER_FALLBACK_ARGUMENT = "ringworldServerCommand";

    private RingClientCommandRouting() { }

    public static <S> LiteralArgumentBuilder<S> withServerFallback(
            LiteralArgumentBuilder<S> root) {
        return root.executes(context -> forwardToServer())
                .then(RequiredArgumentBuilder.<S, String>argument(
                                SERVER_FALLBACK_ARGUMENT,
                                StringArgumentType.greedyString())
                        .executes(context -> forwardToServer()));
    }

    private static int forwardToServer() throws CommandSyntaxException {
        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS
                .dispatcherUnknownCommand().create();
    }
}
