package nl.hauntedmc.featureframework.host;

import nl.hauntedmc.featureframework.feature.Feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Immutable, typed collection of features hosted in one plugin artifact. */
public final class FeatureCollection<F extends Feature, C> {
    private final List<FeatureDefinition<F, C>> definitions;

    private FeatureCollection(Collection<FeatureDefinition<F, C>> definitions) {
        Map<String, FeatureDefinition<F, C>> unique = new LinkedHashMap<>();
        for (FeatureDefinition<F, C> definition : Objects.requireNonNull(definitions, "definitions")) {
            FeatureDefinition<F, C> required = Objects.requireNonNull(definition, "definition");
            String key = required.featureName().toLowerCase(Locale.ROOT);
            if (unique.putIfAbsent(key, required) != null) {
                throw new IllegalArgumentException("Duplicate feature name: " + required.featureName());
            }
        }
        if (unique.isEmpty()) throw new IllegalArgumentException("A feature collection must not be empty");
        this.definitions = List.copyOf(unique.values());
    }

    public static <F extends Feature, C> Builder<F, C> builder() {
        return new Builder<>();
    }

    @SafeVarargs
    public static <F extends Feature, C> FeatureCollection<F, C> of(FeatureDefinition<F, C>... definitions) {
        List<FeatureDefinition<F, C>> copy = new ArrayList<>(definitions.length);
        for (FeatureDefinition<F, C> definition : definitions) copy.add(definition);
        return new FeatureCollection<>(copy);
    }

    /** Creates a collection from an existing ordered inventory without generic-array conversion. */
    public static <F extends Feature, C> FeatureCollection<F, C> copyOf(
            Collection<? extends FeatureDefinition<? extends F, C>> definitions
    ) {
        Builder<F, C> builder = builder();
        Objects.requireNonNull(definitions, "definitions").forEach(builder::feature);
        return builder.build();
    }

    public List<FeatureDefinition<F, C>> definitions() {
        return definitions;
    }

    /** Builder useful when a product composes definitions from several feature packs. */
    public static final class Builder<F extends Feature, C> {
        private final List<FeatureDefinition<F, C>> definitions = new ArrayList<>();

        public Builder<F, C> feature(FeatureDefinition<? extends F, C> definition) {
            Objects.requireNonNull(definition, "definition");
            @SuppressWarnings("unchecked")
            FeatureDefinition<F, C> compatible = (FeatureDefinition<F, C>) definition;
            definitions.add(compatible);
            return this;
        }

        /** Adds an ordered batch of definitions without forcing callers to loop manually. */
        public Builder<F, C> features(Iterable<? extends FeatureDefinition<? extends F, C>> values) {
            Objects.requireNonNull(values, "definitions").forEach(this::feature);
            return this;
        }

        public Builder<F, C> include(FeatureCollection<? extends F, C> collection) {
            Objects.requireNonNull(collection, "collection");
            collection.definitions().forEach(this::feature);
            return this;
        }

        public FeatureCollection<F, C> build() {
            return new FeatureCollection<>(definitions);
        }
    }
}
