package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class StreamSessionTest {

    private static VoiceKitClient clientFor(TestWebSocketServer server, Duration timeout) {
        return new VoiceKitClient("rtt_test", "http://127.0.0.1:" + server.port(), timeout,
            new FakeTransport());
    }

    @Test
    void opens_a_session_and_reads_the_first_event() throws Exception {
        try (TestWebSocketServer server = new TestWebSocketServer(TestWebSocketServer.Mode.NORMAL)) {
            VoiceKitClient client = clientFor(server, Duration.ofSeconds(10));

            try (StreamSession session = client.transcribeStream(
                new StreamOptions().language("ru").keyterms(List.of("диагноз")))) {
                Result event = session.receive();

                assertEquals("session", event.getString("type"));
                assertEquals("s1", event.getString("id"));
                assertTrue(session.uri().toString().startsWith("ws://127.0.0.1:"));
            }

            assertEquals("rtt_test", server.handshakeHeaders().get("x-api-key"));
            assertEquals("voicekit-java/" + VoiceKitClient.VERSION,
                server.handshakeHeaders().get("user-agent"));
            assertTrue(server.handshakeHeaders().containsKey("sec-websocket-key"));
            assertTrue(server.requestPath().startsWith("/v1/transcribe/stream?language=ru&keyterms="));
        }
    }

    @Test
    void sends_audio_and_finalises_the_utterance() throws Exception {
        try (TestWebSocketServer server = new TestWebSocketServer(TestWebSocketServer.Mode.NORMAL)) {
            VoiceKitClient client = clientFor(server, Duration.ofSeconds(10));

            try (StreamSession session = client.transcribeStream()) {
                assertEquals("session", session.receive().getString("type"));

                session.sendAudio(new byte[] {1, 2, 3});
                session.sendJson(java.util.Map.of("type", "flush"));
                session.stop();

                Result last = session.receive();

                assertEquals("final", last.getString("type"));
                assertEquals("привет", last.getString("text"));

                // A normal server-side close is reported as null.
                assertNull(session.receive());
            }

            assertEquals(1, server.receivedBinary().size());
            assertArrayEquals(new byte[] {1, 2, 3}, server.receivedBinary().get(0));
            assertEquals(2, server.receivedText().size());
            assertTrue(server.receivedText().get(1).contains("stop"));
        }
    }

    @Test
    void uses_the_vad_endpoint_for_a_vad_session() throws Exception {
        try (TestWebSocketServer server = new TestWebSocketServer(TestWebSocketServer.Mode.NORMAL)) {
            VoiceKitClient client = clientFor(server, Duration.ofSeconds(10));

            try (StreamSession session = client.vadStream()) {
                assertEquals("session", session.receive().getString("type"));
            }

            assertEquals("/v1/vad/stream", server.requestPath());
        }
    }

    @Test
    void reports_a_rejected_handshake() throws Exception {
        try (TestWebSocketServer server = new TestWebSocketServer(TestWebSocketServer.Mode.REJECT)) {
            VoiceKitClient client = clientFor(server, Duration.ofSeconds(10));

            VoiceKitException error = assertThrows(VoiceKitException.class, client::vadStream);

            assertEquals(403, error.statusCode());
            assertEquals("streaming_forbidden", error.errorCode());
            assertTrue(error.isForbidden());
        }
    }

    @Test
    void times_out_when_the_server_stays_silent() throws Exception {
        try (TestWebSocketServer server = new TestWebSocketServer(TestWebSocketServer.Mode.SILENT)) {
            VoiceKitClient client = clientFor(server, Duration.ofMillis(500));

            try (StreamSession session = client.vadStream()) {
                VoiceKitException error = assertThrows(VoiceKitException.class, session::receive);

                assertTrue(error.getMessage().contains("timed out"));
            }
        }
    }

    @Test
    void rejects_an_unreachable_endpoint() {
        VoiceKitClient client = new VoiceKitClient("rtt_test", "http://127.0.0.1:1",
            Duration.ofSeconds(2), new FakeTransport());

        VoiceKitException error = assertThrows(VoiceKitException.class, client::vadStream);

        assertTrue(error.getMessage().contains("unable to open the WebSocket"));
    }
}
