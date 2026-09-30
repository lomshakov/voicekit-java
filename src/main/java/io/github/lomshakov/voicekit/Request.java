package io.github.lomshakov.voicekit;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * An HTTP request handed to a {@link Transport}.
 *
 * <p>Requests are built by {@link VoiceKitClient} and are read-only; custom
 * transports receive them to perform the actual call.
 */
public final class Request {

    private final String method;
    private final URI uri;
    private final Map<String, String> headers;
    private final byte[] body;

    /**
     * Creates a request.
     *
     * @param method the HTTP method
     * @param uri the absolute URL
     * @param headers the request headers
     * @param body the payload, or {@code null} when there is none
     */
    public Request(String method, URI uri, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.uri = uri;
        this.headers = Collections.unmodifiableMap(new TreeMap<>(String.CASE_INSENSITIVE_ORDER) {
            private static final long serialVersionUID = 1L;

            {
                if (headers != null) {
                    putAll(headers);
                }
            }
        });
        this.body = body == null ? null : body.clone();
    }

    /** The HTTP method. */
    public String method() {
        return method;
    }

    /** The absolute URL. */
    public URI uri() {
        return uri;
    }

    /** The request headers, matched case-insensitively. */
    public Map<String, String> headers() {
        return headers;
    }

    /** The value of a header, or {@code ""} when absent. */
    public String header(String name) {
        String value = headers.get(name);

        return value == null ? "" : value;
    }

    /** Whether the request carries a payload. */
    public boolean hasBody() {
        return body != null;
    }

    /** A copy of the payload, or {@code null} when there is none. */
    public byte[] body() {
        return body == null ? null : body.clone();
    }

    /** The payload decoded as UTF-8 ({@code ""} when there is none). */
    public String bodyText() {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return method + " " + uri;
    }
}
