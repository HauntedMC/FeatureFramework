# Toolkit

The toolkit is the platform-neutral utility layer. It may depend on ordinary Java libraries and Adventure where the
shared module already exposes Adventure-based text utilities, but it must not depend on Paper, Bukkit, Velocity, host
bootstrap internals, lifecycle orchestration, or optional plugin integrations.

## Scope

`ToolkitContext` intentionally remains small: configuration/cache paths and HTTP support belong here. Do not turn it
into a global service locator.

Primary toolkit areas are:

- configuration and YAML views;
- cache and JSON persistence helpers;
- HTTP transport abstractions;
- localization file helpers;
- text/component formatting;
- placeholders, tokens, pagination, and collection helpers.

## Formatting

`TextFormatter` owns string normalization and conversion between legacy, MiniMessage, and plain text.
`ComponentFormatter` owns Adventure parsing, feature allowlisting/sanitization, URL linking, and component
serialization. `ComponentFormatter` delegates mixed-input normalization to `TextFormatter`; it should not acquire a
second legacy-normalization implementation.

Keep the public entry points separate because their output contracts differ. Extract an internal formatting stage only
when two implementations actually contain the same behavior. Behavioral parity must be protected by golden tests
before moving such a stage.

## Internal decomposition

Large implementation classes may be split under `internal.toolkit` without expanding the consumer API. Prefer small
responsibilities such as representation, conversion/validation, serialization, persistence/atomic replacement, and
cache metadata/TTL handling. Do not split a class merely to reduce line count if the new type would only forward calls.
