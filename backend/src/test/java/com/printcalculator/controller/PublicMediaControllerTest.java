package com.printcalculator.controller;

import com.printcalculator.service.media.PublicMediaQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicMediaControllerTest {
    private final PublicMediaQueryService service = mock(PublicMediaQueryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new PublicMediaController(service)).build();

    @Test
    void keepsSingleUsageResponseAndReturnsBatchResultsByKey() throws Exception {
        when(service.getUsageMedia("FILAMENT_VARIANT", "101", "en")).thenReturn(List.of());
        when(service.getUsageMediaMap("FILAMENT_VARIANT", List.of("101", "102"), "en"))
                .thenReturn(Map.of("101", List.of()));

        mvc.perform(get("/api/public/media/usages")
                        .param("usageType", "FILAMENT_VARIANT").param("lang", "en").param("usageKey", "101"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/public/media/usages")
                        .param("usageType", "FILAMENT_VARIANT").param("lang", "en").param("usageKeys", "101", "102"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"101\":[]}"));
    }

    @Test
    void preservesCommasInsideAUsageKey() throws Exception {
        when(service.getUsageMediaMap("HOME_SECTION", List.of("one,two"), null))
                .thenReturn(Map.of("one,two", List.of()));

        mvc.perform(get("/api/public/media/usages")
                        .param("usageType", "HOME_SECTION").param("usageKeys", "one,two"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"one,two\":[]}"));
    }

    @Test
    void rejectsBatchesAboveTheLimitBeforeQuerying() throws Exception {
        String[] keys = IntStream.rangeClosed(1, 51).mapToObj(Integer::toString).toArray(String[]::new);
        mvc.perform(get("/api/public/media/usages")
                        .param("usageType", "FILAMENT_VARIANT").param("usageKeys", keys))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
