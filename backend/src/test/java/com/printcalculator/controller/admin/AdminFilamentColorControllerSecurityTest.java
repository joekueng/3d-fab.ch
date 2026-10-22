package com.printcalculator.controller.admin;

import com.printcalculator.config.AllowedOriginService;
import com.printcalculator.config.CorsConfig;
import com.printcalculator.config.SecurityConfig;
import com.printcalculator.dto.AdminTranslateFilamentColorsResponse;
import com.printcalculator.service.admin.AdminFilamentControllerService;
import com.printcalculator.service.admin.AdminFilamentColorTranslationService;
import com.printcalculator.security.AdminCsrfProtectionFilter;
import com.printcalculator.security.AdminLoginThrottleService;
import com.printcalculator.security.AdminSessionAuthenticationFilter;
import com.printcalculator.security.AdminSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminAuthController.class, AdminFilamentController.class})
@Import({
        CorsConfig.class,
        AllowedOriginService.class,
        SecurityConfig.class,
        AdminCsrfProtectionFilter.class,
        AdminSessionAuthenticationFilter.class,
        AdminSessionService.class,
        AdminLoginThrottleService.class,
        AdminFilamentColorControllerSecurityTest.TransactionTestConfig.class
})
@TestPropertySource(properties = {
        "admin.password=test-admin-password",
        "admin.session.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "admin.session.ttl-minutes=60"
})
class AdminFilamentColorControllerSecurityTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminFilamentControllerService filamentControllerService;

    @MockitoBean
    private AdminFilamentColorTranslationService translationService;

    @Test
    void translateColors_withoutAdminCookie_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/admin/filaments/translate-colors")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void translateColors_withAdminCookieAndMissingOrigin_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/admin/filaments/translate-colors")
                        .cookie(loginAndExtractCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_INVALID"));
    }

    @Test
    void translateColors_withAdminCookie_shouldReturnTranslations() throws Exception {
        when(translationService.translateColors())
                .thenReturn(new AdminTranslateFilamentColorsResponse(3, 10, 20));

        mockMvc.perform(post("/api/admin/filaments/translate-colors")
                        .cookie(loginAndExtractCookie())
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translatedColors").value(3))
                .andExpect(jsonPath("$.filamentVariants").value(10))
                .andExpect(jsonPath("$.shopVariants").value(20));
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
