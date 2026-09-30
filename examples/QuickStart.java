import io.github.lomshakov.voicekit.FilePart;
import io.github.lomshakov.voicekit.Result;
import io.github.lomshakov.voicekit.SynthesizeOptions;
import io.github.lomshakov.voicekit.TranscribeOptions;
import io.github.lomshakov.voicekit.VoiceKitClient;
import java.nio.file.Files;
import java.nio.file.Path;

/** A tour of the VoiceKit Java SDK. */
public final class QuickStart {

    private QuickStart() {
    }

    public static void main(String[] args) throws Exception {
        // The key can also come from the VOICEKIT_API_KEY environment variable.
        VoiceKitClient client = new VoiceKitClient("rtt_…");

        // ── Synthesis → raw audio bytes ──────────────────────────────────
        byte[] audio = client.synthesize(
            "Привет! Это синтез русской речи.",
            new SynthesizeOptions().voice("preset_anna").format("mp3").normalize(true));

        Files.write(Path.of("speech.mp3"), audio);

        // ── Streaming synthesis (Pro/Business) ───────────────────────────
        try (var stream = client.synthesizeStream("Первое предложение. Второе.", null)) {
            Files.copy(stream, Path.of("long.mp3"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // ── Transcription (async → poll) ─────────────────────────────────
        Result job = client.transcribe(FilePart.ofPath("speech.mp3"),
            new TranscribeOptions().language("ru").keyterms(java.util.List.of("диагноз")));

        Result transcript;
        do {
            transcript = client.transcriptionJob(job.getString("job_id"));
            Thread.sleep(1_000);
        } while (!java.util.List.of("completed", "failed").contains(transcript.getString("status")));

        System.out.println(transcript.getString("text"));

        // ── Short files can be transcribed synchronously ─────────────────
        Result inline = client.transcribeSync(FilePart.ofPath("speech.mp3"));
        System.out.println(inline.getString("text"));

        Result analysis = client.analyzeSync(FilePart.ofPath("speech.mp3"));
        System.out.println(analysis.getObject("sentiment"));

        // ── Text intelligence ───────────────────────────────────────────
        System.out.println(client.detectLanguage("Как дела?").getString("language"));
        System.out.println(client.topics("Нейросети и алгоритмы"));
        System.out.println(client.summarize("Длинный текст.", null).getString("summary"));
        System.out.println(client.redact("Иван позвонил на +7 900 123-45-67 из Москвы."));

        // ── Recordings, QA and meeting intelligence (Pro/Business) ──────
        Result linkJob = client.recordingFromLink("https://example.com/call.mp3", "ru");
        Result speakers = client.recordingSpeakers("rec_…");
        System.out.println(speakers);

        client.updateSpeaker("rec_…", "SPEAKER_00", "Иван", "operator");
        client.updateRecording("rec_…", java.util.List.of("sales", "warm"), "Q3");

        byte[] pdf = client.exportRecording("rec_…", "pdf");
        Files.write(Path.of("transcript.pdf"), pdf);

        System.out.println(client.meetingProtocol("rec_…", "standup"));
        System.out.println(client.evaluate(FilePart.ofPath("speech.mp3"),
            "Здравствуйте, это сервис синтеза речи.", null));
        System.out.println(client.translateTranscript(linkJob.getString("job_id"), "en"));

        // ── Batches ─────────────────────────────────────────────────────
        Result batch = client.batchSynthesize(java.util.List.of(
            java.util.Map.of("text", "Первый текст", "voice", "preset_anna"),
            java.util.Map.of("text", "Второй текст", "voice", "preset_dmitri")));

        System.out.println(client.batch(batch.getString("batch_id")));

        // ── Account ─────────────────────────────────────────────────────
        System.out.println(client.usage());
        System.out.println(client.billingBalance());
    }
}
