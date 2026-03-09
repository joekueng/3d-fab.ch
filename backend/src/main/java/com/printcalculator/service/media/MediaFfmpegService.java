package com.printcalculator.service.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class MediaFfmpegService {

    private static final Logger logger = LoggerFactory.getLogger(MediaFfmpegService.class);

    private static final Map<String, List<String>> ENCODER_CANDIDATES = Map.of(
            "JPEG", List.of("mjpeg"),
            "WEBP", List.of("libwebp", "webp"),
            "AVIF", List.of("libaom-av1", "librav1e", "libsvtav1")
    );

    private final String ffmpegPath;
    private final Set<String> availableEncoders;

    public MediaFfmpegService(@Value("${media.ffmpeg.path:ffmpeg}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
        this.availableEncoders = Collections.unmodifiableSet(loadAvailableEncoders());
    }

    public void generateVariant(Path source, Path target, int widthPx, int heightPx, String format) throws IOException {
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("Variant dimensions must be positive.");
        }

        String encoder = resolveEncoder(format);
        if (encoder == null) {
            throw new IOException("FFmpeg encoder not available for media format " + format + ".");
        }

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-i");
        command.add(source.toAbsolutePath().toString());
        command.add("-vf");
        command.add("scale=" + widthPx + ":" + heightPx + ":flags=lanczos,setsar=1");
        command.add("-frames:v");
        command.add("1");
        command.add("-an");

        switch (format) {
            case "JPEG" -> {
                command.add("-c:v");
                command.add(encoder);
                command.add("-q:v");
                command.add("2");
            }
            case "WEBP" -> {
                command.add("-c:v");
                command.add(encoder);
                command.add("-quality");
                command.add("82");
            }
            case "AVIF" -> {
                command.add("-c:v");
                command.add(encoder);
                command.add("-still-picture");
                command.add("1");
                command.add("-crf");
                command.add("30");
                command.add("-b:v");
                command.add("0");
            }
            default -> throw new IllegalArgumentException("Unsupported media format: " + format);
        }

        command.add(target.toAbsolutePath().toString());

        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream processStream = process.getInputStream()) {
            output = new String(processStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("FFmpeg execution interrupted.", e);
        }

        if (exitCode != 0 || !Files.exists(target) || Files.size(target) == 0) {
            throw new IOException("FFmpeg failed to generate media variant. " + truncate(output));
        }
    }

    public boolean canEncode(String format) {
        return resolveEncoder(format) != null;
    }

    private String resolveEncoder(String format) {
        if (format == null) {
            return null;
        }
        List<String> candidates = ENCODER_CANDIDATES.get(format.trim().toUpperCase(Locale.ROOT));
        if (candidates == null) {
            return null;
        }
        return candidates.stream()
                .filter(availableEncoders::contains)
                .findFirst()
                .orElse(null);
    }

    private Set<String> loadAvailableEncoders() {
        List<String> command = List.of(ffmpegPath, "-hide_banner", "-encoders");
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (InputStream processStream = process.getInputStream()) {
                output = new String(processStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warn("Unable to inspect FFmpeg encoders. Falling back to empty encoder list.");
                return Set.of();
            }
            return parseAvailableEncoders(output);
        } catch (Exception e) {
            logger.warn("Unable to inspect FFmpeg encoders. Falling back to empty encoder list.", e);
            return Set.of();
        }
    }

    private Set<String> parseAvailableEncoders(String output) {
        if (output == null || output.isBlank()) {
            return Set.of();
        }

        Set<String> encoders = new LinkedHashSet<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("--") || trimmed.startsWith("Encoders:")) {
                continue;
            }
            if (trimmed.length() < 7) {
                continue;
            }
            String[] parts = trimmed.split("\\s+", 3);
            if (parts.length < 2) {
                continue;
            }
            encoders.add(parts[1]);
        }
        return encoders;
    }

    private String truncate(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String normalized = output.trim().replace('\n', ' ');
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }
}
