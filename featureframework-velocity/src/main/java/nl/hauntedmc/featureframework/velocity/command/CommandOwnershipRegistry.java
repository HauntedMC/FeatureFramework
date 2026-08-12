package nl.hauntedmc.featureframework.velocity.command;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Plugin-wide ownership registry for feature command names and aliases. */
public final class CommandOwnershipRegistry {
    private record Owner(String featureName, String commandName) {
    }

    private final Map<String, Owner> owners = new ConcurrentHashMap<>();

    public Registration claim(String featureName, String commandName, Collection<String> aliases) {
        Owner owner = new Owner(requireText(featureName, "featureName"), requireText(commandName, "commandName"));
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        normalized.add(normalize(commandName));
        for (String alias : Objects.requireNonNull(aliases, "aliases")) {
            normalized.add(normalize(alias));
        }

        synchronized (owners) {
            for (String alias : normalized) {
                Owner current = owners.get(alias);
                if (current != null && !current.equals(owner)) {
                    throw new CommandRegistrationException(
                            "Command alias '" + alias + "' for feature '" + featureName
                                    + "' is already owned by feature '" + current.featureName
                                    + "' command '" + current.commandName + "'"
                    );
                }
            }
            normalized.forEach(alias -> owners.put(alias, owner));
        }
        return new Registration(owner, SetSnapshot.copyOf(normalized));
    }

    public int size() {
        return owners.size();
    }

    public final class Registration implements AutoCloseable {
        private final Owner owner;
        private final Collection<String> aliases;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Registration(Owner owner, Collection<String> aliases) {
            this.owner = owner;
            this.aliases = aliases;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            synchronized (owners) {
                aliases.forEach(alias -> owners.remove(alias, owner));
            }
        }
    }

    private static String normalize(String value) {
        return requireText(value, "command alias").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static final class SetSnapshot {
        private SetSnapshot() {
        }

        private static Collection<String> copyOf(Collection<String> values) {
            return java.util.Set.copyOf(values);
        }
    }
}
