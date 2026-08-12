package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.api.ApiFailureCode;
import nl.hauntedmc.featureframework.api.ApiOperationException;
import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.service.CapabilityListener;
import nl.hauntedmc.featureframework.api.service.CapabilityRef;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.api.service.CapabilityUnavailableException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** Thread-safe, generation-aware public capability registry with reload-safe invocation leases. */
public class DefaultCapabilityRegistry implements CapabilityRegistry, OwnedServiceRegistry<FeatureId> {

    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private static final class Provider {
        private final FeatureId owner;
        private final Object instance;
        private final long generation;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition drained = lock.newCondition();
        private final Set<AsyncInvocation> asynchronousInvocations = ConcurrentHashMap.newKeySet();
        private final Duration drainTimeout;
        private boolean accepting = true;
        private int inFlight;

        private Provider(FeatureId owner, Object instance, long generation, Duration drainTimeout) {
            this.owner = owner;
            this.instance = instance;
            this.generation = generation;
            this.drainTimeout = drainTimeout;
        }

        private InvocationLease tryAcquire() {
            lock.lock();
            try {
                if (!accepting) {
                    return null;
                }
                inFlight++;
                return new InvocationLease(this);
            } finally {
                lock.unlock();
            }
        }

        private void release() {
            lock.lock();
            try {
                inFlight--;
                if (inFlight == 0) {
                    drained.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Tracks an asynchronous invocation only while the provider still accepts work. This
         * closes the acquire-to-track race with concurrent provider withdrawal.
         */
        private boolean track(AsyncInvocation invocation) {
            lock.lock();
            try {
                if (!accepting) {
                    return false;
                }
                asynchronousInvocations.add(invocation);
                return true;
            } finally {
                lock.unlock();
            }
        }

        private void complete(AsyncInvocation invocation) {
            asynchronousInvocations.remove(invocation);
        }

        /**
         * Completes a provider stage only while this provider is still accepting work. Holding the
         * provider lock makes this completion linearize with withdrawal, so a stage cannot win the
         * race after the provider has been withdrawn.
         */
        private void completeAsync(AsyncInvocation invocation, Object value, Throwable failure) {
            lock.lock();
            try {
                if (accepting) {
                    invocation.complete(value, failure);
                } else {
                    invocation.invalidate();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Rejects new invocations and gives synchronous work a finite drain window. Outstanding
         * asynchronous calls are failed immediately and deliberately abandoned: their source
         * stages may still complete, but their result is no longer observed by this registry.
         */
        private Set<AsyncInvocation> stopAccepting() {
            lock.lock();
            try {
                accepting = false;
                return Set.copyOf(asynchronousInvocations);
            } finally {
                lock.unlock();
            }
        }

        private ApiOperationException stopAndAwaitDrain(Set<AsyncInvocation> pending) {
            boolean interrupted = false;
            pending.forEach(AsyncInvocation::invalidate);

            lock.lock();
            try {
                long remainingNanos = drainTimeout.toNanos();
                while (inFlight > 0) {
                    if (remainingNanos <= 0) {
                        return new ApiOperationException(
                                ApiFailureCode.TIMEOUT,
                                "Timed out draining capability provider " + owner + " with " + inFlight
                                        + " synchronous invocation(s) still running"
                        );
                    }
                    try {
                        remainingNanos = drained.awaitNanos(remainingNanos);
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
            } finally {
                lock.unlock();
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        }
    }

    private static final class InvocationLease implements AutoCloseable {
        private final Provider provider;
        private final AtomicBoolean closed = new AtomicBoolean();

        private InvocationLease(Provider provider) {
            this.provider = provider;
        }

        private Object instance() {
            return provider.instance;
        }

        private Provider provider() {
            return provider;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                provider.release();
            }
        }
    }

    private static final class AsyncInvocation {
        private final Class<?> type;
        private final Provider provider;
        private final InvocationLease lease;
        private final CompletableFuture<Object> result = new CompletableFuture<>();
        private final AtomicBoolean completed = new AtomicBoolean();

        private AsyncInvocation(Class<?> type, InvocationLease lease) {
            this.type = type;
            this.lease = lease;
            this.provider = lease.provider();
        }

        private CompletionStage<Object> result() {
            return result;
        }

        private void complete(Object value, Throwable failure) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            try {
                if (failure == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(failure);
                }
            } finally {
                provider.complete(this);
                lease.close();
            }
        }

        private void invalidate() {
            // Do not attempt to cancel an arbitrary CompletionStage. Its provider owns that work;
            // after withdrawal this registry abandons the stage and releases its lifecycle lease.
            complete(null, new ApiOperationException(
                    ApiFailureCode.PROVIDER_RELOADED,
                    "Feature capability provider reloaded: " + type.getName()
            ));
        }
    }

    private record Withdrawal(Provider provider, Set<AsyncInvocation> pending) {
    }

    private final ConcurrentHashMap<Class<?>, Provider> providers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, CapabilityRef<?>> references = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();
    private final CopyOnWriteArrayList<CapabilityListener> listeners = new CopyOnWriteArrayList<>();
    private final Duration drainTimeout;
    private final String capabilityPackagePrefix;
    private final ClassLoader canonicalClassLoader;

    public DefaultCapabilityRegistry(String capabilityPackagePrefix, ClassLoader canonicalClassLoader) {
        this(capabilityPackagePrefix, canonicalClassLoader, DEFAULT_DRAIN_TIMEOUT);
    }

    protected DefaultCapabilityRegistry(
            String capabilityPackagePrefix,
            ClassLoader canonicalClassLoader,
            Duration drainTimeout
    ) {
        this.capabilityPackagePrefix = requirePackagePrefix(capabilityPackagePrefix);
        this.canonicalClassLoader = Objects.requireNonNull(canonicalClassLoader, "canonicalClassLoader");
        this.drainTimeout = Objects.requireNonNull(drainTimeout, "drainTimeout");
        if (drainTimeout.isNegative() || drainTimeout.isZero()) {
            throw new IllegalArgumentException("drainTimeout must be positive");
        }
    }

    @Override
    public <T> Registration register(FeatureId owner, Class<T> type, T instance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        validateCapabilityType(type);
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Capability implementation does not implement " + type.getName());
        }

        Provider provider = new Provider(owner, instance, generations.incrementAndGet(), drainTimeout);
        configureProviderGeneration(instance, provider.generation);
        providers.compute(type, (ignored, current) -> {
            if (current != null) {
                throw new IllegalStateException(
                        "Capability " + type.getName() + " is already provided by " + current.owner
                );
            }
            return provider;
        });
        notifyAvailable(type, provider.generation);
        return registration(type, provider);
    }

    @Override
    public <T> Registration replace(FeatureId owner, Class<T> type, T instance) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
        validateCapabilityType(type);
        if (!type.isInstance(instance)) {
            throw new IllegalArgumentException("Capability implementation does not implement " + type.getName());
        }

        Provider replacement = new Provider(owner, instance, generations.incrementAndGet(), drainTimeout);
        configureProviderGeneration(instance, replacement.generation);
        Withdrawal[] withdrawal = new Withdrawal[1];
        providers.compute(type, (ignored, current) -> {
            if (current == null) {
                throw new IllegalStateException("Capability " + type.getName() + " is not currently registered");
            }
            if (!current.owner.equals(owner)) {
                throw new IllegalStateException(
                        "Capability " + type.getName() + " is provided by another owner: " + current.owner
                );
            }
            withdrawal[0] = new Withdrawal(current, current.stopAccepting());
            return replacement;
        });
        // The replacement is already visible. Do not throw a drain timeout here: doing so would
        // strand the replacement without the registration its owner needs to withdraw it later.
        withdrawal[0].provider().stopAndAwaitDrain(withdrawal[0].pending());
        notifyReplaced(type, withdrawal[0].provider().generation, replacement.generation);
        return registration(type, replacement);
    }

    private Registration registration(Class<?> type, Provider provider) {
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                Withdrawal[] withdrawal = new Withdrawal[1];
                providers.compute(type, (ignored, current) -> {
                    if (current != provider) {
                        return current;
                    }
                    withdrawal[0] = new Withdrawal(provider, provider.stopAccepting());
                    return null;
                });
                if (withdrawal[0] != null) {
                    try {
                        ApiOperationException timeout = withdrawal[0].provider()
                                .stopAndAwaitDrain(withdrawal[0].pending());
                        if (timeout != null) {
                            throw timeout;
                        }
                    } finally {
                        notifyUnavailable(type, withdrawal[0].provider().generation);
                    }
                }
            }
        };
    }

    @Override
    public <T> CapabilityRef<T> reference(Class<T> type) {
        Objects.requireNonNull(type, "type");
        validateCapabilityType(type);
        CapabilityRef<?> reference = references.computeIfAbsent(type, DefaultCapabilityRef::new);
        return typeSafeReference(type, reference);
    }

    @Override
    public Set<Class<?>> availableTypes() {
        return Set.copyOf(new LinkedHashSet<>(providers.keySet()));
    }

    @Override
    public AutoCloseable subscribe(CapabilityListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        return () -> listeners.remove(listener);
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
    private static void safely(Runnable callback) { try { callback.run(); } catch (RuntimeException ignored) { } }

    private static void configureProviderGeneration(Object instance, long generation) {
        if (instance instanceof CapabilityProviderGenerationAware generationAware) {
            generationAware.providerGeneration(generation);
        }
    }

    public Optional<FeatureId> owner(Class<?> type) {
        Provider provider = providers.get(Objects.requireNonNull(type, "type"));
        return provider == null ? Optional.empty() : Optional.of(provider.owner);
    }

    private void validateCapabilityType(Class<?> type) {
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Capability contract must be an interface: " + type.getName());
        }
        String capabilityPackage = type.getPackageName();
        String capabilityPackageRoot = capabilityPackagePrefix.substring(0, capabilityPackagePrefix.length() - 1);
        if (!capabilityPackage.equals(capabilityPackageRoot)
                && !capabilityPackage.startsWith(capabilityPackagePrefix)) {
            throw new IllegalArgumentException(
                    "Capability contract must come from " + capabilityPackagePrefix + ": " + type.getName()
            );
        }

        Class<?> canonicalType;
        try {
            canonicalType = Class.forName(
                    type.getName(),
                    false,
                    canonicalClassLoader
            );
        } catch (ClassNotFoundException missingApiType) {
            throw new IllegalArgumentException(
                    "Capability contract is not part of the active host API: " + type.getName(),
                    missingApiType
            );
        }
        if (canonicalType != type) {
            throw new IllegalArgumentException(
                    "Capability contract was loaded from a duplicate host API copy: " + type.getName()
            );
        }
    }

    public int cachedReferenceCount() {
        return references.size();
    }

    private static String requirePackagePrefix(String value) {
        String normalized = Objects.requireNonNull(value, "capabilityPackagePrefix").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("capabilityPackagePrefix must not be blank");
        }
        return normalized.endsWith(".") ? normalized : normalized + ".";
    }

    private InvocationLease acquire(Class<?> type) {
        while (true) {
            Provider provider = providers.get(type);
            if (provider == null) {
                throw new CapabilityUnavailableException(type);
            }
            InvocationLease lease = provider.tryAcquire();
            if (lease != null) {
                return lease;
            }
        }
    }

    private <T> Optional<T> resolveProxy(DefaultCapabilityRef<T> reference) {
        return providers.containsKey(reference.type) ? Optional.of(reference.proxy) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <T> CapabilityRef<T> typeSafeReference(Class<T> type, CapabilityRef<?> reference) {
        if (reference.type() != type) {
            throw new IllegalStateException("Capability reference type mismatch");
        }
        return (CapabilityRef<T>) reference;
    }

    private final class DefaultCapabilityRef<T> implements CapabilityRef<T> {
        private final Class<T> type;
        private final T proxy;

        private DefaultCapabilityRef(Class<T> type) {
            this.type = type;
            this.proxy = type.cast(Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    this::invoke
            ));
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public Optional<T> get() {
            return resolveProxy(this);
        }

        @Override
        public OptionalLong generation() {
            Provider provider = providers.get(type);
            return provider == null ? OptionalLong.empty() : OptionalLong.of(provider.generation);
        }

        private Object invoke(Object proxyInstance, Method method, Object[] arguments) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxyInstance, method, arguments);
            }

            InvocationLease lease = acquire(type);
            boolean async = false;
            try {
                Object result;
                try {
                    result = method.invoke(lease.instance(), arguments);
                } catch (InvocationTargetException invocationFailure) {
                    throw invocationFailure.getCause();
                }

                if (result instanceof CompletionStage<?> stage) {
                    AsyncInvocation invocation = new AsyncInvocation(type, lease);
                    if (lease.provider().track(invocation)) {
                        stage.whenComplete((value, failure) ->
                                lease.provider().completeAsync(invocation, value, failure));
                    } else {
                        invocation.invalidate();
                    }
                    async = true;
                    return invocation.result();
                }
                return result;
            } finally {
                if (!async) {
                    lease.close();
                }
            }
        }

        private Object invokeObjectMethod(Object proxyInstance, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "equals" -> proxyInstance == arguments[0];
                case "hashCode" -> System.identityHashCode(proxyInstance);
                case "toString" -> "CapabilityRefProxy[" + type.getName() + "]";
                default -> throw new IllegalStateException("Unsupported Object method: " + method);
            };
        }
    }
}
