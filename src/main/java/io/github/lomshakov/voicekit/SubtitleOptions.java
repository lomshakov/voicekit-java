package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls {@code GET /v1/transcribe/{id}/subtitles}.
 *
 * <pre>{@code
 * String vtt = client.subtitles(jobId, new SubtitleOptions()
 *     .format("vtt")
 *     .targetLanguage("en")
 *     .hotMarks(true));
 * }</pre>
 */
public final class SubtitleOptions {

    private String format;
    private String targetLanguage;
    private boolean hotMarks;

    /** {@code vtt} (default) or {@code srt}. */
    public SubtitleOptions format(String value) {
        this.format = value;

        return this;
    }

    /** Translates the captions, e.g. {@code "en"}. */
    public SubtitleOptions targetLanguage(String value) {
        this.targetLanguage = value;

        return this;
    }

    /** Marks fast or unclear speech. */
    public SubtitleOptions hotMarks(boolean value) {
        this.hotMarks = value;

        return this;
    }

    Map<String, Object> query() {
        Map<String, Object> query = new LinkedHashMap<>();
        Payload.putString(query, "format", format);
        Payload.putString(query, "target_language", targetLanguage);

        if (hotMarks) {
            query.put("hot_marks", "true");
        }

        return query;
    }
}
