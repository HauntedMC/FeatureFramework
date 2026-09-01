package nl.hauntedmc.featureframework.processor;

import nl.hauntedmc.featureframework.api.feature.FeatureDeclaration;
import nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase;
import nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Generates reflection-free feature collections from {@link FeatureDeclaration} annotations. */
@SupportedAnnotationTypes({
        "nl.hauntedmc.featureframework.api.feature.FeatureDeclaration",
        "nl.hauntedmc.featureframework.api.feature.GenerateFeatureCatalog"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class FeatureCatalogProcessor extends AbstractProcessor {
    private static final Pattern SEMVER = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");
    private static final String DECLARATION = FeatureDeclaration.class.getCanonicalName();
    private static final String CATALOG = GenerateFeatureCatalog.class.getCanonicalName();
    private final Set<String> generatedCatalogs = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        if (roundEnvironment.processingOver()) return false;
        for (Element root : roundEnvironment.getElementsAnnotatedWith(GenerateFeatureCatalog.class)) {
            if (!(root instanceof TypeElement host) || host.getKind() != ElementKind.CLASS) {
                error(root, "@GenerateFeatureCatalog may only target a class");
                continue;
            }
            Config config = config(host);
            if (config == null || !generatedCatalogs.add(config.generatedClassName())) continue;
            List<Entry> entries = entries(roundEnvironment, config);
            boolean valid = validate(entries, config);
            if (!entries.isEmpty()) {
                valid &= declaredConcreteFeatures(roundEnvironment, config, entries.getFirst().element().getSuperclass());
            }
            if (!valid) continue;
            try {
                generate(host, config, entries);
            } catch (IOException exception) {
                error(host, "Could not generate feature catalog: " + exception.getMessage());
            }
        }
        return false;
    }

    private Config config(TypeElement host) {
        Map<String, Object> values = values(host, CATALOG);
        String generatedName = text(values, "generatedClassName", host);
        String featurePackage = text(values, "featurePackage", host);
        List<TypeMirror> bootstrap = types(values, "bootstrapCapabilities");
        if (generatedName == null || featurePackage == null) return null;
        if (!generatedName.contains(".")) {
            error(host, "generatedClassName must be fully qualified");
            return null;
        }
        return new Config(generatedName, featurePackage, Set.copyOf(bootstrap));
    }

    private List<Entry> entries(RoundEnvironment environment, Config config) {
        List<Entry> entries = new ArrayList<>();
        for (Element element : environment.getElementsAnnotatedWith(FeatureDeclaration.class)) {
            if (!(element instanceof TypeElement type) || !inPackage(type, config.featurePackage())) continue;
            Map<String, Object> values = values(type, DECLARATION);
            String name = text(values, "name", type);
            String version = text(values, "version", type);
            TypeMirror constructorContext = constructorContext(type);
            if (name == null || version == null || constructorContext == null) continue;
            entries.add(new Entry(
                    type,
                    name,
                    version,
                    FeatureStartupPhase.valueOf(enumName(values, "startupPhase")),
                    enumName(values, "scope"),
                    bool(values, "enabledByDefault"),
                    enumNames(values, "roles"),
                    texts(values, "requiresFeatures"),
                    texts(values, "optionallyUsesFeatures"),
                    texts(values, "requiresPlugins"),
                    types(values, "requiresResourceExtensions"),
                    types(values, "optionallyUsesResourceExtensions"),
                    types(values, "requiresCapabilities"),
                    types(values, "optionallyUsesCapabilities"),
                    types(values, "providesCapabilities"),
                    types(values, "requiresInternalServices"),
                    types(values, "optionallyUsesInternalServices"),
                    types(values, "providesInternalServices"),
                    constructorContext
            ));
        }
        return entries;
    }

    private boolean declaredConcreteFeatures(RoundEnvironment environment, Config config, TypeMirror baseType) {
        boolean valid = true;
        for (Element root : environment.getRootElements()) {
            if (!(root instanceof TypeElement type)
                    || type.getKind() != ElementKind.CLASS
                    || type.getModifiers().contains(Modifier.ABSTRACT)
                    || !inPackage(type, config.featurePackage())
                    || !types().isAssignable(types().erasure(type.asType()), types().erasure(baseType))) {
                continue;
            }
            if (!hasAnnotation(type, DECLARATION)) {
                valid &= invalid(type, "Concrete feature " + type.getQualifiedName() + " extending "
                        + baseType + " must declare @FeatureDeclaration");
            }
        }
        return valid;
    }

    private TypeMirror constructorContext(TypeElement type) {
        List<ExecutableElement> matching = new ArrayList<>();
        for (Element enclosed : type.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.CONSTRUCTOR) continue;
            ExecutableElement constructor = (ExecutableElement) enclosed;
            if (!constructor.getModifiers().contains(Modifier.PUBLIC) || constructor.getParameters().size() != 1) continue;
            matching.add(constructor);
        }
        if (matching.size() != 1) {
            error(type, "Feature must declare exactly one public single-argument constructor");
            return null;
        }
        return matching.getFirst().getParameters().getFirst().asType();
    }

    private boolean validate(List<Entry> entries, Config config) {
        boolean valid = true;
        if (entries.isEmpty()) {
            error(null, "No @FeatureDeclaration classes found in " + config.featurePackage());
            return false;
        }
        Map<String, Entry> names = new LinkedHashMap<>();
        Map<String, Entry> implementations = new LinkedHashMap<>();
        Map<String, Entry> capabilities = new LinkedHashMap<>();
        Map<String, Entry> internalServices = new LinkedHashMap<>();
        TypeMirror baseType = entries.getFirst().element().getSuperclass();
        TypeMirror contextType = entries.getFirst().constructorContext();
        for (Entry entry : entries) {
            valid &= SEMVER.matcher(entry.version()).matches() || invalid(entry.element(), "version must be semantic X.Y.Z");
            valid &= !entry.name().isBlank() || invalid(entry.element(), "name must not be blank");
            valid &= types().isSameType(types().erasure(entry.element().getSuperclass()), types().erasure(baseType))
                    || invalid(entry.element(), "All generated features must share the same declared base type " + baseType);
            valid &= types().isSameType(types().erasure(entry.constructorContext()), types().erasure(contextType))
                    || invalid(entry.element(), "All generated features must use the same constructor context " + contextType);
            valid &= unique(names, entry.name(), entry, "feature name");
            valid &= unique(implementations, entry.element().getQualifiedName().toString(), entry, "implementation type");
            valid &= uniqueProviders(capabilities, entry.providesCapabilities(), entry, "capability");
            valid &= uniqueProviders(internalServices, entry.providesInternalServices(), entry, "internal service");
            valid &= disjoint(entry.element(), entry.requiresCapabilities(), entry.providesCapabilities(), "required", "provided capability");
            valid &= disjoint(entry.element(), entry.optionalCapabilities(), entry.providesCapabilities(), "optional", "provided capability");
            valid &= disjoint(entry.element(), entry.requiresInternalServices(), entry.providesInternalServices(), "required", "provided internal service");
            valid &= disjoint(entry.element(), entry.optionalInternalServices(), entry.providesInternalServices(), "optional", "provided internal service");
            valid &= disjoint(entry.element(), entry.requiredResourceExtensions(), entry.optionalResourceExtensions(),
                    "required", "optional resource extension");
            if (entry.roles().contains("EXTENSION_PROVIDER") && entry.providesCapabilities().isEmpty()) {
                valid &= invalid(entry.element(), "Extension providers must declare a provided capability");
            }
        }
        for (Entry entry : entries) {
            valid &= references(entries, entry, entry.requiredFeatures(), "required feature");
            valid &= references(entries, entry, entry.optionalFeatures(), "optional feature");
            valid &= providers(entry, entry.requiresCapabilities(), capabilities, config.bootstrapCapabilities(), "capability");
            valid &= providers(entry, entry.requiresInternalServices(), internalServices, Set.of(), "internal service");
        }
        return valid;
    }

    private boolean references(Collection<Entry> entries, Entry owner, List<String> references, String kind) {
        Set<String> known = entries.stream().map(entry -> entry.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        boolean valid = true;
        for (String reference : references) {
            if (reference.equalsIgnoreCase(owner.name())) valid &= invalid(owner.element(), "Feature cannot declare itself as " + kind);
            else if (!known.contains(reference.toLowerCase(Locale.ROOT))) valid &= invalid(owner.element(), "Unknown " + kind + ": " + reference);
        }
        return valid;
    }

    private boolean providers(Entry owner, List<TypeMirror> references, Map<String, Entry> providers,
                              Set<TypeMirror> bootstrap, String kind) {
        boolean valid = true;
        Set<String> available = new HashSet<>(providers.keySet());
        bootstrap.forEach(type -> available.add(key(type)));
        for (TypeMirror reference : references) {
            if (!available.contains(key(reference))) {
                valid &= invalid(owner.element(), "No provider declared for " + kind + " " + reference);
            }
        }
        return valid;
    }

    private boolean unique(Map<String, Entry> values, String value, Entry entry, String kind) {
        Entry previous = values.putIfAbsent(value.toLowerCase(Locale.ROOT), entry);
        return previous == null || invalid(entry.element(), "Duplicate " + kind + ": " + value);
    }

    private boolean uniqueProviders(Map<String, Entry> values, List<TypeMirror> types, Entry entry, String kind) {
        boolean valid = true;
        for (TypeMirror type : types) {
            Entry previous = values.putIfAbsent(key(type), entry);
            if (previous != null) valid &= invalid(entry.element(), kind + " is also provided by " + previous.name() + ": " + type);
        }
        return valid;
    }

    private boolean disjoint(Element element, List<TypeMirror> first, List<TypeMirror> second, String firstName, String secondName) {
        Set<String> secondKeys = second.stream().map(this::key).collect(java.util.stream.Collectors.toSet());
        boolean valid = true;
        for (TypeMirror type : first) {
            if (secondKeys.contains(key(type))) valid &= invalid(element, type + " cannot be both " + firstName + " and " + secondName);
        }
        return valid;
    }

    private void generate(TypeElement host, Config config, List<Entry> entries) throws IOException {
        entries.sort(Comparator.comparing(Entry::startupPhase).thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        int split = config.generatedClassName().lastIndexOf('.');
        String packageName = config.generatedClassName().substring(0, split);
        String className = config.generatedClassName().substring(split + 1);
        TypeMirror featureType = entries.getFirst().element().getSuperclass();
        TypeMirror contextType = entries.getFirst().constructorContext();
        for (Entry entry : entries) {
            if (!types().isSameType(featureType, entry.element().getSuperclass())
                    || !types().isSameType(contextType, entry.constructorContext())) {
                error(entry.element(), "All generated features must share the same declared base and context types");
                return;
            }
        }
        Filer filer = processingEnv.getFiler();
        JavaFileObject file = filer.createSourceFile(config.generatedClassName(), host);
        try (Writer writer = file.openWriter()) {
            writer.write("package " + packageName + ";\n\n");
            writer.write("/** Generated by FeatureFramework; do not edit. */\n");
            writer.write("public final class " + className + " {\n");
            writer.write("    private static final java.util.List<nl.hauntedmc.featureframework.host.FeatureDefinition<"
                    + featureType + ", " + contextType + ">> DEFINITIONS = java.util.List.of(\n");
            for (int index = 0; index < entries.size(); index++) {
                writer.write("            " + definition(entries.get(index)) + (index + 1 == entries.size() ? "\n" : ",\n"));
            }
            writer.write("    );\n\n    private " + className + "() {}\n\n");
            writer.write("    public static java.util.List<nl.hauntedmc.featureframework.host.FeatureDefinition<"
                    + featureType + ", " + contextType + ">> definitions() { return DEFINITIONS; }\n\n");
            writer.write("    public static nl.hauntedmc.featureframework.host.FeatureCollection<" + featureType + ", "
                    + contextType + "> collection() { return nl.hauntedmc.featureframework.host.FeatureCollection.copyOf(DEFINITIONS); }\n\n");
            writer.write("    private static nl.hauntedmc.featureframework.host.FeatureDefinition<" + featureType + ", "
                    + contextType + "> feature(String name, String version, Class<? extends " + featureType + "> type, "
                    + "java.util.function.Function<" + contextType + ", ? extends " + featureType + "> constructor, "
                    + "nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase startupPhase, "
                    + "nl.hauntedmc.featureframework.api.feature.FeatureScope scope, "
                    + "boolean enabledByDefault, nl.hauntedmc.featureframework.api.feature.FeatureRole[] roles, String[] requiredFeatures, "
                    + "String[] optionalFeatures, String[] plugins, Class<?>[] requiredResources, Class<?>[] optionalResources, "
                    + "Class<?>[] requiredCapabilities, Class<?>[] optionalCapabilities, "
                    + "Class<?>[] providedCapabilities, Class<?>[] requiredServices, Class<?>[] optionalServices, Class<?>[] providedServices) {\n");
            writer.write("        var builder = nl.hauntedmc.featureframework.host.FeatureDefinition.<" + featureType + ", "
                    + contextType + ">builder(name, version, type, constructor).startupPhase(startupPhase)"
                    + ".scope(scope)"
                    + ".roles(roles).requiresFeatures(requiredFeatures).optionallyUsesFeatures(optionalFeatures).requiresPlugins(plugins)"
                    + ".requiresResourceExtensions(requiredResources).optionallyUsesResourceExtensions(optionalResources)"
                    + ".requiresCapabilities(requiredCapabilities).optionallyUsesCapabilities(optionalCapabilities).providesCapabilities(providedCapabilities)"
                    + ".requiresInternalServices(requiredServices).optionallyUsesInternalServices(optionalServices).providesInternalServices(providedServices);\n");
            writer.write("        if (enabledByDefault) builder.enabledByDefault();\n        return builder.build();\n    }\n}\n");
        }
    }

    private String definition(Entry entry) {
        return "feature(" + quote(entry.name()) + ", " + quote(entry.version()) + ", " + entry.element().getQualifiedName()
                + ".class, " + entry.element().getQualifiedName() + "::new, "
                + "nl.hauntedmc.featureframework.api.feature.FeatureStartupPhase." + entry.startupPhase() + ", "
                + "nl.hauntedmc.featureframework.api.feature.FeatureScope." + entry.scope() + ", "
                + entry.enabledByDefault() + ", " + enumArray("FeatureRole", entry.roles()) + ", " + stringArray(entry.requiredFeatures())
                + ", " + stringArray(entry.optionalFeatures()) + ", " + stringArray(entry.plugins()) + ", "
                + typeArray(entry.requiredResourceExtensions()) + ", " + typeArray(entry.optionalResourceExtensions()) + ", "
                + typeArray(entry.requiresCapabilities()) + ", " + typeArray(entry.optionalCapabilities()) + ", "
                + typeArray(entry.providesCapabilities()) + ", " + typeArray(entry.requiresInternalServices()) + ", "
                + typeArray(entry.optionalInternalServices()) + ", " + typeArray(entry.providesInternalServices()) + ")";
    }

    private String enumArray(String type, List<String> values) {
        String prefix = "new nl.hauntedmc.featureframework.api.feature." + type + "[] {";
        return prefix + values.stream().map(value -> "nl.hauntedmc.featureframework.api.feature." + type + "." + value)
                .collect(java.util.stream.Collectors.joining(", ")) + "}";
    }

    private String stringArray(List<String> values) {
        return "new String[] {" + values.stream().map(this::quote).collect(java.util.stream.Collectors.joining(", ")) + "}";
    }

    private String typeArray(List<TypeMirror> values) {
        return "new Class<?>[] {" + values.stream().map(value -> value + ".class")
                .collect(java.util.stream.Collectors.joining(", ")) + "}";
    }

    private Map<String, Object> values(Element element, String annotationType) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().toString().equals(annotationType)) {
                Map<String, Object> values = new HashMap<>();
                processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).forEach((method, value) ->
                        values.put(method.getSimpleName().toString(), value.getValue()));
                return values;
            }
        }
        return Map.of();
    }

    private boolean hasAnnotation(Element element, String annotationType) {
        return element.getAnnotationMirrors().stream()
                .anyMatch(mirror -> mirror.getAnnotationType().toString().equals(annotationType));
    }

    private String text(Map<String, Object> values, String name, Element element) {
        Object value = values.get(name);
        if (value instanceof String text && !text.isBlank()) return text;
        error(element, name + " must not be blank");
        return null;
    }

    private boolean bool(Map<String, Object> values, String name) { return (Boolean) values.get(name); }
    private String enumName(Map<String, Object> values, String name) { return ((VariableElement) values.get(name)).getSimpleName().toString(); }

    private List<String> enumNames(Map<String, Object> values, String name) {
        return array(values, name).stream().map(value -> ((VariableElement) value).getSimpleName().toString()).toList();
    }

    private List<String> texts(Map<String, Object> values, String name) {
        return array(values, name).stream().map(Objects::toString).map(String::trim).toList();
    }

    private TypeMirror type(Map<String, Object> values, String name, Element element) {
        Object value = values.get(name);
        if (value instanceof TypeMirror mirror) return mirror;
        error(element, name + " must be a class literal");
        return null;
    }

    private List<TypeMirror> types(Map<String, Object> values, String name) {
        return array(values, name).stream().map(TypeMirror.class::cast).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Object> array(Map<String, Object> values, String name) {
        Object value = values.get(name);
        if (!(value instanceof List<?> list)) return List.of();
        return ((List<? extends AnnotationValue>) list).stream().map(AnnotationValue::getValue).toList();
    }

    private boolean inPackage(TypeElement type, String featurePackage) {
        String packageName = elements().getPackageOf(type).getQualifiedName().toString();
        return packageName.equals(featurePackage) || packageName.startsWith(featurePackage + ".");
    }

    private String key(TypeMirror type) { return types().erasure(type).toString(); }
    private boolean invalid(Element element, String message) { error(element, message); return false; }
    private void error(Element element, String message) { processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element); }
    private Elements elements() { return processingEnv.getElementUtils(); }
    private Types types() { return processingEnv.getTypeUtils(); }
    private String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }

    private record Config(String generatedClassName, String featurePackage,
                          Set<TypeMirror> bootstrapCapabilities) {}

    private record Entry(TypeElement element, String name, String version, FeatureStartupPhase startupPhase,
                         String scope, boolean enabledByDefault,
                         List<String> roles, List<String> requiredFeatures,
                         List<String> optionalFeatures, List<String> plugins,
                         List<TypeMirror> requiredResourceExtensions, List<TypeMirror> optionalResourceExtensions,
                         List<TypeMirror> requiresCapabilities,
                         List<TypeMirror> optionalCapabilities, List<TypeMirror> providesCapabilities,
                         List<TypeMirror> requiresInternalServices, List<TypeMirror> optionalInternalServices,
                         List<TypeMirror> providesInternalServices, TypeMirror constructorContext) {}
}
