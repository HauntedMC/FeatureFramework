package nl.hauntedmc.featureframework.loader;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureGraphReloadTransactionTest {

    @Test
    void reloadsTheWholeGraphInOneTransaction() {
        List<String> calls = new ArrayList<>();

        FeatureGraphReloadTransaction.Result result = FeatureGraphReloadTransaction.execute(
                "root",
                () -> List.of("root", "dependent"),
                order -> Map.of("root", "r", "dependent", "d"),
                order -> { calls.add("stop"); return null; },
                (order, states) -> { calls.add("start:" + states.size()); return true; }
        );

        assertTrue(result.success());
        assertEquals(List.of("stop", "start:2"), calls);
        assertEquals(java.util.Set.of("dependent"), result.reloadedDependents());
    }

    @Test
    void restoresThePreviousGraphWhenReplacementStartupFails() {
        List<String> calls = new ArrayList<>();
        int[] starts = {0};

        FeatureGraphReloadTransaction.Result result = FeatureGraphReloadTransaction.execute(
                "root",
                () -> List.of("root", "dependent"),
                order -> Map.of("root", "r"),
                order -> { calls.add("stop"); return null; },
                (order, states) -> { calls.add("start"); return ++starts[0] > 1; }
        );

        assertFalse(result.success());
        assertEquals(FeatureGraphReloadTransaction.Stage.START, result.stage());
        assertTrue(result.rollbackSucceeded());
        assertEquals(List.of("stop", "start", "stop", "start"), calls);
    }

    @Test
    void reportsPreparationAndRollbackCleanupFailures() {
        RuntimeException preparation = new RuntimeException("capture");
        FeatureGraphReloadTransaction.Result preparationResult = FeatureGraphReloadTransaction.execute(
                "root", () -> List.of("root"), order -> { throw preparation; }, order -> null,
                (order, states) -> true
        );
        assertSame(preparation, preparationResult.failure());
        assertEquals(FeatureGraphReloadTransaction.Stage.PREPARATION, preparationResult.stage());

        RuntimeException quiesce = new RuntimeException("quiesce");
        RuntimeException rollbackCleanup = new RuntimeException("rollback cleanup");
        int[] stops = {0};
        FeatureGraphReloadTransaction.Result quiesceResult = FeatureGraphReloadTransaction.execute(
                "root", () -> List.of("root"), order -> Map.of(),
                order -> ++stops[0] == 1 ? quiesce : rollbackCleanup,
                (order, states) -> false
        );
        assertSame(quiesce, quiesceResult.failure());
        assertSame(rollbackCleanup, quiesceResult.cleanupFailure());
        assertFalse(quiesceResult.rollbackSucceeded());
    }
}
