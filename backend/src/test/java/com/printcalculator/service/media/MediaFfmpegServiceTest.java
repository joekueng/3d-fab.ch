package com.printcalculator.service.media;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaFfmpegServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void sanitizeExecutable_rejectsControlCharacters() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> MediaFfmpegService.sanitizeExecutable("ffmpeg\n--help")
        );

        assertEquals("media.ffmpeg.path contains control characters.", ex.getMessage());
    }

    @Test
    void resolveExecutable_shouldFallbackToPathWhenAbsoluteLocationIsMissing() {
        String resolved = MediaFfmpegService.resolveExecutable("/opt/homebrew/bin/ffmpeg");

        assertEquals("ffmpeg", resolved);
    }

    @Test
    void generateVariant_rejectsSourceNamesStartingWithDash() throws Exception {
        MediaFfmpegService service = new MediaFfmpegService("missing-ffmpeg-binary");
        Path source = tempDir.resolve("-input.png");
        Path target = tempDir.resolve("output.jpg");
        Files.writeString(source, "image");

        IOException ex = assertThrows(
                IOException.class,
                () -> service.generateVariant(source, target, 120, 80, "JPEG")
        );

        assertEquals("Media source file name must not start with '-'.", ex.getMessage());
    }

    @Test
    void generateVariant_rejectsTargetNamesStartingWithDash() throws Exception {
        MediaFfmpegService service = new MediaFfmpegService("missing-ffmpeg-binary");
        Path source = tempDir.resolve("input.png");
        Path target = tempDir.resolve("-output.jpg");
        Files.writeString(source, "image");

        IOException ex = assertThrows(
                IOException.class,
                () -> service.generateVariant(source, target, 120, 80, "JPEG")
        );

        assertEquals("Media target file name must not start with '-'.", ex.getMessage());
    }
}
