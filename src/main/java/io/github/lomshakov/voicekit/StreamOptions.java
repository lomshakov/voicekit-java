package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls a WebSocket transcription session
 * ({@code WS /v1/transcribe/stream}) or a VAD session.
 *
 * <pre>{@code
 * try (StreamSession stream = client.transcribeStream(new StreamOptions()
 *         .language("ru")
 *         .keyterms(List.of("диагноз")))) {
 *     stream.sendAudio(pcm16);
 *     stream.stop();
 * }
 * }</pre>
 */
public final class StreamOptions {

    private String language;
    private List<String> keyterms;
    private Boolean interim;

    /** An ISO-639-1 hint; empty means auto-detect. */
    public StreamOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Domain vocabulary that biases recognition. */
    public StreamOptions keyterms(List<String> value) {
        this.keyterms = value;

        return this;
    }

    /** Whether interim results are requested (the server default is true). */
    public StreamOptions interim(Boolean value) {
        this.interim = value;

        return this;
    }

    Map<String, Object> query() {
        Map<String, Object> query = new LinkedHashMap<>();
        Payload.putString(query, "language", language);

        String vocabulary = Payload.csv(keyterms);
        Payload.putString(query, "keyterms", vocabulary);

        if (interim != null) {
            query.put("interim", Payload.bool(interim));
        }

        return query;
    }
}
