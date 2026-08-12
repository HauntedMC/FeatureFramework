package nl.hauntedmc.featureframework.toolkit.io.localization;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered message defaults used by feature localization files. */
public class MessageMap {
    private final Map<String, String> messages = new LinkedHashMap<>();
    public void add(String key, String defaultValue) { messages.put(key, defaultValue); }
    public Map<String, String> getMessages() { return Map.copyOf(messages); }
}
