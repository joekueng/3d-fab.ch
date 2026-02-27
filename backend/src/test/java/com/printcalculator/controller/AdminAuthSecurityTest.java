package com.printcalculator.controller;

import com.printcalculator.config.SecurityConfig;
import com.printcalculator.controller.admin.AdminAuthController;
import com.printcalculator.security.AdminLoginThrottleService;
import com.printcalculator.security.AdminSessionAuthenticationFilter;
import com.printcalculator.security.AdminSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminAuthController.class)
@Import({
        SecurityConfig.class,
        AdminSessionAuthenticationFilter.class,
        AdminSessionService.class,
        AdminLoginThrottleService.class
})
@TestPropertySource(properties = {
        "admin.password=test-admin-password",
        "admin.session.secret=0123456789abcdef0123456789abcdef",
        "admin.session.ttl-minutes=60"
})
class AdminAuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginOk_ShouldReturnCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.1");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.contains("admin_session="));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Lax"));
    }

    @Test
    void loginKo_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.2");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.retryAfterSeconds").value(2));
    }

    @Test
    void loginKoSecondAttemptDuringLock_ShouldReturnTooManyRequests() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.3");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.retryAfterSeconds").value(2));

        mockMvc.perform(post("/api/admin/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.3");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void adminAccessWithoutCookie_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminAccessWithValidCookie_ShouldReturn200() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/admin/auth/login")
                        .with(req -> {
                            req.setRemoteAddr("10.0.0.4");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-admin-password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);

        Cookie adminCookie = toCookie(setCookie);
        mockMvc.perform(get("/api/admin/auth/me").cookie(adminCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));
    }

    private Cookie toCookie(String setCookieHeader) {
        String[] parts = setCookieHeader.split(";", 2);
        String[] keyValue = parts[0].split("=", 2);
        return new Cookie(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
    }
}
