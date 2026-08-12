package nl.hauntedmc.featureframework.paper.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CommandLabelOwnershipTest {

    @Test
    void preventsCaseInsensitiveLabelCollisionsAcrossFeatures() {
        CommandLabelOwnership ownership = new CommandLabelOwnership();
        Object first = new Object();
        Object second = new Object();

        assertNull(ownership.claim(first, List.of("demo", "alias")));
        assertEquals("ALIAS", ownership.claim(second, List.of("other", "ALIAS")));
    }

    @Test
    void releasesOnlyLabelsOwnedByTheGivenCommand() {
        CommandLabelOwnership ownership = new CommandLabelOwnership();
        Object first = new Object();
        Object second = new Object();

        assertNull(ownership.claim(first, List.of("demo")));
        ownership.release(second, List.of("demo"));
        assertEquals("demo", ownership.claim(second, List.of("demo")));

        ownership.release(first, List.of("demo"));
        assertNull(ownership.claim(second, List.of("demo")));
    }
}
