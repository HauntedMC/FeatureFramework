package nl.hauntedmc.featureframework.toolkit.text.placeholder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;

/** Immutable typed placeholder bag used by framework and feature messages. */
public final class MessagePlaceholders {
    private static final MessagePlaceholders EMPTY = new MessagePlaceholders(Map.of());

    private final Map<String, String> values;

    private MessagePlaceholders(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder() { return new Builder(); }
    public static MessagePlaceholders empty() { return EMPTY; }
    public static MessagePlaceholders of(String key, String value) { return new MessagePlaceholders(Map.of(key, value)); }
    public static MessagePlaceholders of(Map<String, String> values) { return new MessagePlaceholders(new HashMap<>(values)); }
    public String get(String key) { return values.get(key); }
    public boolean contains(String key) { return values.containsKey(key); }
    public int size() { return values.size(); }
    public boolean isEmpty() { return values.isEmpty(); }
    public Map<String, String> asMap() { return values; }
    public String apply(String message) { return applyPlaceholders(message, this); }

    public static String applyPlaceholders(String message, MessagePlaceholders placeholders) {
        if (message == null || placeholders == null || placeholders.values.isEmpty()) return message;
        var entries = new ArrayList<>(placeholders.values.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, ?> entry) -> entry.getKey().length()).reversed());
        String output = message;
        for (var entry : entries) output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        return output;
    }

    @Override
    public String toString() { return values.toString(); }

    public static final class Builder {
        private final Map<String, String> values = new LinkedHashMap<>();
        public Builder addString(String key, String value) { values.put(key, value == null ? "" : value); return this; }
        public Builder addNumber(String key, Number value) { values.put(key, value == null ? "0" : String.valueOf(value)); return this; }
        public Builder addComponent(String key, Component value) {
            values.put(key, value == null ? "" : MiniMessage.miniMessage().serialize(value));
            return this;
        }
        public Builder add(String key, Object value) {
            if (value instanceof Component component) return addComponent(key, component);
            if (value instanceof Number number) return addNumber(key, number);
            return addString(key, value == null ? "" : String.valueOf(value));
        }
        public Builder addAll(MessagePlaceholders existing) {
            if (existing != null) values.putAll(existing.values);
            return this;
        }
        public MessagePlaceholders build() {
            return values.isEmpty() ? empty() : new MessagePlaceholders(new LinkedHashMap<>(values));
        }
    }
}
