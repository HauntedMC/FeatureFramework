package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.lifecycle.FeatureResourceState;

import java.util.*;

/** Stages public and internal feature services until post-restoration activation. */
public class FeatureServiceManager<O> {
    private enum RegistryKind { PUBLIC, INTERNAL }

    private final Map<Class<?>, ServiceDefinition> serviceDefinitions = new LinkedHashMap<>();
    private final Map<Class<?>, Registration> activeRegistrations = new LinkedHashMap<>();
    private final List<Runnable> activationHooks = new ArrayList<>();
    private OwnedServiceRegistry<O> publicRegistry;
    private OwnedServiceRegistry<O> internalRegistry;
    private O owner;
    private boolean active;
    private FeatureResourceState state = FeatureResourceState.OPEN;

    public synchronized void bindRegistries(
            OwnedServiceRegistry<O> publicRegistry,
            OwnedServiceRegistry<O> internalRegistry,
            O owner
    ) {
        requireOpen();
        if (!serviceDefinitions.isEmpty() || !activeRegistrations.isEmpty() || !activationHooks.isEmpty()) {
            throw new IllegalStateException("Feature service manager cannot be rebound after resources were registered");
        }
        this.publicRegistry = Objects.requireNonNull(publicRegistry, "publicRegistry");
        this.internalRegistry = Objects.requireNonNull(internalRegistry, "internalRegistry");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public synchronized <T> void registerInternalService(Class<T> type, T instance) {
        register(type, instance, RegistryKind.INTERNAL);
    }

    public synchronized <T> void registerService(Class<T> type, T instance) {
        register(type, instance, RegistryKind.PUBLIC);
    }

    public synchronized void registerActivationHook(Runnable activationHook) {
        requireOpen();
        if (active) {
            throw new IllegalStateException("Activation hooks cannot be added after feature activation");
        }
        activationHooks.add(Objects.requireNonNull(activationHook, "activationHook"));
    }

    public synchronized void activateServices() {
        requireOpen();
        requireBound();
        if (active) {
            return;
        }
        for (Runnable activationHook : List.copyOf(activationHooks)) {
            activationHook.run();
        }

        Map<Class<?>, Registration> published = new LinkedHashMap<>();
        try {
            for (Map.Entry<Class<?>, ServiceDefinition> entry : serviceDefinitions.entrySet()) {
                published.put(entry.getKey(), publish(entry.getKey(), entry.getValue()));
            }
        } catch (Throwable activationFailure) {
            closeRegistrations(published, activationFailure);
            throwUnchecked(activationFailure);
        }
        activeRegistrations.putAll(published);
        active = true;
    }

    public synchronized void deactivateServices() {
        active = false;
        Throwable failure = closeRegistrations(activeRegistrations, null);
        activeRegistrations.clear();
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public synchronized void quiesce() {
        if (state == FeatureResourceState.OPEN) {
            state = FeatureResourceState.QUIESCING;
        }
    }

    public synchronized void unregisterService(Class<?> type) {
        Objects.requireNonNull(type, "type");
        serviceDefinitions.remove(type);
        Registration registration = activeRegistrations.remove(type);
        if (registration != null) {
            registration.close();
        }
    }

    public synchronized void unregisterAllServices() {
        quiesce();
        Throwable failure = null;
        try {
            deactivateServices();
        } catch (Throwable deactivationFailure) {
            failure = deactivationFailure;
        } finally {
            serviceDefinitions.clear();
            activationHooks.clear();
            state = FeatureResourceState.CLOSED;
        }
        if (failure != null) {
            throwUnchecked(failure);
        }
    }

    public synchronized int getRegisteredServiceCount() {
        return serviceDefinitions.size();
    }

    public synchronized int getActivationHookCount() {
        return activationHooks.size();
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized FeatureResourceState state() {
        return state;
    }

    private <T> void register(Class<T> type, T instance, RegistryKind kind) {
        requireOpen();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        requireBound();

        ServiceDefinition previous = serviceDefinitions.get(type);
        if (previous != null && previous.instance() == instance && previous.kind() == kind) {
            return;
        }
        ServiceDefinition replacement = new ServiceDefinition(kind, instance);
        if (!active) {
            serviceDefinitions.put(type, replacement);
            return;
        }
        if (previous != null && previous.kind() != kind) {
            throw new IllegalStateException(
                    "Active service cannot change registry kind without deactivation: " + type.getName()
            );
        }

        Registration replacementRegistration = previous == null
                ? publish(type, replacement)
                : replace(type, replacement);
        Registration previousRegistration = activeRegistrations.put(type, replacementRegistration);
        serviceDefinitions.put(type, replacement);
        if (previousRegistration != null) {
            previousRegistration.close();
        }
    }

    private Registration publish(Class<?> type, ServiceDefinition definition) {
        return switch (definition.kind()) {
            case PUBLIC -> publish(publicRegistry, type, definition.instance());
            case INTERNAL -> publish(internalRegistry, type, definition.instance());
        };
    }

    private Registration replace(Class<?> type, ServiceDefinition definition) {
        return switch (definition.kind()) {
            case PUBLIC -> replace(publicRegistry, type, definition.instance());
            case INTERNAL -> replace(internalRegistry, type, definition.instance());
        };
    }

    private <T> Registration publish(OwnedServiceRegistry<O> registry, Class<T> type, Object instance) {
        return registry.register(owner, type, type.cast(instance));
    }

    private <T> Registration replace(OwnedServiceRegistry<O> registry, Class<T> type, Object instance) {
        return registry.replace(owner, type, type.cast(instance));
    }

    private void requireBound() {
        if (publicRegistry == null || internalRegistry == null || owner == null) {
            throw new IllegalStateException("Feature service manager is not bound to service registries");
        }
    }

    private void requireOpen() {
        if (state != FeatureResourceState.OPEN) {
            throw new IllegalStateException("Feature service manager is " + state);
        }
    }

    private static Throwable closeRegistrations(Map<Class<?>, Registration> registrations, Throwable failure) {
        List<Registration> values = new ArrayList<>(registrations.values());
        for (int index = values.size() - 1; index >= 0; index--) {
            try {
                values.get(index).close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        return failure;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private record ServiceDefinition(RegistryKind kind, Object instance) {
    }
}
