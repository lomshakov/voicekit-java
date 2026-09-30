package io.github.lomshakov.voicekit;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The VoiceKit API client: Russian text-to-speech, speech-to-text with
 * diarization and timestamps, voice cloning, voice biometrics, audio/video
 * effects, call QA, semantic search and batch jobs.
 *
 * <pre>{@code
 * VoiceKitClient client = new VoiceKitClient("rtt_…");
 *
 * byte[] audio = client.synthesize("Привет! Это синтез русской речи.",
 *     new SynthesizeOptions().voice("preset_anna").format("mp3"));
 *
 * Files.write(Path.of("speech.mp3"), audio);
 * }</pre>
 *
 * <p>A client is safe to use from several threads and worth reusing: it holds a
 * connection-pooling HTTP client.
 */
public final class VoiceKitClient {

    /** The SDK release version (also sent as part of the {@code User-Agent}). */
    public static final String VERSION = "0.4.0";

    /** The public VoiceKit endpoint. */
    public static final String DEFAULT_BASE_URL = "https://ttsapi.ru";

    /** The default per-request timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private static final String USER_AGENT = "voicekit-java/" + VERSION;

    /** Response keys that may wrap a list of objects. */
    private static final List<String> LIST_KEYS =
        List.of("items", "values", "data", "voices", "profiles", "recordings");

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final Transport transport;
    private final Map<String, String> headers;

    /**
     * Creates a client for the public endpoint.
     *
     * @param apiKey the API key; required
     * @throws IllegalArgumentException when the key is blank
     */
    public VoiceKitClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Creates a client.
     *
     * @param apiKey the API key; required
     * @param baseUrl the API endpoint; {@code null} keeps {@link #DEFAULT_BASE_URL}
     * @throws IllegalArgumentException when the key is blank
     */
    public VoiceKitClient(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, DEFAULT_TIMEOUT, null, null);
    }

    /**
     * Creates a client.
     *
     * @param apiKey the API key; required
     * @param baseUrl the API endpoint; {@code null} keeps {@link #DEFAULT_BASE_URL}
     * @param timeout the per-request timeout; {@code null} keeps
     *     {@link #DEFAULT_TIMEOUT}
     * @throws IllegalArgumentException when the key is blank
     */
    public VoiceKitClient(String apiKey, String baseUrl, Duration timeout) {
        this(apiKey, baseUrl, timeout, null, null);
    }

    /**
     * Creates a client with a custom transport (proxy, mock server, tracing).
     *
     * @param apiKey the API key; required
     * @param baseUrl the API endpoint; {@code null} keeps {@link #DEFAULT_BASE_URL}
     * @param timeout the per-request timeout; {@code null} keeps
     *     {@link #DEFAULT_TIMEOUT}
     * @param transport the transport; {@code null} builds an {@link HttpTransport}
     * @throws IllegalArgumentException when the key is blank
     */
    public VoiceKitClient(String apiKey, String baseUrl, Duration timeout, Transport transport) {
        this(apiKey, baseUrl, timeout, transport, null);
    }

    /**
     * Creates a client.
     *
     * @param apiKey the API key; required
     * @param baseUrl the API endpoint; {@code null} keeps {@link #DEFAULT_BASE_URL}
     * @param timeout the per-request timeout; {@code null} keeps
     *     {@link #DEFAULT_TIMEOUT}
     * @param transport the transport; {@code null} builds an {@link HttpTransport}
     * @param headers extra headers sent with every request; {@code null} for none
     * @throws IllegalArgumentException when the key is blank
     */
    public VoiceKitClient(
        String apiKey,
        String baseUrl,
        Duration timeout,
        Transport transport,
        Map<String, String> headers
    ) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("VoiceKit: the apiKey is required.");
        }

        this.apiKey = apiKey.trim();
        this.baseUrl = trimTrailingSlashes(
            baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim());
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.transport = transport == null ? new HttpTransport(this.timeout) : transport;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /**
     * Reads the key from {@code VOICEKIT_API_KEY}.
     *
     * @throws IllegalArgumentException when the variable is missing or blank
     */
    public static VoiceKitClient fromEnvironment() {
        return fromEnvironment("VOICEKIT_API_KEY");
    }

    /**
     * Reads the key from an environment variable.
     *
     * @param variable the variable name; blank falls back to
     *     {@code VOICEKIT_API_KEY}
     * @throws IllegalArgumentException when the variable is missing or blank
     */
    public static VoiceKitClient fromEnvironment(String variable) {
        String name = variable == null || variable.isBlank() ? "VOICEKIT_API_KEY" : variable;
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("VoiceKit: the environment variable " + name + " is not set.");
        }

        return new VoiceKitClient(value);
    }

    /** The configured API endpoint. */
    public String baseUrl() {
        return baseUrl;
    }

    /** The configured per-request timeout. */
    public Duration timeout() {
        return timeout;
    }

    /** The transport performing the calls. */
    public Transport transport() {
        return transport;
    }

    // ──────────────────────────── synthesis ───────────────────────────────

    /**
     * Converts text to speech.
     *
     * @param text the text to synthesize
     * @return the raw audio bytes
     */
    public byte[] synthesize(String text) {
        return synthesize(text, null);
    }

    /**
     * Converts text to speech.
     *
     * @param text the text to synthesize
     * @param options the synthesis options, or {@code null} for the defaults
     * @return the raw audio bytes
     */
    public byte[] synthesize(String text, SynthesizeOptions options) {
        SynthesizeOptions resolved = options == null ? new SynthesizeOptions() : options;

        return sendBinary("POST", "/v1/synthesize", null, resolved.body(text));
    }

    /**
     * Starts a streaming synthesis (Pro/Business); audio chunks arrive as the
     * engine produces them. The caller must close the stream.
     *
     * @param text the text to synthesize
     * @return the audio stream
     */
    public InputStream synthesizeStream(String text) {
        return synthesizeStream(text, null);
    }

    /**
     * Starts a streaming synthesis (Pro/Business); audio chunks arrive as the
     * engine produces them. The caller must close the stream.
     *
     * @param text the text to synthesize
     * @param options the synthesis options, or {@code null} for the defaults
     * @return the audio stream
     */
    public InputStream synthesizeStream(String text, SynthesizeOptions options) {
        SynthesizeOptions resolved = options == null ? new SynthesizeOptions() : options;

        return openStream("POST", "/v1/synthesize/stream", null, resolved.body(text), "*/*");
    }

    /**
     * Queues a long-form synthesis job. Poll it with
     * {@link #synthesisJob(String)} and fetch the audio with
     * {@link #downloadSynthesisAudio(String)}.
     *
     * @param text the text to synthesize
     * @param options the job options, or {@code null} for the defaults
     */
    public Result synthesizeAsync(String text, SynthesizeAsyncOptions options) {
        Map<String, Object> query = new LinkedHashMap<>();
        if (options != null) {
            Payload.putString(query, "webhookUrl", options.webhookUrl());
        }

        return sendJson("POST", "/v1/synthesize/async", query, options == null
            ? new SynthesizeAsyncOptions().body(text)
            : options.body(text));
    }

    /**
     * Queues a long-form synthesis job.
     *
     * @param text the text to synthesize
     */
    public Result synthesizeAsync(String text) {
        return synthesizeAsync(text, null);
    }

    /**
     * Polls a long-form synthesis job and returns its manifest.
     *
     * @param jobId the job id
     */
    public Result synthesisJob(String jobId) {
        return get("/v1/synthesize/async/" + jobId);
    }

    /**
     * Downloads the WAV produced by a completed long-form job.
     *
     * @param jobId the job id
     */
    public byte[] downloadSynthesisAudio(String jobId) {
        return download("/v1/synthesize/async/" + jobId + "/audio");
    }

    // ───────────────────────────── voices ─────────────────────────────────

    /** The preset voice catalog. */
    public List<Result> voices() {
        return objects("/v1/voices");
    }

    /**
     * One voice by id (e.g. {@code "preset_anna"}).
     *
     * @param voiceId the voice id
     */
    public Result voice(String voiceId) {
        return get("/v1/voices/" + voiceId);
    }

    /**
     * Creates a cloned voice from reference audio (Pro/Business).
     *
     * @param name the voice name
     * @param promptText the exact transcript of the reference clip
     * @param samples one or more reference files
     */
    public Result createCloneVoice(String name, String promptText, List<FilePart> samples) {
        return createCloneVoice(name, promptText, samples, null);
    }

    /**
     * Creates a cloned voice from reference audio (Pro/Business).
     *
     * @param name the voice name
     * @param promptText the exact transcript of the reference clip
     * @param samples one or more reference files
     * @param language the reference language, or {@code null}
     */
    public Result createCloneVoice(String name, String promptText, List<FilePart> samples, String language) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("VoiceKit: at least one reference sample is required.");
        }

        List<FileField> files = new ArrayList<>(samples.size());
        for (FilePart sample : samples) {
            files.add(new FileField("samples", sample));
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("name", name == null ? "" : name);
        fields.put("prompt_text", promptText == null ? "" : promptText);
        Payload.putField(fields, "language", language);

        return multipart("/v1/voices/clone", files, fields, null);
    }

    /** The caller's cloned voices. */
    public List<Result> cloneVoices() {
        return objects("/v1/voices/clone");
    }

    /**
     * One cloned voice by id.
     *
     * @param cloneId the cloned voice id
     */
    public Result cloneVoice(String cloneId) {
        return get("/v1/voices/clone/" + cloneId);
    }

    /**
     * Deletes a cloned voice and its reference latents.
     *
     * @param cloneId the cloned voice id
     */
    public void deleteCloneVoice(String cloneId) {
        delete("/v1/voices/clone/" + cloneId);
    }

    // ─────────────────────────── transcription ────────────────────────────

    /**
     * Starts an asynchronous transcription job; poll it with
     * {@link #transcriptionJob(String)}.
     *
     * @param audio the audio file
     */
    public Result transcribe(FilePart audio) {
        return transcribe(audio, null);
    }

    /**
     * Starts an asynchronous transcription job; poll it with
     * {@link #transcriptionJob(String)}.
     *
     * @param audio the audio file
     * @param options the job options, or {@code null} for the defaults
     */
    public Result transcribe(FilePart audio, TranscribeOptions options) {
        return multipart(
            "/v1/transcribe",
            List.of(new FileField("audio", audio)),
            options == null ? Map.of() : options.fields(),
            null);
    }

    /**
     * Transcribes a short file (up to 3 minutes) synchronously.
     *
     * @param audio the audio file
     */
    public Result transcribeSync(FilePart audio) {
        return transcribeSync(audio, null);
    }

    /**
     * Transcribes a short file (up to 3 minutes) synchronously.
     *
     * @param audio the audio file
     * @param options the options, or {@code null} for the defaults
     */
    public Result transcribeSync(FilePart audio, TranscribeSyncOptions options) {
        return multipart(
            "/v1/transcribe/sync",
            List.of(new FileField("audio", audio)),
            options == null ? Map.of() : options.fields(),
            null);
    }

    /**
     * Polls a transcription job.
     *
     * @param jobId the job id
     */
    public Result transcriptionJob(String jobId) {
        return get("/v1/transcribe/" + jobId);
    }

    /**
     * Downloads VTT captions for a completed transcription job.
     *
     * @param jobId the job id
     */
    public String subtitles(String jobId) {
        return subtitles(jobId, null);
    }

    /**
     * Downloads VTT/SRT captions for a completed transcription job.
     *
     * @param jobId the job id
     * @param options the caption options, or {@code null} for the defaults
     */
    public String subtitles(String jobId, SubtitleOptions options) {
        return downloadText("/v1/transcribe/" + jobId + "/subtitles",
            options == null ? null : options.query());
    }

    /**
     * Translates a completed transcript, preserving timestamps and speakers.
     * Billed against the plan's LLM token budget.
     *
     * @param jobId the transcription job id
     * @param targetLanguage the target language, e.g. {@code "en"}
     */
    public Result translateTranscript(String jobId, String targetLanguage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("target_language", targetLanguage);

        return post("/v1/transcribe/" + jobId + "/translate", body);
    }

    /**
     * Detects speech segments in an audio file (Silero VAD).
     *
     * @param audio the audio file
     */
    public Result vad(FilePart audio) {
        return multipart("/v1/vad", List.of(new FileField("audio", audio)), Map.of(), null);
    }

    // ───────────────────────────── analysis ───────────────────────────────

    /**
     * Starts an asynchronous analysis job (emotions, keywords, entities,
     * optionally per speaker); poll it with {@link #analysisJob(String)}.
     *
     * @param audio the audio file
     */
    public Result analyze(FilePart audio) {
        return analyze(audio, null);
    }

    /**
     * Starts an asynchronous analysis job.
     *
     * @param audio the audio file
     * @param options the job options, or {@code null} for the defaults
     */
    public Result analyze(FilePart audio, AnalyzeOptions options) {
        return multipart(
            "/v1/analyze",
            List.of(new FileField("audio", audio)),
            options == null ? Map.of() : options.fields(),
            null);
    }

    /**
     * Analyses a short file (up to 3 minutes) synchronously.
     *
     * @param audio the audio file
     */
    public Result analyzeSync(FilePart audio) {
        return analyzeSync(audio, null);
    }

    /**
     * Analyses a short file (up to 3 minutes) synchronously.
     *
     * @param audio the audio file
     * @param options the options, or {@code null} for the defaults
     */
    public Result analyzeSync(FilePart audio, AnalyzeSyncOptions options) {
        return multipart(
            "/v1/analyze/sync",
            List.of(new FileField("audio", audio)),
            options == null ? Map.of() : options.fields(),
            null);
    }

    /**
     * Polls an analysis job.
     *
     * @param jobId the job id
     */
    public Result analysisJob(String jobId) {
        return get("/v1/analyze/" + jobId);
    }

    // ────────────────────────── text intelligence ─────────────────────────

    /**
     * Identifies the language of a raw text.
     *
     * @param text the text
     */
    public Result detectLanguage(String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", text);

        return post("/v1/detect-language", body);
    }

    /**
     * Masks PII (names, phones, addresses, card numbers) in raw text.
     *
     * @param text the text
     */
    public Result redact(String text) {
        return redact(text, null);
    }

    /**
     * Masks PII (names, phones, addresses, card numbers) in raw text.
     *
     * @param text the text
     * @param language the language hint, or {@code null}
     */
    public Result redact(String text, String language) {
        return post("/v1/redact", textBody(text, language));
    }

    /**
     * Extracts key topics from raw text.
     *
     * @param text the text
     */
    public Result topics(String text) {
        return topics(text, null);
    }

    /**
     * Extracts key topics from raw text.
     *
     * @param text the text
     * @param language the language hint, or {@code null}
     */
    public Result topics(String text, String language) {
        return post("/v1/analyze/topics", textBody(text, language));
    }

    /**
     * Condenses raw text into a short summary.
     *
     * @param text the text
     */
    public Result summarize(String text) {
        return summarize(text, null);
    }

    /**
     * Condenses raw text into a short summary.
     *
     * @param text the text
     * @param options the summary options, or {@code null} for the defaults
     */
    public Result summarize(String text, SummarizeOptions options) {
        return post("/v1/analyze/summarize",
            options == null ? new SummarizeOptions().body(text) : options.body(text));
    }

    /**
     * Flags profanity, insults and hate speech in raw text.
     *
     * @param text the text
     */
    public Result moderate(String text) {
        return moderate(text, null);
    }

    /**
     * Flags profanity, insults and hate speech in raw text.
     *
     * @param text the text
     * @param language the language hint, or {@code null}
     */
    public Result moderate(String text, String language) {
        return post("/v1/moderate", textBody(text, language));
    }

    /**
     * Measures speech quality (word error rate) against a reference text.
     *
     * @param audio the audio file
     * @param reference the expected transcript
     */
    public Result evaluate(FilePart audio, String reference) {
        return evaluate(audio, reference, null);
    }

    /**
     * Measures speech quality (word error rate) against a reference text.
     *
     * @param audio the audio file
     * @param reference the expected transcript
     * @param options the evaluation options, or {@code null} for the defaults
     */
    public Result evaluate(FilePart audio, String reference, EvaluateOptions options) {
        return multipart(
            "/v1/eval",
            List.of(new FileField("audio", audio)),
            options == null ? new EvaluateOptions().fields(reference) : options.fields(reference),
            null);
    }

    // ───────────────────────────── effects ────────────────────────────────

    /**
     * Starts an asynchronous audio-effects job (Free/Basic/Pro/Business).
     *
     * @param audio the audio file
     */
    public Result applyAudioEffects(FilePart audio) {
        return applyAudioEffects(audio, null);
    }

    /**
     * Starts an asynchronous audio-effects job.
     *
     * @param audio the audio file
     * @param options the effect chain and output options, or {@code null} to
     *     pass the audio through unchanged
     */
    public Result applyAudioEffects(FilePart audio, AudioEffectsOptions options) {
        return multipart(
            "/v1/audio/effects",
            List.of(new FileField("audio", audio)),
            options == null ? new AudioEffectsOptions().fields() : options.fields(),
            null);
    }

    /**
     * Polls an audio-effects job.
     *
     * @param jobId the job id
     */
    public Result audioEffectsJob(String jobId) {
        return get("/v1/audio/effects/" + jobId);
    }

    /**
     * Downloads the processed audio of a completed job.
     *
     * @param jobId the job id
     */
    public byte[] downloadAudioEffects(String jobId) {
        return download("/v1/audio/effects/" + jobId + "/audio");
    }

    /**
     * Starts an asynchronous video-effects job.
     *
     * @param video the video file
     */
    public Result applyVideoEffects(FilePart video) {
        return applyVideoEffects(video, null);
    }

    /**
     * Starts an asynchronous video-effects job.
     *
     * @param video the video file
     * @param options the effect chain and output options, or {@code null} to
     *     pass the video through unchanged
     */
    public Result applyVideoEffects(FilePart video, VideoEffectsOptions options) {
        List<FileField> files = new ArrayList<>();
        files.add(new FileField("video", video.withContentType("video/mp4")));

        if (options != null && options.audioTrack() != null) {
            files.add(new FileField("audio", options.audioTrack()));
        }

        return multipart(
            "/v1/video/effects",
            files,
            options == null ? new VideoEffectsOptions().fields() : options.fields(),
            null);
    }

    /**
     * Polls a video-effects job.
     *
     * @param jobId the job id
     */
    public Result videoEffectsJob(String jobId) {
        return get("/v1/video/effects/" + jobId);
    }

    /**
     * Downloads the artifact (video or audio) of a completed job.
     *
     * @param jobId the job id
     */
    public byte[] downloadVideoEffects(String jobId) {
        return download("/v1/video/effects/" + jobId + "/file");
    }

    /**
     * Starts an asynchronous audio-cleaning job with the default preset
     * (denoise + normalise).
     *
     * @param audio the audio file
     */
    public Result cleanAudio(FilePart audio) {
        return cleanAudio(audio, null);
    }

    /**
     * Starts an asynchronous audio-cleaning job.
     *
     * @param audio the audio file
     * @param options the cleaning preset, or {@code null} for denoise + normalise
     */
    public Result cleanAudio(FilePart audio, CleanAudioOptions options) {
        return multipart(
            "/v1/audio/clean",
            List.of(new FileField("audio", audio)),
            options == null ? Map.of() : options.fields(),
            null);
    }

    /**
     * Polls an audio-cleaning job.
     *
     * @param jobId the job id
     */
    public Result audioCleaningJob(String jobId) {
        return get("/v1/audio/clean/" + jobId);
    }

    /**
     * Downloads the cleaned audio of a completed job.
     *
     * @param jobId the job id
     */
    public byte[] downloadAudioCleaning(String jobId) {
        return download("/v1/audio/clean/" + jobId + "/audio");
    }

    // ──────────────────────────── voice id ────────────────────────────────

    /**
     * Returns a voice passport: language, gender, age group, emotional
     * background, a speaker embedding and an AI-vs-human probability.
     *
     * @param audio the audio file
     */
    public Result analyzeVoice(FilePart audio) {
        return multipart("/v1/voice-id", List.of(new FileField("audio", audio)), Map.of(), null);
    }

    /**
     * Stores an audio clip as a reusable voice profile.
     *
     * @param audio the audio file
     * @param name a label for the profile, or {@code null}
     */
    public Result enrollVoice(FilePart audio, String name) {
        Map<String, String> fields = new LinkedHashMap<>();
        Payload.putField(fields, "name", name);

        return multipart("/v1/voice-id/enroll", List.of(new FileField("audio", audio)), fields, null);
    }

    /**
     * Compares a clip against an enrolled profile (1:1).
     *
     * @param audio the audio file
     * @param profileId the enrolled profile id
     */
    public Result verifyVoice(FilePart audio, String profileId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("profile_id", profileId == null ? "" : profileId);

        return multipart("/v1/voice-id/verify", List.of(new FileField("audio", audio)), fields, null);
    }

    /**
     * Finds the closest matching profile (1:N) across every profile of the
     * caller.
     *
     * @param audio the audio file
     */
    public Result identifyVoice(FilePart audio) {
        return identifyVoice(audio, null);
    }

    /**
     * Finds the closest matching profile (1:N).
     *
     * @param audio the audio file
     * @param profileIds the profiles to search, or {@code null}/empty for all
     */
    public Result identifyVoice(FilePart audio, List<String> profileIds) {
        Map<String, String> fields = new LinkedHashMap<>();
        Payload.putField(fields, "profile_ids", Payload.csv(profileIds));

        return multipart("/v1/voice-id/identify", List.of(new FileField("audio", audio)), fields, null);
    }

    /** The caller's enrolled voice profiles. */
    public List<Result> voiceProfiles() {
        return objects("/v1/voice-id/profiles");
    }

    /**
     * Deletes an enrolled voice profile.
     *
     * @param profileId the profile id
     */
    public void deleteVoiceProfile(String profileId) {
        delete("/v1/voice-id/profiles/" + profileId);
    }

    // ──────────────────────────── recordings ──────────────────────────────

    /** The caller's recordings, newest first. */
    public Result recordings() {
        return recordings(null);
    }

    /**
     * The caller's recordings, newest first.
     *
     * @param options the filters, or {@code null}
     */
    public Result recordings(RecordingListOptions options) {
        return sendJson("GET", "/v1/recordings", options == null ? null : options.query(), null);
    }

    /** The distinct tags across the caller's recordings. */
    public List<String> recordingTags() {
        return strings("/v1/recordings/tags");
    }

    /** The distinct folders across the caller's recordings. */
    public List<String> recordingFolders() {
        return strings("/v1/recordings/folders");
    }

    /**
     * Starts a diarized transcription from a public audio URL; the recording is
     * tagged with source {@code link}.
     *
     * @param audioUrl the public audio URL
     */
    public Result recordingFromLink(String audioUrl) {
        return recordingFromLink(audioUrl, null);
    }

    /**
     * Starts a diarized transcription from a public audio URL.
     *
     * @param audioUrl the public audio URL
     * @param language the language hint, or {@code null}
     */
    public Result recordingFromLink(String audioUrl, String language) {
        return post("/v1/recordings/from-link", textBody(audioUrl, language, "url"));
    }

    /**
     * A recording's metadata.
     *
     * @param recordingId the recording id
     */
    public Result recording(String recordingId) {
        return get("/v1/recordings/" + recordingId);
    }

    /**
     * A recording's transcript.
     *
     * @param recordingId the recording id
     */
    public Result recordingTranscript(String recordingId) {
        return get("/v1/recordings/" + recordingId + "/transcript");
    }

    /**
     * A recording's speakers, timeline and metrics.
     *
     * @param recordingId the recording id
     */
    public Result recordingSpeakers(String recordingId) {
        return get("/v1/recordings/" + recordingId + "/speakers");
    }

    /**
     * Updates a recording's tags and/or folder.
     *
     * @param recordingId the recording id
     * @param options the patch to apply
     */
    public Result updateRecording(String recordingId, UpdateRecordingOptions options) {
        return sendJson("PATCH", "/v1/recordings/" + recordingId, null,
            options == null ? Map.of() : options.body());
    }

    /**
     * Replaces a recording's tags and moves it to a folder.
     *
     * @param recordingId the recording id
     * @param tags the new tag set
     * @param folder the new folder
     */
    public Result updateRecording(String recordingId, List<String> tags, String folder) {
        return updateRecording(recordingId, new UpdateRecordingOptions().tags(tags).folder(folder));
    }

    /**
     * Renames a diarized speaker.
     *
     * @param recordingId the recording id
     * @param speakerId the diarization label, e.g. {@code SPEAKER_00}
     * @param displayName the display name
     */
    public Result updateSpeaker(String recordingId, String speakerId, String displayName) {
        return updateSpeaker(recordingId, speakerId, displayName, null);
    }

    /**
     * Renames a diarized speaker and assigns a role
     * ({@code operator}, {@code client}, {@code participant}).
     *
     * @param recordingId the recording id
     * @param speakerId the diarization label, e.g. {@code SPEAKER_00}
     * @param displayName the display name, or {@code null}
     * @param role the role, or {@code null}
     */
    public Result updateSpeaker(String recordingId, String speakerId, String displayName, String role) {
        Map<String, Object> body = new LinkedHashMap<>();
        Payload.putString(body, "display_name", displayName);
        Payload.putString(body, "role", role);

        return sendJson("PATCH",
            "/v1/recordings/" + recordingId + "/speakers/" + speakerId, null, body);
    }

    /**
     * Deletes a recording and its stored audio.
     *
     * @param recordingId the recording id
     */
    public void deleteRecording(String recordingId) {
        delete("/v1/recordings/" + recordingId);
    }

    /**
     * Downloads a recording's stored audio.
     *
     * @param recordingId the recording id
     */
    public byte[] downloadRecordingAudio(String recordingId) {
        return download("/v1/recordings/" + recordingId + "/audio");
    }

    /**
     * Exports a transcript as {@code txt}, {@code md}, {@code srt}, {@code vtt},
     * {@code docx} or {@code pdf}.
     *
     * @param recordingId the recording id
     * @param format the export format; blank means {@code txt}
     */
    public byte[] exportRecording(String recordingId, String format) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("format", format == null || format.isBlank() ? "txt" : format);

        return download("/v1/recordings/" + recordingId + "/export", query);
    }

    // ──────────────────────────── share links ─────────────────────────────

    /**
     * Creates a public share link for a recording.
     *
     * @param recordingId the recording id
     */
    public Result createShare(String recordingId) {
        return createShare(recordingId, null);
    }

    /**
     * Creates a public share link for a recording.
     *
     * @param recordingId the recording id
     * @param options the expiry and password, or {@code null}
     */
    public Result createShare(String recordingId, ShareOptions options) {
        return post("/v1/recordings/" + recordingId + "/share",
            options == null ? Map.of() : options.body());
    }

    /**
     * The active share links of a recording.
     *
     * @param recordingId the recording id
     */
    public List<Result> shares(String recordingId) {
        return objects("/v1/recordings/" + recordingId + "/share");
    }

    /**
     * Revokes a share link.
     *
     * @param recordingId the recording id
     * @param token the share token
     */
    public void revokeShare(String recordingId, String token) {
        delete("/v1/recordings/" + recordingId + "/share/" + token);
    }

    // ─────────────────────────────── call QA ──────────────────────────────

    /**
     * Scores a recording against a QA checklist (Pro/Business).
     *
     * @param recordingId the recording id
     */
    public Result qaEvaluate(String recordingId) {
        return qaEvaluate(recordingId, null);
    }

    /**
     * Scores a recording against a QA checklist (Pro/Business).
     *
     * @param recordingId the recording id
     * @param options the checklist and webhook, or {@code null}
     */
    public Result qaEvaluate(String recordingId, QaEvaluateOptions options) {
        return post("/v1/qa/evaluate", (options == null
            ? new QaEvaluateOptions()
            : options).body(recordingId));
    }

    /**
     * The score trend, top violations and score by operator.
     *
     * @param days the look-back window; {@code <= 0} means 30
     */
    public Result qaAnalytics(int days) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("days", days <= 0 ? 30 : days);

        return sendJson("GET", "/v1/qa/analytics", query, null);
    }

    /**
     * Persisted QA evaluations, newest first.
     *
     * @param limit the page size; {@code <= 0} means 20
     * @param offset the page offset
     */
    public Result qaEvaluations(int limit, int offset) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", limit <= 0 ? 20 : limit);
        query.put("offset", Math.max(offset, 0));

        return sendJson("GET", "/v1/qa/evaluations", query, null);
    }

    /**
     * Exports QA evaluations as CSV or JSON for CRM import.
     *
     * @param format {@code csv} (default) or {@code json}
     * @param days the look-back window; {@code <= 0} means 30
     */
    public String qaExport(String format, int days) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("format", format == null || format.isBlank() ? "csv" : format);
        query.put("days", days <= 0 ? 30 : days);

        return downloadText("/v1/qa/evaluations/export", query);
    }

    // ──────────────────────── search & meeting notes ──────────────────────

    /**
     * Runs a hybrid semantic/full-text search over recordings (Pro/Business).
     *
     * @param query the search query
     */
    public Result search(String query) {
        return search(query, null);
    }

    /**
     * Runs a hybrid semantic/full-text search over recordings (Pro/Business).
     *
     * @param query the search query
     * @param options the filters, or {@code null}
     */
    public Result search(String query, SearchOptions options) {
        return post("/v1/search", options == null
            ? new SearchOptions().body(query)
            : options.body(query));
    }

    /**
     * Answers a question over the recording library (RAG) with verbatim
     * citations.
     *
     * @param query the question
     */
    public Result ask(String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);

        return post("/v1/ask", body);
    }

    /**
     * Generates a meeting protocol from a recording transcript.
     *
     * @param recordingId the recording id
     */
    public Result meetingProtocol(String recordingId) {
        return meetingProtocol(recordingId, null);
    }

    /**
     * Generates a meeting protocol from a recording transcript.
     *
     * @param recordingId the recording id
     * @param template one of {@code custom}, {@code standup}, {@code demo},
     *     {@code interview}, {@code retro}, {@code one_on_one}; blank means
     *     {@code custom}
     */
    public Result meetingProtocol(String recordingId, String template) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recording_id", recordingId);
        body.put("template", template == null || template.isBlank() ? "custom" : template);

        return post("/v1/meetings/protocol", body);
    }

    // ──────────────────────────── batches ─────────────────────────────────

    /**
     * Queues a batch of synthesis requests; each item is a
     * {@code /v1/synthesize} body.
     *
     * @param items the synthesis items
     */
    public Result batchSynthesize(List<Map<String, Object>> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);

        return post("/v1/batch/synthesize", body);
    }

    /**
     * Queues a batch of analysis requests; each item needs an inline base64
     * {@code audio} field (see {@link FilePart#base64()}).
     *
     * @param items the analysis items
     */
    public Result batchAnalyze(List<Map<String, Object>> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);

        return post("/v1/batch/analyze", body);
    }

    /**
     * Polls a batch job.
     *
     * @param batchId the batch id
     */
    public Result batch(String batchId) {
        return get("/v1/batch/" + batchId);
    }

    // ──────────────────────────── account ─────────────────────────────────

    /** The current monthly usage for the authenticated key. */
    public Result usage() {
        return get("/v1/usage");
    }

    /** The current balance, plan and recent transactions. */
    public Result billingBalance() {
        return get("/v1/billing/balance");
    }

    // ────────────────────────── WebSocket sessions ────────────────────────

    /**
     * Opens {@code WS /v1/transcribe/stream} (Pro/Business).
     *
     * @param options the session options, or {@code null}
     */
    public StreamSession transcribeStream(StreamOptions options) {
        return StreamSession.open(
            webSocketUri("/v1/transcribe/stream", options == null ? null : options.query()),
            webSocketHeaders(),
            timeout);
    }

    /** Opens {@code WS /v1/transcribe/stream} (Pro/Business). */
    public StreamSession transcribeStream() {
        return transcribeStream(null);
    }

    /**
     * Opens {@code WS /v1/vad/stream} for speech turn detection without
     * recognition.
     */
    public StreamSession vadStream() {
        return StreamSession.open(webSocketUri("/v1/vad/stream", null), webSocketHeaders(), timeout);
    }

    // ─────────────────────── low-level escape hatches ─────────────────────

    /**
     * Calls any endpoint and decodes the JSON object it returns.
     *
     * @param method the HTTP method
     * @param path the path (a query string may be included)
     */
    public Result request(String method, String path) {
        return sendJson(method, path, null, null);
    }

    /**
     * Calls any endpoint with a JSON body and decodes the JSON object it
     * returns.
     *
     * @param method the HTTP method
     * @param path the path (a query string may be included)
     * @param body the request body, or {@code null}
     */
    public Result request(String method, String path, Map<String, Object> body) {
        return sendJson(method, path, null, body);
    }

    /**
     * Calls {@code GET} and decodes the JSON object it returns.
     *
     * @param path the path
     */
    public Result get(String path) {
        return sendJson("GET", path, null, null);
    }

    /**
     * Calls {@code POST} with a JSON body and decodes the JSON object it
     * returns.
     *
     * @param path the path
     * @param body the request body
     */
    public Result post(String path, Map<String, Object> body) {
        return sendJson("POST", path, null, body);
    }

    /**
     * Calls {@code PATCH} with a JSON body and decodes the JSON object it
     * returns.
     *
     * @param path the path
     * @param body the request body
     */
    public Result patch(String path, Map<String, Object> body) {
        return sendJson("PATCH", path, null, body);
    }

    /**
     * Calls {@code DELETE} and decodes the JSON object it returns (empty when
     * the endpoint returns no body).
     *
     * @param path the path
     */
    public Result delete(String path) {
        return sendJson("DELETE", path, null, null);
    }

    /**
     * Uploads a multipart/form-data request and decodes the JSON object it
     * returns.
     *
     * @param path the path
     * @param fields the text fields
     * @param files the files, each with the field it is sent under
     * @param query the query parameters, or {@code null}
     */
    public Result upload(
        String path,
        Map<String, String> fields,
        List<FilePart> files,
        Map<String, Object> query
    ) {
        List<FileField> parts = new ArrayList<>();
        for (FilePart file : files) {
            parts.add(new FileField("audio", file));
        }

        return multipart(path, parts, fields, query);
    }

    /**
     * Calls any endpoint and decodes a list of objects from the response —
     * either a bare JSON array or one wrapped under a well-known key.
     *
     * @param path the path
     */
    public List<Result> getObjects(String path) {
        return objects(path);
    }

    /**
     * Calls any endpoint and decodes a list of strings from the response.
     *
     * @param path the path
     */
    public List<String> getStrings(String path) {
        return strings(path);
    }

    /**
     * Downloads a binary payload (or a text payload as raw bytes).
     *
     * @param path the path
     */
    public byte[] download(String path) {
        return download(path, null);
    }

    /**
     * Downloads a binary payload and decodes a text payload as UTF-8.
     *
     * @param path the path
     */
    public String downloadText(String path) {
        return new String(download(path, null), StandardCharsets.UTF_8);
    }

    /**
     * Opens a streaming response without buffering it — used by streaming
     * synthesis and available for any endpoint that streams.
     *
     * @param method the HTTP method
     * @param path the path
     * @param body the request body, or {@code null}
     */
    public InputStream openStream(String method, String path, Map<String, Object> body) {
        return openStream(method, path, null, body, null);
    }

    // ────────────────────────────── internals ─────────────────────────────

    private static String trimTrailingSlashes(String value) {
        String trimmed = value;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        return trimmed;
    }

    private static Map<String, Object> textBody(String text, String language) {
        return textBody(text, language, "text");
    }

    private static Map<String, Object> textBody(String text, String language, String field) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(field, text);
        Payload.putString(body, "language", language);

        return body;
    }

    private List<Result> objects(String path) {
        return decodeObjects(send("GET", path, null, null, null));
    }

    private List<String> strings(String path) {
        return decodeStrings(send("GET", path, null, null, null));
    }

    private byte[] download(String path, Map<String, Object> query) {
        return send("GET", path, query, null, null).bytes();
    }

    private String downloadText(String path, Map<String, Object> query) {
        return new String(download(path, query), StandardCharsets.UTF_8);
    }

    /** Performs a request from a JSON body and decodes the JSON object answer. */
    private Result sendJson(String method, String path, Map<String, ?> query, Object body) {
        byte[] payload = body == null ? null : Json.write(body).getBytes(StandardCharsets.UTF_8);

        try (Response response = send(method, path, query, payload == null ? null : "application/json", payload)) {
            String text = response.text();
            if (!response.isSuccessful()) {
                throw VoiceKitException.fromResponse(response.statusCode(), text);
            }

            return Result.fromJson(text);
        }
    }

    /** Performs a request from a JSON body and returns the raw answer. */
    private byte[] sendBinary(String method, String path, Map<String, ?> query, Object body) {
        byte[] payload = Json.write(body).getBytes(StandardCharsets.UTF_8);

        try (Response response = send(method, path, query, "application/json", payload)) {
            byte[] answer = response.bytes();
            if (!response.isSuccessful()) {
                throw VoiceKitException.fromResponse(
                    response.statusCode(), new String(answer, StandardCharsets.UTF_8));
            }

            return answer;
        }
    }

    /** Uploads a multipart/form-data request and decodes the JSON object answer. */
    private Result multipart(
        String path,
        List<FileField> files,
        Map<String, String> fields,
        Map<String, ?> query
    ) {
        Multipart body = Multipart.of(files, fields);

        try (Response response = send("POST", path, query, body.contentType(), body.body())) {
            String text = response.text();
            if (!response.isSuccessful()) {
                throw VoiceKitException.fromResponse(response.statusCode(), text);
            }

            return Result.fromJson(text);
        }
    }

    /** Opens a response whose body is handed to the caller unread. */
    private InputStream openStream(
        String method,
        String path,
        Map<String, ?> query,
        Object body,
        String accept
    ) {
        byte[] payload = body == null ? null : Json.write(body).getBytes(StandardCharsets.UTF_8);
        Response response = send(method, path, query, payload == null ? null : "application/json", accept, payload);

        if (!response.isSuccessful()) {
            String text = response.text();
            throw VoiceKitException.fromResponse(response.statusCode(), text);
        }

        return response.body();
    }

    private Response send(
        String method,
        String path,
        Map<String, ?> query,
        String contentType,
        byte[] body
    ) {
        return send(method, path, query, contentType, null, body);
    }

    private Response send(
        String method,
        String path,
        Map<String, ?> query,
        String contentType,
        String accept,
        byte[] body
    ) {
        Map<String, String> requestHeaders = new LinkedHashMap<>();
        requestHeaders.put("X-Api-Key", apiKey);
        requestHeaders.put("Accept", accept == null ? "application/json" : accept);
        requestHeaders.put("User-Agent", USER_AGENT);

        if (contentType != null) {
            requestHeaders.put("Content-Type", contentType);
        }

        requestHeaders.putAll(headers);

        return transport.send(new Request(method, uri(path, query), requestHeaders, body));
    }

    @SuppressWarnings("unchecked")
    private static List<Result> wrapObjects(List<?> items) {
        List<Result> result = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                result.add(Result.of((Map<String, Object>) map));
            }
        }

        return result;
    }

    private static List<Result> decodeObjects(Response response) {
        String text;

        try {
            text = response.text();
            if (!response.isSuccessful()) {
                throw VoiceKitException.fromResponse(response.statusCode(), text);
            }
        } finally {
            response.close();
        }

        if (Json.isBlank(text)) {
            return List.of();
        }

        Object decoded = parse(text);
        if (decoded instanceof List<?> list) {
            return wrapObjects(list);
        }

        if (decoded instanceof Map<?, ?> map) {
            for (String key : LIST_KEYS) {
                Object value = map.get(key);
                if (value instanceof List<?> list) {
                    return wrapObjects(list);
                }
            }

            return List.of();
        }

        throw new VoiceKitException(
            "VoiceKit: expected a JSON array or object, got: " + abbreviate(text));
    }

    private static List<String> decodeStrings(Response response) {
        String text;

        try {
            text = response.text();
            if (!response.isSuccessful()) {
                throw VoiceKitException.fromResponse(response.statusCode(), text);
            }
        } finally {
            response.close();
        }

        if (Json.isBlank(text)) {
            return List.of();
        }

        Object decoded = parse(text);
        List<?> items = null;

        if (decoded instanceof List<?> list) {
            items = list;
        } else if (decoded instanceof Map<?, ?> map) {
            for (String key : LIST_KEYS) {
                Object value = map.get(key);
                if (value instanceof List<?> list) {
                    items = list;
                    break;
                }
            }
        }

        if (items == null) {
            return List.of();
        }

        List<String> result = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item instanceof String value) {
                result.add(value);
            }
        }

        return result;
    }

    private static Object parse(String json) {
        try {
            return Json.parse(json);
        } catch (IllegalArgumentException exception) {
            throw new VoiceKitException(
                "VoiceKit: the API returned malformed JSON: " + exception.getMessage(), 0, "", exception);
        }
    }

    private static String abbreviate(String text) {
        String trimmed = text == null ? "" : text.trim();

        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "…";
    }

    private URI uri(String path, Map<String, ?> query) {
        String relative = path == null ? "" : path;
        String rawQuery = null;

        int mark = relative.indexOf('?');
        if (mark >= 0) {
            rawQuery = relative.substring(mark + 1);
            relative = relative.substring(0, mark);
        }

        if (!relative.startsWith("/")) {
            relative = "/" + relative;
        }

        String encoded = encodeQuery(query);
        if (rawQuery == null || rawQuery.isEmpty()) {
            rawQuery = encoded.isEmpty() ? null : encoded;
        } else if (!encoded.isEmpty()) {
            rawQuery = rawQuery + "&" + encoded;
        }

        StringBuilder url = new StringBuilder(baseUrl).append(relative);
        if (rawQuery != null) {
            url.append('?').append(rawQuery);
        }

        try {
            return URI.create(url.toString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "VoiceKit: invalid request URL \"" + url + "\": " + exception.getMessage(), exception);
        }
    }

    private URI webSocketUri(String path, Map<String, ?> query) {
        String url = uri(path, query).toString();

        if (url.startsWith("https://")) {
            return URI.create("wss://" + url.substring("https://".length()));
        }
        if (url.startsWith("http://")) {
            return URI.create("ws://" + url.substring("http://".length()));
        }

        return URI.create(url);
    }

    private Map<String, String> webSocketHeaders() {
        Map<String, String> result = new LinkedHashMap<>(headers);
        result.put("X-Api-Key", apiKey);
        result.put("User-Agent", USER_AGENT);

        return result;
    }

    private static String encodeQuery(Map<String, ?> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, ?> entry : query.entrySet()) {
            String value = render(entry.getValue());
            if (value == null || value.isEmpty()) {
                continue;
            }

            if (out.length() > 0) {
                out.append('&');
            }

            out.append(encode(entry.getKey())).append('=').append(encode(value));
        }

        return out.toString();
    }

    private static String render(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Iterable<?> items) {
            StringBuilder joined = new StringBuilder();
            for (Object item : items) {
                if (item == null) {
                    continue;
                }
                if (joined.length() > 0) {
                    joined.append(',');
                }
                joined.append(item);
            }

            return joined.toString();
        }

        return String.valueOf(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
