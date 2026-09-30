package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/transcribe/sync} — files up to 3 minutes.
 *
 * <pre>{@code
 * Result transcript = client.transcribeSync(FilePart.ofPath("note.wav"),
 *     new TranscribeSyncOptions().language("ru").clean(true));
 * }</pre>
 */
public final class TranscribeSyncOptions {

    private String language;
    private boolean diarization;
    private List<String> keyterms;
    private boolean clean;

    /** An ISO-639-1 hint; empty means auto-detect. */
    public TranscribeSyncOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Labels speakers (Basic and above). */
    public TranscribeSyncOptions diarization(boolean value) {
        this.diarization = value;

        return this;
    }

    /** Domain vocabulary that biases recognition. */
    public TranscribeSyncOptions keyterms(List<String> value) {
        this.keyterms = value;

        return this;
    }

    /** Runs denoise + normalise before recognition. */
    public TranscribeSyncOptions clean(boolean value) {
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

        Payload.putField(fields, "keyterms", Payload.csv(keyterms));

        return fields;
    }
}
