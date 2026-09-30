package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/video/effects}.
 *
 * <pre>{@code
 * Result job = client.applyVideoEffects(FilePart.ofPath("clip.mp4"),
 *     new VideoEffectsOptions()
 *         .effects(Effects.effect("reverb", "room_size", 0.4))
 *         .mode("audio"));
 * }</pre>
 */
public final class VideoEffectsOptions {

    private String effects = "[]";
    private String mode;
    private FilePart audio;
    private String outputFormat;
    private String webhookUrl;

    /** An already-encoded chain; see {@link Effects#chain}. */
    public VideoEffectsOptions effects(String value) {
        this.effects = value == null || value.isBlank() ? "[]" : value;

        return this;
    }

    /** An effect chain, encoded for you. */
    public VideoEffectsOptions effects(List<Map<String, Object>> chain) {
        return effects(Effects.encode(chain));
    }

    /** A single effect descriptor, encoded for you. */
    public VideoEffectsOptions effects(Map<String, Object> effect) {
        return effects(Effects.encode(List.of(effect)));
    }

    /** {@code mux} (default, video output) or {@code audio}. */
    public VideoEffectsOptions mode(String value) {
        this.mode = value;

        return this;
    }

    /** Replaces the video's audio track (mux mode only). */
    public VideoEffectsOptions audio(FilePart value) {
        this.audio = value;

        return this;
    }

    /** Overrides the container of the produced artifact. */
    public VideoEffectsOptions outputFormat(String value) {
        this.outputFormat = value;

        return this;
    }

    /** Receives the result when the job completes. */
    public VideoEffectsOptions webhookUrl(String value) {
        this.webhookUrl = value;

        return this;
    }

    FilePart audioTrack() {
        return audio;
    }

    Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("effects", effects);

        Payload.putField(fields, "mode", mode);
        Payload.putField(fields, "output_format", outputFormat);
        Payload.putField(fields, "webhookUrl", webhookUrl);

        return fields;
    }
}
