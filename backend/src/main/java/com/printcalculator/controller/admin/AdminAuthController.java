package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminLoginRequest;
import com.printcalculator.security.AdminLoginThrottleService;
import com.printcalculator.security.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.OptionalLong;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminSessionService adminSessionService;
    private final AdminLoginThrottleService adminLoginThrottleService;

    public AdminAuthController(
            AdminSessionService adminSessionService,
            AdminLoginThrottleService adminLoginThrottleService
    ) {
        this.adminSessionService = adminSessionService;
        this.adminLoginThrottleService = adminLoginThrottleService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        String clientKey = adminLoginThrottleService.resolveClientKey(httpRequest);
        OptionalLong remainingLock = adminLoginThrottleService.getRemainingLockSeconds(clientKey);
        if (remainingLock.isPresent()) {
            long retryAfter = remainingLock.getAsLong();
            return ResponseEntity.status(429)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .body(Map.of(
                            "authenticated", false,
                            "retryAfterSeconds", retryAfter
                    ));
        }

        if (!adminSessionService.isPasswordValid(request.getPassword())) {
            long retryAfter = adminLoginThrottleService.registerFailure(clientKey);
            return ResponseEntity.status(401)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .body(Map.of(
                            "authenticated", false,
                            "retryAfterSeconds", retryAfter
                    ));
        }

        adminLoginThrottleService.reset(clientKey);
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
