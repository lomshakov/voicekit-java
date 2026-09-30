package io.github.lomshakov.voicekit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer used internally by the SDK.
 *
 * <p>The SDK has no runtime dependencies, so it brings its own encoder and
 * decoder. JSON values map onto plain Java types: objects become
 * {@link LinkedHashMap} (insertion order is preserved), arrays become
 * {@link ArrayList}, integral numbers become {@link Long}, fractional numbers
 * become {@link Double}, and JSON {@code null} is represented by a Java
 * {@code null}.
 *
 * <pre>{@code
 * Map<String, Object> body = Map.of("text", "Привет");
 * String json = Json.write(body);              // {"text":"Привет"}
 * Object value = Json.parse(json);             // a LinkedHashMap
 * }</pre>
 */
public final class Json {

    private Json() {
    }

    /**
     * Decodes a JSON document.
     *
     * @param text the JSON text
     * @return the decoded value ({@link Map}, {@link List}, {@link String},
     *     {@link Long}, {@link Double}, {@link Boolean} or {@code null})
     * @throws IllegalArgumentException when the text is not valid JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("VoiceKit: the JSON input is null");
        }

        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("trailing characters after the JSON value");
        }

        return value;
    }

    /**
     * Decodes a JSON object.
     *
     * @param text the JSON text
     * @return a mutable map with the decoded properties
     * @throws IllegalArgumentException when the text is not a JSON object
     */
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }

        throw new IllegalArgumentException("VoiceKit: expected a JSON object, got: " + abbreviate(text));
    }

    /**
     * Decodes a JSON array.
     *
     * @param text the JSON text
     * @return a mutable list with the decoded items
     * @throws IllegalArgumentException when the text is not a JSON array
     */
    public static List<Object> parseArray(String text) {
        Object value = parse(text);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }

        throw new IllegalArgumentException("VoiceKit: expected a JSON array, got: " + abbreviate(text));
    }

    /**
     * Encodes a value as JSON.
     *
     * @param value a {@link Map}, {@link Iterable}, array, {@link Result},
     *     {@link String}, {@link Number}, {@link Boolean} or {@code null}
     * @return the JSON text
     */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(value, out);

        return out.toString();
    }

    /** Whether the text is {@code null} or blank. */
    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static String abbreviate(String text) {
        String trimmed = text == null ? "" : text.trim();

        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "…";
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");

            return;
        }

        if (value instanceof CharSequence text) {
            writeString(text.toString(), out);

            return;
        }

        if (value instanceof Boolean flag) {
            out.append(flag ? "true" : "false");

            return;
        }

        if (value instanceof Double || value instanceof Float) {
            writeDouble(((Number) value).doubleValue(), out);

            return;
        }

        if (value instanceof Number number) {
            out.append(number.toString());

            return;
        }

        if (value instanceof Result result) {
            writeMap(result.toMap(), out);

            return;
        }

        if (value instanceof Map<?, ?> map) {
            writeMap(map, out);

            return;
        }

        if (value instanceof Iterable<?> items) {
            writeIterable(items, out);

            return;
        }

        if (value instanceof Object[] items) {
            writeIterable(Arrays.asList(items), out);

            return;
        }

        if (value instanceof Enum<?> constant) {
            writeString(constant.name(), out);

            return;
        }

        writeString(String.valueOf(value), out);
    }

    private static void writeDouble(double value, StringBuilder out) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            // Invalid JSON; the API treats a missing value and a null the same way.
            out.append("null");

            return;
        }

        out.append(Double.toString(value));
    }

    private static void writeMap(Map<?, ?> map, StringBuilder out) {
        out.append('{');

        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;

            writeString(String.valueOf(entry.getKey()), out);
            out.append(':');
            writeValue(entry.getValue(), out);
        }

        out.append('}');
    }

    private static void writeIterable(Iterable<?> items, StringBuilder out) {
        out.append('[');

        boolean first = true;
        for (Object item : items) {
            if (!first) {
                out.append(',');
            }
            first = false;

            writeValue(item, out);
        }

        out.append(']');
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');

        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        out.append('"');
    }

    /** A recursive-descent JSON reader. */
    private static final class Parser {

        private final String text;

        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(
                "VoiceKit: invalid JSON at offset " + index + ": " + message);
        }

        void skipWhitespace() {
            while (index < text.length()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("unexpected end of input");
            }

            return switch (text.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> {
                    readLiteral("true");
                    yield Boolean.TRUE;
                }
                case 'f' -> {
                    readLiteral("false");
                    yield Boolean.FALSE;
                }
                case 'n' -> {
                    readLiteral("null");
                    yield null;
                }
                default -> readNumber();
            };
        }

        private void readLiteral(String literal) {
            if (!text.startsWith(literal, index)) {
                throw error("expected " + literal);
            }

            index += literal.length();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;

            skipWhitespace();
            if (!atEnd() && text.charAt(index) == '}') {
                index++;

                return result;
            }

            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') {
                    throw error("expected a property name");
                }

                String key = readString();

                skipWhitespace();
                if (atEnd() || text.charAt(index) != ':') {
                    throw error("expected ':'");
                }
                index++;

                result.put(key, readValue());

                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated object");
                }

                char c = text.charAt(index++);
                if (c == '}') {
                    return result;
                }
                if (c != ',') {
                    throw error("expected ',' or '}'");
                }
            }
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            index++;

            skipWhitespace();
            if (!atEnd() && text.charAt(index) == ']') {
                index++;

                return result;
            }

            while (true) {
                result.add(readValue());

                skipWhitespace();
                if (atEnd()) {
                    throw error("unterminated array");
                }

                char c = text.charAt(index++);
                if (c == ']') {
                    return result;
                }
                if (c != ',') {
                    throw error("expected ',' or ']'");
                }
            }
        }

        private String readString() {
            index++;

            StringBuilder builder = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("unterminated string");
                }

                char c = text.charAt(index++);
                if (c == '"') {
                    return builder.toString();
                }

                if (c != '\\') {
                    builder.append(c);

                    continue;
                }

                if (atEnd()) {
                    throw error("unterminated escape sequence");
                }

                char escape = text.charAt(index++);
                switch (escape) {
                    case '"' -> builder.append('"');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> builder.append(readUnicodeEscape());
                    default -> throw error("invalid escape '\\" + escape + "'");
                }
            }
        }

        private char readUnicodeEscape() {
            if (index + 4 > text.length()) {
                throw error("truncated \\u escape");
            }

            String hex = text.substring(index, index + 4);
            index += 4;

            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                throw error("invalid \\u escape '" + hex + "'");
            }
        }

        private Object readNumber() {
            int start = index;

            if (!atEnd() && (text.charAt(index) == '-' || text.charAt(index) == '+')) {
                index++;
            }

            boolean fractional = false;
            while (!atEnd()) {
                char c = text.charAt(index);
                if (c >= '0' && c <= '9') {
                    index++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fractional = fractional || c == '.' || c == 'e' || c == 'E';
                    index++;
                } else {
                    break;
                }
            }

            String literal = text.substring(start, index);
            if (literal.isEmpty()) {
                throw error("expected a value");
            }

            if (!fractional) {
                try {
                    return Long.valueOf(literal);
                } catch (NumberFormatException exception) {
                    // Too large for a long — fall through to a double.
                }
            }

            try {
                return Double.valueOf(literal);
            } catch (NumberFormatException exception) {
                throw error("invalid number '" + literal + "'");
            }
        }
    }
}
