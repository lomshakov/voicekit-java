package io.github.lomshakov.voicekit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilePartTest {

    @Test
    void builds_from_bytes_with_defaults() {
        FilePart part = FilePart.ofBytes(new byte[] {1, 2, 3});

        assertEquals("audio.wav", part.name());
        assertEquals("audio/wav", part.contentType());
        assertEquals(3, part.size());
    }

    @Test
    void guesses_the_content_type_from_the_name() {
        assertEquals("audio/mpeg", FilePart.ofBytes(new byte[] {1}, "call.mp3").contentType());
        assertEquals("video/mp4", FilePart.ofBytes(new byte[] {1}, "clip.MP4").contentType());
        assertEquals("audio/ogg", FilePart.ofBytes(new byte[] {1}, "note.opus").contentType());
        assertEquals("application/octet-stream", FilePart.ofBytes(new byte[] {1}, "blob").contentType());
        assertEquals("application/octet-stream", FilePart.mimeTypeFor(null));
    }

    @Test
    void builds_from_text_as_utf8() {
        FilePart part = FilePart.ofText("Привет", "note.txt");

        assertEquals("text/plain", part.contentType());
        assertArrayEquals("Привет".getBytes(StandardCharsets.UTF_8), part.content());
    }

    @Test
    void reads_a_file_from_disk(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("call.mp3");
        Files.write(path, new byte[] {7, 8, 9});

        FilePart part = FilePart.ofPath(path);

        assertEquals("call.mp3", part.name());
        assertEquals("audio/mpeg", part.contentType());
        assertArrayEquals(new byte[] {7, 8, 9}, part.content());
    }

    @Test
    void reads_a_file_with_a_renamed_part(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("call.bin");
        Files.write(path, new byte[] {1});

        assertEquals("clip.wav", FilePart.ofPath(path, "clip.wav").name());
        assertEquals("clip.wav", FilePart.ofPath(path.toString(), "clip.wav").name());
    }

    @Test
    void rejects_a_missing_file() {
        assertThrows(IllegalArgumentException.class, () -> FilePart.ofPath("does-not-exist.wav"));
        assertThrows(IllegalArgumentException.class, () -> FilePart.ofPath((Path) null));
        assertThrows(IllegalArgumentException.class, () -> new FilePart(null, "a.wav", null));
    }

    @Test
    void normalises_the_shapes_a_caller_may_pass(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("call.wav");
        Files.write(path, new byte[] {5});

        FilePart part = FilePart.ofPath(path);

        assertSame(part, FilePart.of(part));
        assertEquals("call.wav", FilePart.of(path).name());
        assertEquals("call.wav", FilePart.of(path.toFile()).name());
        assertEquals("call.wav", FilePart.of(path.toString()).name());
        assertThrows(IllegalArgumentException.class, () -> FilePart.of(42));
        assertThrows(IllegalArgumentException.class, () -> FilePart.of(null));
    }

    @Test
    void copies_itself_when_overridden(@TempDir Path directory) throws Exception {
        FilePart part = FilePart.ofBytes(new byte[] {1}, "a.wav");

        assertEquals("b.wav", part.withName("b.wav").name());
        assertEquals("audio/wav", part.withName("b.wav").contentType());
        assertEquals("audio/flac", part.withContentType("audio/flac").contentType());
        assertEquals("a.wav", part.name());
    }

    @Test
    void encodes_base64_for_inline_batch_items(@TempDir Path directory) throws Exception {
        FilePart part = FilePart.ofBytes("hi".getBytes(StandardCharsets.UTF_8), "a.txt");

        assertEquals("aGk=", part.base64());

        Path path = directory.resolve("a.txt");
        Files.write(path, "hi".getBytes(StandardCharsets.UTF_8));

        assertEquals("aGk=", FilePart.base64Of(path.toString()));
    }

    @Test
    void keeps_its_payload_private() {
        byte[] source = {1, 2, 3};
        FilePart part = new FilePart(source, "a.wav", null);

        source[0] = 9;
        assertEquals(1, part.content()[0]);

        byte[] copy = part.content();
        copy[0] = 7;
        assertEquals(1, part.content()[0]);
    }

    @Test
    void compares_by_value() {
        FilePart first = FilePart.ofBytes(new byte[] {1}, "a.wav");
        FilePart second = FilePart.ofBytes(new byte[] {1}, "a.wav");
        FilePart third = FilePart.ofBytes(new byte[] {2}, "a.wav");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotSame(first, third);
        assertTrue(first.toString().contains("a.wav"));
    }

    @Test
    void accepts_a_file_object(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("x.wav");
        Files.write(path, new byte[] {1});

        assertEquals("x.wav", FilePart.of(path.toFile()).name());
        assertEquals("x.wav", FilePart.of(new File(path.toString())).name());
    }
}
