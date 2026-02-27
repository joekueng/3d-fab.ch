package com.printcalculator.controller;

import com.printcalculator.dto.AdminLoginRequest;
import com.printcalculator.security.AdminSessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminSessionService adminSessionService;

    public AdminAuthController(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletResponse response
    ) {
        if (!adminSessionService.isPasswordValid(request.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }

        String token = adminSessionService.createSessionToken();
        response.addHeader(HttpHeaders.SET_COOKIE, adminSessionService.buildLoginCookie(token).toString());

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "expiresInMinutes", adminSessionService.getSessionTtlMinutes()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, adminSessionService.buildLogoutCookie().toString());
        return ResponseEntity.ok(Map.of("authenticated", false));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me() {
        return ResponseEntity.ok(Map.of("authenticated", true));
    }
}
