package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConfigViewListMutationTest {

    @Test
    void listMutationsRejectScalarValuesWithoutOverwritingThem() {
        YamlFile file = mock(YamlFile.class);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        CommentedConfigurationNode root = CommentedConfigurationNode.root();
        root.node("scope", "items").raw("not-a-list");

        when(file.lock()).thenReturn(lock);
        when(file.copyRootUnsafe()).thenReturn(root.copy());

        ConfigView view = new ConfigView(file, "scope");
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> view.appendToList("items", "new-value"));

        assertTrue(exception.getMessage().contains("Unable to append configuration list"));
        IllegalStateException cause = assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertTrue(cause.getMessage().contains("not a list"));
        assertEquals("not-a-list", root.node("scope", "items").raw());
        assertEquals(0, view.removeFromList("items", ignored -> true));
        assertEquals("not-a-list", root.node("scope", "items").raw());
        assertFalse(lock.isWriteLocked());
        verify(file, never()).commitCandidateUnsafe(any());
    }
}
