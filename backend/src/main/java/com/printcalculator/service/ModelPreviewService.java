package com.printcalculator.service;

import com.printcalculator.exception.ModelProcessingException;
import com.printcalculator.service.quote.QuoteStorageService;
import com.printcalculator.service.storage.ClamAVService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

@Service
public class ModelPreviewService {
    private static final Logger logger = Logger.getLogger(ModelPreviewService.class.getName());
    private static final long MAX_INPUT_BYTES = 200L * 1024 * 1024;
    private static final int MAX_PREVIEW_BYTES = 75 * 1024 * 1024;

    private final SlicerService slicerService;
    private final SlicingCapacityService slicingCapacityService;
    private final ClamAVService clamAVService;
    private final QuoteStorageService quoteStorageService;

    public ModelPreviewService(SlicerService slicerService,
                               SlicingCapacityService slicingCapacityService,
                               ClamAVService clamAVService,
                               QuoteStorageService quoteStorageService) {
        this.slicerService = slicerService;
        this.slicingCapacityService = slicingCapacityService;
        this.clamAVService = clamAVService;
        this.quoteStorageService = quoteStorageService;
    }

    public byte[] createStlPreview(MultipartFile file) throws IOException {
        return createStlPreview(file, "mm");
    }

    public byte[] createStlPreview(MultipartFile file, String modelUnit) throws IOException {
        try {
            com.printcalculator.model.ModelUnit.parse(modelUnit);
        } catch (IllegalArgumentException failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, failure.getMessage());
        }
        String extension = quoteStorageService.getSafeExtension(file.getOriginalFilename(), "");
        if (file.isEmpty() || extension.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A supported model file is required.");
        }
        if (file.getSize() > MAX_INPUT_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Model exceeds the upload limit.");
        }

        try (var lease = slicingCapacityService.acquire()) {
            try (var input = file.getInputStream()) {
                clamAVService.scan(input);
            }
            Path tempDir = Files.createTempDirectory("model_preview_");
            try {
                Path original = tempDir.resolve("original." + extension);
                file.transferTo(original);
                Path preview = original;
                if (!"stl".equals(extension)) {
                    preview = slicerService.convertModelToPersistentStl(original.toFile(), tempDir.resolve("preview.stl"), modelUnit);
                }
                try (var input = Files.newInputStream(preview)) {
                    byte[] bytes = input.readNBytes(MAX_PREVIEW_BYTES + 1);
                    if (bytes.length == 0 || bytes.length > MAX_PREVIEW_BYTES) {
                        throw new ModelProcessingException("MODEL_PREVIEW_UNAVAILABLE", "Model exceeds the preview limit.");
                    }
                    return bytes;
                }
            } finally {
                SlicerFileSupport.deleteRecursively(tempDir, logger);
            }
        }
    }
}
