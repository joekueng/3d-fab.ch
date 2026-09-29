package com.printcalculator.controller.admin;

import com.printcalculator.config.AllowedOriginService;
import com.printcalculator.config.CorsConfig;
import com.printcalculator.config.SecurityConfig;
import com.printcalculator.dto.QuoteSessionAttachmentDto;
import com.printcalculator.security.AdminCsrfProtectionFilter;
import com.printcalculator.security.AdminLoginThrottleService;
import com.printcalculator.security.AdminSessionAuthenticationFilter;
import com.printcalculator.security.AdminSessionService;
import com.printcalculator.service.quote.QuoteSessionAttachmentService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminAuthController.class, AdminQuoteSessionAttachmentController.class})
@Import({
        CorsConfig.class,
        AllowedOriginService.class,
        SecurityConfig.class,
        AdminCsrfProtectionFilter.class,
        AdminSessionAuthenticationFilter.class,
        AdminSessionService.class,
        AdminLoginThrottleService.class,
        AdminQuoteSessionAttachmentControllerSecurityTest.TransactionTestConfig.class
})
@TestPropertySource(properties = {
        "admin.password=test-admin-password",
        "admin.session.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "admin.session.ttl-minutes=60"
})
class AdminQuoteSessionAttachmentControllerSecurityTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";
    private static final String SESSION_ID = "00000000-0000-0000-0000-000000000011";
    private static final String ATTACHMENT_ID = "00000000-0000-0000-0000-000000000012";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuoteSessionAttachmentService attachmentService;

    @Test
    void listAttachments_withoutAdminCookie_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/quote-sessions/{sessionId}/attachments", SESSION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadAttachments_withoutAdminCookie_shouldReturn401() throws Exception {
        mockMvc.perform(multipart("/api/admin/quote-sessions/{sessionId}/attachments", SESSION_ID)
                        .file(new MockMultipartFile("files", "photo.png", "image/png", "data".getBytes()))
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previewAttachment_withoutAdminCookie_shouldReturn401() throws Exception {
        mockMvc.perform(get(
                        "/api/admin/quote-sessions/{sessionId}/attachments/{attachmentId}/preview",
                        SESSION_ID,
                        ATTACHMENT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadAttachments_withAdminCookieAndMissingOrigin_shouldReturn403() throws Exception {
        mockMvc.perform(multipart("/api/admin/quote-sessions/{sessionId}/attachments", SESSION_ID)
                        .file(new MockMultipartFile("files", "photo.png", "image/png", "data".getBytes()))
                        .cookie(loginAndExtractCookie()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAttachments_withAdminCookie_shouldReturnAttachments() throws Exception {
        when(attachmentService.list(UUID.fromString(SESSION_ID)))
                .thenReturn(List.of(new QuoteSessionAttachmentDto()));

        mockMvc.perform(get("/api/admin/quote-sessions/{sessionId}/attachments", SESSION_ID)
                        .cookie(loginAndExtractCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void uploadAttachments_withAdminCookie_shouldReturnSavedAttachments() throws Exception {
        when(attachmentService.upload(eq(UUID.fromString(SESSION_ID)), anyList()))
                .thenReturn(List.of(new QuoteSessionAttachmentDto()));

        mockMvc.perform(multipart("/api/admin/quote-sessions/{sessionId}/attachments", SESSION_ID)
                        .file(new MockMultipartFile("files", "photo.png", "image/png", "data".getBytes()))
                        .cookie(loginAndExtractCookie())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isOk());
    }

    @Test
    void previewAttachment_withAdminCookie_shouldReturnPreview() throws Exception {
        when(attachmentService.loadPreview(
                UUID.fromString(SESSION_ID),
                UUID.fromString(ATTACHMENT_ID)
        )).thenReturn(new QuoteSessionAttachmentService.AttachmentPreview(
                new ByteArrayResource(new byte[]{1, 2, 3}),
                "photo.png",
                MediaType.IMAGE_PNG_VALUE,
                3
        ));

        mockMvc.perform(get(
                        "/api/admin/quote-sessions/{sessionId}/attachments/{attachmentId}/preview",
                        SESSION_ID,
                        ATTACHMENT_ID)
                        .cookie(loginAndExtractCookie()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteAttachment_withAdminCookie_shouldReturn204() throws Exception {
        mockMvc.perform(delete(
                        "/api/admin/quote-sessions/{sessionId}/attachments/{attachmentId}",
                        SESSION_ID,
                        ATTACHMENT_ID)
                        .cookie(loginAndExtractCookie())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isNoContent());
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
