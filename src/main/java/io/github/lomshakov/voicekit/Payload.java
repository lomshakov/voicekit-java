package io.github.lomshakov.voicekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small helpers shared by the option classes: they keep request bodies free of
 * empty strings, zeroes and unset flags, so the API always sees exactly what
 * the caller asked for.
 */
final class Payload {

    private Payload() {
    }

    static void putString(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isEmpty()) {
            body.put(key, value);
        }
    }

    static void putInt(Map<String, Object> body, String key, int value) {
        if (value != 0) {
            body.put(key, value);
        }
    }

    static void putLong(Map<String, Object> body, String key, long value) {
        if (value != 0L) {
            body.put(key, value);
        }
    }

    static void putDouble(Map<String, Object> body, String key, double value) {
        if (value != 0.0d) {
            body.put(key, value);
        }
    }

    static void putBoolean(Map<String, Object> body, String key, Boolean value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    static void put(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }

    static void putField(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isEmpty()) {
            fields.put(key, value);
        }
    }

    /** Renders a list as the comma-separated form the API expects. */
    static String csv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }

        List<String> filtered = new ArrayList<>(values.size());
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                filtered.add(value.trim());
            }
        }

        return String.join(",", filtered);
    }

    /** Renders a boolean as the lowercase text form fields use. */
    static String bool(boolean value) {
        return value ? "true" : "false";
    }
}
