package dev.ringworld.world;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingLodCommandSuggestionsTest {
    @Test void sharedServerRootRetainsCommandsAndAcceptsEveryClientQuality() throws Exception {
        var dispatcher = new CommandDispatcher<Object>();
        var serverRoot = dispatcher.register(LiteralArgumentBuilder.<Object>literal("ringworld")
                .then(LiteralArgumentBuilder.<Object>literal("status").executes(context -> 17)));
        RingLodCommandSuggestions.ensurePresent(dispatcher);
        RingLodCommandSuggestions.ensurePresent(dispatcher);
        assertSame(serverRoot, dispatcher.getRoot().getChild("ringworld"));
        assertEquals(17, dispatcher.execute("ringworld status", new Object()));
        for (var quality : RingLodQuality.values()) {
            var parsed = dispatcher.parse("ringworld lod " + quality.command(), new Object());
            assertFalse(parsed.getReader().canRead());
            assertNotNull(parsed.getContext().getCommand());
        }
        var suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("ringworld ", new Object())).get();
        assertTrue(suggestions.getList().stream().anyMatch(s -> s.getText().equals("lod")));
    }

    @Test void freshCommandTreeGetsSuggestionsAfterReconnectOrServerRefresh() {
        var dispatcher = new CommandDispatcher<Object>();
        RingLodCommandSuggestions.ensurePresent(dispatcher);
        assertNotNull(dispatcher.getRoot().getChild("ringworld").getChild("lod").getChild("show"));
        assertNotNull(dispatcher.getRoot().getChild("ringworld").getChild("lod").getChild("reset"));
    }
}
