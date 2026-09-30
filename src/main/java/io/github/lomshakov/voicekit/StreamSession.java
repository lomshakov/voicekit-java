package io.github.lomshakov.voicekit;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A bidirectional VoiceKit WebSocket session.
 *
 * <p>Raw PCM16 frames (16 kHz, mono, little-endian) travel up; JSON events
 * ({@code session}, {@code vad}, {@code partial}, {@code final}, {@code error})
 * come down. A session is <em>not</em> thread-safe: send and receive from the
 * same thread, or guard them yourself.
 *
 * <pre>{@code
 * try (StreamSession session = client.transcribeStream(new StreamOptions().language("ru"))) {
 *     session.sendAudio(pcm16Chunk);
 *     session.stop();                          // finalise the utterance
 *
 *     Result event;
 *     while ((event = session.receive()) != null) {
 *         System.out.println(event.getString("type") + ": " + event.getString("text"));
 *     }
 * }
 * }</pre>
 */
public final class StreamSession implements AutoCloseable {

    /** A queue item that marks a server-side close. */
    private static final Object CLOSED = new Object();

    private final WebSocket socket;
    private final URI uri;
    private final Duration receiveTimeout;
    private final BlockingQueue<Object> events;

    private StreamSession(WebSocket socket, URI uri, Duration receiveTimeout, BlockingQueue<Object> events) {
        this.socket = socket;
        this.uri = uri;
        this.receiveTimeout = receiveTimeout;
        this.events = events;
    }

    /** Opens a session described by a URI and its headers. */
    static StreamSession open(URI uri, Map<String, String> headers, Duration timeout) {
        BlockingQueue<Object> events = new LinkedBlockingQueue<>();
        WebSocket.Listener listener = new Listener(events);

        WebSocket.Builder builder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(timeout)
            .build()
            .newWebSocketBuilder()
            .connectTimeout(timeout);

        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        try {
            WebSocket socket = builder.buildAsync(uri, listener).join();

            return new StreamSession(socket, uri, timeout, events);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();

            if (cause instanceof WebSocketHandshakeException handshake) {
                throw handshakeError(uri, handshake);
            }

            throw new VoiceKitException(
                "VoiceKit: unable to open the WebSocket " + uri + ": " + cause.getMessage(),
                0, "", cause);
        }
    }

    private static VoiceKitException handshakeError(URI uri, WebSocketHandshakeException handshake) {
        HttpResponse<?> response = handshake.getResponse();
        int status = response == null ? 0 : response.statusCode();
        String body = response != null && response.body() instanceof String text ? text : "";
        VoiceKitException detail = VoiceKitException.fromResponse(status, body);

        return new VoiceKitException(
            "VoiceKit: unable to open the WebSocket " + uri + ": " + detail.getMessage(),
            status, detail.errorCode(), handshake);
    }

    /** The URI of the underlying session. */
    public URI uri() {
        return uri;
    }

    /**
     * Writes a raw PCM16 frame (16 kHz, mono, little-endian).
     *
     * @param pcm16 the samples
     */
    public void sendAudio(byte[] pcm16) {
        await(socket.sendBinary(ByteBuffer.wrap(pcm16), true), "send an audio frame");
    }

    /**
     * Writes a text frame verbatim.
     *
     * @param text the message
     */
    public void sendText(String text) {
        await(socket.sendText(text, true), "send a message");
    }

    /**
     * Writes a JSON control message.
     *
     * @param message the message; values are encoded with {@link Json}
     */
    public void sendJson(Map<String, Object> message) {
        sendText(Json.write(message));
    }

    /** Signals the end of speech so the server finalises the utterance. */
    public void stop() {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "stop");

        sendJson(message);
    }

    /**
     * Reads the next JSON event, blocking until one arrives.
     *
     * @return the event, or {@code null} after a normal server-side close
     * @throws VoiceKitException when the session fails or the wait times out
     */
    public Result receive() {
        Object item;
        try {
            item = events.poll(receiveTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new VoiceKitException("VoiceKit: interrupted while waiting for a stream event", 0, "", exception);
        }

        if (item == null) {
            throw new VoiceKitException(
                "VoiceKit: timed out after " + receiveTimeout.toSeconds() + "s waiting for a stream event");
        }

        if (item == CLOSED) {
            return null;
        }

        if (item instanceof Throwable failure) {
            throw new VoiceKitException(
                "VoiceKit: the stream failed: " + failure.getMessage(), 0, "", failure);
        }

        return Result.fromJson((String) item);
    }

    /** Closes the session with a normal-closure status. */
    @Override
    public void close() {
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        } catch (CompletionException exception) {
            // The peer is already gone; nothing left to close.
        } finally {
            socket.abort();
        }
    }

    private static void await(CompletableFuture<WebSocket> future, String action) {
        try {
            future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();

            throw new VoiceKitException(
                "VoiceKit: unable to " + action + ": " + cause.getMessage(), 0, "", cause);
        }
    }

    /** Collects incoming frames on the client's reader thread. */
    private static final class Listener implements WebSocket.Listener {

        private final BlockingQueue<Object> events;

        private final StringBuilder buffer = new StringBuilder();

        Listener(BlockingQueue<Object> events) {
            this.events = events;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);

            if (last) {
                events.add(buffer.toString());
                buffer.setLength(0);
            }

            webSocket.request(1);

            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            buffer.append(new String(bytes, StandardCharsets.UTF_8));

            if (last) {
                events.add(buffer.toString());
                buffer.setLength(0);
            }

            webSocket.request(1);

            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            events.add(CLOSED);

            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            events.add(error);
        }
    }
}
