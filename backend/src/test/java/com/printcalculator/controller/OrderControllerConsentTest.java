package com.printcalculator.controller;

import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.dto.PublicOrderDto;
import com.printcalculator.service.LegalConsentRequestService;
import com.printcalculator.service.order.OrderControllerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderControllerConsentTest {
    @Test
    void checkoutPassesServerObservedIpToOrderCreation() throws Exception {
        var service = mock(OrderControllerService.class);
        var controller = new OrderController(service, new LegalConsentRequestService(false, ""));
        UUID sessionId = UUID.randomUUID();
        when(service.createOrderFromQuote(eq(sessionId), any(CreateOrderRequest.class), eq("203.0.113.42")))
                .thenReturn(new PublicOrderDto());

        MockMvcBuilders.standaloneSetup(controller).build().perform(
                        post("/api/orders/from-quote/{id}", sessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"acceptTerms\":true,\"acceptPrivacy\":true}")
                                .header("X-Forwarded-For", "198.51.100.99")
                                .with(request -> { request.setRemoteAddr("203.0.113.42"); return request; }))
                .andExpect(status().isOk());

        verify(service).createOrderFromQuote(eq(sessionId), any(CreateOrderRequest.class), eq("203.0.113.42"));
    }
}
