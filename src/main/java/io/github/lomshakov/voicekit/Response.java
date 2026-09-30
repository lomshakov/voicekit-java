package io.github.lomshakov.voicekit;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * An HTTP response produced by a {@link Transport}.
 *
 * <p>The body is exposed as a stream so large downloads and streaming
 * synthesis never buffer the whole payload. {@link #bytes()} and
 * {@link #text()} read the stream to the end and close it.
 */
public final class Response implements Closeable {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final InputStream body;

    /**
     * Creates a response.
     *
     * @param statusCode the HTTP status
     * @param headers the response headers
     * @param body the payload stream
     */
    public Response(int statusCode, Map<String, List<String>> headers, InputStream body) {
        this.statusCode = statusCode;
        this.headers = Collections.unmodifiableMap(new TreeMap<>(String.CASE_INSENSITIVE_ORDER) {
            private static final long serialVersionUID = 1L;

            {
                if (headers != null) {
                    putAll(headers);
                }
            }
        });
        this.body = body == null ? InputStream.nullInputStream() : body;
    }

    /**
     * Creates a fully buffered response, handy for transports and test doubles.
     *
     * @param statusCode the HTTP status
     * @param headers the response headers
     * @param body the payload
     */
    public static Response of(int statusCode, Map<String, List<String>> headers, byte[] body) {
        return new Response(
            statusCode,
            headers,
            new ByteArrayInputStream(body == null ? new byte[0] : body));
    }

    /**
     * Creates a {@code text/plain} response, handy for test doubles.
     *
     * @param statusCode the HTTP status
     * @param body the payload
     */
    public static Response text(int statusCode, String body) {
        return of(
            statusCode,
            Map.of("Content-Type", List.of("application/json")),
            (body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
    }

    /** The HTTP status. */
    public int statusCode() {
        return statusCode;
    }

    /** Whether the status is in the 2xx range. */
    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode <= 299;
    }

    /** The response headers, matched case-insensitively. */
    public Map<String, List<String>> headers() {
        return headers;
    }

    /** The first value of a header, or {@code ""} when absent. */
    public String header(String name) {
        List<String> values = headers.get(name);

        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    /** The payload stream. */
    public InputStream body() {
        return body;
    }

    /**
     * Reads the payload to the end and closes the stream.
     *
     * @throws VoiceKitException when the read fails
     */
    public byte[] bytes() {
        try (InputStream stream = body) {
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new VoiceKitException(
                "VoiceKit: unable to read the response body: " + exception.getMessage(), 0, "", exception);
        }
    }

    /**
     * Reads the payload as UTF-8 and closes the stream.
     *
     * @throws VoiceKitException when the read fails
     */
    public String text() {
        return new String(bytes(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        try {
            body.close();
        } catch (IOException exception) {
            // Nothing useful to do while closing.
        }
    }

    @Override
    public String toString() {
        return "Response{" + statusCode + "}";
    }
}
