package nl.hauntedmc.featureframework.velocity.command.brigadier;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.velocitypowered.api.command.CommandSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrigadierCommandTest {

    @Test
    void defaultAliasesAndDescriptionAreStable() {
        BrigadierCommand command = new BrigadierCommand() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public com.mojang.brigadier.tree.LiteralCommandNode<CommandSource> buildTree() {
                return LiteralArgumentBuilder.<CommandSource>literal("test").build();
            }
        };

        assertEquals(List.of(), command.aliases());
        assertNull(command.description());
    }
}
