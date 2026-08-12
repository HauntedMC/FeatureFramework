package nl.hauntedmc.featureframework.operation.softreload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureSoftReloadResponseTest {

    @Test
    void successIsTrueOnlyForSuccessResult() {
        FeatureSoftReloadResponse ok = new FeatureSoftReloadResponse(FeatureSoftReloadResult.SUCCESS, "a");
        FeatureSoftReloadResponse fail = new FeatureSoftReloadResponse(FeatureSoftReloadResult.NOT_LOADED, "a");

        assertTrue(ok.success());
        assertFalse(fail.success());
        assertEquals(FeatureSoftReloadResult.NOT_LOADED, fail.result());
        assertEquals("a", fail.feature());
    }
}
