package nl.hauntedmc.featureframework.feature.stateful;

import java.util.Optional;

/** Optional contract for features that preserve transient state across a full reload. */
public interface StatefulFeature<S extends SnapshotState> {
    Optional<S> captureReloadState();
    void restoreReloadState(S state);
}
