package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/audio/effects}.
 *
 * <pre>{@code
 * Result job = client.applyAudioEffects(FilePart.ofPath("voice.wav"),
 *     new AudioEffectsOptions()
 *         .effects(Effects.effect("reverb", "room_size", 0.5))
 *         .outputFormat("mp3"));
 * }</pre>
 */
public final class AudioEffectsOptions {

    private String effects = "[]";
    private String outputFormat;
    private String webhookUrl;

    /** An already-encoded chain; see {@link Effects#chain}. */
    public AudioEffectsOptions effects(String value) {
        this.effects = value == null || value.isBlank() ? "[]" : value;

        return this;
    }

    /** An effect chain, encoded for you. */
    public AudioEffectsOptions effects(List<Map<String, Object>> chain) {
        return effects(Effects.encode(chain));
    }

    /** A single effect descriptor, encoded for you. */
    public AudioEffectsOptions effects(Map<String, Object> effect) {
        return effects(Effects.encode(List.of(effect)));
    }

    /** {@code wav} (default), {@code mp3} or {@code ogg}. */
    public AudioEffectsOptions outputFormat(String value) {
        this.outputFormat = value;

        return this;
    }

    /** Receives the result when the job completes. */
    public AudioEffectsOptions webhookUrl(String value) {
        this.webhookUrl = value;

        return this;
    }

    String encodedEffects() {
        return effects;
    }

    Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("effects", effects);

        Payload.putField(fields, "output_format", outputFormat);
        Payload.putField(fields, "webhookUrl", webhookUrl);

        return fields;
    }
}
