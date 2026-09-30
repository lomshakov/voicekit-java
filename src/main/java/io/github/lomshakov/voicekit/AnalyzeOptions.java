package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/analyze} — an asynchronous analysis job (emotions,
 * keywords, entities, optionally per speaker).
 *
 * <pre>{@code
 * Result job = client.analyze(FilePart.ofPath("call.mp3"), new AnalyzeOptions()
 *     .language("ru")
 *     .diarization(true));
 * }</pre>
 */
public final class AnalyzeOptions {

    private String language;
    private boolean diarization;
    private List<String> keyterms;
    private boolean clean;
    private String webhookUrl;

    /** An ISO-639-1 hint; empty means auto-detect. */
    public AnalyzeOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Splits the analysis per speaker (Basic and above). */
    public AnalyzeOptions diarization(boolean value) {
        this.diarization = value;

        return this;
    }

    /** Domain vocabulary that biases recognition. */
    public AnalyzeOptions keyterms(List<String> value) {
        this.keyterms = value;

        return this;
    }

    /** Runs denoise + normalise before recognition. */
    public AnalyzeOptions clean(boolean value) {
        this.clean = value;

        return this;
    }

    /** Receives the result when the job completes. */
    public AnalyzeOptions webhookUrl(String value) {
        this.webhookUrl = value;

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

        Payload.putField(fields, "webhookUrl", webhookUrl);
        Payload.putField(fields, "keyterms", Payload.csv(keyterms));

        return fields;
    }
}
