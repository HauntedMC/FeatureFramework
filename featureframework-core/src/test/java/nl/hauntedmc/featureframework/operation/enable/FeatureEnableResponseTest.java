package nl.hauntedmc.featureframework.operation.enable;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureEnableResponseTest {

    @Test
    void successIsTrueOnlyForSuccessResult() {
        FeatureEnableResponse ok = new FeatureEnableResponse(FeatureEnableResult.SUCCESS, Set.of(), Set.of());
        FeatureEnableResponse fail = new FeatureEnableResponse(
                FeatureEnableResult.MISSING_FEATURE_DEPENDENCY,
                Set.of("Vault"),
                Set.of("chat")
        );

        assertTrue(ok.success());
        assertFalse(fail.success());
        assertEquals(FeatureEnableResult.MISSING_FEATURE_DEPENDENCY, fail.result());
        assertEquals(Set.of("Vault"), fail.missingPlugins());
        assertEquals(Set.of("chat"), fail.missingFeatures());
    }
}
