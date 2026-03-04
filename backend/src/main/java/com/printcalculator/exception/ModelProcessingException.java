package com.printcalculator.exception;

import java.io.IOException;

public class ModelProcessingException extends IOException {
    private final String code;

    public ModelProcessingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ModelProcessingException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
