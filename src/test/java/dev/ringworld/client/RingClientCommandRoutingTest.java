package dev.ringworld.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingClientCommandRoutingTest {
    @Test
    void ringlightsExecutesLocallyWhileServerBranchesUseForwardingSignal()
            throws Exception {
        CommandDispatcher<Object> dispatcher = dispatcher();

        assertEquals(1, dispatcher.execute("ringworld ringlights", new Object()));
        assertForwarded(dispatcher, "ringworld sky night");
        assertForwarded(dispatcher, "ringworld sun large");
        assertForwarded(dispatcher, "ringworld unknown child");
        assertForwarded(dispatcher, "ringworld");
    }

    private static CommandDispatcher<Object> dispatcher() {
        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        var ringlights = LiteralArgumentBuilder.<Object>literal("ringlights")
                .executes(context -> 1);
        dispatcher.register(RingClientCommandRouting.withServerFallback(
                LiteralArgumentBuilder.<Object>literal("ringworld")
                        .then(ringlights)));
        return dispatcher;
    }

    private static void assertForwarded(
            CommandDispatcher<Object> dispatcher, String command) {
        CommandSyntaxException failure = assertThrows(
                CommandSyntaxException.class,
                () -> dispatcher.execute(command, new Object()));
        assertSame(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand(),
                failure.getType());
    }
}
