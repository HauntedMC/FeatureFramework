package nl.hauntedmc.featureframework.integration.dataregistry;

import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.featureframework.resource.ResourceKey;

import java.util.Objects;

/** DataRegistry services scoped to one feature generation. */
public final class DataRegistryResources {
    public static final ResourceKey<DataRegistryResources> KEY = ResourceKey.of(DataRegistryResources.class);

    private final DataRegistryApi registry;
    private final PlayerReferenceResolver players;

    public DataRegistryResources(DataRegistryApi registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        players = new PlayerReferenceResolver(registry);
    }

    public DataRegistryApi registry() { return registry; }
    public PlayerReferenceResolver players() { return players; }
}
