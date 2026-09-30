package io.github.lomshakov.voicekit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A decoded JSON object returned by the API.
 *
 * <p>Response shapes evolve server-side, so the SDK hands back dynamic objects
 * with typed accessors instead of freezing every field into a class. Nested
 * objects become {@link Result} instances too, so chains stay readable:
 *
 * <pre>{@code
 * Result job = client.transcriptionJob(jobId);
 *
 * String text = job.getString("text");
 * double seconds = job.getDouble("duration_seconds");
 *
 * for (Result segment : job.getList("segments")) {
 *     System.out.println(segment.getDouble("start") + " " + segment.getString("text"));
 * }
 * }</pre>
 *
 * <p>Iteration walks the raw entries; {@link #toMap()} returns the untouched
 * payload (nested objects stay maps).
 */
public final class Result implements Iterable<Map.Entry<String, Object>> {

    private final Map<String, Object> values;

    /**
     * Wraps a map in a result.
     *
     * @param values the decoded properties
     */
    public Result(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    /** An empty result. */
    public static Result empty() {
        return new Result(new LinkedHashMap<>());
    }

    /**
     * Wraps a map in a result.
     *
     * @param values the decoded properties
     */
    public static Result of(Map<String, Object> values) {
        return new Result(values);
    }

    /**
     * Decodes a JSON object body. Blank bodies and {@code {}} / {@code []}
     * yield an empty result.
     *
     * @param json the raw response body
     * @throws VoiceKitException when the body is neither an object nor an empty array
     */
    public static Result fromJson(String json) {
        if (Json.isBlank(json)) {
            return empty();
        }

        Object decoded;
        try {
            decoded = Json.parse(json);
        } catch (IllegalArgumentException exception) {
            throw new VoiceKitException(
                "VoiceKit: the API returned malformed JSON: " + exception.getMessage(), 0, "", exception);
        }

        if (decoded instanceof Map<?, ?> map) {
            return new Result(cast(map));
        }

        if (decoded instanceof List<?> list && list.isEmpty()) {
            return empty();
        }

        throw new VoiceKitException(
            "VoiceKit: expected a JSON object, got: " + abbreviate(json));
    }

    /**
     * Converts a raw decoded value: maps become {@link Result}, lists become
     * lists of converted items, scalars pass through unchanged.
     *
     * @param value the raw value
     */
    public static Object wrap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new Result(cast(map));
        }

        if (value instanceof List<?> list) {
            List<Object> wrapped = new ArrayList<>(list.size());
            for (Object item : list) {
                wrapped.add(wrap(item));
            }

            return wrapped;
        }

        return value;
    }

    /** The raw value at the key, or {@code null} when absent. */
    public Object get(String key) {
        return values.get(key);
    }

    /** The string at the key, or {@code ""} when absent or not a string. */
    public String getString(String key) {
        return getString(key, "");
    }

    /** The string at the key, or the fallback when absent or not a string. */
    public String getString(String key, String fallback) {
        Object value = values.get(key);

        return value instanceof String text ? text : fallback;
    }

    /** The integer at the key, or {@code 0} when absent or not a number. */
    public int getInt(String key) {
        return (int) getLong(key);
    }

    /** The long at the key, or {@code 0} when absent or not a number. */
    public long getLong(String key) {
        Object value = values.get(key);

        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** The floating-point number at the key, or {@code 0} when absent or not a number. */
    public double getDouble(String key) {
        Object value = values.get(key);

        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    /** The boolean at the key, or {@code false} when absent or not a boolean. */
    public boolean getBoolean(String key) {
        Object value = values.get(key);

        return value instanceof Boolean flag && flag;
    }

    /** The nested object at the key, or {@code null} when absent. */
    public Result getObject(String key) {
        Object value = values.get(key);

        return value instanceof Map<?, ?> map ? new Result(cast(map)) : null;
    }

    /**
     * The nested array at the key as objects. Items that are not objects are
     * skipped; an absent key yields an empty list.
     */
    public List<Result> getList(String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<Result> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new Result(cast(map)));
            }
        }

        return result;
    }

    /** The nested array of strings at the key; non-string items are skipped. */
    public List<String> getStrings(String key) {
        Object value = values.get(key);
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof String text) {
                result.add(text);
            }
        }

        return result;
    }

    /** The nested array at the key, untranslated. */
    public List<Object> getRawList(String key) {
        Object value = values.get(key);

        return value instanceof List<?> list ? new ArrayList<>(list) : Collections.emptyList();
    }

    /** Whether the key is present (a {@code null} value still counts as present). */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** Whether the key is present and holds {@code null}. */
    public boolean isNull(String key) {
        return values.containsKey(key) && values.get(key) == null;
    }

    /** The property names, in payload order. */
    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    /** The number of properties. */
    public int size() {
        return values.size();
    }

    /** Whether the result has no properties. */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * The underlying payload, untouched (nested objects stay maps). The
     * returned map is a defensive copy.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> copy = new LinkedHashMap<>(values.size());
        copy.putAll(values);

        return copy;
    }

    @Override
    public Iterator<Map.Entry<String, Object>> iterator() {
        return toMap().entrySet().iterator();
    }

    /** The result rendered as JSON. */
    @Override
    public String toString() {
        return Json.write(values);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String abbreviate(String text) {
        String trimmed = text == null ? "" : text.trim();

        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "…";
    }
}
