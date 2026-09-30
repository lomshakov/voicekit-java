package io.github.lomshakov.voicekit;

import java.util.Map;

/**
 * Raised for every failure: a non-2xx API response, a transport problem or a
 * malformed payload.
 *
 * <p>The API answers errors with RFC 7807 Problem Details, so
 * {@link #errorCode()} carries the machine-readable code
 * ({@code quota_exceeded}, {@code streaming_forbidden}, …) and
 * {@link #statusCode()} the HTTP status. Transport failures (DNS, TLS,
 * timeouts, a dropped connection) carry status {@code 0}.
 *
 * <p>The exception is unchecked, so callers only handle what they care about:
 *
 * <pre>{@code
 * try {
 *     client.synthesize("Привет", new SynthesizeOptions().voice("preset_anna"));
 * } catch (VoiceKitException error) {
 *     if (error.isForbidden()) {
 *         System.out.println("upgrade your plan: " + error.errorCode());
 *     } else if (error.isCode("quota_exceeded")) {
 *         System.out.println("monthly quota is over");
 *     } else {
 *         System.out.println(error.getMessage());
 *     }
 * }
 * }</pre>
 */
public class VoiceKitException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The HTTP status, or {@code 0} when the failure was not an HTTP response. */
    private final int statusCode;

    /** The RFC 7807 {@code code}, or {@code ""} when the body carried none. */
    private final String errorCode;

    /**
     * Creates an exception.
     *
     * @param message the human-readable description
     * @param statusCode the HTTP status, or {@code 0} for transport failures
     * @param errorCode the machine-readable API code, or {@code ""}
     * @param cause the underlying failure, or {@code null}
     */
    public VoiceKitException(String message, int statusCode, String errorCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode == null ? "" : errorCode;
    }

    /**
     * Creates an exception without an HTTP status or an API error code.
     *
     * @param message the human-readable description
     */
    public VoiceKitException(String message) {
        this(message, 0, "", null);
    }

    /** The HTTP status, or {@code 0} when the failure was not an HTTP response. */
    public int statusCode() {
        return statusCode;
    }

    /** The machine-readable API error code, or {@code ""} when the body carried none. */
    public String errorCode() {
        return errorCode;
    }

    /** Whether the response was a {@code 401} (missing or invalid API key). */
    public boolean isUnauthorized() {
        return statusCode == 401;
    }

    /** Whether the response was a {@code 403} (the plan does not include the feature). */
    public boolean isForbidden() {
        return statusCode == 403;
    }

    /** Whether the response was a {@code 404}. */
    public boolean isNotFound() {
        return statusCode == 404;
    }

    /** Whether the response was a {@code 429} (rate limited). */
    public boolean isRateLimited() {
        return statusCode == 429;
    }

    /**
     * Whether the API answered with the given error code.
     *
     * @param code the expected code, e.g. {@code "quota_exceeded"}
     */
    public boolean isCode(String code) {
        return !errorCode.isEmpty() && errorCode.equals(code);
    }

    /**
     * Builds an exception from a non-2xx response body.
     *
     * @param status the HTTP status
     * @param body the raw response body
     */
    static VoiceKitException fromResponse(int status, String body) {
        String code = "";
        String message = "";

        if (!Json.isBlank(body)) {
            try {
                Map<String, Object> payload = Json.parseObject(body);
                code = string(payload.get("code"));

                String detail = string(payload.get("detail"));
                String title = string(payload.get("title"));
                message = !detail.isEmpty() ? detail : title;
            } catch (RuntimeException exception) {
                // Not a JSON object — fall back to the raw text below.
            }
        }

        if (message.isEmpty()) {
            message = !Json.isBlank(body) && body.trim().length() < 512
                ? body.trim()
                : "HTTP " + status;
        }

        String suffix = code.isEmpty()
            ? "(HTTP " + status + ")"
            : "(HTTP " + status + ", code " + code + ")";

        return new VoiceKitException("VoiceKit: " + message + " " + suffix, status, code, null);
    }

    private static String string(Object value) {
        return value instanceof String text ? text : "";
    }
}
