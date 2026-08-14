package nl.hauntedmc.featureframework.command;

import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResponse;
import nl.hauntedmc.featureframework.operation.disable.FeatureDisableResult;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResponse;
import nl.hauntedmc.featureframework.operation.enable.FeatureEnableResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureOperationMessagesTest {
    @Test
    void exposesMissingPluginsAsAPlaceholder() {
        var response = new FeatureEnableResponse(
                FeatureEnableResult.MISSING_PLUGIN_DEPENDENCY, Set.of("Vault"), Set.of());

        var message = FeatureOperationMessages.enable("economy", response);

        assertEquals("command.enable.missing_plugin_dependency", message.key());
        assertEquals("economy", message.placeholders().get("feature"));
        assertEquals("Vault", message.placeholders().get("plugins"));
    }

    @Test
    void selectsDependentAwareDisableMessage() {
        var response = new FeatureDisableResponse(
                FeatureDisableResult.SUCCESS, "base", Set.of("dependent"));

        var message = FeatureOperationMessages.disable("base", response);

        assertEquals("command.disable.success_with_dependents", message.key());
        assertEquals("dependent", message.placeholders().get("dependents"));
    }
}
