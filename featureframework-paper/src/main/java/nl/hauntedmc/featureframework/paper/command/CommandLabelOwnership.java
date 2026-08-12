package nl.hauntedmc.featureframework.paper.command;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Plugin-wide command-label ownership shared by every feature lifecycle. */
public class CommandLabelOwnership {

    private final Map<String, Object> ownersByLabel = new HashMap<>();

    public synchronized String claim(Object owner, Collection<String> labels) {
        for (String label : labels) {
            String normalized = normalize(label);
            Object existing = ownersByLabel.get(normalized);
            if (existing != null && existing != owner) {
                return label;
            }
        }
        for (String label : labels) {
            ownersByLabel.put(normalize(label), owner);
        }
        return null;
    }

    public synchronized void release(Object owner, Collection<String> labels) {
        for (String label : labels) {
            ownersByLabel.remove(normalize(label), owner);
        }
    }

    public synchronized boolean isClaimed(String label) {
        return label != null && ownersByLabel.containsKey(normalize(label));
    }

    private static String normalize(String label) {
        return label.toLowerCase(Locale.ROOT);
    }
}
