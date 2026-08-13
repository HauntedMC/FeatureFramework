package nl.hauntedmc.featureframework.api.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the complete host metadata for a concrete feature.
 *
 * <p>The FeatureFramework annotation processor turns declarations into a typed, deterministic
 * {@code FeatureCollection}; the annotation is deliberately not retained by deployed plugins.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface FeatureDeclaration {
    /** Stable, human-readable feature identity within its host plugin. */
    String name();

    /** Semantic feature version in {@code major.minor.patch} form. */
    String version();

    /**
     * Readable startup phase used when otherwise independent features start together.
     *
     * <p>Declare {@linkplain #requiresFeatures() dependencies} for a real ordering requirement;
     * phases are deliberately broad and never override dependencies.</p>
     */
    FeatureStartupPhase startupPhase() default FeatureStartupPhase.CORE;

    /** Whether a newly created configuration enables this feature. */
    boolean enabledByDefault() default false;

    /** Architectural responsibility of this feature. */
    FeatureClassification classification() default FeatureClassification.INTERNAL;

    /** Independent responsibilities that supplement {@link #classification()}. */
    FeatureRole[] roles() default {};

    /** Feature names that must be running before this feature. */
    String[] requiresFeatures() default {};

    /** Feature names used when available, without creating a required startup dependency. */
    String[] optionallyUsesFeatures() default {};

    /** Platform plugin identifiers required for this feature to run. */
    String[] requiresPlugins() default {};

    /** Capabilities that must have a catalog or bootstrap provider. */
    Class<?>[] requiresCapabilities() default {};

    /** Capabilities used opportunistically; no provider is required at compile time. */
    Class<?>[] optionallyUsesCapabilities() default {};

    /** Capabilities published by this feature. Each capability has one catalog provider. */
    Class<?>[] providesCapabilities() default {};

    /** Internal services that must have a provider in this catalog. */
    Class<?>[] requiresInternalServices() default {};

    /** Internal services used opportunistically; no provider is required at compile time. */
    Class<?>[] optionallyUsesInternalServices() default {};

    /** Internal services published by this feature. Each service has one catalog provider. */
    Class<?>[] providesInternalServices() default {};
}
