package nl.hauntedmc.featureframework.processor;

import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
import nl.hauntedmc.featureframework.api.feature.FeatureRole;
import nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase;
import nl.hauntedmc.featureframework.host.FeatureDefinition;
import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureCatalogProcessorTest {
    @Test
    void generatesCatalogForFeaturesInTheBootstrapPackage() throws IOException {
        Compilation compilation = compile("""
                package sample;
                import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
                import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
                import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
                class Context { }
                abstract class Base implements nl.hauntedmc.featureframework.feature.Feature {
                    Base(Context context) { }
                    public String getFeatureName() { return ""; }
                    public String getFeatureVersion() { return ""; }
                    public java.util.List<String> getDependencies() { return java.util.List.of(); }
                    public java.util.List<String> getPluginDependencies() { return java.util.List.of(); }
                    public nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap getDefaultConfig() { return new nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap(); }
                    public nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap getDefaultMessages() { return new nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap(); }
                    public void initialize() { }
                    public void disable() { }
                }
                @GenerateFeatureCatalog(generatedClassName = "sample.Catalog", featurePackage = "sample", featureBase = Base.class, featureContext = Context.class)
                class Bootstrap { }
                @FeatureDeclaration(name = "Provider", version = "1.0.0", classification = FeatureClassification.CAPABILITY_PROVIDER, providesCapabilities = Contract.class)
                final class Provider extends Base { public Provider(Context context) { super(context); } }
                @FeatureDeclaration(name = "Consumer", version = "1.0.0", requiresCapabilities = Contract.class, requiresFeatures = "Provider")
                final class Consumer extends Base { public Consumer(Context context) { super(context); } }
                interface Contract { }
                """);

        assertTrue(compilation.success(), compilation.diagnostics());
        String generated = Files.readString(compilation.generated().resolve("sample/Catalog.java"));
        assertTrue(generated.contains("Provider.class, sample.Provider::new"));
        assertTrue(generated.contains("Consumer.class, sample.Consumer::new"));
        compilation.close();
    }

    @Test
    void rejectsMissingCapabilityProviders() throws IOException {
        Compilation compilation = compile("""
                package sample;
                import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
                import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
                class Context { }
                abstract class Base implements nl.hauntedmc.featureframework.feature.Feature {
                    Base(Context context) { }
                    public String getFeatureName() { return ""; }
                    public String getFeatureVersion() { return ""; }
                    public java.util.List<String> getDependencies() { return java.util.List.of(); }
                    public java.util.List<String> getPluginDependencies() { return java.util.List.of(); }
                    public nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap getDefaultConfig() { return new nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap(); }
                    public nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap getDefaultMessages() { return new nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap(); }
                    public void initialize() { }
                    public void disable() { }
                }
                @GenerateFeatureCatalog(generatedClassName = "sample.Catalog", featurePackage = "sample", featureBase = Base.class, featureContext = Context.class)
                class Bootstrap { }
                @FeatureDeclaration(name = "Consumer", version = "1.0.0", requiresCapabilities = Contract.class)
                final class Consumer extends Base { public Consumer(Context context) { super(context); } }
                interface Contract { }
                """);

        assertFalse(compilation.success());
        assertTrue(compilation.diagnostics().contains("No provider declared for capability sample.Contract"));
        compilation.close();
    }

    @Test
    void permitsOptionalCapabilitiesAndInternalServicesWithoutProviders() throws IOException {
        Compilation compilation = compile(source(
                """
                @GenerateFeatureCatalog(generatedClassName = "sample.Catalog", featurePackage = "sample", featureBase = Base.class, featureContext = Context.class)
                class Bootstrap { }
                """,
                """
                @FeatureDeclaration(name = "Consumer", version = "1.0.0", classification = FeatureClassification.CAPABILITY_CONSUMER,
                        optionallyUsesCapabilities = OptionalCapability.class, optionallyUsesInternalServices = OptionalService.class)
                final class Consumer extends Base { public Consumer(Context context) { super(context); } }
                interface OptionalCapability { }
                interface OptionalService { }
                """
        ));

        assertTrue(compilation.success(), compilation.diagnostics());
        compilation.close();
    }

    @Test
    void permitsRequiredBootstrapCapabilities() throws IOException {
        Compilation compilation = compile(source(
                """
                @GenerateFeatureCatalog(generatedClassName = "sample.Catalog", featurePackage = "sample", featureBase = Base.class,
                        featureContext = Context.class, bootstrapCapabilities = BootstrapCapability.class)
                class Bootstrap { }
                """,
                """
                @FeatureDeclaration(name = "Consumer", version = "1.0.0", classification = FeatureClassification.CAPABILITY_CONSUMER,
                        requiresCapabilities = BootstrapCapability.class)
                final class Consumer extends Base { public Consumer(Context context) { super(context); } }
                interface BootstrapCapability { }
                """
        ));

        assertTrue(compilation.success(), compilation.diagnostics());
        compilation.close();
    }

    @Test
    void generatesCompleteMetadataInDeterministicStartupPhaseOrder() throws IOException {
        Compilation compilation = compile(source(
                """
                @GenerateFeatureCatalog(generatedClassName = "sample.Catalog", featurePackage = "sample", featureBase = Base.class,
                        featureContext = Context.class, bootstrapCapabilities = BootstrapCapability.class)
                class Bootstrap { }
                """,
                """
                @FeatureDeclaration(name = "Provider", version = "1.2.3", startupPhase = FeatureStartupPhase.DEFERRED, enabledByDefault = true,
                        classification = FeatureClassification.CAPABILITY_PROVIDER, roles = FeatureRole.CAPABILITY_PROVIDER,
                        requiresPlugins = "bridge", providesCapabilities = ProvidedCapability.class,
                        providesInternalServices = ProvidedService.class)
                final class Provider extends Base { public Provider(Context context) { super(context); } }
                @FeatureDeclaration(name = "Consumer", version = "2.0.0", startupPhase = FeatureStartupPhase.SECURITY,
                        classification = FeatureClassification.CAPABILITY_CONSUMER,
                        roles = {FeatureRole.CAPABILITY_CONSUMER, FeatureRole.OPERATOR_FACING}, requiresFeatures = "Provider",
                        requiresCapabilities = BootstrapCapability.class, optionallyUsesCapabilities = OptionalCapability.class,
                        requiresInternalServices = ProvidedService.class, optionallyUsesInternalServices = OptionalService.class)
                final class Consumer extends Base { public Consumer(Context context) { super(context); } }
                interface BootstrapCapability { }
                interface ProvidedCapability { }
                interface OptionalCapability { }
                interface ProvidedService { }
                interface OptionalService { }
                """
        ));

        assertTrue(compilation.success(), compilation.diagnostics());
        try (var loader = new java.net.URLClassLoader(
                new java.net.URL[] {compilation.directory().toUri().toURL()}, getClass().getClassLoader())) {
            @SuppressWarnings("unchecked")
            List<FeatureDefinition<?, ?>> definitions = (List<FeatureDefinition<?, ?>>) loader
                    .loadClass("sample.Catalog")
                    .getMethod("definitions")
                    .invoke(null);

            assertEquals(List.of("Consumer", "Provider"), definitions.stream()
                    .map(FeatureDefinition::featureName)
                    .toList());
            FeatureDefinition<?, ?> consumer = definitions.getFirst();
            FeatureDefinition<?, ?> provider = definitions.getLast();
            assertEquals(FeatureStartupPhase.SECURITY, consumer.startupPhase());
            assertEquals(FeatureStartupPhase.DEFERRED, provider.startupPhase());
            assertEquals(FeatureClassification.CAPABILITY_CONSUMER, consumer.classification());
            assertEquals(Set.of(FeatureRole.CAPABILITY_CONSUMER, FeatureRole.OPERATOR_FACING), consumer.roles());
            assertEquals(Set.of("Provider"), consumer.requiredFeatures());
            assertEquals(Set.of("sample.BootstrapCapability"), typeNames(consumer.requiredCapabilities()));
            assertEquals(Set.of("sample.OptionalCapability"), typeNames(consumer.optionalCapabilities()));
            assertEquals(Set.of("sample.ProvidedService"), typeNames(consumer.requiredInternalServices()));
            assertEquals(Set.of("sample.OptionalService"), typeNames(consumer.optionalInternalServices()));
            assertTrue(provider.enabledByDefault());
            assertEquals(Set.of("bridge"), provider.pluginDependencies());
            assertEquals(Set.of("sample.ProvidedCapability"), typeNames(provider.providedCapabilities()));
            assertEquals(Set.of("sample.ProvidedService"), typeNames(provider.providedInternalServices()));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not load the generated catalog", exception);
        } finally {
            compilation.close();
        }
    }

    @Test
    void rejectsInvalidClassificationAtTheFeatureDeclaration() throws IOException {
        Compilation compilation = compile(source(
                """
                @GenerateFeatureCatalog(generatedClassName = "sample.Catalog", featurePackage = "sample", featureBase = Base.class, featureContext = Context.class)
                class Bootstrap { }
                """,
                """
                @FeatureDeclaration(name = "Provider", version = "1.0.0", classification = FeatureClassification.CAPABILITY_PROVIDER)
                final class Provider extends Base { public Provider(Context context) { super(context); } }
                """
        ));

        assertFalse(compilation.success());
        assertTrue(compilation.diagnostics().contains("CAPABILITY_PROVIDER features must declare a provided capability"));
        compilation.close();
    }

    @Test
    void rejectsConcreteFeaturesThatAreMissingADeclaration() throws IOException {
        Compilation compilation = compile(source(
                """
                @GenerateFeatureCatalog(generatedClassName = "sample.Catalog", featurePackage = "sample", featureBase = Base.class, featureContext = Context.class)
                class Bootstrap { }
                """,
                """
                @FeatureDeclaration(name = "Declared", version = "1.0.0")
                final class Declared extends Base { public Declared(Context context) { super(context); } }
                final class Omitted extends Base { public Omitted(Context context) { super(context); } }
                """
        ));

        assertFalse(compilation.success());
        assertTrue(compilation.diagnostics().contains("Omitted"));
        assertTrue(compilation.diagnostics().contains("must declare @FeatureDeclaration"));
        compilation.close();
    }

    private static Set<String> typeNames(Set<Class<?>> types) {
        return types.stream().map(Class::getName).collect(java.util.stream.Collectors.toSet());
    }

    private static String source(String catalog, String features) {
        return """
                package sample;
                import nl.hauntedmc.featureframework.api.feature.FeatureClassification;
                import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
                import nl.hauntedmc.featureframework.api.feature.FeatureRole;
                import nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase;
                import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;
                class Context { }
                abstract class Base implements nl.hauntedmc.featureframework.feature.Feature {
                    Base(Context context) { }
                    public String getFeatureName() { return ""; }
                    public String getFeatureVersion() { return ""; }
                    public java.util.List<String> getDependencies() { return java.util.List.of(); }
                    public java.util.List<String> getPluginDependencies() { return java.util.List.of(); }
                    public nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap getDefaultConfig() { return new nl.hauntedmc.featureframework.toolkit.io.config.ConfigMap(); }
                    public nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap getDefaultMessages() { return new nl.hauntedmc.featureframework.toolkit.io.localization.MessageMap(); }
                    public void initialize() { }
                    public void disable() { }
                }
                %s
                %s
                """.formatted(catalog, features);
    }

    private Compilation compile(String source) throws IOException {
        Path directory = Files.createTempDirectory("feature-catalog-processor-test");
        Path sourceFile = directory.resolve("sample/Bootstrap.java");
        Path generated = directory.resolve("generated");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    List.of("--release", "25", "-classpath", System.getProperty("java.class.path"), "-d", directory.toString(), "-s", generated.toString()),
                    null,
                    files.getJavaFileObjects(sourceFile.toFile()));
            task.setProcessors(List.of(new FeatureCatalogProcessor()));
            boolean success = Boolean.TRUE.equals(task.call());
            String messages = diagnostics.getDiagnostics().stream()
                    .map(diagnostic -> diagnostic.getMessage(null))
                    .reduce("", (left, right) -> left + right + System.lineSeparator());
            return new Compilation(success, messages, generated, directory);
        }
    }

    private record Compilation(boolean success, String diagnostics, Path generated, Path directory) {
        private void close() throws IOException {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not clean compiler test directory", exception);
                    }
                });
            }
        }
    }
}
