package nl.hauntedmc.featureframework.service;

import nl.hauntedmc.featureframework.api.feature.FeatureId;
import nl.hauntedmc.featureframework.api.feature.FeaturePlacement;
import nl.hauntedmc.featureframework.feature.Feature;
import nl.hauntedmc.featureframework.loader.ResolvedFeatureDefinition;
import nl.hauntedmc.featureframework.resource.FeatureResourceOwner;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap;
import nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureServicesTest {
    @Test
    void optionalReferenceCanBeRequiredByAConditionalRuntimeBranch() {
        Fixture fixture = fixture();
        ServiceRef<OptionalCapability> reference = fixture.services.reference(OptionalCapability.class);

        assertThrows(IllegalStateException.class, reference::require);
        OptionalCapability optional = () -> "optional";
        Registration registration = fixture.capabilities.register(
                FeatureId.of("provider"), OptionalCapability.class, optional);
        assertEquals("optional", reference.require().value());

        registration.close();
        assertThrows(IllegalStateException.class, reference::require);
    }

    @Test
    void enforcesTheDeclaredRequiredOptionalAndProvidedOperations() {
        Fixture fixture = fixture();
        RequiredCapability required = () -> "required";
        Registration requiredRegistration = fixture.capabilities.register(
                FeatureId.of("provider"), RequiredCapability.class, required);
        RequiredPort port = () -> "port";
        Registration portRegistration = fixture.internal.register(
                FeatureId.of("provider"), RequiredPort.class, port);

        assertEquals("required", fixture.services.require(RequiredCapability.class).value());
        assertSame(port, fixture.services.require(RequiredPort.class));
        assertTrue(fixture.services.reference(OptionalCapability.class).get().isEmpty());

        ProvidedCapability provided = () -> "provided";
        PublishedPort published = () -> "published";
        fixture.services.publish(ProvidedCapability.class, provided);
        fixture.services.publish(PublishedPort.class, published);
        fixture.publications.activateServices();

        assertEquals("provided", fixture.capabilities.requireCapability(ProvidedCapability.class).value());
        assertSame(published, fixture.internal.require(PublishedPort.class));
        assertThrows(IllegalStateException.class,
                () -> fixture.services.require(OptionalCapability.class));
        assertThrows(IllegalStateException.class,
                () -> fixture.services.reference(RequiredCapability.class));
        assertThrows(IllegalStateException.class,
                () -> fixture.services.publish(UndeclaredPort.class, () -> "no"));

        fixture.publications.unregisterAllServices();
        requiredRegistration.close();
        portRegistration.close();
    }

    @Test
    void optionalIntegrationFollowsProviderGenerationsAndIsOwnedByTheFeature() {
        Fixture fixture = fixture();
        List<String> events = new ArrayList<>();
        fixture.services.integrate(OptionalPort.class, provider -> {
            events.add("attach:" + provider.value());
            return () -> events.add("detach:" + provider.value());
        });

        Registration first = fixture.internal.register(
                FeatureId.of("provider"), OptionalPort.class, () -> "first");
        Registration replacement = fixture.internal.replace(
                FeatureId.of("provider"), OptionalPort.class, () -> "second");
        fixture.ownership.cleanup();
        replacement.close();
        first.close();

        assertEquals(List.of("attach:first", "detach:first", "attach:second", "detach:second"), events);
    }

    @Test
    void failedOptionalIntegrationDoesNotLeaveAProviderSubscriptionBehind() {
        Fixture fixture = fixture();
        Registration first = fixture.internal.register(
                FeatureId.of("provider"), OptionalPort.class, () -> "first");
        List<String> attempts = new ArrayList<>();

        assertThrows(IllegalStateException.class, () -> fixture.services.integrate(OptionalPort.class, provider -> {
            attempts.add(provider.value());
            throw new IllegalStateException("cannot attach");
        }));

        first.close();
        Registration second = fixture.internal.register(
                FeatureId.of("provider"), OptionalPort.class, () -> "second");
        assertEquals(List.of("first"), attempts);
        second.close();
    }

    private static Fixture fixture() {
        DefaultCapabilityRegistry capabilities = new DefaultCapabilityRegistry(
                "nl.hauntedmc.featureframework.service", FeatureServicesTest.class.getClassLoader());
        InternalServiceRegistry<FeatureId> internal = new InternalServiceRegistry<>();
        FeatureServiceManager<FeatureId> publications = new FeatureServiceManager<>();
        publications.bindRegistries(capabilities, internal, FeatureId.of("consumer"));
        FeatureResourceOwner ownership = new FeatureResourceOwner();
        ResolvedFeatureDefinition<DummyFeature, Object> definition = new ResolvedFeatureDefinition<>(
                "consumer", "Consumer", "1", DummyFeature.class, ignored -> new DummyFeature(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), FeaturePlacement.ALL_NODES,
                Set.of(RequiredCapability.class), Set.of(OptionalCapability.class),
                Set.of(ProvidedCapability.class), Set.of(RequiredPort.class), Set.of(OptionalPort.class),
                Set.of(PublishedPort.class));
        return new Fixture(capabilities, internal, publications, ownership,
                new FeatureServices(definition, capabilities, internal, publications, ownership));
    }

    private record Fixture(
            DefaultCapabilityRegistry capabilities,
            InternalServiceRegistry<FeatureId> internal,
            FeatureServiceManager<FeatureId> publications,
            FeatureResourceOwner ownership,
            FeatureServices services
    ) { }

    interface RequiredCapability { String value(); }
    interface OptionalCapability { String value(); }
    interface ProvidedCapability { String value(); }
    interface RequiredPort { String value(); }
    interface OptionalPort { String value(); }
    interface PublishedPort { String value(); }
    interface UndeclaredPort { String value(); }

    private static final class DummyFeature implements Feature {
        @Override public String name() { return "dummy"; }
        @Override public String version() { return "1"; }
        @Override public List<String> dependencies() { return List.of(); }
        @Override public List<String> pluginDependencies() { return List.of(); }
        @Override public ConfigMap defaultConfig() { return new ConfigMap(); }
        @Override public MessageMap defaultMessages() { return new MessageMap(); }
        @Override public void initialize() { }
        @Override public void disable() { }
    }
}
