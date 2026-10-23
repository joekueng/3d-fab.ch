package com.printcalculator.controller;

import com.printcalculator.service.ModelPreviewService;
import com.printcalculator.service.SlicerService;
import com.printcalculator.service.SlicingCapacityService;
import com.printcalculator.service.ProfileManager;
import com.printcalculator.service.GCodeParser;
import com.printcalculator.service.StepConversionService;
import com.printcalculator.service.QuoteRateLimitService;
import com.printcalculator.service.quote.QuoteStorageService;
import com.printcalculator.service.storage.ClamAVService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ModelPreviewControllerTest {
    @Test
    void convertsAnObjThroughTheApiWithoutCreatingAQuoteOrRunningTheSlicer() throws Exception {
        var scanner = mock(ClamAVService.class);
        when(scanner.scan(any(java.io.InputStream.class))).thenReturn(true);
        var slicer = new SlicerService("unused-orca", "unused-assimp", mock(ProfileManager.class),
                mock(GCodeParser.class), new com.fasterxml.jackson.databind.ObjectMapper(), mock(StepConversionService.class));
        var previews = new ModelPreviewService(slicer, new SlicingCapacityService(1, 0, 0),
                scanner, new QuoteStorageService());
        var mvc = MockMvcBuilders.standaloneSetup(new ModelPreviewController(previews,
                new QuoteRateLimitService(15, 60, false, 15, 75))).build();
        var file = new MockMultipartFile("file", "model.obj", "text/plain",
                "v 0 0 0\nv 10 0 0\nv 0 20 1\nf 1 2 3\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/model-previews").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("model/stl"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("facet normal")));
        verify(scanner).scan(any(java.io.InputStream.class));
    }

    @Test
    void returnsPrivateStlResponseAndRateLimitsConversion() throws Exception {
        var previews = mock(ModelPreviewService.class);
        byte[] stl = "solid test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(previews.createStlPreview(any(), eq("mm"))).thenReturn(stl);
        var limiter = new QuoteRateLimitService(1, 60, false, 15, 75);
        var mvc = MockMvcBuilders.standaloneSetup(new ModelPreviewController(previews, limiter)).build();
        var file = new MockMultipartFile("file", "model.3mf", "application/octet-stream", new byte[]{1});

        mvc.perform(multipart("/api/model-previews").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentType("model/stl"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(stl));
        mvc.perform(multipart("/api/model-previews").file(file))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        verify(previews, times(1)).createStlPreview(any(), eq("mm"));
    }
}
