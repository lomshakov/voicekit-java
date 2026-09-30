package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls {@code POST /v1/audio/clean}.
 *
 * <pre>{@code
 * // One click: denoise + normalise
 * Result job = client.cleanAudio(FilePart.ofPath("noisy.wav"));
 *
 * // Or a custom preset
 * Map<String, Object> preset = Map.of(
 *     "denoise", Map.of("strength", 0.8, "stationary", true),
 *     "normalize", Map.of("target_db", -1.0),
 *     "high_pass", 80);
 * Result custom = client.cleanAudio(FilePart.ofPath("noisy.wav"),
 *     new CleanAudioOptions().options(preset).outputFormat("mp3"));
 * }</pre>
 */
public final class CleanAudioOptions {

    private Map<String, Object> options;
    private String outputFormat;
    private String webhookUrl;

    /**
     * The cleaning preset; {@code null} applies denoise + normalise.
     *
     * @param value keys such as {@code denoise}, {@code normalize},
     *     {@code high_pass}, {@code low_pass}
     */
    public CleanAudioOptions options(Map<String, Object> value) {
        this.options = value;

        return this;
    }

    /** {@code wav} (default), {@code mp3} or {@code ogg}. */
    public CleanAudioOptions outputFormat(String value) {
        this.outputFormat = value;

        return this;
    }

    /** Receives the result when the job completes. */
    public CleanAudioOptions webhookUrl(String value) {
        this.webhookUrl = value;

        return this;
    }

    Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();

        Payload.putField(fields, "output_format", outputFormat);
        Payload.putField(fields, "webhookUrl", webhookUrl);

        if (options != null) {
            fields.put("options", Json.write(options));
        }

        return fields;
    }
}
