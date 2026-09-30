package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls {@code POST /v1/analyze/summarize}.
 *
 * <pre>{@code
 * Result summary = client.summarize(longText, new SummarizeOptions()
 *     .language("ru")
 *     .maxSentences(3));
 * }</pre>
 */
public final class SummarizeOptions {

    private String language;
    private int maxSentences;

    /** The language of the text. */
    public SummarizeOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Caps the summary length; {@code 0} keeps the server default. */
    public SummarizeOptions maxSentences(int value) {
        this.maxSentences = value;

        return this;
    }

    Map<String, Object> body(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);

        Payload.putString(body, "language", language);
        Payload.putInt(body, "max_sentences", maxSentences);

        return body;
    }
}
