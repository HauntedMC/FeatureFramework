package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.api.service.CapabilityListener;
import nl.hauntedmc.featureframework.api.service.CapabilityRef;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.resource.FeatureResourceOwner;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * The single dependency and publication boundary available to a feature implementation.
 *
 * <p>Every operation is checked against the feature declaration. This prevents a feature from
 * accidentally reaching an undeclared provider, treating an optional provider as required, or
 * publishing a contract it does not own.</p>
 */
public final class FeatureServices {
    private final String featureName;
    private final ResolvedFeatureDefinition<?, ?> definition;
    private final CapabilityRegistry capabilities;
    private final InternalServiceRegistry<?> internalServices;
    private final FeatureServiceManager<?> publications;
    private final FeatureResourceOwner ownership;

    public FeatureServices(
            ResolvedFeatureDefinition<?, ?> definition,
            CapabilityRegistry capabilities,
            InternalServiceRegistry<?> internalServices,
            FeatureServiceManager<?> publications,
            FeatureResourceOwner ownership
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.featureName = definition.featureName();
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.internalServices = Objects.requireNonNull(internalServices, "internalServices");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.ownership = Objects.requireNonNull(ownership, "ownership");
    }

    /** Resolves a dependency declared as required. */
    public <T> T require(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (definition.requiredCapabilities().contains(type)) {
            return capabilities.requireCapability(type);
        }
        if (definition.requiredInternalServices().contains(type)) {
            return internalServices.require(type);
        }
        throw invalid(type, "require", "required");
    }

    /** Returns a reload-safe reference for a dependency declared as optional. */
    public <T> ServiceRef<T> reference(Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (definition.optionalCapabilities().contains(type)) {
            return new CapabilityServiceRef<>(capabilities.reference(type));
        }
        if (definition.optionalInternalServices().contains(type)) {
            return internalServices.reference(type);
        }
        throw invalid(type, "reference", "optional");
    }

    /** Stages a declared provider for atomic lifecycle activation. */
    public <T> void publish(Class<T> type, T provider) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        if (definition.providedCapabilities().contains(type)) {
            publications.registerService(type, provider);
            return;
        }
        if (definition.providedInternalServices().contains(type)) {
            publications.registerInternalService(type, provider);
            return;
        }
        throw invalid(type, "publish", "provided");
    }

    /** Runs after all of this feature's providers have been staged, immediately before publication. */
    public void onActivation(Runnable action) {
        publications.registerActivationHook(Objects.requireNonNull(action, "action"));
    }

    /**
     * Attaches an integration to every active generation of an optional provider.
     *
     * <p>The current attachment is closed before a provider is removed or replaced and during
     * consumer cleanup. The returned attachment must contain all provider-specific resources.</p>
     */
    public <T> void integrate(Class<T> type, Function<? super T, ? extends AutoCloseable> attachmentFactory) {
        Objects.requireNonNull(attachmentFactory, "attachmentFactory");
        ServiceRef<T> reference = reference(type);
        Integration<T> integration = new Integration<>(reference, attachmentFactory);
        AutoCloseable subscription;
        if (definition.optionalCapabilities().contains(type)) {
            subscription = capabilities.subscribe(integration.capabilityListener());
        } else {
            subscription = internalServices.subscribe(integration.internalListener());
        }
        integration.subscription(subscription);
        try {
            integration.attachCurrent();
            ownership.own(integration);
        } catch (RuntimeException | Error failure) {
            try {
                integration.close();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private IllegalStateException invalid(Class<?> type, String operation, String expected) {
        return new IllegalStateException("Feature '" + featureName + "' cannot " + operation + " "
                + type.getName() + "; the contract is not declared as " + expected);
    }

    private static final class CapabilityServiceRef<T> implements ServiceRef<T> {
        private final CapabilityRef<T> delegate;

        private CapabilityServiceRef(CapabilityRef<T> delegate) {
            this.delegate = delegate;
        }

        @Override public Class<T> type() { return delegate.type(); }
        @Override public Optional<T> get() { return delegate.get(); }
        @Override public OptionalLong generation() { return delegate.generation(); }
    }

    private static final class Integration<T> implements AutoCloseable {
        private final ServiceRef<T> reference;
        private final Function<? super T, ? extends AutoCloseable> factory;
        private final AtomicBoolean closed = new AtomicBoolean();
        private AutoCloseable attachment;
        private AutoCloseable subscription;

        private Integration(ServiceRef<T> reference, Function<? super T, ? extends AutoCloseable> factory) {
            this.reference = reference;
            this.factory = factory;
        }

        private synchronized void subscription(AutoCloseable value) {
            subscription = Objects.requireNonNull(value, "subscription");
        }

        private CapabilityListener capabilityListener() {
            return new CapabilityListener() {
                @Override public void available(Class<?> type, long generation) { changed(type); }
                @Override public void unavailable(Class<?> type, long generation) { changed(type); }
                @Override public void replaced(Class<?> type, long previous, long next) { changed(type); }
            };
        }

        private InternalServiceListener internalListener() {
            return new InternalServiceListener() {
                @Override public void available(Class<?> type, long generation) { changed(type); }
                @Override public void unavailable(Class<?> type, long generation) { changed(type); }
                @Override public void replaced(Class<?> type, long previous, long next) { changed(type); }
            };
        }

        private void changed(Class<?> changedType) {
            if (changedType == reference.type()) {
                refresh();
            }
        }

        private synchronized void attachCurrent() {
            if (closed.get() || attachment != null) return;
            reference.get().ifPresent(provider -> attachment = Objects.requireNonNull(
                    factory.apply(provider), "optional service attachment"));
        }

        private synchronized void refresh() {
            if (closed.get()) return;
            closeAttachment();
            attachCurrent();
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) return;
            Throwable failure = closeAttachment();
            try {
                if (subscription != null) subscription.close();
            } catch (Throwable current) {
                if (failure == null) failure = current;
                else failure.addSuppressed(current);
            }
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure != null) throw new IllegalStateException("Optional service integration cleanup failed", failure);
        }

        private Throwable closeAttachment() {
            AutoCloseable current = attachment;
            attachment = null;
            if (current == null) return null;
            try {
                current.close();
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        }
    }
}
