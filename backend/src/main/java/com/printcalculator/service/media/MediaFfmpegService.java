package com.printcalculator.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class MediaFfmpegService {

    private final String ffmpegPath;

    public MediaFfmpegService(@Value("${media.ffmpeg.path:ffmpeg}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public void generateVariant(Path source, Path target, int widthPx, int heightPx, String format) throws IOException {
        if (widthPx <= 0 || heightPx <= 0) {
            throw new IllegalArgumentException("Variant dimensions must be positive.");
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
                command.add("mjpeg");
                command.add("-q:v");
                command.add("2");
            }
            case "WEBP" -> {
                command.add("-c:v");
                command.add("libwebp");
                command.add("-quality");
                command.add("82");
            }
            case "AVIF" -> {
                command.add("-c:v");
                command.add("libaom-av1");
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

    private String truncate(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        String normalized = output.trim().replace('\n', ' ');
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }
}
