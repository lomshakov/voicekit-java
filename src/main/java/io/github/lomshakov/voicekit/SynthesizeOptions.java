package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/synthesize} and {@code POST /v1/synthesize/stream}.
 *
 * <p>Every setter is optional and fluent:
 *
 * <pre>{@code
 * SynthesizeOptions options = new SynthesizeOptions()
 *     .voice("preset_anna")
 *     .format("mp3")
 *     .speed(1.1)
 *     .normalize(true);
 * }</pre>
 */
public final class SynthesizeOptions {

    private String voice;
    private String format;
    private int sampleRate;
    private double speed;
    private double pitch;
    private String emotion;
    private Boolean ssml;
    private Boolean putAccent;
    private Boolean putYo;
    private Boolean normalize;
    private String model;
    private String language;
    private String effects;

    /** A preset id (e.g. {@code "preset_anna"}) or a cloned voice id. */
    public SynthesizeOptions voice(String value) {
        this.voice = value;

        return this;
    }

    /** The output format: {@code mp3} (default), {@code wav} or {@code ogg}. */
    public SynthesizeOptions format(String value) {
        this.format = value;

        return this;
    }

    /** The output sample rate in Hz. */
    public SynthesizeOptions sampleRate(int value) {
        this.sampleRate = value;

        return this;
    }

    /** The speed multiplier, {@code 1.0} = normal. */
    public SynthesizeOptions speed(double value) {
        this.speed = value;

        return this;
    }

    /** The pitch shift in semitones. */
    public SynthesizeOptions pitch(double value) {
        this.pitch = value;

        return this;
    }

    /** A preset emotion, on voices that support it. */
    public SynthesizeOptions emotion(String value) {
        this.emotion = value;

        return this;
    }

    /** Switches the input to SSML markup. */
    public SynthesizeOptions ssml(Boolean value) {
        this.ssml = value;

        return this;
    }

    /**
     * Accepted for compatibility; it does not affect synthesis (the engine
     * reads stress on its own).
     */
    public SynthesizeOptions putAccent(Boolean value) {
        this.putAccent = value;

        return this;
    }

    /**
     * Accepted for compatibility; it does not affect synthesis (the engine
     * reads "ё" on its own).
     */
    public SynthesizeOptions putYo(Boolean value) {
        this.putYo = value;

        return this;
    }

    /** Applies loudness normalisation to the result. */
    public SynthesizeOptions normalize(Boolean value) {
        this.normalize = value;

        return this;
    }

    /** {@code standard} (default) or {@code premium} (clone, Pro/Business). */
    public SynthesizeOptions model(String value) {
        this.model = value;

        return this;
    }

    /** Overrides the synthesis language for cloned voices. */
    public SynthesizeOptions language(String value) {
        this.language = value;

        return this;
    }

    /**
     * An already-encoded effect chain; see {@link Effects#chain}.
     *
     * @param value the JSON array produced by {@link Effects}
     */
    public SynthesizeOptions effects(String value) {
        this.effects = value;

        return this;
    }

    /**
     * An effect chain, encoded for you.
     *
     * @param chain the effect descriptors
     */
    public SynthesizeOptions effects(List<Map<String, Object>> chain) {
        this.effects = Effects.encode(chain);

        return this;
    }

    Map<String, Object> body(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);

        Payload.putString(body, "voice", voice);
        Payload.putString(body, "format", format);
        Payload.putInt(body, "sample_rate", sampleRate);
        Payload.putDouble(body, "speed", speed);
        Payload.putDouble(body, "pitch", pitch);
        Payload.putString(body, "emotion", emotion);
        Payload.putBoolean(body, "ssml", ssml);
        Payload.putBoolean(body, "put_accent", putAccent);
        Payload.putBoolean(body, "put_yo", putYo);
        Payload.putBoolean(body, "normalize", normalize);
        Payload.putString(body, "model", model);
        Payload.putString(body, "language", language);
        Payload.putString(body, "effects", effects);

        return body;
    }
}
