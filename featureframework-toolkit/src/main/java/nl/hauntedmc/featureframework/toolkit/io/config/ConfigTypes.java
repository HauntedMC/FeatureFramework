package nl.hauntedmc.featureframework.toolkit.io.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared conversion and normalization utilities for configuration values. */
public final class ConfigTypes {
    private ConfigTypes() { }

    public static Object toPlain(Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> map -> {
                LinkedHashMap<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    out.put(String.valueOf(entry.getKey()), toPlain(entry.getValue()));
                }
                yield out;
            }
            case List<?> list -> {
                ArrayList<Object> out = new ArrayList<>(list.size());
                for (Object element : list) out.add(toPlain(element));
                yield out;
            }
            default -> value;
        };
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> type) {
        if (value == null) return null;
        if (type.isInstance(value)) return (T) value;
        if (type == Map.class || type == List.class) {
            Object plain = toPlain(value);
            if (type.isInstance(plain)) return (T) plain;
            throw typeError(type.getSimpleName(), value);
        }
        if (type == String.class) return (T) String.valueOf(value);
        if (type == Boolean.class || type == boolean.class) {
            return switch (value) {
                case Boolean bool -> (T) bool;
                case Number number -> (T) Boolean.valueOf(number.intValue() != 0);
                case String string -> (T) parseBoolean(string);
                default -> throw typeError("boolean", value);
            };
        }
        if (type == Integer.class || type == int.class) {
            if (value instanceof Number number) return (T) Integer.valueOf(number.intValue());
            if (value instanceof String string) return (T) Integer.valueOf(Integer.parseInt(string.trim()));
            throw typeError("int", value);
        }
        if (type == Long.class || type == long.class) {
            if (value instanceof Number number) return (T) Long.valueOf(number.longValue());
            if (value instanceof String string) return (T) Long.valueOf(Long.parseLong(string.trim()));
            throw typeError("long", value);
        }
        if (type == Double.class || type == double.class) {
            if (value instanceof Number number) return (T) Double.valueOf(number.doubleValue());
            if (value instanceof String string) return (T) Double.valueOf(Double.parseDouble(string.trim()));
            throw typeError("double", value);
        }
        if (type == Float.class || type == float.class) {
            if (value instanceof Number number) return (T) Float.valueOf(number.floatValue());
            if (value instanceof String string) return (T) Float.valueOf(Float.parseFloat(string.trim()));
            throw typeError("float", value);
        }
        if (type.isEnum() && value instanceof String string) {
            for (Object constant : type.getEnumConstants()) {
                Enum<?> enumValue = (Enum<?>) constant;
                if (enumValue.name().equalsIgnoreCase(string.trim())) return (T) enumValue;
            }
            throw new IllegalArgumentException("Unknown enum constant '" + string + "' for " + type.getName());
        }
        throw new IllegalArgumentException("Unsupported conversion to " + type.getName()
                + " from " + value.getClass().getName());
    }

    public static <T> T convertOrDefault(Object value, Class<T> type, T defaultValue) {
        try {
            T converted = convert(value, type);
            return converted != null ? converted : defaultValue;
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    public static <T> List<T> convertList(Object raw, Class<T> elementType) {
        if (raw == null) return null;
        Object normalized = toPlain(raw);
        if (!(normalized instanceof List<?> source)) {
            T single = tryConvert(normalized, elementType);
            if (single != null) return List.of(single);
            throw typeError("List<" + elementType.getSimpleName() + ">", normalized);
        }
        List<T> output = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            Object element = source.get(index);
            try {
                output.add(convert(element, elementType));
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Invalid element at index " + index
                        + " in list; expected " + elementType.getSimpleName(), exception);
            }
        }
        return output;
    }

    public static <V> Map<String, V> convertMapValues(Object raw, Class<V> valueType) {
        if (raw == null) return null;
        Object normalized = toPlain(raw);
        if (!(normalized instanceof Map<?, ?> map)) {
            throw typeError("Map<String," + valueType.getSimpleName() + ">", normalized);
        }
        LinkedHashMap<String, V> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            output.put(String.valueOf(entry.getKey()), convert(entry.getValue(), valueType));
        }
        return output;
    }

    private static <T> T tryConvert(Object value, Class<T> type) {
        try { return convert(value, type); } catch (RuntimeException ignored) { return null; }
    }

    private static Boolean parseBoolean(String value) {
        String normalized = value.trim();
        if (normalized.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (normalized.equalsIgnoreCase("false")) return Boolean.FALSE;
        throw new IllegalArgumentException("Expected boolean but got '" + value + "'");
    }

    private static IllegalArgumentException typeError(String expected, Object got) {
        return new IllegalArgumentException("Expected " + expected + " but got "
                + (got == null ? "null" : got.getClass().getName()));
    }
}
