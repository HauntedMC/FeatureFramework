package nl.hauntedmc.featureframework.toolkit.io.localization;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered message defaults used by feature localization files. */
public class MessageMap {
    private final Map<String, String> messages = new LinkedHashMap<>();

    public void add(String key, String defaultValue) { messages.put(key, defaultValue); }

    /** Fluent equivalent of {@link #add(String, String)} for programmatic default construction. */
    public MessageMap put(String key, String defaultValue) {
        add(key, defaultValue);
        return this;
    }

    /** Adds an ordered batch of message defaults. */
    public MessageMap putAll(Map<String, String> values) {
        messages.putAll(values);
        return this;
    }

    public boolean contains(String key) { return messages.containsKey(key); }
    public boolean isEmpty() { return messages.isEmpty(); }
    public int size() { return messages.size(); }
    public Map<String, String> getMessages() { return Map.copyOf(messages); }
}
