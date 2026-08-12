package nl.hauntedmc.featureframework.velocity.log;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.featureframework.velocity.testutil.ComponentLoggerRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FeatureLoggerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void prefixesAndDelegatesAllLevelsForComponents() {
        ComponentLoggerRecorder recorder = ComponentLoggerRecorder.create();
        FeatureLogger logger = new FeatureLogger(recorder.logger(), "Queue");

        logger.info(Component.text("i"));
        logger.warn(Component.text("w"));
        logger.error(Component.text("e"));
        logger.debug(Component.text("d"));
        logger.trace(Component.text("t"));

        assertEquals("[Queue] i", PLAIN.serialize(firstComponentArg(recorder, "info")));
        assertEquals("[Queue] w", PLAIN.serialize(firstComponentArg(recorder, "warn")));
        assertEquals("[Queue] e", PLAIN.serialize(firstComponentArg(recorder, "error")));
        assertEquals("[Queue] d", PLAIN.serialize(firstComponentArg(recorder, "debug")));
        assertEquals("[Queue] t", PLAIN.serialize(firstComponentArg(recorder, "trace")));
    }

    @Test
    void stringOverloadsDelegateToComponentOverloads() {
        ComponentLoggerRecorder recorder = ComponentLoggerRecorder.create();
        FeatureLogger logger = new FeatureLogger(recorder.logger(), "Queue");

        logger.info("i");
        logger.warn("w");
        logger.error("e");
        logger.debug("d");
        logger.trace("t");

        assertEquals(1, countCalls(recorder, "info"));
        assertEquals(1, countCalls(recorder, "warn"));
        assertEquals(1, countCalls(recorder, "error"));
        assertEquals(1, countCalls(recorder, "debug"));
        assertEquals(1, countCalls(recorder, "trace"));
    }

    @Test
    void parameterizedInfoUsesTheSameSafePlaceholderExpansion() {
        ComponentLoggerRecorder recorder = ComponentLoggerRecorder.create();
        FeatureLogger logger = new FeatureLogger(recorder.logger(), "Capacity");

        logger.info("Started revision {}", 42L);
        logger.info("No placeholder", "value");

        var messages = recorder.calls().stream()
                .filter(call -> "info".equals(call.method()))
                .map(call -> PLAIN.serialize((Component) call.args()[0]))
                .toList();
        assertEquals(List.of(
                "[Capacity] Started revision 42",
                "[Capacity] No placeholder value"
        ), messages);
    }

    @Test
    void throwableOverloadsKeepPrefixMessageAndCause() {
        ComponentLoggerRecorder recorder = ComponentLoggerRecorder.create();
        FeatureLogger logger = new FeatureLogger(recorder.logger(), "Capacity");
        IllegalStateException failure = new IllegalStateException("broken");

        logger.warn("warning", failure);
        logger.error("target '{}' failed", "survival", failure);

        var warn = recorder.calls().stream().filter(call -> "warn".equals(call.method())).findFirst().orElseThrow();
        var error = recorder.calls().stream().filter(call -> "error".equals(call.method())).findFirst().orElseThrow();
        assertEquals("[Capacity] warning", PLAIN.serialize((Component) warn.args()[0]));
        assertSame(failure, warn.args()[1]);
        assertEquals("[Capacity] target 'survival' failed", PLAIN.serialize((Component) error.args()[0]));
        assertSame(failure, error.args()[1]);
    }

    private static Component firstComponentArg(ComponentLoggerRecorder recorder, String methodName) {
        return recorder.calls().stream()
                .filter(call -> methodName.equals(call.method()))
                .map(call -> call.args()[0])
                .filter(Component.class::isInstance)
                .map(Component.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing " + methodName + " call"));
    }

    private static long countCalls(ComponentLoggerRecorder recorder, String methodName) {
        return recorder.calls().stream()
                .filter(call -> methodName.equals(call.method()))
                .count();
    }
}
