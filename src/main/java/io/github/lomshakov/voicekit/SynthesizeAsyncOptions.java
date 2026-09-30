package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls {@code POST /v1/synthesize/async} — long-form audiobook jobs.
 *
 * <pre>{@code
 * Result job = client.synthesizeAsync(chapter, new SynthesizeAsyncOptions()
 *     .voice("preset_anna")
 *     .webhookUrl("https://example.com/hooks/voicekit"));
 * }</pre>
 */
public final class SynthesizeAsyncOptions {

    private String voice;
    private String format;
    private int sampleRate;
    private double speed;
    private String model;
    private String language;
    private String webhookUrl;

    /** A preset id or a cloned voice id. */
    public SynthesizeAsyncOptions voice(String value) {
        this.voice = value;

        return this;
    }

    /** The requested format; long-form jobs always produce WAV. */
    public SynthesizeAsyncOptions format(String value) {
        this.format = value;

        return this;
    }

    /** The output sample rate in Hz. */
    public SynthesizeAsyncOptions sampleRate(int value) {
        this.sampleRate = value;

        return this;
    }

    /** The speed multiplier, {@code 1.0} = normal. */
    public SynthesizeAsyncOptions speed(double value) {
        this.speed = value;

        return this;
    }

    /** {@code standard} (default) or {@code premium}. */
    public SynthesizeAsyncOptions model(String value) {
        this.model = value;

        return this;
    }

    /** Overrides the synthesis language for cloned voices. */
    public SynthesizeAsyncOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Receives a callback when the job completes. */
    public SynthesizeAsyncOptions webhookUrl(String value) {
        this.webhookUrl = value;

        return this;
    }

    Map<String, Object> body(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);

        Payload.putString(body, "voice", voice);
        Payload.putString(body, "format", format);
        Payload.putInt(body, "sample_rate", sampleRate);
        Payload.putDouble(body, "speed", speed);
        Payload.putString(body, "model", model);
        Payload.putString(body, "language", language);

        return body;
    }

    String webhookUrl() {
        return webhookUrl;
    }
}
