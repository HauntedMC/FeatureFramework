# Contributing

Use Java 25 and the checked-in Maven wrapper. Changes must preserve the module dependency direction,
add tests for behavior changes, and pass `./mvnw clean verify`.

Do not add a concrete feature, plugin bootstrap, feature catalog, domain capability, or persistence
entity to this repository. Prefer a small shared contract plus a platform adapter when behavior differs
between Paper and Velocity. Public API changes require migration notes and a version appropriate to
semantic versioning. Any explicitly documented release-boundary exception, such as 1.6.0's removal of
HauntedMC-private APIs, must state the compatibility impact and migration path.

## Fork pull requests

Fork PRs run with a read-only GitHub token and receive no repository package secrets. CI attempts to resolve public HauntedMC Maven packages with that token and still runs static checks. If GitHub Packages denies cross-repository access, the required Maven check cannot pass on the fork; a maintainer reviews the change and opens an upstream branch PR for full CI before merge. Never include a package token in a PR or build log.
