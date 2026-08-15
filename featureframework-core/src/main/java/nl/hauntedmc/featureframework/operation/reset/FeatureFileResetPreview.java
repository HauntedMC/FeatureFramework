package nl.hauntedmc.featureframework.operation.reset;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only reset plan suitable for an operator confirmation prompt. */
public record FeatureFileResetPreview(
        boolean valid,
        String feature,
        FeatureFileResetRequest request,
        List<String> targetFiles,
        boolean configuredEnabled,
        boolean loaded,
        Set<String> affectedDependents,
        String failure
) {
    public FeatureFileResetPreview {
        feature = feature == null ? "" : feature;
        Objects.requireNonNull(request, "request");
        targetFiles = targetFiles == null ? List.of() : List.copyOf(targetFiles);
        affectedDependents = affectedDependents == null ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(affectedDependents));
        failure = failure == null ? "" : failure;
    }
}
