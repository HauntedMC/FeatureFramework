# Contributing

Use Java 25 and the checked-in Maven wrapper. Changes must preserve the module dependency direction,
add tests for behavior changes, and pass `./mvnw clean verify`.

Do not add a concrete feature, plugin bootstrap, feature catalog, domain capability, or persistence
entity to this repository. Prefer a small shared contract plus a platform adapter when behavior differs
between Paper and Velocity. Public API changes require migration notes and a version appropriate to
semantic versioning.
