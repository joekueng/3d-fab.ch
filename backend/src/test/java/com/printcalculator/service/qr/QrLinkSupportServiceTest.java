package com.printcalculator.service.qr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrLinkSupportServiceTest {

    private final QrLinkSupportService service = new QrLinkSupportService("https://3d-fab.ch");

    @Test
    void normalizeTargetPath_shouldStripLanguagePrefixAndCanonicalizeCalculator() {
        assertEquals("/contact", service.normalizeTargetPath("/de/contact/"));
        assertEquals("/calculator/basic", service.normalizeTargetPath("/calculator"));
        assertEquals("/", service.normalizeTargetPath("/it"));
    }

    @Test
    void buildLocalizedPath_shouldResolveRootAndInternalPages() {
        assertEquals("/fr", service.buildLocalizedPath("/", "fr-CH"));
        assertEquals("/de/contact", service.buildLocalizedPath("/contact", "de-CH"));
    }

    @Test
    void generateSvgForPublicUrl_shouldGenerateCrispSvg() {
        String svg = service.generateSvgForPublicUrl("flyer-fiera-2026");

        assertTrue(svg.contains("<svg"));
        assertTrue(svg.contains("shape-rendering=\"crispEdges\""));
        assertTrue(svg.contains("<path fill=\"#000000\""));
        assertFalse(svg.contains("<rect"));
        assertFalse(svg.contains("fill=\"#FFFFFF\""));
        assertEquals(
                "https://3d-fab.ch/go/flyer-fiera-2026",
                service.buildPublicUrl("flyer-fiera-2026")
        );
    }
}
