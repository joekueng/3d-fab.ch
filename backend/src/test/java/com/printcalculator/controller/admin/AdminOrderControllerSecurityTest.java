package com.printcalculator.controller.admin;

import com.printcalculator.config.AllowedOriginService;
import com.printcalculator.config.CorsConfig;
import com.printcalculator.config.SecurityConfig;
import com.printcalculator.dto.AdminContactRequestDto;
import com.printcalculator.dto.AdminOrderSearchQuery;
import com.printcalculator.dto.AdminPageQuery;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.service.admin.AdminOperationsControllerService;
import com.printcalculator.service.order.AdminOrderControllerService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminAuthController.class, AdminOrderController.class,
        AdminOrderInformationController.class, AdminOperationsController.class})
@Import({
        CorsConfig.class,
        AllowedOriginService.class,
        SecurityConfig.class,
        AdminCsrfProtectionFilter.class,
        AdminSessionAuthenticationFilter.class,
        AdminSessionService.class,
        AdminLoginThrottleService.class,
        AdminOrderControllerSecurityTest.TransactionTestConfig.class
})
@TestPropertySource(properties = {
        "admin.password=test-admin-password",
        "admin.session.secret=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "admin.session.ttl-minutes=60"
})
class AdminOrderControllerSecurityTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminOrderControllerService adminOrderControllerService;

    @MockitoBean
    private AdminOperationsControllerService adminOperationsControllerService;

    @MockitoBean
    private com.printcalculator.service.information.OrderInformationService informationService;

    @Test
    void orderSearch_requiresAdminSessionAndTrustedOriginAndReturnsFilteredPage() throws Exception {
        String body = """
                {"pagination":{},"filters":{"q":"Rossi","orderStatus":"PAID"}}
                """;
        mockMvc.perform(post("/api/admin/orders/search").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        Cookie admin = loginAndExtractCookie();
        mockMvc.perform(post("/api/admin/orders/search").cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CSRF_INVALID"));

        OrderDto order = new OrderDto();
        order.setId(UUID.randomUUID());
        var filters = new AdminOrderSearchQuery("Rossi", AdminOrderSearchQuery.OrderStatus.PAID, null, null);
        var pageable = new AdminPageQuery(null, null).toPageable();
        when(adminOrderControllerService.searchOrders(filters, pageable))
                .thenReturn(new PageImpl<>(List.of(order), pageable, 1));
        when(adminOrderControllerService.listOrders()).thenReturn(List.of(order));

        mockMvc.perform(post("/api/admin/orders/search").cookie(admin).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.items[0].id").value(order.getId().toString()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/admin/orders").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(order.getId().toString()));
        mockMvc.perform(get("/api/admin/orders").cookie(admin).param("q", "Rossi"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/orders/search").cookie(admin).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pagination":{"page":-1},"filters":{"q":"%s"}}
                                """.formatted("a".repeat(321))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.length()").value(2));
        mockMvc.perform(post("/api/admin/orders/search").cookie(admin).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pagination":{},"filters":{"paymentStatus":"UNKNOWN"}}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contactRequests_supportsLegacyArrayAndPagedResponse() throws Exception {
        mockMvc.perform(get("/api/admin/contact-requests").param("page", "1"))
                .andExpect(status().isUnauthorized());

        Cookie admin = loginAndExtractCookie();
        AdminContactRequestDto request = new AdminContactRequestDto();
        request.setId(UUID.randomUUID());
        var pageable = new AdminPageQuery(1, 1).toPageable();
        when(adminOperationsControllerService.getContactRequests(pageable))
                .thenReturn(new PageImpl<>(List.of(request), pageable, 2));
        when(adminOperationsControllerService.getContactRequests()).thenReturn(List.of(request));

        mockMvc.perform(get("/api/admin/contact-requests").cookie(admin).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(request.getId().toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/admin/contact-requests").cookie(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(request.getId().toString()));
        mockMvc.perform(get("/api/admin/contact-requests").cookie(admin).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test void reviewRequest_requiresAdminSession() throws Exception {
        UUID orderId = UUID.randomUUID();
        mockMvc.perform(get("/api/admin/orders/{id}/review-request/preview", orderId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/orders/{id}/review-request", orderId)
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test void reviewRequest_sendRequiresTrustedOrigin() throws Exception {
        mockMvc.perform(post("/api/admin/orders/{id}/review-request", UUID.randomUUID())
                        .cookie(loginAndExtractCookie()))
                .andExpect(status().isForbidden());
    }

    @Test void informationWithoutAdminSessionIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/orders/information-unread")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/orders/{id}/information/files/{file}", UUID.randomUUID(), UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
    @Test void informationReadAcknowledgementRequiresTrustedOrigin() throws Exception {
        mockMvc.perform(post("/api/admin/orders/{id}/information/read", UUID.randomUUID()).cookie(loginAndExtractCookie())
                .contentType(MediaType.APPLICATION_JSON).content("{\"entries\":[]}"))
                .andExpect(status().isForbidden());
    }
    @Test void informationReadAcknowledgementAcceptsAuthenticatedSameOriginRequest() throws Exception {
        mockMvc.perform(post("/api/admin/orders/{id}/information/read", UUID.randomUUID()).cookie(loginAndExtractCookie())
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN).contentType(MediaType.APPLICATION_JSON).content("{\"entries\":[]}"))
                .andExpect(status().isOk());
    }

    @Test
    void orderDetails_withoutAdminCookie_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void orderDetails_withAdminCookie_returnsAdministrativeCustomerFields() throws Exception {
        UUID orderId = UUID.randomUUID();
        var dto = new com.printcalculator.dto.OrderDto();
        dto.setId(orderId);
        dto.setCustomerEmail("customer@example.com");
        dto.setCustomerPhone("+41910000000");
        var address = new com.printcalculator.dto.AddressDto();
        address.setFirstName("Test");
        address.setAddressLine1("Via Test 1");
        dto.setBillingAddress(address);
        dto.setShippingAddress(address);
        when(adminOrderControllerService.getOrder(orderId)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/orders/{id}", orderId).cookie(loginAndExtractCookie()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.customerEmail").value("customer@example.com"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.customerPhone").value("+41910000000"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.billingAddress.firstName").value("Test"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.shippingAddress.addressLine1").value("Via Test 1"));
    }

    @Test
    void confirmationDocument_withoutAdminCookie_shouldReturn401() throws Exception {
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(get("/api/admin/orders/{orderId}/documents/confirmation", orderId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirmationDocument_withAdminCookie_shouldReturnPdf() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(adminOrderControllerService.downloadOrderConfirmation(orderId))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .body("confirmation".getBytes()));

        mockMvc.perform(get("/api/admin/orders/{orderId}/documents/confirmation", orderId)
                        .cookie(loginAndExtractCookie()))
                .andExpect(status().isOk())
                .andExpect(content().bytes("confirmation".getBytes()));
    }

    @Test
    void invoiceDocument_withAdminCookie_shouldReturnPdf() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(adminOrderControllerService.downloadOrderInvoice(orderId))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .body("invoice".getBytes()));

        mockMvc.perform(get("/api/admin/orders/{orderId}/documents/invoice", orderId)
                        .cookie(loginAndExtractCookie()))
                .andExpect(status().isOk())
                .andExpect(content().bytes("invoice".getBytes()));
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
