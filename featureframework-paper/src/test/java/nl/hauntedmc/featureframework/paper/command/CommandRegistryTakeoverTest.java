package nl.hauntedmc.featureframework.paper.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import nl.hauntedmc.featureframework.paper.command.brigadier.BrigadierDispatcher;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandRegistryTakeoverTest {

    @Test
    void blocksConflictsWithoutMutatingRegistriesWhenOverwriteIsDisabled() {
        Fixture fixture = fixture();

        CommandRegistryTakeover.Claim claim = fixture.takeover.claim(List.of("god"), false);

        assertFalse(claim.claimed());
        assertEquals("god", claim.blockingConflict().label());
        assertSame(fixture.original, fixture.knownCommands.get("god"));
    }

    @Test
    void removesOnlyPlainBindingsAndRestoresThem() {
        Fixture fixture = fixture();
        fixture.knownCommands.put("essentials:god", fixture.original);
        when(fixture.dispatcher.takeRootLiteral("god")).thenReturn(fixture.root);
        when(fixture.dispatcher.restoreRootLiteral("god", fixture.root)).thenReturn(true);

        CommandRegistryTakeover.Claim claim = fixture.takeover.claim(List.of("god"), true);

        assertTrue(claim.claimed());
        assertNull(fixture.knownCommands.get("god"));
        assertSame(fixture.original, fixture.knownCommands.get("essentials:god"));

        CommandRegistryTakeover.RestoreResult restored = fixture.takeover.restore(claim.takeover());
        assertTrue(restored.complete());
        assertSame(fixture.original, fixture.knownCommands.get("god"));
        verify(fixture.dispatcher).restoreRootLiteral("god", fixture.root);
    }

    @Test
    void rollsBackBukkitRemovalWhenBrigadierTakeoverFails() {
        Fixture fixture = fixture();
        when(fixture.dispatcher.takeRootLiteral("god"))
                .thenThrow(new IllegalStateException("dispatcher locked"));

        assertThrows(IllegalStateException.class, () -> fixture.takeover.claim(List.of("god"), true));
        assertSame(fixture.original, fixture.knownCommands.get("god"));
    }

    @Test
    void doesNotOverwriteAReplacementThatAppearedBeforeRestore() {
        Fixture fixture = fixtureWithoutBrigadierRoot();
        Command replacement = new TestCommand("god");
        CommandRegistryTakeover.Claim claim = fixture.takeover.claim(List.of("god"), true);
        fixture.knownCommands.put("god", replacement);

        CommandRegistryTakeover.RestoreResult restored = fixture.takeover.restore(claim.takeover());

        assertFalse(restored.complete());
        assertEquals(Set.of("god"), restored.skippedBukkitLabels());
        assertSame(replacement, fixture.knownCommands.get("god"));
    }

    private static Fixture fixture() {
        Fixture fixture = fixtureWithoutBrigadierRoot();
        when(fixture.dispatcher.getRootLiteral("god")).thenReturn(fixture.root);
        return fixture;
    }

    private static Fixture fixtureWithoutBrigadierRoot() {
        CommandMap commandMap = mock(CommandMap.class);
        BrigadierDispatcher dispatcher = mock(BrigadierDispatcher.class);
        Map<String, Command> knownCommands = new LinkedHashMap<>();
        Command original = new TestCommand("god");
        CommandNode<CommandSourceStack> root =
                LiteralArgumentBuilder.<CommandSourceStack>literal("god").build();
        knownCommands.put("god", original);
        when(commandMap.getKnownCommands()).thenReturn(knownCommands);
        return new Fixture(
                dispatcher,
                knownCommands,
                original,
                root,
                new CommandRegistryTakeover(commandMap, dispatcher)
        );
    }

    private record Fixture(
            BrigadierDispatcher dispatcher,
            Map<String, Command> knownCommands,
            Command original,
            CommandNode<CommandSourceStack> root,
            CommandRegistryTakeover takeover
    ) { }

    private static final class TestCommand extends Command {
        private TestCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return true;
        }
    }
}
