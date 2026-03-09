package com.printcalculator.service.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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

    private final String ffmpegExecutable;
    private final Set<String> availableEncoders;

    public MediaFfmpegService(@Value("${media.ffmpeg.path:ffmpeg}") String ffmpegPath) {
        this.ffmpegExecutable = sanitizeExecutable(ffmpegPath);
        this.availableEncoders = Collections.unmodifiableSet(loadAvailableEncoders());
    }

    public void generateVariant(Path source, Path target, int widthPx, int heightPx, String format) throws IOException {
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("Variant dimensions must be positive.");
        }

        Path sourcePath = sanitizeMediaPath(source, "source", true);
        Path targetPath = sanitizeMediaPath(target, "target", false);
        Files.createDirectories(targetPath.getParent());

        String encoder = resolveEncoder(format);
        if (encoder == null) {
            throw new IOException("FFmpeg encoder not available for media format " + format + ".");
        }

        List<String> command = new ArrayList<>();
        command.add(ffmpegExecutable);
        command.add("-y");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-i");
        command.add(sourcePath.toString());
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

        command.add(targetPath.toString());

        Process process = startValidatedProcess(command);
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

        if (exitCode != 0 || !Files.exists(targetPath) || Files.size(targetPath) == 0) {
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
        List<String> command = List.of(ffmpegExecutable, "-hide_banner", "-encoders");
        try {
            Process process = startValidatedProcess(command);
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

    private Process startValidatedProcess(List<String> command) throws IOException {
        // nosemgrep: java.lang.security.audit.command-injection-process-builder.command-injection-process-builder
        return new ProcessBuilder(List.copyOf(command))
                .redirectErrorStream(true)
                .start();
    }

    static String sanitizeExecutable(String configuredExecutable) {
        if (configuredExecutable == null) {
            throw new IllegalArgumentException("media.ffmpeg.path must not be null.");
        }

        String candidate = configuredExecutable.trim();
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("media.ffmpeg.path must point to an FFmpeg executable.");
        }
        if (candidate.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("media.ffmpeg.path contains control characters.");
        }

        try {
            Path executablePath = Path.of(candidate);
            Path filename = executablePath.getFileName();
            String executableName = filename == null ? candidate : filename.toString();
            if (executableName.isBlank() || executableName.startsWith("-")) {
                throw new IllegalArgumentException("media.ffmpeg.path must be an executable path, not an option.");
            }

            return executablePath.normalize().toString();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("media.ffmpeg.path is not a valid executable path.", e);
        }
    }

    private Path sanitizeMediaPath(Path path, String label, boolean requireExistingFile) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Media " + label + " path is required.");
        }

        Path normalized = path.toAbsolutePath().normalize();
        Path filename = normalized.getFileName();
        if (filename == null || filename.toString().isBlank()) {
            throw new IOException("Media " + label + " path must include a file name.");
        }
        if (filename.toString().startsWith("-")) {
            throw new IOException("Media " + label + " file name must not start with '-'.");
        }

        if (requireExistingFile) {
            if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
                throw new IOException("Media " + label + " file is not readable.");
            }
        } else if (normalized.getParent() == null) {
            throw new IOException("Media " + label + " path must include a parent directory.");
        }

        return normalized;
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
