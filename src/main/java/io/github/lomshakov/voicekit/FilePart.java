package io.github.lomshakov.voicekit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * A file sent with a multipart/form-data request (transcription, analysis,
 * voice cloning, effects, …).
 *
 * <pre>{@code
 * client.transcribe(FilePart.ofPath("call.mp3"), new TranscribeOptions().language("ru"));
 *
 * client.transcribe(FilePart.ofBytes(pcm).withName("clip.wav"), null);
 * }</pre>
 */
public final class FilePart {

    /** The fallback file name used when none is given. */
    public static final String DEFAULT_NAME = "audio.wav";

    /** The fallback content type used when none can be guessed. */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final byte[] content;
    private final String name;
    private final String contentType;

    /**
     * Creates a file part.
     *
     * @param content the raw bytes
     * @param name the file name reported to the server; defaults to
     *     {@value #DEFAULT_NAME}
     * @param contentType the MIME type; guessed from the file name when blank
     */
    public FilePart(byte[] content, String name, String contentType) {
        if (content == null) {
            throw new IllegalArgumentException("VoiceKit: the file content is required");
        }

        String resolvedName = name == null ? "" : name.trim();
        if (resolvedName.isEmpty()) {
            resolvedName = DEFAULT_NAME;
        }

        String resolvedType = contentType == null ? "" : contentType.trim();
        if (resolvedType.isEmpty()) {
            resolvedType = mimeTypeFor(resolvedName);
        }

        this.content = content.clone();
        this.name = resolvedName;
        this.contentType = resolvedType;
    }

    /**
     * Creates a file part from raw bytes, named {@value #DEFAULT_NAME}.
     *
     * @param content the raw bytes
     */
    public static FilePart ofBytes(byte[] content) {
        return new FilePart(content, DEFAULT_NAME, null);
    }

    /**
     * Creates a file part from raw bytes.
     *
     * @param content the raw bytes
     * @param name the file name reported to the server
     */
    public static FilePart ofBytes(byte[] content, String name) {
        return new FilePart(content, name, null);
    }

    /**
     * Creates a file part from text, encoded as UTF-8.
     *
     * @param content the payload
     * @param name the file name reported to the server
     */
    public static FilePart ofText(String content, String name) {
        return new FilePart(content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8), name, null);
    }

    /**
     * Reads a file from disk; the MIME type is guessed from the extension.
     *
     * @param path the file path
     * @throws IllegalArgumentException when the file is missing or unreadable
     */
    public static FilePart ofPath(String path) {
        return ofPath(Path.of(path), null);
    }

    /**
     * Reads a file from disk, overriding the reported file name.
     *
     * @param path the file path
     * @param name the file name reported to the server, or {@code null} to keep
     *     the file's own name
     * @throws IllegalArgumentException when the file is missing or unreadable
     */
    public static FilePart ofPath(String path, String name) {
        return ofPath(Path.of(path), name);
    }

    /**
     * Reads a file from disk; the MIME type is guessed from the extension.
     *
     * @param path the file path
     * @throws IllegalArgumentException when the file is missing or unreadable
     */
    public static FilePart ofPath(Path path) {
        return ofPath(path, null);
    }

    /**
     * Reads a file from disk, overriding the reported file name.
     *
     * @param path the file path
     * @param name the file name reported to the server, or {@code null} to keep
     *     the file's own name
     * @throws IllegalArgumentException when the file is missing or unreadable
     */
    public static FilePart ofPath(Path path, String name) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException(
                "VoiceKit: file \"" + path + "\" does not exist or is not readable.");
        }

        try {
            String resolvedName = name == null || name.isBlank() ? path.getFileName().toString() : name;

            return new FilePart(Files.readAllBytes(path), resolvedName, null);
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                "VoiceKit: unable to read file \"" + path + "\": " + exception.getMessage(), exception);
        }
    }

    /**
     * Reads a file from disk; the MIME type is guessed from the extension.
     *
     * @param file the file
     * @throws IllegalArgumentException when the file is missing or unreadable
     */
    public static FilePart of(File file) {
        if (file == null) {
            throw new IllegalArgumentException("VoiceKit: the file is required.");
        }

        return ofPath(file.toPath(), null);
    }

    /**
     * Normalises the shapes a caller may pass — a path, a {@link Path}, a
     * {@link File} or a {@link FilePart}.
     *
     * @param value the value to normalise
     * @throws IllegalArgumentException when the value is not a supported shape
     */
    public static FilePart of(Object value) {
        if (value instanceof FilePart part) {
            return part;
        }
        if (value instanceof Path path) {
            return ofPath(path, null);
        }
        if (value instanceof File file) {
            return ofPath(file.toPath(), null);
        }
        if (value instanceof String path) {
            return ofPath(path);
        }

        throw new IllegalArgumentException(
            "VoiceKit: expected a FilePart, Path, File or file path, got "
                + (value == null ? "null" : value.getClass().getName()));
    }

    /** The file name reported to the server. */
    public String name() {
        return name;
    }

    /** The MIME type. */
    public String contentType() {
        return contentType;
    }

    /** A copy of the payload. */
    public byte[] content() {
        return content.clone();
    }

    /** The payload size in bytes. */
    public int size() {
        return content.length;
    }

    /** The payload encoded as base64, ready for inline batch items. */
    public String base64() {
        return Base64.getEncoder().encodeToString(content);
    }

    /** A copy with a different file name. */
    public FilePart withName(String name) {
        return new FilePart(content, name, contentType);
    }

    /** A copy with a different MIME type. */
    public FilePart withContentType(String contentType) {
        return new FilePart(content, name, contentType);
    }

    /**
     * Reads the file at the path and encodes it as base64, ready for inline
     * batch items.
     *
     * @param path the file path
     * @throws IllegalArgumentException when the file is missing or unreadable
     */
    public static String base64Of(String path) {
        return ofPath(path).base64();
    }

    /**
     * Guesses a MIME type from a file name.
     *
     * @param name the file name
     * @return the MIME type, or {@value #DEFAULT_CONTENT_TYPE}
     */
    public static String mimeTypeFor(String name) {
        if (name == null) {
            return DEFAULT_CONTENT_TYPE;
        }

        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return DEFAULT_CONTENT_TYPE;
        }

        return switch (name.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "ogg", "opus" -> "audio/ogg";
            case "m4a", "aac" -> "audio/aac";
            case "flac" -> "audio/flac";
            case "mp4", "m4v" -> "video/mp4";
            case "mov" -> "video/quicktime";
            case "webm" -> "video/webm";
            case "mkv" -> "video/x-matroska";
            case "txt" -> "text/plain";
            case "json" -> "application/json";
            case "csv" -> "text/csv";
            case "vtt" -> "text/vtt";
            case "srt" -> "application/x-subrip";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> DEFAULT_CONTENT_TYPE;
        };
    }

    @Override
    public String toString() {
        return "FilePart{" + name + ", " + contentType + ", " + content.length + " bytes}";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FilePart part)) {
            return false;
        }

        return name.equals(part.name)
            && contentType.equals(part.contentType)
            && Arrays.equals(content, part.content);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * name.hashCode() + contentType.hashCode()) + Arrays.hashCode(content);
    }
}
