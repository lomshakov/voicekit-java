package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * End-to-end checks against the real API. They only run when
 * {@code VOICEKIT_API_KEY} is set:
 *
 * <pre>{@code
 * VOICEKIT_API_KEY=rtt_… mvn test
 * }</pre>
 */
@EnabledIfEnvironmentVariable(named = "VOICEKIT_API_KEY", matches = ".+")
class LiveApiTest {

    private VoiceKitClient client() {
        return VoiceKitClient.fromEnvironment();
    }

    @Test
    void synthesizes_and_returns_audio() {
        byte[] audio = client().synthesize("Здравствуйте! Это проверка Java SDK.");

        assertTrue(audio.length > 0, "the API returned no audio");
    }

    @Test
    void lists_the_voice_catalog() {
        List<Result> voices = client().voices();

        assertFalse(voices.isEmpty(), "the voice catalog is empty");
        assertFalse(voices.get(0).getString("id").isEmpty());
    }

    @Test
    void transcribes_the_synthesized_audio() {
        VoiceKitClient client = client();

        byte[] audio = client.synthesize("Проверка распознавания речи.");
        Result transcript = client.transcribeSync(
            FilePart.ofBytes(audio, "speech.wav").withContentType("audio/wav"),
            new TranscribeSyncOptions().language("ru"));

        assertNotNull(transcript.get("text"));
    }

    @Test
    void reports_usage() {
        Result usage = client().usage();

        assertTrue(usage.size() > 0, "the usage payload is empty");
    }
}
