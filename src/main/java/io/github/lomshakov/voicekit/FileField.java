package io.github.lomshakov.voicekit;

/**
 * One file of a multipart/form-data request: the form field it is sent under
 * plus the payload.
 *
 * @param field the form field name, e.g. {@code "audio"} or {@code "samples"}
 * @param part the file
 */
record FileField(String field, FilePart part) {
}
