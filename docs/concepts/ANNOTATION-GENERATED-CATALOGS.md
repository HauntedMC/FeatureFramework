# Annotation-generated feature catalogs

FeatureFramework's default registration model is compile-time generation. Put `@FeatureDeclaration` on every
concrete feature and put `@GenerateFeatureCatalog` on the plugin bootstrap. The processor generates the catalog
class requested by the bootstrap annotation.

The processor validates that every concrete framework feature in the configured package has a declaration, as well
as duplicate identities and providers, required feature/capability/internal-service references, metadata conflicts,
semantic versions, feature base types, and the public context constructor. Optional capability and internal-service
references deliberately do not need a provider: they model integrations that can be absent. Generated catalogs use
ordinary typed constructor references, so deployed Paper and Velocity plugins perform no reflective package or JAR
scanning.

Configure `nl.hauntedmc.featureframework:featureframework-processor` as an explicit Maven annotation processor.
JDK 25 does not implicitly execute processors. Keep manual `FeatureDefinition` construction only for inventories
that cannot be known at compilation time.

## Startup phases

Use `startupPhase` only to order features that are otherwise independent. The available phases are
`FOUNDATION`, `SECURITY`, `CORE` (the default), `PRESENTATION`, `OPERATIONS`, and `DEFERRED`.
Choose the phase that describes the feature's responsibility, for example
`startupPhase = FeatureStartupPhase.SECURITY`; do not use magic numbers. A required feature or
capability relationship is stronger and always determines the lifecycle order.
For a manual `FeatureDefinition`, use the equivalent fluent
`.startupPhase(FeatureStartupPhase.SECURITY)` method.
