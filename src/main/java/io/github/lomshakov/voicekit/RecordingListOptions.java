package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Filters {@code GET /v1/recordings}.
 *
 * <pre>{@code
 * Result page = client.recordings(new RecordingListOptions()
 *     .source("link")
 *     .limit(10));
 * }</pre>
 */
public final class RecordingListOptions {

    private String source;
    private String tag;
    private String folder;
    private int limit;
    private int offset;

    /** One of {@code upload}, {@code link}, {@code bot}, {@code stream}. */
    public RecordingListOptions source(String value) {
        this.source = value;

        return this;
    }

    /** Keeps recordings carrying the tag. */
    public RecordingListOptions tag(String value) {
        this.tag = value;

        return this;
    }

    /** Keeps recordings in the folder. */
    public RecordingListOptions folder(String value) {
        this.folder = value;

        return this;
    }

    /** The page size. */
    public RecordingListOptions limit(int value) {
        this.limit = value;

        return this;
    }

    /** The page offset. */
    public RecordingListOptions offset(int value) {
        this.offset = value;

        return this;
    }

    Map<String, Object> query() {
        Map<String, Object> query = new LinkedHashMap<>();
        Payload.putString(query, "source", source);
        Payload.putString(query, "tag", tag);
        Payload.putString(query, "folder", folder);
        Payload.putInt(query, "limit", limit);
        Payload.putInt(query, "offset", offset);

        return query;
    }
}
