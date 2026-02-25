package com.printcalculator.dto;

import lombok.Data;

@Data
public class PrintSettingsDto {
    // Mode: "BASIC" or "ADVANCED"
    private String complexityMode;
    
    // Common
    private String material; // e.g. "PLA", "PETG"
    private String color;    // e.g. "White", "#FFFFFF"
    
    // Basic Mode
    private String quality;  // "draft", "standard", "high"
    
    // Advanced Mode (Optional in Basic)
    private Double nozzleDiameter;
    private Double layerHeight;
    private Double infillDensity;
    private String infillPattern;
    private Boolean supportsEnabled;
    private String notes;

    // Dimensions
    private Double boundingBoxX;
    private Double boundingBoxY;
    private Double boundingBoxZ;
}
