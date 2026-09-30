package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoiceKitExceptionTest {

    @Test
    void reads_the_problem_details_code_and_detail() {
        VoiceKitException error = VoiceKitException.fromResponse(
            403,
            "{\"code\":\"streaming_forbidden\",\"detail\":\"Streaming requires Pro\",\"title\":\"Forbidden\"}");

        assertEquals(403, error.statusCode());
        assertEquals("streaming_forbidden", error.errorCode());
        assertTrue(error.getMessage().contains("Streaming requires Pro"));
        assertTrue(error.getMessage().contains("HTTP 403"));
        assertTrue(error.getMessage().contains("code streaming_forbidden"));
    }

    @Test
    void falls_back_to_the_title() {
        VoiceKitException error =
            VoiceKitException.fromResponse(400, "{\"title\":\"Bad request\",\"code\":\"invalid_input\"}");

        assertTrue(error.getMessage().contains("Bad request"));
        assertEquals("invalid_input", error.errorCode());
    }

    @Test
    void falls_back_to_the_raw_body() {
        VoiceKitException error = VoiceKitException.fromResponse(502, "upstream is down");

        assertEquals("", error.errorCode());
        assertTrue(error.getMessage().contains("upstream is down"));
    }

    @Test
    void falls_back_to_the_status_text() {
        VoiceKitException error = VoiceKitException.fromResponse(500, "");

        assertEquals(500, error.statusCode());
        assertTrue(error.getMessage().contains("HTTP 500"));
        assertEquals("", error.errorCode());
    }

    @Test
    void classifies_common_statuses() {
        assertTrue(VoiceKitException.fromResponse(401, "{}").isUnauthorized());
        assertTrue(VoiceKitException.fromResponse(403, "{}").isForbidden());
        assertTrue(VoiceKitException.fromResponse(404, "{}").isNotFound());
        assertTrue(VoiceKitException.fromResponse(429, "{}").isRateLimited());

        VoiceKitException error = VoiceKitException.fromResponse(403, "{}");

        assertFalse(error.isCode(""));
        assertFalse(error.isCode("quota_exceeded"));
    }

    @Test
    void matches_error_codes() {
        VoiceKitException error = VoiceKitException.fromResponse(429, "{\"code\":\"quota_exceeded\"}");

        assertTrue(error.isCode("quota_exceeded"));
        assertFalse(error.isCode("other"));
    }

    @Test
    void keeps_the_cause() {
        RuntimeException cause = new RuntimeException("socket closed");
        VoiceKitException error = new VoiceKitException("boom", 0, "", cause);

        assertSame(cause, error.getCause());
        assertEquals(0, error.statusCode());
    }

    @Test
    void builds_a_bare_exception() {
        VoiceKitException error = new VoiceKitException("something went wrong");

        assertEquals("something went wrong", error.getMessage());
        assertEquals(0, error.statusCode());
        assertEquals("", error.errorCode());
    }
}
