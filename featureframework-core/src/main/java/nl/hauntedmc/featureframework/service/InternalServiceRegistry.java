package nl.hauntedmc.featureframework.service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe registry for runtime-only collaboration ports.
 *
 * <p>The owner type is deliberately application-defined. The framework therefore understands
 * ownership and safe replacement without knowing a plugin's feature identifier type.</p>
 */
public final class InternalServiceRegistry<O> implements OwnedServiceRegistry<O> {
    private record Provider<O>(O owner, Object instance, long generation) { }

    private final ConcurrentHashMap<Class<?>, Provider<O>> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, ServiceRef<?>> references = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<InternalServiceListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong generations = new AtomicLong();

    @Override
    public <T> Registration register(O owner, Class<T> type, T instance) {
        Provider<O> provider = provider(owner, type, instance);
        providers.compute(type, (ignored, current) -> {
            if (current != null) {
                throw new IllegalStateException(type.getName() + " is already provided by " + current.owner());
            }
            return provider;
        });
        notifyAvailable(type, provider.generation());
        return registration(type, provider);
    }

    @Override
    public <T> Registration replace(O owner, Class<T> type, T instance) {
        Provider<O> replacement = provider(owner, type, instance);
        long[] previousGeneration = new long[1];
        providers.compute(type, (ignored, current) -> {
            if (current == null) {
                throw new IllegalStateException(type.getName() + " is not currently registered");
            }
            if (!current.owner().equals(owner)) {
                throw new IllegalStateException(type.getName() + " is provided by another owner: " + current.owner());
            }
            previousGeneration[0] = current.generation();
            return replacement;
        });
        notifyReplaced(type, previousGeneration[0], replacement.generation());
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

    /** Returns a stable reference that resolves the active provider on every access. */
    public <T> ServiceRef<T> reference(Class<T> type) {
        Objects.requireNonNull(type, "type");
        ServiceRef<?> reference = references.computeIfAbsent(type, ignored -> new InternalServiceRef<>(type));
        return typeSafeReference(type, reference);
    }

    public AutoCloseable subscribe(InternalServiceListener listener) {
        InternalServiceListener required = Objects.requireNonNull(listener, "listener");
        listeners.add(required);
        return () -> listeners.remove(required);
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

    private <T> Provider<O> provider(O owner, Class<T> type, T instance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Internal service contract must be an interface: " + type.getName());
        }
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Service implementation does not implement " + type.getName());
        }
        return new Provider<>(owner, instance, generations.incrementAndGet());
    }

    private Registration registration(Class<?> type, Provider<O> provider) {
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                if (providers.remove(type, provider)) {
                    notifyUnavailable(type, provider.generation());
                }
            }
        };
    }

    private void notifyAvailable(Class<?> type, long generation) {
        listeners.forEach(listener -> safely(() -> listener.available(type, generation)));
    }

    private void notifyUnavailable(Class<?> type, long generation) {
        listeners.forEach(listener -> safely(() -> listener.unavailable(type, generation)));
    }

    private void notifyReplaced(Class<?> type, long previous, long next) {
        listeners.forEach(listener -> safely(() -> listener.replaced(type, previous, next)));
    }

    private static void safely(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException ignored) {
            // Registry changes must not be rolled back by an observer.
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ServiceRef<T> typeSafeReference(Class<T> type, ServiceRef<?> reference) {
        if (reference.type() != type) throw new IllegalStateException("Internal service reference type mismatch");
        return (ServiceRef<T>) reference;
    }

    private final class InternalServiceRef<T> implements ServiceRef<T> {
        private final Class<T> type;

        private InternalServiceRef(Class<T> type) {
            this.type = type;
        }

        @Override public Class<T> type() { return type; }
        @Override public Optional<T> get() { return find(type); }

        @Override
        public java.util.OptionalLong generation() {
            Provider<O> provider = providers.get(type);
            return provider == null
                    ? java.util.OptionalLong.empty()
                    : java.util.OptionalLong.of(provider.generation());
        }
    }
}
