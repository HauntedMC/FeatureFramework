package nl.hauntedmc.featureframework.toolkit.io.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConfigViewTest {

    @TempDir
    Path tempDir;

    @Test
    void typedCrudScopesAndDefaultsWork() throws Exception {
        ConfigView view = create("config.yml", "");
        view.put("global.name", "server");
        view.put("numbers", List.of("1", 2));
        view.put("weights", Map.of("a", "1"));

        assertEquals("server", view.get("global.name", String.class));
        assertEquals("fallback", view.get("missing", String.class, "fallback"));
        assertEquals(List.of(1, 2), view.getList("numbers", Integer.class));
        assertEquals(Map.of("a", 1), view.getMapValues("weights", Integer.class));
        assertEquals("server", view.nodeAt("global.name").asRequired(String.class));
        assertEquals("server", view.getAt("global.name", String.class));
        assertEquals("fallback", view.getAt("global.missing", String.class, "fallback"));

        assertTrue(view.putIfAbsent("global.mode", "on"));
        assertFalse(view.putIfAbsent("global.mode", "off"));
        view.remove("global.mode");
        assertNull(view.get("global.mode"));

        assertEquals("server", view.scope("global").get("name", String.class));
        assertEquals("server", view.globals().get("name", String.class));
        assertSame(view, view.root());
        assertEquals("server", view.at("global").get("name", String.class));
    }

    @Test
    void computeListBatchAndRawMutationsPersistAtomically() throws Exception {
        ConfigView view = create("mutations.yml", "");
        view.put("counter", "bad");
        assertEquals(11, view.compute("counter", Integer.class, value -> value + 1, () -> 10));

        view.appendToList("items", "a");
        view.appendToList("items", "b");
        assertEquals(1, view.removeFromList("items", "a"::equals));
        assertEquals(List.of("b"), view.getList("items", String.class));
        assertEquals(0, view.removeFromList("missing", ignored -> true));

        view.batch(batch -> {
            try {
                batch.put("batch.value", 1)
                        .putIfAbsent("batch.value", 2)
                        .compute("batch.value", Integer.class, value -> value + 1, () -> 0)
                        .appendToList("batch.items", "x")
                        .appendToList("batch.items", "y")
                        .removeFromList("batch.items", "x"::equals);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        assertEquals(2, view.get("batch.value", Integer.class));
        assertEquals(List.of("y"), view.getList("batch.items", String.class));

        view.batch(batch -> {
            try {
                batch.remove("batch.value");
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        });
        assertNull(view.get("batch.value"));

        view.mutateRaw(root -> root.node("raw", "ok").raw(true));
        assertEquals(true, view.get("raw.ok", Boolean.class));
    }

    @Test
    void fallbackReadersReturnProvidedValuesOnTypeMismatch() throws Exception {
        ConfigView view = create("fallback.yml", null);
        view.put("bad.list", Map.of("x", 1));
        view.put("bad.map", List.of(1, 2));

        assertEquals(List.of(9), view.getList("bad.list", Integer.class, List.of(9)));
        assertEquals(Map.of("fallback", 1),
                view.getMapValues("bad.map", Integer.class, Map.of("fallback", 1)));
        assertEquals(List.of(9), view.getList("missing.list", Integer.class, List.of(9)));
        assertEquals(Map.of("fallback", 1),
                view.getMapValues("missing.map", Integer.class, Map.of("fallback", 1)));
        assertTrue(view.scope(null).putIfAbsent("new", 1));
        assertEquals(Integer.valueOf(1), view.get("new", Integer.class));
    }

    private ConfigView create(String fileName, String base) throws Exception {
        Path path = tempDir.resolve(fileName);
        Files.createFile(path);
        return new ConfigView(new YamlFile(path, mock(Logger.class)), base);
    }
}
