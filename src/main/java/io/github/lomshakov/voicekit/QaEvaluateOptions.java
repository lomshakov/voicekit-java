package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controls {@code POST /v1/qa/evaluate} (Pro/Business).
 *
 * <pre>{@code
 * Map<String, Object> greeting = new LinkedHashMap<>();
 * greeting.put("id", "greeting");
 * greeting.put("kind", "required");
 * greeting.put("description", "Поздоровался");
 * greeting.put("weight", 1.0);
 *
 * Result report = client.qaEvaluate(recordingId,
 *     new QaEvaluateOptions().checklist(List.of(greeting)));
 * }</pre>
 */
public final class QaEvaluateOptions {

    private List<Map<String, Object>> checklist;
    private String webhookUrl;

    /** The QA checklist; each item is a {@code {"id", "kind", …}} descriptor. */
    public QaEvaluateOptions checklist(List<Map<String, Object>> value) {
        this.checklist = value;

        return this;
    }

    /** Receives a {@code qa.violation} event when the call is flagged. */
    public QaEvaluateOptions webhookUrl(String value) {
        this.webhookUrl = value;

        return this;
    }

    Map<String, Object> body(String recordingId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recording_id", recordingId);

        Payload.put(body, "checklist", checklist);
        Payload.putString(body, "webhook_url", webhookUrl);

        return body;
    }
}
