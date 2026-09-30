package io.github.lomshakov.voicekit;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * The default {@link Transport}, built on the JDK's {@code java.net.http}
 * client — no third-party dependencies.
 *
 * <pre>{@code
 * Transport transport = new HttpTransport(Duration.ofSeconds(30));
 * }</pre>
 */
public final class HttpTransport implements Transport {

    /** The default per-request timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final HttpClient client;
    private final Duration timeout;

    /** Creates a transport with the default timeout. */
    public HttpTransport() {
        this(null, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a transport.
     *
     * @param timeout the per-request timeout; {@code null} keeps the default
     */
    public HttpTransport(Duration timeout) {
        this(null, timeout);
    }

    /**
     * Creates a transport around a caller-managed client (use it to configure a
     * proxy, a custom SSL context or an executor).
     *
     * @param client the HTTP client, or {@code null} to build a default one
     * @param timeout the per-request timeout; {@code null} keeps the default
     */
    public HttpTransport(HttpClient client, Duration timeout) {
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.client = client != null ? client : HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(this.timeout)
            .build();
    }

    /** The HTTP client used by this transport. */
    public HttpClient httpClient() {
        return client;
    }

    /** The per-request timeout. */
    public Duration timeout() {
        return timeout;
    }

    @Override
    public Response send(Request request) {
        HttpRequest.BodyPublisher publisher = request.hasBody()
            ? HttpRequest.BodyPublishers.ofByteArray(request.body())
            : HttpRequest.BodyPublishers.noBody();

        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
            .timeout(timeout)
            .method(request.method(), publisher);

        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            try {
                builder.header(header.getKey(), header.getValue());
            } catch (IllegalArgumentException exception) {
                // A restricted header (Host, Connection, …) — let the JDK pick it.
            }
        }

        try {
            HttpResponse<InputStream> response =
                client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());

            return new Response(response.statusCode(), response.headers().map(), response.body());
        } catch (IOException exception) {
            throw new VoiceKitException(
                "VoiceKit: " + request.method() + " " + request.uri() + " failed: " + exception.getMessage(),
                0, "", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VoiceKitException(
                "VoiceKit: " + request.method() + " " + request.uri() + " was interrupted",
                0, "", exception);
        }
    }
}
