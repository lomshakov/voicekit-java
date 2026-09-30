package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EffectsTest {

    @Test
    void builds_a_descriptor_from_pairs() {
        Map<String, Object> effect = Effects.effect("reverb", "room_size", 0.5, "wet", 0.3);

        assertEquals("reverb", effect.get("type"));
        assertEquals(0.5, effect.get("room_size"));
        assertEquals(0.3, effect.get("wet"));
    }

    @Test
    void rejects_malformed_descriptors() {
        assertThrows(IllegalArgumentException.class, () -> Effects.effect("reverb", "room_size"));
        assertThrows(IllegalArgumentException.class, () -> Effects.effect("", "a", 1));
        assertThrows(IllegalArgumentException.class, () -> Effects.effect(null));
        assertThrows(IllegalArgumentException.class, () -> Effects.effect("reverb", 1, 2));
    }

    @Test
    void encodes_a_chain() {
        String chain = Effects.chain(
            Effects.effect("reverb", "room_size", 0.5),
            Effects.effect("pitch", "semitones", 2));

        assertEquals("[{\"type\":\"reverb\",\"room_size\":0.5},{\"type\":\"pitch\",\"semitones\":2}]", chain);
    }

    @Test
    void encodes_a_single_descriptor() {
        String chain = Effects.encode(List.of(Effects.effect("compressor", "ratio", 3)));

        assertEquals("[{\"type\":\"compressor\",\"ratio\":3}]", chain);
    }

    @Test
    void encodes_an_empty_chain() {
        assertEquals("[]", Effects.encode(null));
        assertEquals("[]", Effects.encode(List.of()));
    }

    @Test
    void normalises_the_shapes_a_caller_may_pass() {
        String encoded = "[{\"type\":\"reverb\"}]";

        assertEquals(encoded, Effects.normalize(encoded));
        assertEquals(encoded, Effects.normalize(List.of(Effects.effect("reverb"))));
        assertEquals("{\"type\":\"reverb\"}", Effects.normalize(Effects.effect("reverb")));
        assertNull(Effects.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> Effects.normalize(12));
        assertThrows(IllegalArgumentException.class, () -> Effects.normalize(List.of("nope")));
    }

    @Test
    void keeps_the_descriptor_order() {
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("type", "eq");
        effect.put("bands", List.of(100, 200));

        assertEquals("{\"type\":\"eq\",\"bands\":[100,200]}", Effects.normalize(effect));
    }

    @Test
    void rejects_a_descriptor_without_a_type() {
        assertTrue(Effects.effect("reverb").containsKey("type"));
        assertThrows(IllegalArgumentException.class, () -> Effects.effect(" "));
    }
}
