package nl.hauntedmc.featureframework.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe registry for runtime-only collaboration ports.
 *
 * <p>The owner type is deliberately application-defined. The framework therefore understands
 * ownership and safe replacement without knowing a plugin's feature identifier type.</p>
 */
public final class InternalServiceRegistry<O> implements OwnedServiceRegistry<O> {
    private record Provider<O>(O owner, Object instance) { }

    private final ConcurrentHashMap<Class<?>, Provider<O>> providers = new ConcurrentHashMap<>();

    @Override
    public <T> Registration register(O owner, Class<T> type, T instance) {
        Provider<O> provider = provider(owner, type, instance);
        providers.compute(type, (ignored, current) -> {
            if (current != null) {
                throw new IllegalStateException(type.getName() + " is already provided by " + current.owner());
            }
            return provider;
        });
        return registration(type, provider);
    }

    @Override
    public <T> Registration replace(O owner, Class<T> type, T instance) {
        Provider<O> replacement = provider(owner, type, instance);
        providers.compute(type, (ignored, current) -> {
            if (current == null) {
                throw new IllegalStateException(type.getName() + " is not currently registered");
            }
            if (!current.owner().equals(owner)) {
                throw new IllegalStateException(type.getName() + " is provided by another owner: " + current.owner());
            }
            return replacement;
        });
        return registration(type, replacement);
    }

    public <T> Optional<T> find(Class<T> type) {
        Provider<O> provider = providers.get(Objects.requireNonNull(type, "type"));
        return provider == null ? Optional.empty() : Optional.of(type.cast(provider.instance()));
    }

    public <T> T require(Class<T> type) {
        return find(type).orElseThrow(() -> new IllegalStateException(
                "Internal feature service is unavailable: " + type.getName()
        ));
    }

    public boolean isAvailable(Class<?> type) {
        return providers.containsKey(Objects.requireNonNull(type, "type"));
    }

    /** Returns an immutable snapshot of currently registered service contracts. */
    public Set<Class<?>> availableTypes() {
        return Set.copyOf(providers.keySet());
    }

    public int size() {
        return providers.size();
    }

    public boolean isEmpty() {
        return providers.isEmpty();
    }

    public Optional<O> owner(Class<?> type) {
        Provider<O> provider = providers.get(Objects.requireNonNull(type, "type"));
        return provider == null ? Optional.empty() : Optional.of(provider.owner());
    }

    private static <O, T> Provider<O> provider(O owner, Class<T> type, T instance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Internal service contract must be an interface: " + type.getName());
        }
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Service implementation does not implement " + type.getName());
        }
        return new Provider<>(owner, instance);
    }

    private Registration registration(Class<?> type, Provider<O> provider) {
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                providers.remove(type, provider);
            }
        };
    }
}
