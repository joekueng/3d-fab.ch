package com.printcalculator.service.media;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class MediaImageInspector {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    public ImageMetadata inspect(Path file) throws IOException {
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] header = inputStream.readNBytes(64);
            if (isJpeg(header)) {
                return readWithImageIo(file, "image/jpeg", "jpg");
            }
            if (isPng(header)) {
                return readWithImageIo(file, "image/png", "png");
            }
            if (isWebp(header)) {
                Dimensions dimensions = readWebpDimensions(header);
                return new ImageMetadata("image/webp", "webp", dimensions.width(), dimensions.height());
            }
        }

        throw new IllegalArgumentException("Unsupported image type. Allowed: jpg, jpeg, png, webp.");
    }

    private ImageMetadata readWithImageIo(Path file, String mimeType, String extension) throws IOException {
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IllegalArgumentException("Uploaded image is invalid or unreadable.");
        }
        return new ImageMetadata(mimeType, extension, image.getWidth(), image.getHeight());
    }

    private boolean isJpeg(byte[] header) {
        return header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] header) {
        if (header.length < PNG_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (header[i] != PNG_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] header) {
        return header.length >= 16
                && "RIFF".equals(ascii(header, 0, 4))
                && "WEBP".equals(ascii(header, 8, 4));
    }

    private Dimensions readWebpDimensions(byte[] header) {
        if (header.length < 30) {
            throw new IllegalArgumentException("Uploaded WebP image is invalid.");
        }

        String chunkType = ascii(header, 12, 4);
        return switch (chunkType) {
            case "VP8X" -> new Dimensions(
                    littleEndian24(header, 24) + 1,
                    littleEndian24(header, 27) + 1
            );
            case "VP8 " -> new Dimensions(
                    littleEndian16(header, 26) & 0x3FFF,
                    littleEndian16(header, 28) & 0x3FFF
            );
            case "VP8L" -> {
                int packed = littleEndian32(header, 21);
                int width = (packed & 0x3FFF) + 1;
                int height = ((packed >> 14) & 0x3FFF) + 1;
                yield new Dimensions(width, height);
            }
            default -> throw new IllegalArgumentException("Uploaded WebP image is invalid.");
        };
    }

    private String ascii(byte[] header, int offset, int length) {
        return new String(header, offset, length, StandardCharsets.US_ASCII);
    }

    private int littleEndian16(byte[] header, int offset) {
        return (header[offset] & 0xFF) | ((header[offset + 1] & 0xFF) << 8);
    }

    private int littleEndian24(byte[] header, int offset) {
        return (header[offset] & 0xFF)
                | ((header[offset + 1] & 0xFF) << 8)
                | ((header[offset + 2] & 0xFF) << 16);
    }

    private int littleEndian32(byte[] header, int offset) {
        return (header[offset] & 0xFF)
                | ((header[offset + 1] & 0xFF) << 8)
                | ((header[offset + 2] & 0xFF) << 16)
                | ((header[offset + 3] & 0xFF) << 24);
    }

    private record Dimensions(int width, int height) {
    }

    public record ImageMetadata(String mimeType, String fileExtension, int widthPx, int heightPx) {
    }
}
