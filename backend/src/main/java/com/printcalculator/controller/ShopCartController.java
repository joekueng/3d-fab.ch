package com.printcalculator.controller;

import com.printcalculator.dto.ShopCartAddItemRequest;
import com.printcalculator.dto.ShopCartUpdateItemRequest;
import com.printcalculator.service.shop.ShopCartCookieService;
import com.printcalculator.service.shop.ShopCartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/shop/cart")
public class ShopCartController {
    private final ShopCartService shopCartService;
    private final ShopCartCookieService shopCartCookieService;

    public ShopCartController(ShopCartService shopCartService, ShopCartCookieService shopCartCookieService) {
        this.shopCartService = shopCartService;
        this.shopCartCookieService = shopCartCookieService;
    }

    @GetMapping
    public ResponseEntity<?> getCart(HttpServletRequest request, HttpServletResponse response) {
        ShopCartService.CartResult result = shopCartService.loadCart(request);
        applyCookie(response, result);
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(HttpServletRequest request,
                                     HttpServletResponse response,
                                     @Valid @RequestBody ShopCartAddItemRequest payload) {
        ShopCartService.CartResult result = shopCartService.addItem(request, payload);
        applyCookie(response, result);
        return ResponseEntity.ok(result.response());
    }

    @PatchMapping("/items/{lineItemId}")
    public ResponseEntity<?> updateItem(HttpServletRequest request,
                                        HttpServletResponse response,
                                        @PathVariable UUID lineItemId,
                                        @Valid @RequestBody ShopCartUpdateItemRequest payload) {
        ShopCartService.CartResult result = shopCartService.updateItem(request, lineItemId, payload);
        applyCookie(response, result);
        return ResponseEntity.ok(result.response());
    }

    @DeleteMapping("/items/{lineItemId}")
    public ResponseEntity<?> removeItem(HttpServletRequest request,
                                        HttpServletResponse response,
                                        @PathVariable UUID lineItemId) {
        ShopCartService.CartResult result = shopCartService.removeItem(request, lineItemId);
        applyCookie(response, result);
        return ResponseEntity.ok(result.response());
    }

    @DeleteMapping
    public ResponseEntity<?> clearCart(HttpServletRequest request, HttpServletResponse response) {
        ShopCartService.CartResult result = shopCartService.clearCart(request);
        applyCookie(response, result);
        return ResponseEntity.ok(result.response());
    }

    private void applyCookie(HttpServletResponse response, ShopCartService.CartResult result) {
        if (result.clearCookie()) {
            response.addHeader(HttpHeaders.SET_COOKIE, shopCartCookieService.buildClearCookie().toString());
            return;
        }
        if (result.sessionId() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, shopCartCookieService.buildSessionCookie(result.sessionId()).toString());
        }
    }
}
