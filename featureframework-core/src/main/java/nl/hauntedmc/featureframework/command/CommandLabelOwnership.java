package nl.hauntedmc.featureframework.command;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Thread-safe ownership of normalized command labels, independent of a command platform. */
public final class CommandLabelOwnership {
    private final Map<String, Object> ownersByLabel = new LinkedHashMap<>();

    public synchronized ClaimResult tryClaim(Object owner, Collection<String> labels) {
        Object requiredOwner = Objects.requireNonNull(owner, "owner");
        Map<String, String> normalized = normalizeLabels(labels);
        for (Map.Entry<String, String> label : normalized.entrySet()) {
            Object existing = ownersByLabel.get(label.getKey());
            if (existing != null && existing != requiredOwner) {
                return ClaimResult.blocked(label.getValue(), existing);
            }
        }
        normalized.keySet().forEach(label -> ownersByLabel.put(label, requiredOwner));
        return ClaimResult.claimed(new Claim(requiredOwner, normalized.keySet()));
    }

    public synchronized void release(Object owner, Collection<String> labels) {
        Object requiredOwner = Objects.requireNonNull(owner, "owner");
        normalizeLabels(labels).keySet().forEach(label -> ownersByLabel.remove(label, requiredOwner));
    }

    public synchronized boolean isClaimed(String label) {
        return label != null && ownersByLabel.containsKey(normalize(label));
    }

    public final class Claim implements AutoCloseable {
        private final Object owner;
        private final Collection<String> labels;
        private boolean closed;

        private Claim(Object owner, Collection<String> labels) {
            this.owner = owner;
            this.labels = labels;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            release(owner, labels);
        }
    }

    public record ClaimResult(Claim claim, String blockingLabel, Object blockingOwner) {
        private static ClaimResult claimed(Claim claim) { return new ClaimResult(claim, null, null); }
        private static ClaimResult blocked(String label, Object owner) { return new ClaimResult(null, label, owner); }
        public boolean claimed() { return claim != null; }
    }

    private static Map<String, String> normalizeLabels(Collection<String> labels) {
        Objects.requireNonNull(labels, "labels");
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String label : labels) {
            String requiredLabel = requireText(label, "command label");
            normalized.putIfAbsent(normalize(requiredLabel), requiredLabel);
        }
        if (normalized.isEmpty()) throw new IllegalArgumentException("labels must not be empty");
        return normalized;
    }

    private static String normalize(String label) {
        return requireText(label, "command label").toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
