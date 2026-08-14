package nl.hauntedmc.featureframework.feature.stateful;

import java.util.Optional;

/** Type-safe runtime bridge for optional feature reload snapshots. */
public final class FeatureReloadState {
    private FeatureReloadState() { }

    public static Optional<SnapshotState> capture(Object feature) {
        if (!(feature instanceof StatefulFeature<?> statefulFeature)) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        StatefulFeature<SnapshotState> typed = (StatefulFeature<SnapshotState>) statefulFeature;
        Optional<SnapshotState> state = typed.captureReloadState();
        return state == null ? Optional.empty() : state;
    }

    public static void restore(Object feature, SnapshotState state) {
        if (!(feature instanceof StatefulFeature<?> statefulFeature)) {
            throw new IllegalStateException(
                    "Captured reload state exists, but replacement feature does not implement StatefulFeature."
            );
        }
        @SuppressWarnings("unchecked")
        StatefulFeature<SnapshotState> typed = (StatefulFeature<SnapshotState>) statefulFeature;
        typed.restoreReloadState(state);
    }
}
