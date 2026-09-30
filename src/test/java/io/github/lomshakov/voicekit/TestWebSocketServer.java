package io.github.lomshakov.voicekit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A tiny RFC 6455 server used to exercise {@link StreamSession} end to end
 * without touching the network.
 *
 * <p>It accepts a single connection, answers the handshake, sends a
 * {@code session} event and then answers {@code {"type":"stop"}} with a
 * {@code final} event followed by a normal closure.
 */
final class TestWebSocketServer implements AutoCloseable {

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    /** How the fixture should behave after accepting a connection. */
    enum Mode {
        /** Complete the handshake and script a normal session. */
        NORMAL,
        /** Complete the handshake, then stay silent. */
        SILENT,
        /** Reject the handshake with {@code 403} and a Problem Details body. */
        REJECT
    }

    private final ServerSocket server;
    private final Thread thread;
    private final Mode mode;
    private final List<String> receivedText = new CopyOnWriteArrayList<>();
    private final List<byte[]> receivedBinary = new CopyOnWriteArrayList<>();
    private final Map<String, String> handshakeHeaders = new LinkedHashMap<>();

    private volatile String requestPath = "";
    private volatile boolean stopping;

    TestWebSocketServer(Mode mode) throws IOException {
        this.mode = mode;
        this.server = new ServerSocket(0);

        Thread worker = new Thread(this::serve, "test-websocket-server");
        worker.setDaemon(true);
        this.thread = worker;
        worker.start();
    }

    /** The ephemeral port the fixture listens on. */
    int port() {
        return server.getLocalPort();
    }

    /** The request target of the upgrade request. */
    String requestPath() {
        return requestPath;
    }

    /** The headers of the upgrade request, keyed in lower case. */
    Map<String, String> handshakeHeaders() {
        return handshakeHeaders;
    }

    /** The text frames the client sent. */
    List<String> receivedText() {
        return receivedText;
    }

    /** The binary frames the client sent. */
    List<byte[]> receivedBinary() {
        return receivedBinary;
    }

    @Override
    public void close() {
        stopping = true;

        try {
            server.close();
        } catch (IOException exception) {
            // Nothing left to close.
        }

        thread.interrupt();
    }

    private void serve() {
        try (Socket connection = server.accept()) {
            connection.setSoTimeout(5_000);

            InputStream in = connection.getInputStream();
            OutputStream out = connection.getOutputStream();

            String requestLine = readLine(in);
            String[] parts = requestLine.split(" ");
            requestPath = parts.length > 1 ? parts[1] : "";

            while (true) {
                String line = readLine(in);
                if (line.isEmpty()) {
                    break;
                }

                int colon = line.indexOf(':');
                if (colon > 0) {
                    handshakeHeaders.put(
                        line.substring(0, colon).trim().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).trim());
                }
            }

            if (mode == Mode.REJECT) {
                reject(out);
                waitForClose(in);

                return;
            }

            completeHandshake(out);

            if (mode == Mode.SILENT) {
                waitForClose(in);

                return;
            }

            writeFrame(out, 0x1, "{\"type\":\"session\",\"id\":\"s1\"}".getBytes(StandardCharsets.UTF_8));

            while (!stopping) {
                Frame frame = readFrame(in);
                if (frame == null) {
                    return;
                }

                switch (frame.opcode()) {
                    case 0x1 -> {
                        String message = new String(frame.payload(), StandardCharsets.UTF_8);
                        receivedText.add(message);

                        if (message.contains("stop")) {
                            writeFrame(out, 0x1,
                                "{\"type\":\"final\",\"text\":\"привет\"}".getBytes(StandardCharsets.UTF_8));
                            writeClose(out);
                            waitForClose(in);

                            return;
                        }
                    }
                    case 0x2 -> receivedBinary.add(frame.payload());
                    case 0x8 -> {
                        writeClose(out);

                        return;
                    }
                    case 0x9 -> writeFrame(out, 0xA, frame.payload());
                    default -> {
                        // Continuation and pong frames need no answer here.
                    }
                }
            }
        } catch (IOException exception) {
            // The test finished; the socket may already be gone.
        }
    }

    private void completeHandshake(OutputStream out) throws IOException {
        String key = handshakeHeaders.getOrDefault("sec-websocket-key", "");
        String accept = Base64.getEncoder().encodeToString(sha1(key + GUID));

        out.write(("HTTP/1.1 101 Switching Protocols\r\n"
            + "Upgrade: websocket\r\n"
            + "Connection: Upgrade\r\n"
            + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private void reject(OutputStream out) throws IOException {
        byte[] body = ("{\"code\":\"streaming_forbidden\","
            + "\"detail\":\"Streaming needs Pro\",\"status\":403}").getBytes(StandardCharsets.UTF_8);

        out.write(("HTTP/1.1 403 Forbidden\r\n"
            + "Content-Type: application/problem+json\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    /** Reads until the peer closes, so a clean close can be observed. */
    private void waitForClose(InputStream in) {
        try {
            while (readFrame(in) != null) {
                // Drain anything the client is still sending.
            }
        } catch (IOException exception) {
            // Expected once the peer disconnects.
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();

        while (true) {
            int value = in.read();
            if (value < 0) {
                break;
            }
            if (value == '\n') {
                break;
            }
            if (value != '\r') {
                line.write(value);
            }
        }

        return line.toString(StandardCharsets.UTF_8);
    }

    private static Frame readFrame(InputStream in) throws IOException {
        int first = in.read();
        if (first < 0) {
            return null;
        }

        int second = in.read();
        if (second < 0) {
            return null;
        }

        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;

        if (length == 126) {
            length = readUnsigned(in, 2);
        } else if (length == 127) {
            length = readUnsigned(in, 8);
        }

        byte[] mask = masked ? readFully(in, 4) : null;
        byte[] payload = readFully(in, (int) length);

        if (mask != null) {
            for (int index = 0; index < payload.length; index++) {
                payload[index] ^= mask[index % 4];
            }
        }

        return new Frame(opcode, payload);
    }

    private static long readUnsigned(InputStream in, int bytes) throws IOException {
        long value = 0;

        for (int index = 0; index < bytes; index++) {
            value = (value << 8) | (readFully(in, 1)[0] & 0xFF);
        }

        return value;
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;

        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new IOException("unexpected end of stream");
            }

            offset += read;
        }

        return buffer;
    }

    private static void writeFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | opcode);

        if (payload.length < 126) {
            frame.write(payload.length);
        } else if (payload.length < 65_536) {
            frame.write(126);
            frame.write((payload.length >> 8) & 0xFF);
            frame.write(payload.length & 0xFF);
        } else {
            frame.write(127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                frame.write((int) ((long) payload.length >> shift) & 0xFF);
            }
        }

        frame.writeBytes(payload);
        out.write(frame.toByteArray());
        out.flush();
    }

    private static void writeClose(OutputStream out) throws IOException {
        writeFrame(out, 0x8, new byte[] {0x03, (byte) 0xE8});
    }

    private static byte[] sha1(String text) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** A decoded WebSocket frame. */
    private record Frame(int opcode, byte[] payload) {
    }
}
