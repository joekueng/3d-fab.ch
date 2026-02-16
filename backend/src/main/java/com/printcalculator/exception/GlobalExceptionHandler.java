package com.printcalculator.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<?> handleStorageException(StorageException exc) {
        // Log the full exception for internal debugging
        log.error("Storage Exception occurred", exc);
        
        Map<String, String> response = new HashMap<>();

        // Check for specific virus case
        if (exc.getMessage() != null && exc.getMessage().contains("antivirus scanner")) {
             response.put("error", "Security Violation");
             // Safe message for client
             response.put("message", "File rejected by security policy.");
             response.put("code", "VIRUS_DETECTED");
             return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }

        // Generic fallback for other storage errors to avoid leaking internal paths/details
        response.put("error", "Storage Operation Failed");
        response.put("message", "Unable to process the file upload.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxSizeException(MaxUploadSizeExceededException exc) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "File too large");
        response.put("message", "The uploaded file exceeds the maximum allowed size.");
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }

    @ExceptionHandler(ModelTooLargeException.class)
    public ResponseEntity<?> handleModelTooLarge(ModelTooLargeException exc) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Model too large");
        response.put("code", "MODEL_TOO_LARGE");
        response.put("message", String.format(
                "Model size %.2fx%.2fx%.2f mm exceeds build volume %dx%dx%d mm.",
                exc.getModelX(), exc.getModelY(), exc.getModelZ(),
                exc.getBuildX(), exc.getBuildY(), exc.getBuildZ()
        ));
        response.put("model_x_mm", formatMm(exc.getModelX()));
        response.put("model_y_mm", formatMm(exc.getModelY()));
        response.put("model_z_mm", formatMm(exc.getModelZ()));
        response.put("build_x_mm", String.valueOf(exc.getBuildX()));
        response.put("build_y_mm", String.valueOf(exc.getBuildY()));
        response.put("build_z_mm", String.valueOf(exc.getBuildZ()));
        return ResponseEntity.unprocessableEntity().body(response);
    }

    private String formatMm(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
