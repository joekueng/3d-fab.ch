package com.printcalculator.dto;

public record ReviewRequestPreviewDto(
        String recipient,
        String subject,
        String headline,
        String greeting,
        String intro,
        String request,
        String photoNote,
        String actionText,
        String reviewUrl,
        String closing,
        String signature,
        String footer
) {}
