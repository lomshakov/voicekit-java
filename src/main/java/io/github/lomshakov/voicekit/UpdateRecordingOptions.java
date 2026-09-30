package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Patches a recording's tags and folder
 * ({@code PATCH /v1/recordings/{id}}). Fields that were never set are left
 * untouched server-side.
 *
 * <pre>{@code
 * client.updateRecording(recordingId, new UpdateRecordingOptions()
 *     .tags(List.of("sales", "warm"))
 *     .folder("Q3"));
 *
 * client.updateRecording(recordingId, new UpdateRecordingOptions().clearTags());
 * client.updateRecording(recordingId, new UpdateRecordingOptions().folder("")); // move to the root
 * }</pre>
 */
public final class UpdateRecordingOptions {

    private List<String> tags;
    private boolean clearTags;
    private String folder;
    private boolean folderSet;

    /** Replaces the tag set. */
    public UpdateRecordingOptions tags(List<String> value) {
        this.tags = value;

        return this;
    }

    /** Removes every tag (wins over {@link #tags(List)}). */
    public UpdateRecordingOptions clearTags() {
        return clearTags(true);
    }

    /** Removes every tag (wins over {@link #tags(List)}). */
    public UpdateRecordingOptions clearTags(boolean value) {
        this.clearTags = value;

        return this;
    }

    /** Moves the recording; {@code ""} clears the folder. */
    public UpdateRecordingOptions folder(String value) {
        this.folder = value;
        this.folderSet = true;

        return this;
    }

    Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();

        if (clearTags) {
            body.put("tags", null);
        } else if (tags != null) {
            body.put("tags", tags);
        }

        if (folderSet) {
            body.put("folder", folder == null ? "" : folder);
        }

        return body;
    }
}
