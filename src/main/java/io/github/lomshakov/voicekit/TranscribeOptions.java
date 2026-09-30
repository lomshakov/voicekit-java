package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/transcribe} — an asynchronous transcription job.
 *
 * <pre>{@code
 * Result job = client.transcribe(FilePart.ofPath("call.mp3"), new TranscribeOptions()
 *     .language("ru")
 *     .diarization(true)
 *     .keyterms(List.of("диагноз", "препарат")));
 * }</pre>
 */
public final class TranscribeOptions {

    private String language;
    private boolean diarization;
    private List<String> keyterms;
    private boolean clean;
    private boolean longForm;
    private String webhookUrl;

    /** An ISO-639-1 hint; empty means auto-detect. */
    public TranscribeOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Labels speakers (Basic and above). */
    public TranscribeOptions diarization(boolean value) {
        this.diarization = value;

        return this;
    }

    /** Domain vocabulary that biases recognition. */
    public TranscribeOptions keyterms(List<String> value) {
        this.keyterms = value;

        return this;
    }

    /** Runs denoise + normalise before recognition. */
    public TranscribeOptions clean(boolean value) {
        this.clean = value;

        return this;
    }

    /** Enables long-form transcription (up to 4 h / 512 MB). */
    public TranscribeOptions longForm(boolean value) {
        this.longForm = value;

        return this;
    }

    /** Receives the result when the job completes. */
    public TranscribeOptions webhookUrl(String value) {
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
        if (longForm) {
            fields.put("longForm", "true");
        }

        Payload.putField(fields, "webhookUrl", webhookUrl);
        Payload.putField(fields, "keyterms", Payload.csv(keyterms));

        return fields;
    }
}
