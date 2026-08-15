package nl.hauntedmc.featureframework.operation.reset;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Complete outcome returned by a concrete feature host. */
public record FeatureFileResetResponse(
        FeatureFileResetResult result,
        String feature,
        FeatureFileResetRequest request,
        boolean committed,
        FeatureResetRuntimeOutcome runtimeOutcome,
        FeatureResetRollbackOutcome rollbackOutcome,
        Set<String> affectedDependents,
        List<String> deletedOverrides,
        Optional<String> backupId,
        Optional<Throwable> failure
) {
    public FeatureFileResetResponse {
        Objects.requireNonNull(result, "result");
        feature = feature == null ? "" : feature;
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(runtimeOutcome, "runtimeOutcome");
        Objects.requireNonNull(rollbackOutcome, "rollbackOutcome");
        affectedDependents = affectedDependents == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(affectedDependents));
        deletedOverrides = deletedOverrides == null ? List.of() : List.copyOf(deletedOverrides);
        backupId = backupId == null ? Optional.empty() : backupId;
        failure = failure == null ? Optional.empty() : failure;
    }

    public boolean success() {
        return result == FeatureFileResetResult.SUCCESS;
    }
}
