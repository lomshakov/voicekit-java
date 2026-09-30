import io.github.lomshakov.voicekit.Result;
import io.github.lomshakov.voicekit.StreamOptions;
import io.github.lomshakov.voicekit.StreamSession;
import io.github.lomshakov.voicekit.VoiceKitClient;
import java.util.List;

/**
 * Real-time transcription: raw PCM16 frames (16 kHz, mono, little-endian) go
 * up, JSON events ({@code session}, {@code vad}, {@code partial}, {@code final},
 * {@code error}) come down.
 *
 * <p>Streaming needs a Pro or Business plan.
 */
public final class Streaming {

    private Streaming() {
    }

    public static void main(String[] args) {
        VoiceKitClient client = VoiceKitClient.fromEnvironment();

        try (StreamSession session = client.transcribeStream(new StreamOptions()
            .language("ru")
            .keyterms(List.of("диагноз", "препарат"))
            .interim(true))) {

            // Feed 100 ms of audio at a time while the caller speaks.
            for (byte[] chunk : microphone()) {
                session.sendAudio(chunk);
            }

            session.stop(); // finalise the utterance

            Result event;
            while ((event = session.receive()) != null) {
                switch (event.getString("type")) {
                    case "partial" -> System.out.print("\r" + event.getString("text"));
                    case "final" -> System.out.println("\n" + event.getString("text"));
                    case "error" -> System.err.println(event.getString("message"));
                    default -> {
                        // session / vad — nothing to print.
                    }
                }
            }
        }
    }

    /** Reads 100 ms of 16 kHz mono PCM16 from a microphone or any other source. */
    private static Iterable<byte[]> microphone() {
        return List.of();
    }
}
