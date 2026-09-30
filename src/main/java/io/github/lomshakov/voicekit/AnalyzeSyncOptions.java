package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/analyze/sync} — files up to 3 minutes.
 *
 * <p>Emotions, keywords and entities are extracted by default; pass
 * {@code false} to switch an individual extractor off.
 *
 * <pre>{@code
 * Result analysis = client.analyzeSync(FilePart.ofPath("note.wav"),
 *     new AnalyzeSyncOptions().keywords(false).emotions(true));
 * }</pre>
 */
public final class AnalyzeSyncOptions {

    private String language;
    private boolean diarization;
    private Boolean emotions;
    private Boolean keywords;
    private Boolean entities;
    private List<String> keyterms;
    private boolean clean;

    /** An ISO-639-1 hint; empty means auto-detect. */
    public AnalyzeSyncOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Splits the analysis per speaker (Basic and above). */
    public AnalyzeSyncOptions diarization(boolean value) {
        this.diarization = value;

        return this;
    }

    /** Enables or disables emotion extraction. */
    public AnalyzeSyncOptions emotions(Boolean value) {
        this.emotions = value;

        return this;
    }

    /** Enables or disables keyword extraction. */
    public AnalyzeSyncOptions keywords(Boolean value) {
        this.keywords = value;

        return this;
    }

    /** Enables or disables named-entity extraction. */
    public AnalyzeSyncOptions entities(Boolean value) {
        this.entities = value;

        return this;
    }

    /** Domain vocabulary that biases recognition. */
    public AnalyzeSyncOptions keyterms(List<String> value) {
        this.keyterms = value;

        return this;
    }

    /** Runs denoise + normalise before recognition. */
    public AnalyzeSyncOptions clean(boolean value) {
        this.clean = value;

        return this;
    }

    Map<String, String> fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        Payload.putField(fields, "language", language);

        if (diarization) {
            fields.put("diarization", "true");
        }
        if (clean) {
            fields.put("clean", "true");
        }
        if (emotions != null) {
            fields.put("emotions", Payload.bool(emotions));
        }
        if (keywords != null) {
            fields.put("keywords", Payload.bool(keywords));
        }
        if (entities != null) {
            fields.put("entities", Payload.bool(entities));
        }

        Payload.putField(fields, "keyterms", Payload.csv(keyterms));

        return fields;
    }
}
