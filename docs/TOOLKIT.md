# Toolkit

The toolkit is the platform-neutral utility layer. It may depend on ordinary Java libraries and Adventure where the
shared module already exposes Adventure-based text utilities, but it must not depend on Paper, Bukkit, Velocity, host
bootstrap internals, platform lifecycle adapters, or optional plugin integrations.

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

`TextFormatter` is the string-oriented frontend and `ComponentFormatter` is the Adventure-component frontend. Public
entry points stay separate because their output contracts differ. Shared parsing/normalization mechanics should live
in internal formatting stages rather than being copied between the two facades.

Behavioral parity must be protected by golden tests before moving formatting stages. The objective is one source of
truth for normalization, not one giant public formatter API.

## Internal decomposition

Large implementation classes may be split under `internal.toolkit` without expanding the consumer API. Prefer small
responsibilities such as representation, conversion/validation, serialization, persistence/atomic replacement, and
cache metadata/TTL handling.
