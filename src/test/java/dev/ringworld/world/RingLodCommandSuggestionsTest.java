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
        var shaderCommands = new CommandDispatcher<Object>();
        shaderCommands.register(LiteralArgumentBuilder.<Object>literal("ringworld").then(
                RingLodCommandSuggestions.shaderSupport(c -> 1, c -> 2, c -> 3)));
        RingLodCommandSuggestions.ensurePresent(shaderCommands);
        assertEquals(1, shaderCommands.execute("ringworld shadersupport", new Object()));
        assertEquals(2, shaderCommands.execute("ringworld shadersupport on", new Object()));
        assertEquals(3, shaderCommands.execute("ringworld shadersupport off", new Object()));
        assertNotNull(serverRoot.getChild("shadersupport").getChild("off"));
        for (String action : new String[]{"on", "off", "show", "distance 1", "distance 8"}) {
            var parsed = dispatcher.parse("ringworld distortion " + action, new Object());
            assertFalse(parsed.getReader().canRead());
            assertNotNull(parsed.getContext().getCommand());
        }
        assertTrue(dispatcher.parse("ringworld distortion distance 9", new Object())
                .getReader().canRead());
        for (String arg : new String[]{"", " 0.5", " 1", " 1.5", " 2"})
            assertEquals(0, dispatcher.execute("ringworld lod band" + arg, new Object()));
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("ringworld lod band 3", new Object()));
        var choices = dispatcher.getCompletionSuggestions(
                dispatcher.parse("ringworld lod ", new Object())).get();
        assertEquals(java.util.Set.of("low", "medium", "high", "show", "reset", "band"),
                choices.getList().stream().map(s -> s.getText()).collect(java.util.stream.Collectors.toSet()));
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
