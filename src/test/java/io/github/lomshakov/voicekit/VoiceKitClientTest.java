package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoiceKitClientTest {

    private FakeTransport transport;

    private VoiceKitClient client;

    @BeforeEach
    void setUp() {
        transport = new FakeTransport();
        client = new VoiceKitClient("rtt_test", "https://api.test", Duration.ofSeconds(5), transport);
    }

    // ──────────────────────────── construction ────────────────────────────

    @Test
    void requires_an_api_key() {
        assertThrows(IllegalArgumentException.class, () -> new VoiceKitClient(null));
        assertThrows(IllegalArgumentException.class, () -> new VoiceKitClient("   "));
        assertThrows(IllegalArgumentException.class, () -> new VoiceKitClient("", "https://api.test"));
    }

    @Test
    void applies_the_documented_defaults() {
        VoiceKitClient defaults = new VoiceKitClient("rtt_test");

        assertEquals("https://ttsapi.ru", defaults.baseUrl());
        assertEquals(VoiceKitClient.DEFAULT_TIMEOUT, defaults.timeout());
        assertTrue(defaults.transport() instanceof HttpTransport);
    }

    @Test
    void trims_trailing_slashes_from_the_base_url() {
        assertEquals("https://api.test",
            new VoiceKitClient("k", "https://api.test///").baseUrl());
    }

    @Test
    void reads_the_key_from_the_environment() {
        assertThrows(IllegalArgumentException.class,
            () -> VoiceKitClient.fromEnvironment("VOICEKIT_TEST_MISSING_" + System.nanoTime()));
        assertThrows(IllegalArgumentException.class, VoiceKitClient::fromEnvironment);
    }

    // ───────────────────────────── headers ────────────────────────────────

    @Test
    void authenticates_and_labels_every_request() {
        transport.always("{}");
        client.usage();

        Request request = transport.lastRequest();

        assertEquals("GET", request.method());
        assertEquals("/v1/usage", request.uri().getPath());
        assertEquals("rtt_test", request.header("X-Api-Key"));
        assertEquals("voicekit-java/" + VoiceKitClient.VERSION, request.header("user-agent"));
        assertEquals("application/json", request.header("Accept"));
    }

    @Test
    void sends_custom_headers_with_every_request() {
        FakeTransport custom = new FakeTransport().always("{}");
        VoiceKitClient configured = new VoiceKitClient("k", "https://api.test", Duration.ofSeconds(5),
            custom, Map.of("X-Trace", "abc"));

        configured.usage();

        assertEquals("abc", custom.lastRequest().header("X-Trace"));
    }

    // ──────────────────────────── synthesis ───────────────────────────────

    @Test
    void synthesizes_and_returns_the_raw_audio() {
        transport.alwaysBytes(new byte[] {1, 2, 3});

        byte[] audio = client.synthesize("Привет! Это синтез русской речи.", new SynthesizeOptions()
            .voice("preset_anna")
            .format("mp3")
            .sampleRate(24000)
            .speed(1.2)
            .pitch(2)
            .emotion("neutral")
            .ssml(false)
            .normalize(true)
            .model("premium")
            .language("ru"));

        assertArrayEquals(new byte[] {1, 2, 3}, audio);

        Map<String, Object> body = transport.lastBody();
        assertEquals("Привет! Это синтез русской речи.", body.get("text"));
        assertEquals("preset_anna", body.get("voice"));
        assertEquals("mp3", body.get("format"));
        assertEquals(24000L, body.get("sample_rate"));
        assertEquals(1.2d, body.get("speed"));
        assertEquals(2.0d, body.get("pitch"));
        assertEquals("neutral", body.get("emotion"));
        assertEquals(Boolean.FALSE, body.get("ssml"));
        assertEquals(Boolean.TRUE, body.get("normalize"));
        assertEquals("premium", body.get("model"));
        assertEquals("ru", body.get("language"));
        assertEquals("/v1/synthesize", transport.lastRequest().uri().getPath());
        assertEquals("application/json", transport.lastRequest().header("content-type"));
    }

    @Test
    void omits_empty_synthesis_options() {
        transport.alwaysBytes(new byte[0]);
        client.synthesize("текст");

        assertEquals(Map.of("text", "текст"), transport.lastBody());
    }

    @Test
    void encodes_an_effect_chain_into_the_synthesis_body() {
        transport.alwaysBytes(new byte[0]);
        client.synthesize("текст", new SynthesizeOptions()
            .effects(List.of(Effects.effect("reverb", "room_size", 0.5))));

        assertEquals("[{\"type\":\"reverb\",\"room_size\":0.5}]", transport.lastBody().get("effects"));
    }

    @Test
    void maps_api_errors_to_exceptions() {
        transport.always(403, "{\"code\":\"streaming_forbidden\",\"detail\":\"Streaming needs Pro\"}");

        VoiceKitException error = assertThrows(VoiceKitException.class, () -> client.synthesize("текст"));

        assertEquals(403, error.statusCode());
        assertEquals("streaming_forbidden", error.errorCode());
        assertTrue(error.isForbidden());
        assertTrue(error.isCode("streaming_forbidden"));
    }

    @Test
    void propagates_transport_failures() {
        transport.onFailure("/v1/usage", new VoiceKitException("socket closed"));

        VoiceKitException error = assertThrows(VoiceKitException.class, client::usage);

        assertEquals("socket closed", error.getMessage());
    }

    @Test
    void streams_synthesis_without_buffering() throws Exception {
        transport.alwaysBytes("chunk".getBytes(StandardCharsets.UTF_8));

        InputStream stream = client.synthesizeStream("Длинный текст");

        assertEquals("chunk", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        assertEquals("/v1/synthesize/stream", transport.lastRequest().uri().getPath());
        assertEquals("*/*", transport.lastRequest().header("accept"));
    }

    @Test
    void reports_streaming_errors_before_handing_back_the_body() {
        transport.always(429, "{\"code\":\"rate_limited\"}");

        VoiceKitException error =
            assertThrows(VoiceKitException.class, () -> client.synthesizeStream("текст"));

        assertTrue(error.isRateLimited());
        assertTrue(error.isCode("rate_limited"));
    }

    @Test
    void queues_a_long_form_job_with_a_webhook() {
        transport.always("{\"job_id\":\"job_1\"}");

        Result job = client.synthesizeAsync("Длинный текст", new SynthesizeAsyncOptions()
            .voice("preset_anna")
            .webhookUrl("https://hook.test/x"));

        assertEquals("job_1", job.getString("job_id"));
        assertEquals("/v1/synthesize/async", transport.lastRequest().uri().getPath());
        assertEquals("webhookUrl=https%3A%2F%2Fhook.test%2Fx", transport.lastQuery());
        assertEquals("preset_anna", transport.lastBody().get("voice"));
    }

    @Test
    void polls_a_long_form_job_and_downloads_its_audio() {
        transport.always("{\"status\":\"completed\"}");
        assertEquals("completed", client.synthesisJob("job_1").getString("status"));
        assertEquals("/v1/synthesize/async/job_1", transport.lastRequest().uri().getPath());

        transport.alwaysBytes(new byte[] {9});
        assertArrayEquals(new byte[] {9}, client.downloadSynthesisAudio("job_1"));
        assertEquals("/v1/synthesize/async/job_1/audio", transport.lastRequest().uri().getPath());
    }

    // ────────────────────────────── voices ────────────────────────────────

    @Test
    void decodes_a_bare_array_of_voices() {
        transport.always("[{\"id\":\"preset_anna\"},{\"id\":\"preset_dmitri\"}]");

        List<Result> voices = client.voices();

        assertEquals(2, voices.size());
        assertEquals("preset_anna", voices.get(0).getString("id"));
    }

    @Test
    void decodes_a_wrapped_array_of_voices() {
        transport.always("{\"items\":[{\"id\":\"preset_anna\"}]}");

        assertEquals(1, client.voices().size());
    }

    @Test
    void ignores_non_object_items_in_a_list_response() {
        transport.always("[{\"id\":\"a\"},\"nope\",7]");

        assertEquals(1, client.voices().size());
    }

    @Test
    void reads_one_voice() {
        transport.always("{\"id\":\"preset_anna\",\"language\":\"ru\"}");

        Result voice = client.voice("preset_anna");

        assertEquals("ru", voice.getString("language"));
        assertEquals("/v1/voices/preset_anna", transport.lastRequest().uri().getPath());
    }

    @Test
    void creates_a_cloned_voice_from_repeated_samples() {
        transport.always("{\"id\":\"clone_1\"}");

        Result clone = client.createCloneVoice("Алиса", "Привет, это образец.",
            List.of(FilePart.ofBytes(new byte[] {1}, "a.wav"), FilePart.ofBytes(new byte[] {2}, "b.wav")),
            "ru");

        assertEquals("clone_1", clone.getString("id"));

        String body = transport.lastRequest().bodyText();
        assertEquals(2, body.split("name=\"samples\"", -1).length - 1);
        assertTrue(body.contains("filename=\"a.wav\""));
        assertTrue(body.contains("filename=\"b.wav\""));
        assertTrue(body.contains("name=\"name\""));
        assertTrue(body.contains("Алиса"));
        assertTrue(body.contains("name=\"prompt_text\""));
        assertTrue(body.contains("Привет, это образец."));
    }

    @Test
    void requires_at_least_one_clone_sample() {
        assertThrows(IllegalArgumentException.class,
            () -> client.createCloneVoice("Алиса", "текст", List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> client.createCloneVoice("Алиса", "текст", null));
    }

    @Test
    void lists_and_deletes_cloned_voices() {
        transport.always("[{\"id\":\"clone_1\"}]");
        assertEquals(1, client.cloneVoices().size());

        transport.always("{\"id\":\"clone_1\"}");
        assertEquals("clone_1", client.cloneVoice("clone_1").getString("id"));

        transport.always("");
        client.deleteCloneVoice("clone_1");
        assertEquals("DELETE", transport.lastRequest().method());
        assertEquals("/v1/voices/clone/clone_1", transport.lastRequest().uri().getPath());
    }

    // ─────────────────────────── transcription ────────────────────────────

    @Test
    void uploads_a_file_as_multipart() {
        transport.always("{\"job_id\":\"job_1\"}");

        Result job = client.transcribe(FilePart.ofBytes(new byte[] {1, 2}, "call.wav"),
            new TranscribeOptions()
                .language("ru")
                .diarization(true)
                .clean(true)
                .longForm(true)
                .keyterms(List.of("диагноз", "препарат"))
                .webhookUrl("https://hook.test/x"));

        assertEquals("job_1", job.getString("job_id"));

        Request request = transport.lastRequest();
        assertEquals("/v1/transcribe", request.uri().getPath());
        assertEquals("POST", request.method());
        assertTrue(request.header("content-type").startsWith("multipart/form-data; boundary="));

        String body = request.bodyText();
        assertTrue(body.contains("name=\"audio\"; filename=\"call.wav\""));
        assertTrue(body.contains("Content-Type: audio/wav"));
        assertTrue(body.contains("name=\"language\"\r\n\r\nru"));
        assertTrue(body.contains("name=\"diarization\"\r\n\r\ntrue"));
        assertTrue(body.contains("name=\"clean\"\r\n\r\ntrue"));
        assertTrue(body.contains("name=\"longForm\"\r\n\r\ntrue"));
        assertTrue(body.contains("name=\"keyterms\"\r\n\r\nдиагноз,препарат"));
        assertTrue(body.endsWith("--\r\n"));
    }

    @Test
    void uploads_without_options() {
        transport.always("{}");
        client.transcribeSync(FilePart.ofBytes(new byte[] {1}, "call.wav"));

        assertEquals("/v1/transcribe/sync", transport.lastRequest().uri().getPath());
        assertFalse(transport.lastRequest().bodyText().contains("name=\"language\""));
    }

    @Test
    void reads_transcription_jobs_and_subtitles() {
        transport.always("{\"status\":\"completed\",\"text\":\"Привет\"}");
        assertEquals("Привет", client.transcriptionJob("job_1").getString("text"));
        assertEquals("/v1/transcribe/job_1", transport.lastRequest().uri().getPath());

        transport.always("WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nПривет");
        String captions = client.subtitles("job_1", new SubtitleOptions()
            .format("vtt")
            .targetLanguage("en")
            .hotMarks(true));

        assertTrue(captions.startsWith("WEBVTT"));
        assertEquals("/v1/transcribe/job_1/subtitles", transport.lastRequest().uri().getPath());
        assertEquals("format=vtt&target_language=en&hot_marks=true", transport.lastQuery());
    }

    @Test
    void translates_a_transcript() {
        transport.always("{\"target_language\":\"en\"}");

        Result translated = client.translateTranscript("job_1", "en");

        assertEquals("en", translated.getString("target_language"));
        assertEquals("/v1/transcribe/job_1/translate", transport.lastRequest().uri().getPath());
        assertEquals("en", transport.lastBody().get("target_language"));
    }

    @Test
    void detects_voice_activity() {
        transport.always("{\"segments\":[]}");

        Result vad = client.vad(FilePart.ofBytes(new byte[] {1}, "call.wav"));

        assertTrue(vad.getList("segments").isEmpty());
        assertEquals("/v1/vad", transport.lastRequest().uri().getPath());
    }

    // ───────────────────────────── analysis ───────────────────────────────

    @Test
    void analyses_a_file_with_extractor_toggles() {
        transport.always("{\"job_id\":\"job_1\"}");

        client.analyzeSync(FilePart.ofBytes(new byte[] {1}, "call.wav"), new AnalyzeSyncOptions()
            .language("ru")
            .diarization(true)
            .emotions(false)
            .keywords(false)
            .entities(true)
            .keyterms(List.of("диагноз")));

        String body = transport.lastRequest().bodyText();
        assertEquals("/v1/analyze/sync", transport.lastRequest().uri().getPath());
        assertTrue(body.contains("name=\"emotions\"\r\n\r\nfalse"));
        assertTrue(body.contains("name=\"keywords\"\r\n\r\nfalse"));
        assertTrue(body.contains("name=\"entities\"\r\n\r\ntrue"));
        assertFalse(body.contains("name=\"emotions\"\r\n\r\ntrue"));
    }

    @Test
    void reads_analysis_jobs() {
        transport.always("{\"status\":\"completed\"}");

        assertEquals("completed", client.analysisJob("job_1").getString("status"));
        assertEquals("/v1/analyze/job_1", transport.lastRequest().uri().getPath());
    }

    @Test
    void keeps_empty_extractor_fields_out_of_the_request() {
        transport.always("{}");
        client.analyzeSync(FilePart.ofBytes(new byte[] {1}, "call.wav"));

        assertFalse(transport.lastRequest().bodyText().contains("emotions"));
    }

    // ────────────────────────── text intelligence ─────────────────────────

    @Test
    void calls_the_text_intelligence_endpoints() {
        transport.always("{\"language\":\"ru\"}");
        assertEquals("ru", client.detectLanguage("Как дела?").getString("language"));
        assertEquals("/v1/detect-language", transport.lastRequest().uri().getPath());
        assertEquals("Как дела?", transport.lastBody().get("text"));

        transport.always("{\"text\":\"<PERSON>\"}");
        client.redact("Иван позвонил на +7 900 123-45-67", "ru");
        assertEquals("/v1/redact", transport.lastRequest().uri().getPath());
        assertEquals("ru", transport.lastBody().get("language"));

        transport.always("{\"topics\":[]}");
        client.topics("Нейросети и алгоритмы");
        assertEquals("/v1/analyze/topics", transport.lastRequest().uri().getPath());
        assertFalse(transport.lastBody().containsKey("language"));

        transport.always("{\"summary\":\"Сжатый текст\"}");
        client.summarize("Длинный текст.", new SummarizeOptions().maxSentences(3));
        assertEquals("/v1/analyze/summarize", transport.lastRequest().uri().getPath());
        assertEquals(3L, transport.lastBody().get("max_sentences"));

        transport.always("{\"flagged\":false}");
        client.moderate("Текст", "ru");
        assertEquals("/v1/moderate", transport.lastRequest().uri().getPath());
    }

    @Test
    void evaluates_quality_against_a_reference() {
        transport.always("{\"wer\":0.1}");

        Result score = client.evaluate(FilePart.ofBytes(new byte[] {1}, "clip.wav"),
            "Здравствуйте, это сервис синтеза речи.",
            new EvaluateOptions().language("ru").normalize(true));

        assertEquals(0.1d, score.getDouble("wer"));
        assertEquals("/v1/eval", transport.lastRequest().uri().getPath());

        String body = transport.lastRequest().bodyText();
        assertTrue(body.contains("name=\"reference\""));
        assertTrue(body.contains("Здравствуйте, это сервис синтеза речи."));
        assertTrue(body.contains("name=\"normalize\"\r\n\r\ntrue"));
    }

    // ───────────────────────────── effects ────────────────────────────────

    @Test
    void applies_audio_effects() {
        transport.always("{\"job_id\":\"job_1\"}");

        client.applyAudioEffects(FilePart.ofBytes(new byte[] {1}, "voice.wav"),
            new AudioEffectsOptions()
                .effects(Effects.effect("compressor", "ratio", 3))
                .outputFormat("mp3"));

        String body = transport.lastRequest().bodyText();
        assertEquals("/v1/audio/effects", transport.lastRequest().uri().getPath());
        assertTrue(body.contains("name=\"effects\"\r\n\r\n[{\"type\":\"compressor\",\"ratio\":3}]"));
        assertTrue(body.contains("name=\"output_format\"\r\n\r\nmp3"));
    }

    @Test
    void applies_video_effects_with_an_optional_audio_track() {
        transport.always("{\"job_id\":\"job_1\"}");

        client.applyVideoEffects(FilePart.ofBytes(new byte[] {1}, "clip.mp4"), new VideoEffectsOptions()
            .effects(Effects.effect("reverb"))
            .mode("mux")
            .audio(FilePart.ofBytes(new byte[] {2}, "voice.wav")));

        String body = transport.lastRequest().bodyText();
        assertEquals("/v1/video/effects", transport.lastRequest().uri().getPath());
        assertTrue(body.contains("name=\"video\"; filename=\"clip.mp4\""));
        assertTrue(body.contains("Content-Type: video/mp4"));
        assertTrue(body.contains("name=\"audio\"; filename=\"voice.wav\""));
        assertTrue(body.contains("name=\"mode\"\r\n\r\nmux"));
    }

    @Test
    void omits_the_audio_track_when_none_was_given() {
        transport.always("{}");
        client.applyVideoEffects(FilePart.ofBytes(new byte[] {1}, "clip.mp4"));

        String body = transport.lastRequest().bodyText();
        assertTrue(body.contains("name=\"video\""));
        assertFalse(body.contains("name=\"audio\""));
        assertTrue(body.contains("name=\"effects\"\r\n\r\n[]"));
    }

    @Test
    void cleans_audio_with_a_custom_preset() {
        transport.always("{\"job_id\":\"job_1\"}");

        client.cleanAudio(FilePart.ofBytes(new byte[] {1}, "noisy.wav"), new CleanAudioOptions()
            .options(Map.of("high_pass", 80))
            .outputFormat("mp3"));

        String body = transport.lastRequest().bodyText();
        assertEquals("/v1/audio/clean", transport.lastRequest().uri().getPath());
        assertTrue(body.contains("name=\"options\"\r\n\r\n{\"high_pass\":80}"));
    }

    @Test
    void cleans_audio_with_the_default_preset() {
        transport.always("{}");
        client.cleanAudio(FilePart.ofBytes(new byte[] {1}, "noisy.wav"));

        assertFalse(transport.lastRequest().bodyText().contains("name=\"options\""));
    }

    @Test
    void polls_and_downloads_effect_jobs() {
        transport.always("{\"status\":\"completed\"}");
        assertEquals("completed", client.audioEffectsJob("job_1").getString("status"));
        assertEquals("/v1/audio/effects/job_1", transport.lastRequest().uri().getPath());

        transport.always("{\"status\":\"completed\"}");
        client.videoEffectsJob("job_2");
        assertEquals("/v1/video/effects/job_2", transport.lastRequest().uri().getPath());

        transport.always("{\"status\":\"completed\"}");
        client.audioCleaningJob("job_3");
        assertEquals("/v1/audio/clean/job_3", transport.lastRequest().uri().getPath());

        transport.alwaysBytes(new byte[] {1});
        assertArrayEquals(new byte[] {1}, client.downloadAudioEffects("job_1"));
        assertEquals("/v1/audio/effects/job_1/audio", transport.lastRequest().uri().getPath());

        transport.alwaysBytes(new byte[] {1});
        client.downloadVideoEffects("job_2");
        assertEquals("/v1/video/effects/job_2/file", transport.lastRequest().uri().getPath());

        transport.alwaysBytes(new byte[] {1});
        client.downloadAudioCleaning("job_3");
        assertEquals("/v1/audio/clean/job_3/audio", transport.lastRequest().uri().getPath());
    }

    // ──────────────────────────── voice id ────────────────────────────────

    @Test
    void reads_a_voice_passport() {
        transport.always("{\"language\":\"ru\",\"gender\":\"female\"}");

        Result passport = client.analyzeVoice(FilePart.ofBytes(new byte[] {1}, "sample.wav"));

        assertEquals("female", passport.getString("gender"));
        assertEquals("/v1/voice-id", transport.lastRequest().uri().getPath());
    }

    @Test
    void enrols_verifies_and_identifies_voices() {
        transport.always("{\"profile_id\":\"p1\"}");
        assertEquals("p1", client.enrollVoice(FilePart.ofBytes(new byte[] {1}, "a.wav"), "Алиса")
            .getString("profile_id"));
        assertEquals("/v1/voice-id/enroll", transport.lastRequest().uri().getPath());
        assertTrue(transport.lastRequest().bodyText().contains("name=\"name\"\r\n\r\nАлиса"));

        transport.always("{\"verified\":true}");
        assertTrue(client.verifyVoice(FilePart.ofBytes(new byte[] {1}, "b.wav"), "p1")
            .getBoolean("verified"));
        assertTrue(transport.lastRequest().bodyText().contains("name=\"profile_id\"\r\n\r\np1"));

        transport.always("{\"profile_id\":\"p1\"}");
        client.identifyVoice(FilePart.ofBytes(new byte[] {1}, "c.wav"), List.of("p1", "p2"));
        assertTrue(transport.lastRequest().bodyText().contains("name=\"profile_ids\"\r\n\r\np1,p2"));

        transport.always("{}");
        client.identifyVoice(FilePart.ofBytes(new byte[] {1}, "c.wav"));
        assertFalse(transport.lastRequest().bodyText().contains("profile_ids"));
    }

    @Test
    void lists_and_deletes_voice_profiles() {
        transport.always("[{\"profile_id\":\"p1\"},{\"profile_id\":\"p2\"}]");
        assertEquals(2, client.voiceProfiles().size());

        transport.always("");
        client.deleteVoiceProfile("p1");
        assertEquals("DELETE", transport.lastRequest().method());
        assertEquals("/v1/voice-id/profiles/p1", transport.lastRequest().uri().getPath());
    }

    // ──────────────────────────── recordings ──────────────────────────────

    @Test
    void lists_recordings_with_filters() {
        transport.always("{\"items\":[{\"id\":\"rec_1\"}],\"total\":1}");

        Result page = client.recordings(new RecordingListOptions()
            .source("link")
            .tag("sales")
            .folder("Q3")
            .limit(10)
            .offset(20));

        assertEquals(1, page.getInt("total"));
        assertEquals("source=link&tag=sales&folder=Q3&limit=10&offset=20", transport.lastQuery());
        assertEquals("/v1/recordings", transport.lastRequest().uri().getPath());
    }

    @Test
    void lists_recording_tags_and_folders() {
        transport.always("{\"values\":[\"sales\",\"warm\"]}");
        assertEquals(List.of("sales", "warm"), client.recordingTags());
        assertEquals("/v1/recordings/tags", transport.lastRequest().uri().getPath());

        transport.always("{\"values\":[\"Q3\"]}");
        assertEquals(List.of("Q3"), client.recordingFolders());
        assertEquals("/v1/recordings/folders", transport.lastRequest().uri().getPath());
    }

    @Test
    void reads_a_recording_from_a_link() {
        transport.always("{\"recording_id\":\"rec_1\"}");

        Result job = client.recordingFromLink("https://example.com/call.mp3", "ru");

        assertEquals("rec_1", job.getString("recording_id"));
        assertEquals("/v1/recordings/from-link", transport.lastRequest().uri().getPath());
        assertEquals("https://example.com/call.mp3", transport.lastBody().get("url"));
        assertEquals("ru", transport.lastBody().get("language"));
    }

    @Test
    void reads_recording_details_transcripts_and_speakers() {
        transport.always("{\"id\":\"rec_1\"}");
        assertEquals("rec_1", client.recording("rec_1").getString("id"));
        assertEquals("/v1/recordings/rec_1", transport.lastRequest().uri().getPath());

        transport.always("{\"text\":\"Привет\"}");
        assertEquals("Привет", client.recordingTranscript("rec_1").getString("text"));
        assertEquals("/v1/recordings/rec_1/transcript", transport.lastRequest().uri().getPath());

        transport.always("{\"speakers\":[]}");
        assertTrue(client.recordingSpeakers("rec_1").getList("speakers").isEmpty());
        assertEquals("/v1/recordings/rec_1/speakers", transport.lastRequest().uri().getPath());
    }

    @Test
    void patches_a_recording() {
        transport.always("{\"id\":\"rec_1\"}");

        client.updateRecording("rec_1", new UpdateRecordingOptions()
            .tags(List.of("sales", "warm"))
            .folder("Q3"));

        Request request = transport.lastRequest();
        assertEquals("PATCH", request.method());
        assertEquals("{\"tags\":[\"sales\",\"warm\"],\"folder\":\"Q3\"}", request.bodyText());
    }

    @Test
    void clears_tags_and_the_folder() {
        transport.always("{}");
        client.updateRecording("rec_1", new UpdateRecordingOptions().clearTags());
        assertEquals("{\"tags\":null}", transport.lastRequest().bodyText());

        transport.always("{}");
        client.updateRecording("rec_1", new UpdateRecordingOptions().folder(""));
        assertEquals("{\"folder\":\"\"}", transport.lastRequest().bodyText());
    }

    @Test
    void patches_a_speaker() {
        transport.always("{\"id\":\"rec_1\"}");

        client.updateSpeaker("rec_1", "SPEAKER_00", "Иван", "operator");

        Request request = transport.lastRequest();
        assertEquals("PATCH", request.method());
        assertEquals("/v1/recordings/rec_1/speakers/SPEAKER_00", request.uri().getPath());
        assertEquals("{\"display_name\":\"Иван\",\"role\":\"operator\"}", request.bodyText());

        transport.always("{}");
        client.updateSpeaker("rec_1", "SPEAKER_00", "Иван");
        assertEquals("{\"display_name\":\"Иван\"}", transport.lastRequest().bodyText());
    }

    @Test
    void deletes_and_downloads_a_recording() {
        transport.always("");
        client.deleteRecording("rec_1");
        assertEquals("DELETE", transport.lastRequest().method());

        transport.alwaysBytes(new byte[] {5});
        assertArrayEquals(new byte[] {5}, client.downloadRecordingAudio("rec_1"));
        assertEquals("/v1/recordings/rec_1/audio", transport.lastRequest().uri().getPath());
    }

    @Test
    void exports_a_recording_with_a_default_format() {
        transport.alwaysBytes("%PDF".getBytes(StandardCharsets.UTF_8));

        byte[] pdf = client.exportRecording("rec_1", "pdf");

        assertEquals("%PDF", new String(pdf, StandardCharsets.UTF_8));
        assertEquals("format=pdf", transport.lastQuery());

        transport.alwaysBytes(new byte[0]);
        client.exportRecording("rec_1", null);
        assertEquals("format=txt", transport.lastQuery());
    }

    @Test
    void manages_share_links() {
        transport.always("{\"token\":\"tok_1\"}");
        assertEquals("tok_1", client.createShare("rec_1", new ShareOptions()
            .expiresInSeconds(60)
            .password("pw")).getString("token"));
        assertEquals("/v1/recordings/rec_1/share", transport.lastRequest().uri().getPath());
        assertEquals("{\"expires_in_seconds\":60,\"password\":\"pw\"}", transport.lastRequest().bodyText());

        transport.always("{}");
        client.createShare("rec_1");
        assertEquals("{}", transport.lastRequest().bodyText());

        transport.always("[{\"token\":\"tok_1\"}]");
        assertEquals(1, client.shares("rec_1").size());

        transport.always("");
        client.revokeShare("rec_1", "tok_1");
        assertEquals("DELETE", transport.lastRequest().method());
        assertEquals("/v1/recordings/rec_1/share/tok_1", transport.lastRequest().uri().getPath());
    }

    // ────────────────────────────── call QA ───────────────────────────────

    @Test
    void scores_a_recording_against_a_checklist() {
        transport.always("{\"score\":0.8}");

        Map<String, Object> greeting = new java.util.LinkedHashMap<>();
        greeting.put("id", "greeting");
        greeting.put("kind", "required");
        greeting.put("weight", 1.0);

        Result report = client.qaEvaluate("rec_1", new QaEvaluateOptions()
            .checklist(List.of(greeting))
            .webhookUrl("https://hook.test/qa"));

        assertEquals(0.8d, report.getDouble("score"));
        assertEquals("/v1/qa/evaluate", transport.lastRequest().uri().getPath());

        Map<String, Object> body = transport.lastBody();
        assertEquals("rec_1", body.get("recording_id"));
        assertEquals("https://hook.test/qa", body.get("webhook_url"));
        assertEquals(1, ((List<?>) body.get("checklist")).size());
    }

    @Test
    void reads_qa_analytics_and_evaluations_with_defaults() {
        transport.always("{\"days\":30}");
        client.qaAnalytics(0);
        assertEquals("days=30", transport.lastQuery());

        transport.always("{\"days\":7}");
        client.qaAnalytics(7);
        assertEquals("days=7", transport.lastQuery());

        transport.always("{\"items\":[]}");
        client.qaEvaluations(0, 0);
        assertEquals("limit=20&offset=0", transport.lastQuery());
    }

    @Test
    void exports_qa_evaluations() {
        transport.alwaysBytes("score,operator\n".getBytes(StandardCharsets.UTF_8));

        String csv = client.qaExport(null, 0);

        assertTrue(csv.startsWith("score"));
        assertEquals("format=csv&days=30", transport.lastQuery());
        assertEquals("/v1/qa/evaluations/export", transport.lastRequest().uri().getPath());
    }

    // ──────────────────────── search & meeting notes ──────────────────────

    @Test
    void searches_the_recording_library() {
        transport.always("{\"hits\":[]}");

        client.search("почему клиент отказался?", new SearchOptions()
            .limit(5)
            .keywords("дорого")
            .source("link")
            .speaker("SPEAKER_00")
            .from("2026-01-01T00:00:00Z")
            .to("2026-02-01T00:00:00Z")
            .minDuration(30)
            .maxDuration(600));

        Map<String, Object> body = transport.lastBody();
        assertEquals("почему клиент отказался?", body.get("query"));
        assertEquals(5L, body.get("limit"));
        assertEquals("дорого", body.get("keywords"));
        assertEquals("SPEAKER_00", body.get("speaker"));
        assertEquals(30.0d, body.get("min_duration_seconds"));
        assertEquals(600.0d, body.get("max_duration_seconds"));
        assertEquals("/v1/search", transport.lastRequest().uri().getPath());
    }

    @Test
    void asks_a_question_over_the_library() {
        transport.always("{\"answer\":\"потому что дорого\"}");

        assertEquals("потому что дорого", client.ask("почему?").getString("answer"));
        assertEquals("/v1/ask", transport.lastRequest().uri().getPath());
    }

    @Test
    void generates_a_meeting_protocol() {
        transport.always("{\"protocol\":\"…\"}");

        client.meetingProtocol("rec_1", "standup");

        assertEquals("/v1/meetings/protocol", transport.lastRequest().uri().getPath());
        assertEquals("standup", transport.lastBody().get("template"));

        transport.always("{}");
        client.meetingProtocol("rec_1");
        assertEquals("custom", transport.lastBody().get("template"));
    }

    // ────────────────────────────── batches ───────────────────────────────

    @Test
    void queues_batches() {
        transport.always("{\"batch_id\":\"b1\"}");

        Result batch = client.batchSynthesize(List.of(Map.of("text", "Первый текст", "voice", "preset_anna")));

        assertEquals("b1", batch.getString("batch_id"));
        assertEquals("/v1/batch/synthesize", transport.lastRequest().uri().getPath());
        assertEquals(1, ((List<?>) transport.lastBody().get("items")).size());

        transport.always("{\"batch_id\":\"b2\"}");
        client.batchAnalyze(List.of(Map.of("audio", FilePart.ofBytes(new byte[] {1}).base64())));

        assertEquals("/v1/batch/analyze", transport.lastRequest().uri().getPath());

        transport.always("{\"status\":\"completed\"}");
        assertEquals("completed", client.batch("b1").getString("status"));
        assertEquals("/v1/batch/b1", transport.lastRequest().uri().getPath());
    }

    // ────────────────────────────── account ───────────────────────────────

    @Test
    void reads_usage_and_the_balance() {
        transport.always("{\"characters_used\":100}");
        assertEquals(100, client.usage().getInt("characters_used"));
        assertEquals("/v1/usage", transport.lastRequest().uri().getPath());

        transport.always("{\"balance\":10}");
        assertEquals(10, client.billingBalance().getInt("balance"));
        assertEquals("/v1/billing/balance", transport.lastRequest().uri().getPath());
    }

    // ─────────────────────── low-level escape hatches ─────────────────────

    @Test
    void allows_calling_endpoints_the_sdk_does_not_wrap() {
        transport.always("{\"ok\":true}");

        assertTrue(client.get("/v1/custom").getBoolean("ok"));
        assertEquals("GET", transport.lastRequest().method());

        transport.always("{\"ok\":true}");
        client.post("/v1/custom", Map.of("a", 1));
        assertEquals("POST", transport.lastRequest().method());
        assertEquals(1L, transport.lastBody().get("a"));

        transport.always("{\"ok\":true}");
        client.request("PUT", "/v1/custom", Map.of("a", 2));
        assertEquals("PUT", transport.lastRequest().method());

        transport.always("{\"ok\":true}");
        client.patch("/v1/custom", Map.of("a", 3));
        assertEquals("PATCH", transport.lastRequest().method());

        transport.always("");
        client.delete("/v1/custom");
        assertEquals("DELETE", transport.lastRequest().method());
    }

    @Test
    void supports_a_path_with_an_inline_query_string() {
        transport.always("{}");

        client.request("GET", "/v1/custom?flag=1");

        assertEquals("flag=1", transport.lastQuery());
    }

    @Test
    void uploads_files_through_the_low_level_helper() {
        transport.always("{\"ok\":true}");

        client.upload("/v1/vad", Map.of("note", "x"), List.of(FilePart.ofBytes(new byte[] {1}, "a.wav")), null);

        String body = transport.lastRequest().bodyText();
        assertTrue(body.contains("name=\"audio\"; filename=\"a.wav\""));
        assertTrue(body.contains("name=\"note\"\r\n\r\nx"));
    }

    @Test
    void lists_objects_and_strings_from_arbitrary_paths() {
        transport.always("[{\"id\":1}]");
        assertEquals(1, client.getObjects("/v1/custom").size());

        transport.always("{\"values\":[\"a\"]}");
        assertEquals(List.of("a"), client.getStrings("/v1/custom"));

        transport.always("{\"unknown\":1}");
        assertTrue(client.getObjects("/v1/custom").isEmpty());
    }

    @Test
    void rejects_a_response_that_cannot_be_decoded() {
        transport.always("\"just a string\"");

        assertThrows(VoiceKitException.class, () -> client.usage());
    }

    @Test
    void reports_malformed_json_from_the_api() {
        transport.always("{\"broken\":");

        VoiceKitException error = assertThrows(VoiceKitException.class, client::usage);

        assertTrue(error.getMessage().contains("malformed JSON"));
    }

    @Test
    void downloads_and_decodes_text_responses() {
        transport.alwaysBytes("hello".getBytes(StandardCharsets.UTF_8));

        assertEquals("hello", client.downloadText("/v1/raw"));
    }

    @Test
    void opens_a_stream_for_any_endpoint() throws Exception {
        transport.alwaysBytes("chunk".getBytes(StandardCharsets.UTF_8));

        InputStream stream = client.openStream("GET", "/v1/raw", null);

        assertEquals("chunk", new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    }
}
