package com.printcalculator.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

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
}
