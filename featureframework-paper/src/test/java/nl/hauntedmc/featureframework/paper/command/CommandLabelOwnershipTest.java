package nl.hauntedmc.featureframework.paper.command;

import nl.hauntedmc.featureframework.command.CommandLabelOwnership;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLabelOwnershipTest {

    @Test
    void preventsCaseInsensitiveLabelCollisionsAcrossFeatures() {
        CommandLabelOwnership ownership = new CommandLabelOwnership();
        Object first = new Object();
        Object second = new Object();

        assertTrue(ownership.tryClaim(first, List.of("demo", "alias")).claimed());
        CommandLabelOwnership.ClaimResult collision = ownership.tryClaim(second, List.of("other", "ALIAS"));
        assertFalse(collision.claimed());
        assertEquals("ALIAS", collision.blockingLabel());
    }

    @Test
    void releasesOnlyLabelsOwnedByTheGivenCommand() {
        CommandLabelOwnership ownership = new CommandLabelOwnership();
        Object first = new Object();
        Object second = new Object();

        assertTrue(ownership.tryClaim(first, List.of("demo")).claimed());
        ownership.release(second, List.of("demo"));
        assertEquals("demo", ownership.tryClaim(second, List.of("demo")).blockingLabel());

        ownership.release(first, List.of("demo"));
        assertTrue(ownership.tryClaim(second, List.of("demo")).claimed());
    }
}
