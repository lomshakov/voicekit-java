package io.github.lomshakov.voicekit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A hand-rolled {@code multipart/form-data} body.
 *
 * <p>Files are written first (one part per file, so the same field name may
 * repeat), then the text fields in alphabetical order — which keeps requests
 * deterministic and easy to assert on in tests.
 */
final class Multipart {

    private final String boundary;
    private final byte[] body;

    private Multipart(String boundary, byte[] body) {
        this.boundary = boundary;
        this.body = body;
    }

    static Multipart of(List<FileField> files, Map<String, String> fields) {
        String boundary = "----VoiceKitBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<FileField> resolvedFiles = files == null ? new ArrayList<>() : files;

        for (FileField file : resolvedFiles) {
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"" + quote(file.field())
                + "\"; filename=\"" + quote(file.part().name()) + "\"\r\n");
            write(out, "Content-Type: " + file.part().contentType() + "\r\n\r\n");
            out.writeBytes(file.part().content());
            write(out, "\r\n");
        }

        Map<String, String> sorted = new TreeMap<>();
        if (fields != null) {
            sorted.putAll(fields);
        }

        for (Map.Entry<String, String> field : sorted.entrySet()) {
            write(out, "--" + boundary + "\r\n");
            write(out, "Content-Disposition: form-data; name=\"" + quote(field.getKey()) + "\"\r\n\r\n");
            write(out, field.getValue());
            write(out, "\r\n");
        }

        write(out, "--" + boundary + "--\r\n");

        return new Multipart(boundary, out.toByteArray());
    }

    String contentType() {
        return "multipart/form-data; boundary=" + boundary;
    }

    byte[] body() {
        return body;
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        StringBuilder builder = new StringBuilder(text.length());

        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (c == '"' || c == '\\') {
                builder.append('\\');
            }
            builder.append(c == '\r' || c == '\n' ? ' ' : c);
        }

        return builder.toString();
    }
}
