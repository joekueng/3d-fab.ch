package com.printcalculator.controller.admin;

import com.printcalculator.config.AllowedOriginService;
import com.printcalculator.config.CorsConfig;
import com.printcalculator.config.SecurityConfig;
import com.printcalculator.dto.AdminQuoteItemsResponse;
import com.printcalculator.security.AdminCsrfProtectionFilter;
import com.printcalculator.security.AdminLoginThrottleService;
import com.printcalculator.security.AdminSessionAuthenticationFilter;
import com.printcalculator.security.AdminSessionService;
import com.printcalculator.service.admin.AdminQuoteItemPricingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminAuthController.class, AdminQuoteSessionItemController.class})
@Import({
        CorsConfig.class,
        AllowedOriginService.class,
        SecurityConfig.class,
        AdminCsrfProtectionFilter.class,
        AdminSessionAuthenticationFilter.class,
        AdminSessionService.class,
        AdminLoginThrottleService.class,
        AdminQuoteSessionItemControllerSecurityTest.TransactionTestConfig.class
})
@TestPropertySource(properties = {
        "admin.password=test-admin-password",
        "admin.session.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "admin.session.ttl-minutes=60"
})
class AdminQuoteSessionItemControllerSecurityTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String SESSION_ID = "00000000-0000-0000-0000-000000000001";
    private static final String ITEM_ID = "00000000-0000-0000-0000-000000000002";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminQuoteItemPricingService adminQuoteItemPricingService;

    @Test
    void getItems_withoutAdminCookie_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/quote-sessions/{sessionId}/items", SESSION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStats_withoutAdminCookie_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/admin/quote-sessions/{sessionId}/items/print-stats", SESSION_ID)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload(false)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStats_withAdminCookieAndMissingOrigin_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/admin/quote-sessions/{sessionId}/items/print-stats", SESSION_ID)
                        .cookie(loginAndExtractCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload(false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getItems_withAdminCookie_shouldReturnItems() throws Exception {
        when(adminQuoteItemPricingService.getItems(any(UUID.class)))
                .thenReturn(new AdminQuoteItemsResponse());

        mockMvc.perform(get("/api/admin/quote-sessions/{sessionId}/items", SESSION_ID)
                        .cookie(loginAndExtractCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void updateStats_withAdminCookie_shouldReturnUpdatedItems() throws Exception {
        when(adminQuoteItemPricingService.updateStats(eq(UUID.fromString(SESSION_ID)), any()))
                .thenReturn(new AdminQuoteItemsResponse());

        mockMvc.perform(post("/api/admin/quote-sessions/{sessionId}/items/print-stats", SESSION_ID)
                        .cookie(loginAndExtractCookie())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload(true)))
                .andExpect(status().isOk());
    }

    @Test
    void updateStats_withInvalidPayload_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/admin/quote-sessions/{sessionId}/items/print-stats", SESSION_ID)
                        .cookie(loginAndExtractCookie())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"persist":false,"items":[{"itemId":"%s","printTimeSeconds":0,"materialGrams":0}]}
                                """.formatted(ITEM_ID)))
                .andExpect(status().isBadRequest());
    }

    private String validPayload(boolean persist) {
        return """
                {"persist":%s,"items":[{"itemId":"%s","printTimeSeconds":3600,"materialGrams":100.5}]}
                """.formatted(persist, ITEM_ID);
    }

    private Cookie loginAndExtractCookie() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/admin/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.44");
                            return req;
                        })
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        String[] parts = setCookie.split(";", 2);
        String[] keyValue = parts[0].split("=", 2);
        return new Cookie(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
    }

    @TestConfiguration
    static class TransactionTestConfig {
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                    // No-op transaction manager for WebMvc security tests.
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                    // No-op transaction manager for WebMvc security tests.
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                    // No-op transaction manager for WebMvc security tests.
                }
            };
        }
    }
}
