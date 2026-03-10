package com.printcalculator.service.shop;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShopCartCookieService {
    public static final String COOKIE_NAME = "shop_cart_session";
    private static final String COOKIE_PATH = "/api/shop";

    private final long cookieTtlDays;
    private final boolean secureCookie;
    private final String sameSite;

    public ShopCartCookieService(
            @Value("${shop.cart.cookie.ttl-days:30}") long cookieTtlDays,
            @Value("${shop.cart.cookie.secure:false}") boolean secureCookie,
            @Value("${shop.cart.cookie.same-site:Lax}") String sameSite
    ) {
        this.cookieTtlDays = cookieTtlDays;
        this.secureCookie = secureCookie;
        this.sameSite = sameSite;
    }

    public Optional<UUID> extractSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }

        for (Cookie cookie : cookies) {
            if (!COOKIE_NAME.equals(cookie.getName())) {
                continue;
            }
            try {
                String value = cookie.getValue();
                if (value == null || value.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public boolean hasCartCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    public ResponseCookie buildSessionCookie(UUID sessionId) {
        return ResponseCookie.from(COOKIE_NAME, sessionId.toString())
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .maxAge(Duration.ofDays(Math.max(cookieTtlDays, 1)))
                .build();
    }

    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .path(COOKIE_PATH)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(sameSite)
                .maxAge(Duration.ZERO)
                .build();
    }

    public long getCookieTtlDays() {
        return cookieTtlDays;
    }
}
