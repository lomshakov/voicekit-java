package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filters {@code POST /v1/search} (Pro/Business).
 *
 * <pre>{@code
 * Result hits = client.search("почему клиент отказался?", new SearchOptions()
 *     .limit(5)
 *     .keywords("дорого")
 *     .source("link")
 *     .minDuration(30));
 * }</pre>
 */
public final class SearchOptions {

    private int limit;
    private String keywords;
    private String source;
    private String speaker;
    private String from;
    private String to;
    private double minDuration;
    private double maxDuration;

    /** The number of hits. */
    public SearchOptions limit(int value) {
        this.limit = value;

        return this;
    }

    /** Enables full-text ranking on top of semantic similarity. */
    public SearchOptions keywords(String value) {
        this.keywords = value;

        return this;
    }

    /** One of {@code upload}, {@code link}, {@code bot}, {@code stream}. */
    public SearchOptions source(String value) {
        this.source = value;

        return this;
    }

    /** A diarization label such as {@code SPEAKER_00}. */
    public SearchOptions speaker(String value) {
        this.speaker = value;

        return this;
    }

    /** An ISO-8601 lower bound (UTC). */
    public SearchOptions from(String value) {
        this.from = value;

        return this;
    }

    /** An ISO-8601 upper bound (UTC). */
    public SearchOptions to(String value) {
        this.to = value;

        return this;
    }

    /** The shortest recording length in seconds. */
    public SearchOptions minDuration(double value) {
        this.minDuration = value;

        return this;
    }

    /** The longest recording length in seconds. */
    public SearchOptions maxDuration(double value) {
        this.maxDuration = value;

        return this;
    }

    Map<String, Object> body(String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);

        Payload.putInt(body, "limit", limit);
        Payload.putString(body, "keywords", keywords);
        Payload.putString(body, "source", source);
        Payload.putString(body, "speaker", speaker);
        Payload.putString(body, "from", from);
        Payload.putString(body, "to", to);
        Payload.putDouble(body, "min_duration_seconds", minDuration);
        Payload.putDouble(body, "max_duration_seconds", maxDuration);

        return body;
    }
}
