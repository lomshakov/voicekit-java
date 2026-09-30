package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    @SuppressWarnings("unchecked")
    void parses_an_object_into_a_map() {
        Object value = Json.parse("{\"text\":\"Привет\",\"count\":3}");

        Map<String, Object> map = assertInstanceOf(Map.class, value);
        assertEquals("Привет", map.get("text"));
        assertEquals(3L, map.get("count"));
    }

    @Test
    void parses_an_array_into_a_list() {
        Object value = Json.parse("[1,2.5,true,null,\"x\"]");

        List<?> list = assertInstanceOf(List.class, value);
        assertEquals(1L, list.get(0));
        assertEquals(2.5d, list.get(1));
        assertEquals(Boolean.TRUE, list.get(2));
        assertNull(list.get(3));
        assertEquals("x", list.get(4));
    }

    @Test
    void parses_nested_structures() {
        Map<String, Object> object = Json.parseObject(
            "{\"segments\":[{\"start\":0.5,\"text\":\"раз\"}],\"speakers\":{\"count\":2}}");

        List<?> segments = (List<?>) object.get("segments");
        assertEquals(1, segments.size());
        assertEquals("раз", ((Map<?, ?>) segments.get(0)).get("text"));
        assertEquals(2L, ((Map<?, ?>) object.get("speakers")).get("count"));
    }

    @Test
    void parses_escape_sequences() {
        Map<String, Object> object = Json.parseObject(
            "{\"a\":\"line\\nbreak\",\"b\":\"quote\\\"here\",\"c\":\"slash\\\\\",\"d\":\"\\u0416\"}");

        assertEquals("line\nbreak", object.get("a"));
        assertEquals("quote\"here", object.get("b"));
        assertEquals("slash\\", object.get("c"));
        assertEquals("Ж", object.get("d"));
    }

    @Test
    void parses_integral_numbers_as_longs_and_fractional_as_doubles() {
        Map<String, Object> object = Json.parseObject("{\"a\":10,\"b\":10.5,\"c\":-3,\"d\":1e3}");

        assertEquals(10L, object.get("a"));
        assertEquals(10.5d, object.get("b"));
        assertEquals(-3L, object.get("c"));
        assertEquals(1000.0d, object.get("d"));
    }

    @Test
    void writes_unicode_untouched() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", "Привет, «ёлка»!");

        assertEquals("{\"text\":\"Привет, «ёлка»!\"}", Json.write(body));
    }

    @Test
    void writes_control_characters_as_escapes() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", "a\"b\\c\nd\te\u0001");

        assertEquals("{\"text\":\"a\\\"b\\\\c\\nd\\te\\u0001\"}", Json.write(body));
    }

    @Test
    void writes_null_entries() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tags", null);

        assertEquals("{\"tags\":null}", Json.write(body));
    }

    @Test
    void writes_invalid_doubles_as_null() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", Double.NaN);

        assertEquals("{\"value\":null}", Json.write(body));
    }

    @Test
    void round_trips_a_payload() {
        String source = "{\"items\":[{\"text\":\"раз\"}],\"n\":1,\"ok\":true,\"nil\":null}";

        assertEquals(source, Json.write(Json.parse(source)));
    }

    @Test
    void rejects_trailing_characters() {
        IllegalArgumentException error =
            assertThrows(IllegalArgumentException.class, () -> Json.parse("{}x"));

        assertTrue(error.getMessage().contains("trailing characters"));
    }

    @Test
    void rejects_unterminated_values() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":\"b"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\" 1}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse(""));
    }

    @Test
    void rejects_a_non_object_when_an_object_is_expected() {
        assertThrows(IllegalArgumentException.class, () -> Json.parseObject("[1]"));
        assertThrows(IllegalArgumentException.class, () -> Json.parseArray("{}"));
    }

    @Test
    void treats_blank_text_as_blank() {
        assertTrue(Json.isBlank(null));
        assertTrue(Json.isBlank("  "));
        assertTrue(!Json.isBlank("{}"));
    }
}
