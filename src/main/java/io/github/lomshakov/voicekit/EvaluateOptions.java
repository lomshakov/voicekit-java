package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls {@code POST /v1/eval}.
 *
 * <pre>{@code
 * Result score = client.evaluate(FilePart.ofPath("clip.wav"),
 *     "Здравствуйте, это сервис синтеза речи.",
 *     new EvaluateOptions().normalize(true));
 * }</pre>
 */
public final class EvaluateOptions {

    private String language;
    private Boolean normalize;

    /** An ISO-639-1 hint; empty means auto-detect. */
    public EvaluateOptions language(String value) {
        this.language = value;

        return this;
    }

    /** Ignores case and punctuation; {@code null} keeps the server default. */
    public EvaluateOptions normalize(Boolean value) {
        this.normalize = value;

        return this;
    }

    Map<String, String> fields(String reference) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("reference", reference == null ? "" : reference);

        Payload.putField(fields, "language", language);

        if (normalize != null) {
            fields.put("normalize", Payload.bool(normalize));
        }

        return fields;
    }
}
