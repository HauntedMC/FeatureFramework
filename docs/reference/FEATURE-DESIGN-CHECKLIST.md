# Feature Design Checklist

Use this before adding a new feature or reviewing one.

## Boundary

- [ ] The feature represents one cohesive lifecycle/domain responsibility.
- [ ] Disabling it should not require unrelated features to stop unless explicitly declared.
- [ ] Normal helper classes remain normal Java classes rather than becoming tiny features.

## Declaration

- [ ] Name and version are stable and descriptive.
- [ ] `enabledByDefault` is intentional.
- [ ] Required feature dependencies are explicit.
- [ ] Optional feature dependencies are actually optional in code.
- [ ] External plugin dependencies are declared on the feature that uses them.
- [ ] Capability/internal-service relationships use interfaces/contracts appropriate to their scope.

## Lifecycle

- [ ] `initialize()` validates required state before exposing functionality.
- [ ] Framework-owned listeners, tasks, commands, caches, GUIs, and services are registered through the feature scope.
- [ ] Direct third-party/platform registrations have explicit cleanup if no owned adapter exists.
- [ ] `disable()` releases domain state not already owned by framework managers.
- [ ] Partial initialization can be cleaned up safely.

## Configuration and messages

- [ ] Defaults live with the feature.
- [ ] Invalid configuration fails clearly.
- [ ] Reload behavior is documented and implemented deliberately.
- [ ] User-facing text uses localization/message keys where appropriate.

## Cross-feature APIs

- [ ] Consumers depend on the contract they need, not a convenient global manager.
- [ ] Public capabilities are small and implementation-independent.
- [ ] Internal services are not exposed as public extension APIs unnecessarily.
- [ ] Optional lookups use `find...`; required lookups use `require...` and are declared in `@FeatureDeclaration`.

## Operations and tests

- [ ] Feature can be enabled, used, disabled, and enabled again without duplicate resources.
- [ ] Required-dependency failure behavior is tested.
- [ ] Optional integration absence is tested.
- [ ] Background work obeys the platform threading contract.
- [ ] Logs identify the feature and failure clearly.
