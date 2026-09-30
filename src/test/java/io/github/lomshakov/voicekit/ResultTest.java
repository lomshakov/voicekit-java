package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultTest {

    private static Result sample() {
        return Result.fromJson("""
            {
              "status": "completed",
              "text": "Привет",
              "duration_seconds": 12.5,
              "segments": 4,
              "ok": true,
              "speaker": null,
              "nested": {"id": "rec_1"},
              "items": [{"text": "раз"}, "skip-me", {"text": "два"}],
              "tags": ["a", "b", 3]
            }
            """);
    }

    @Test
    void reads_scalars_with_typed_accessors() {
        Result result = sample();

        assertEquals("Привет", result.getString("text"));
        assertEquals(12.5d, result.getDouble("duration_seconds"));
        assertEquals(4, result.getInt("segments"));
        assertEquals(4L, result.getLong("segments"));
        assertTrue(result.getBoolean("ok"));
    }

    @Test
    void falls_back_for_missing_or_mistyped_keys() {
        Result result = sample();

        assertEquals("", result.getString("missing"));
        assertEquals("fallback", result.getString("missing", "fallback"));
        assertEquals("", result.getString("segments"));
        assertEquals(0, result.getInt("text"));
        assertEquals(0.0d, result.getDouble("missing"));
        assertFalse(result.getBoolean("missing"));
    }

    @Test
    void wraps_nested_objects() {
        Result nested = sample().getObject("nested");

        assertEquals("rec_1", nested.getString("id"));
        assertNull(sample().getObject("missing"));
        assertNull(sample().getObject("text"));
    }

    @Test
    void wraps_arrays_of_objects_and_skips_other_items() {
        List<Result> items = sample().getList("items");

        assertEquals(2, items.size());
        assertEquals("раз", items.get(0).getString("text"));
        assertEquals("два", items.get(1).getString("text"));
        assertTrue(sample().getList("missing").isEmpty());
    }

    @Test
    void reads_arrays_of_strings() {
        assertEquals(List.of("a", "b"), sample().getStrings("tags"));
        assertTrue(sample().getStrings("missing").isEmpty());
    }

    @Test
    void reports_key_presence() {
        Result result = sample();

        assertTrue(result.has("status"));
        assertTrue(result.has("speaker"));
        assertTrue(result.isNull("speaker"));
        assertFalse(result.has("missing"));
        assertFalse(result.isNull("missing"));
    }

    @Test
    void exposes_keys_size_and_a_defensive_copy() {
        Result result = sample();

        assertEquals(9, result.size());
        assertFalse(result.isEmpty());
        assertTrue(result.keys().contains("status"));

        Map<String, Object> copy = result.toMap();
        copy.clear();
        assertEquals(9, result.size());
    }

    @Test
    void iterates_over_entries() {
        int count = 0;
        for (Map.Entry<String, Object> entry : sample()) {
            assertTrue(entry.getKey() != null);

            count++;
        }

        assertEquals(9, count);
    }

    @Test
    void renders_itself_as_json() {
        assertTrue(sample().toString().startsWith("{\"status\":\"completed\""));
    }

    @Test
    void accepts_blank_and_empty_bodies() {
        assertTrue(Result.fromJson("").isEmpty());
        assertTrue(Result.fromJson("  ").isEmpty());
        assertTrue(Result.fromJson("{}").isEmpty());
        assertTrue(Result.fromJson("[]").isEmpty());
        assertTrue(Result.empty().isEmpty());
    }

    @Test
    void rejects_payloads_that_are_not_objects() {
        assertThrows(VoiceKitException.class, () -> Result.fromJson("[1,2]"));
        assertThrows(VoiceKitException.class, () -> Result.fromJson("12"));
        assertThrows(VoiceKitException.class, () -> Result.fromJson("\"text\""));
    }

    @Test
    void reports_malformed_json() {
        VoiceKitException error =
            assertThrows(VoiceKitException.class, () -> Result.fromJson("{\"a\":"));

        assertTrue(error.getMessage().contains("malformed JSON"));
    }

    @Test
    void wraps_raw_values() {
        assertTrue(Result.wrap(Map.of("a", 1)) instanceof Result);
        assertNull(Result.wrap(null));
        assertEquals("x", Result.wrap("x"));

        List<?> wrapped = (List<?>) Result.wrap(List.of(Map.of("a", 1)));

        assertEquals(1, wrapped.size());
        assertTrue(wrapped.get(0) instanceof Result);
    }
}
