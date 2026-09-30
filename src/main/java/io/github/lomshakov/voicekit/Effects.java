package io.github.lomshakov.voicekit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for the effect chains accepted by {@code /v1/synthesize},
 * {@code /v1/audio/effects} and {@code /v1/video/effects}.
 *
 * <p>An effect is a descriptor such as {@code {"type": "reverb", "room_size": 0.5}};
 * the API applies the chain in order.
 *
 * <pre>{@code
 * String chain = Effects.chain(
 *     Effects.effect("reverb", "room_size", 0.5),
 *     Effects.effect("pitch", "semitones", 2));
 *
 * byte[] audio = client.synthesize("Привет!", new SynthesizeOptions()
 *     .voice("preset_anna")
 *     .effects(chain));
 * }</pre>
 */
public final class Effects {

    private Effects() {
    }

    /**
     * Builds a single effect descriptor.
     *
     * @param type the effect name, e.g. {@code "reverb"}
     * @param keyValues alternating property names and values, e.g.
     *     {@code "room_size", 0.5}
     * @throws IllegalArgumentException when the pairs are malformed
     */
    public static Map<String, Object> effect(String type, Object... keyValues) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("VoiceKit: the effect type is required.");
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException(
                "VoiceKit: the effect properties must come in name/value pairs.");
        }

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("type", type);

        for (int index = 0; index < keyValues.length; index += 2) {
            Object key = keyValues[index];
            if (!(key instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException(
                    "VoiceKit: the effect property name at index " + index + " must be a non-blank string.");
            }

            descriptor.put(name, keyValues[index + 1]);
        }

        return descriptor;
    }

    /**
     * Encodes an effect chain into the JSON string the API expects.
     *
     * @param chain the effect descriptors
     * @throws IllegalArgumentException when the chain cannot be encoded
     */
    @SafeVarargs
    public static String chain(Map<String, Object>... chain) {
        return encode(chain == null ? null : List.of(chain));
    }

    /**
     * Encodes an effect chain into the JSON string the API expects.
     *
     * @param chain the effect descriptors
     * @throws IllegalArgumentException when the chain cannot be encoded
     */
    public static String encode(List<? extends Map<String, Object>> chain) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (chain != null) {
            items.addAll(chain);
        }

        return Json.write(items);
    }

    /**
     * Accepts either an already-encoded chain or a list of descriptors.
     *
     * @param effects a JSON string, a single descriptor or a list of descriptors
     * @throws IllegalArgumentException when the value is not a supported shape
     */
    public static String normalize(Object effects) {
        if (effects == null) {
            return null;
        }

        if (effects instanceof String encoded) {
            return encoded;
        }

        if (effects instanceof List<?> list) {
            List<Map<String, Object>> items = new ArrayList<>(list.size());
            for (Object item : list) {
                items.add(descriptor(item));
            }

            return encode(items);
        }

        return Json.write(descriptor(effects));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> descriptor(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        throw new IllegalArgumentException(
            "VoiceKit: expected an effect descriptor (a map), got "
                + (value == null ? "null" : value.getClass().getName()));
    }
}
