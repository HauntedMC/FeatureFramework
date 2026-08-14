package nl.hauntedmc.featureframework.api.feature;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Configures compile-time generation of a product's typed feature catalog. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateFeatureCatalog {
    /** Fully qualified name of the generated catalog class. */
    String generatedClassName();

    /** Root package containing this host's concrete feature declarations. */
    String featurePackage();

    /** Capabilities made available by the bootstrap before features start. */
    Class<?>[] bootstrapCapabilities() default {};
}
