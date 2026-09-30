package io.github.lomshakov.voicekit;

/**
 * Performs the HTTP call behind a {@link VoiceKitClient}.
 *
 * <p>Applications rarely need this: pass a custom transport to route the SDK
 * through a proxy, a mock server or a tracing wrapper.
 *
 * <pre>{@code
 * VoiceKitClient client = new VoiceKitClient("rtt_…", VoiceKitClient.DEFAULT_BASE_URL,
 *     VoiceKitClient.DEFAULT_TIMEOUT, new MyTransport());
 * }</pre>
 */
@FunctionalInterface
public interface Transport {

    /**
     * Performs the request.
     *
     * @param request the request to perform
     * @return the response; its body must be closed by the caller
     * @throws VoiceKitException when the request cannot be performed at all
     */
    Response send(Request request);
}
