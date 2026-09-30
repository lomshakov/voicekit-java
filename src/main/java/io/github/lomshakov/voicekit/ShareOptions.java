package io.github.lomshakov.voicekit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controls {@code POST /v1/recordings/{id}/share}.
 *
 * <pre>{@code
 * Result link = client.createShare(recordingId, new ShareOptions()
 *     .expiresInSeconds(86_400)
 *     .password("secret"));
 * }</pre>
 */
public final class ShareOptions {

    private int expiresInSeconds;
    private String password;

    /** Limits the link lifetime; {@code 0} means no expiry. */
    public ShareOptions expiresInSeconds(int value) {
        this.expiresInSeconds = value;

        return this;
    }

    /** Protects the link with a password. */
    public ShareOptions password(String value) {
        this.password = value;

        return this;
    }

    Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        Payload.putInt(body, "expires_in_seconds", expiresInSeconds);
        Payload.putString(body, "password", password);

        return body;
    }
}
