# VoiceKit — Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lomshakov/voicekit-client.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.lomshakov/voicekit-client)
[![CI](https://github.com/lomshakov/voicekit-java/actions/workflows/ci.yml/badge.svg)](https://github.com/lomshakov/voicekit-java/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)

Official Java client for **[VoiceKit](https://ttsapi.ru)** — the REST API for Russian speech:
neural speech synthesis (TTS), transcription (STT) with diarization and timestamps,
sentiment analysis, voice cloning, voice biometrics, audio/video effects, call QA,
semantic search over recordings and batch operations.

> **Links:** [Website](https://ttsapi.ru) · [Documentation](https://ttsapi.ru/docs) · [API reference](https://ttsapi.ru/swagger) · [Pricing](https://ttsapi.ru/pricing) · [Blog](https://ttsapi.ru/blog)

No runtime dependencies: HTTP rides on the JDK's `java.net.http` client and
streaming sessions use the built-in WebSocket implementation. Requires
**Java 17+** (LTS 17, 21 and 25 are all tested).

## Install

### Maven

```xml
<dependency>
  <groupId>io.github.lomshakov</groupId>
  <artifactId>voicekit-client</artifactId>
  <version>0.4.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.github.lomshakov:voicekit-client:0.4.0")
```

## Quick start

```java
import io.github.lomshakov.voicekit.*;
import java.nio.file.*;

VoiceKitClient client = new VoiceKitClient("rtt_…");   // or VoiceKitClient.fromEnvironment()

// Speech synthesis → raw audio bytes
byte[] audio = client.synthesize("Привет! Это синтез русской речи.",
    new SynthesizeOptions().voice("preset_anna").format("mp3"));

Files.write(Path.of("speech.mp3"), audio);

// Transcription (async → poll)
Result job = client.transcribe(FilePart.ofPath("speech.mp3"),
    new TranscribeOptions().language("ru").diarization(true).keyterms(List.of("диагноз", "препарат")));

Result transcript;
do {
    transcript = client.transcriptionJob(job.getString("job_id"));
    Thread.sleep(1_000);
} while (!List.of("completed", "failed").contains(transcript.getString("status")));

System.out.println(transcript.getString("text"));
```

### Configuration

```java
VoiceKitClient client = new VoiceKitClient(
    "rtt_…",                                              // apiKey — required
    VoiceKitClient.DEFAULT_BASE_URL,                      // https://ttsapi.ru
    VoiceKitClient.DEFAULT_TIMEOUT,                       // 120 s
    new HttpTransport(Duration.ofSeconds(30)),            // optional custom transport
    Map.of("X-Trace", "abc"));                            // optional extra headers
```

`VoiceKitClient.fromEnvironment()` reads `VOICEKIT_API_KEY` (or
`fromEnvironment("MY_KEY")` for another variable). Any `Transport`
implementation can replace the built-in one — a proxy, a mock server or a
tracing wrapper — and `HttpTransport` accepts a caller-managed
`java.net.http.HttpClient`.

### Error handling

Every non-2xx response becomes a `VoiceKitException` carrying the RFC 7807
`code`; transport failures carry a message and status `0`:

```java
try {
    client.synthesizeStream(longText, null);
} catch (VoiceKitException error) {
    if (error.isForbidden()) {
        System.out.println("upgrade your plan: " + error.errorCode()); // streaming_forbidden
    } else if (error.isCode("quota_exceeded")) {
        System.out.println("monthly quota is over");
    } else if (error.isRateLimited()) {
        System.out.println("slow down");
    } else {
        System.out.println(error.getMessage());
    }
}
```

The exception is unchecked, so callers only handle what they care about.
`isUnauthorized()`, `isForbidden()`, `isNotFound()`, `isRateLimited()` and
`isCode(String)` cover the common cases.

## Features

| Area | Methods |
| --- | --- |
| Synthesis | `synthesize`, `synthesizeStream`, `synthesizeAsync`, `synthesisJob`, `downloadSynthesisAudio` |
| Voices | `voices`, `voice`, `createCloneVoice`, `cloneVoices`, `cloneVoice`, `deleteCloneVoice` |
| Transcription | `transcribe`, `transcribeSync`, `transcriptionJob`, `subtitles`, `translateTranscript`, `vad` |
| Analysis | `analyze`, `analyzeSync`, `analysisJob`, `evaluate` |
| Text intelligence | `detectLanguage`, `redact`, `topics`, `summarize`, `moderate` |
| Effects | `applyAudioEffects`, `applyVideoEffects`, `cleanAudio` + job/download helpers |
| Voice ID | `analyzeVoice`, `enrollVoice`, `verifyVoice`, `identifyVoice`, `voiceProfiles`, `deleteVoiceProfile` |
| Recordings | `recordings`, `recording`, `recordingTranscript`, `recordingSpeakers`, `updateRecording`, `updateSpeaker`, `recordingFromLink`, `exportRecording`, `downloadRecordingAudio`, share links |
| Call QA | `qaEvaluate`, `qaAnalytics`, `qaEvaluations`, `qaExport` |
| Search | `search`, `ask`, `meetingProtocol` |
| Batch / account | `batchSynthesize`, `batchAnalyze`, `batch`, `usage`, `billingBalance` |
| Streaming | `transcribeStream`, `vadStream` (WebSocket, Pro/Business) |

### Streaming synthesis (Pro/Business)

```java
try (InputStream stream = client.synthesizeStream(longText, new SynthesizeOptions()
        .voice("preset_anna"))) {
    Files.copy(stream, Path.of("long.mp3"), StandardCopyOption.REPLACE_EXISTING);
}
```

### Audio effects

```java
// Inline during synthesis
byte[] audio = client.synthesize("Привет!", new SynthesizeOptions()
    .voice("preset_anna")
    .effects(List.of(Effects.effect("reverb", "room_size", 0.5))));

// Or as a background job over an existing file
Result job = client.applyAudioEffects(FilePart.ofPath("voice.wav"), new AudioEffectsOptions()
    .effects(Effects.chain(
        Effects.effect("compressor", "ratio", 3),
        Effects.effect("reverb", "room_size", 0.4)))
    .outputFormat("mp3"));

byte[] processed = client.downloadAudioEffects(job.getString("job_id"));
```

### Audio cleaning

```java
Result job = client.cleanAudio(FilePart.ofPath("noisy.wav")); // denoise + normalise

Result custom = client.cleanAudio(FilePart.ofPath("noisy.wav"), new CleanAudioOptions()
    .options(Map.of(
        "denoise", Map.of("strength", 0.8, "stationary", true),
        "normalize", Map.of("target_db", -1.0),
        "high_pass", 80))
    .outputFormat("mp3"));

byte[] clean = client.downloadAudioCleaning(custom.getString("job_id"));
```

### Voice ID (Pro/Business)

```java
// Voice passport: language, gender, age, emotion, speaker embedding, AI-vs-human
Result passport = client.analyzeVoice(FilePart.ofPath("sample.wav"));

// Voice biometrics over your own profiles
Result profile = client.enrollVoice(FilePart.ofPath("speaker.wav"), "Алиса");
Result check = client.verifyVoice(FilePart.ofPath("other.wav"), profile.getString("profile_id"));
Result match = client.identifyVoice(FilePart.ofPath("other.wav")); // 1:N over every profile
```

### Recordings, QA & meeting intelligence (Pro/Business)

```java
Result page = client.recordings(new RecordingListOptions().source("link").limit(10));
Result job = client.recordingFromLink("https://example.com/call.mp3", "ru");
Result speakers = client.recordingSpeakers(recordingId);

client.updateSpeaker(recordingId, "SPEAKER_00", "Иван", "operator");
client.updateRecording(recordingId, List.of("sales", "warm"), "Q3");

byte[] pdf = client.exportRecording(recordingId, "pdf");
byte[] csv = client.qaExport(null, 30);

Result evaluation = client.qaEvaluate(recordingId, new QaEvaluateOptions().checklist(List.of(
    Map.of("id", "greeting", "kind", "required", "description", "Поздоровался", "weight", 1.0))));

Result hits = client.search("почему клиент отказался?", new SearchOptions().limit(5).keywords("дорого"));
Result answer = client.ask("почему клиент отказался от Pro?");
Result protocol = client.meetingProtocol(recordingId, "standup");
```

### WebSocket streaming (Pro/Business)

```java
try (StreamSession session = client.transcribeStream(new StreamOptions()
        .language("ru")
        .keyterms(List.of("диагноз")))) {

    session.sendAudio(pcm16Chunk);   // raw PCM16, 16 kHz mono, little-endian
    session.stop();                  // finalise the utterance

    Result event;
    while ((event = session.receive()) != null) {   // null after a normal close
        System.out.println(event.getString("type") + ": " + event.getString("text"));
    }
}
```

### Batches

```java
Result batch = client.batchSynthesize(List.of(
    Map.of("text", "Первый текст", "voice", "preset_anna"),
    Map.of("text", "Второй текст", "voice", "preset_dmitri")));

Result status = client.batch(batch.getString("batch_id"));

// Analysis batches take inline base64 audio
Result analysis = client.batchAnalyze(List.of(
    Map.of("audio", FilePart.ofPath("call.wav").base64(), "language", "ru")));
```

## Dynamic responses

The API evolves, so JSON answers come back as `Result` — a wrapper with typed
accessors. Nested objects are wrapped too:

```java
Result job = client.transcriptionJob(jobId);

String text = job.getString("text");
double seconds = job.getDouble("duration_seconds");
boolean ready = job.getBoolean("completed");
Result meta = job.getObject("meta");

for (Result segment : job.getList("segments")) {
    System.out.println(segment.getDouble("start") + " " + segment.getString("text"));
}

for (String tag : job.getStrings("tags")) {
    System.out.println(tag);
}

Map<String, Object> raw = job.toMap();   // the untouched payload
```

## Calling endpoints the SDK does not wrap yet

```java
Result response = client.post("/v1/synthesize", Map.of("text", "Привет"));

Result usage = client.get("/v1/usage");
List<Result> voices = client.getObjects("/v1/voices");
List<String> tags = client.getStrings("/v1/recordings/tags");
byte[] audio = client.download("/v1/recordings/rec_1/audio");
Result job = client.upload("/v1/vad", Map.of("note", "x"), List.of(FilePart.ofPath("call.wav")), null);
```

## Requirements

| Java | Runtime dependencies | HTTP | WebSocket |
| --- | --- | --- | --- |
| 17+ | none | `java.net.http` (HTTP/1.1) | `java.net.http` |

The `Automatic-Module-Name` is `io.github.lomshakov.voicekit`, so the SDK works
on the module path as well as the classpath.

## Development

```bash
mvn test        # unit tests, offline (the live suite is skipped)
mvn package     # also builds the sources and javadoc jars
VOICEKIT_API_KEY=rtt_… mvn test   # additionally runs LiveApiTest

mvn -Prelease deploy              # signs and publishes to Maven Central
```

## License

[MIT](./LICENSE) © VoiceKit
