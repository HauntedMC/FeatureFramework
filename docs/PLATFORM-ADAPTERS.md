# Platform adapters

Paper and Velocity adapters translate native platform mechanisms into FeatureFramework's shared ownership and host
policies. They should stay thin, but they should not erase meaningful platform differences.

## What is shared

- feature graph and operation policy;
- feature scope creation and caching;
- capability/service ownership;
- lifecycle serialization;
- task ownership and teardown state;
- registration ownership state;
- configuration and localization policy.

## What stays native

- Paper primary-thread lifecycle affinity;
- Bukkit and Velocity scheduler APIs;
- Bukkit event registration and Velocity EventManager registration;
- Paper command takeover/CommandMap mechanics and Velocity CommandManager mechanics;
- platform logging capabilities;
- Paper UI, packets, registries, persistence and clocks;
- Velocity network utilities.

## Adapter rule

Prefer narrow hooks and factories over a single `PlatformAdapter` interface. A mega-adapter tends to accumulate every
platform capability and makes otherwise independent mechanisms depend on each other.

Optional integrations must remain classloading-isolated. Absence of PlaceholderAPI, ViaVersion, DataProvider, or
DataRegistry must not cause eager classloading failures in unrelated framework paths.
