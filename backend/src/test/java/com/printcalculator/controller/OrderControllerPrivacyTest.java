package com.printcalculator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.OrderService;
import com.printcalculator.service.LegalConsentRequestService;
import com.printcalculator.service.order.OrderCadFileService;
import com.printcalculator.service.order.OrderControllerService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.payment.TwintPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerPrivacyTest {

    @Mock
    private OrderService orderService;
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private OrderItemRepository orderItemRepo;
    @Mock
    private InvoicePdfRenderingService invoiceService;
    @Mock
    private QrBillService qrBillService;
    @Mock
    private TwintPaymentService twintPaymentService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentRepository paymentRepo;
    @Mock
    private OrderCadFileService orderCadFileService;

    private OrderController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderControllerService orderControllerService = new OrderControllerService(
                orderService,
                orderRepo,
                orderItemRepo,
                invoiceService,
                qrBillService,
                twintPaymentService,
                paymentService,
                paymentRepo,
                orderCadFileService
        );
        controller = new OrderController(orderControllerService, new LegalConsentRequestService(false, ""));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void confirmationDownloadUsesCurrentTemplateOnEveryRequest() {
        UUID id = UUID.randomUUID();
        Order order = buildOrder(id, "PENDING_PAYMENT");
        when(orderRepo.findById(id)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(id)).thenReturn(List.of());
        byte[] currentPdf = "current PDF without logo subtitle".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(invoiceService.generateDocumentPdf(order, List.of(), true, qrBillService, null)).thenReturn(currentPdf);
        var response = controller.getConfirmation(id);
        org.junit.jupiter.api.Assertions.assertArrayEquals(currentPdf, response.getBody());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        controller.getConfirmation(id);
        org.mockito.Mockito.verify(invoiceService, org.mockito.Mockito.times(2))
                .generateDocumentPdf(order, List.of(), true, qrBillService, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "REPORTED"})
    void confirmationDownload_remainsAvailableWhilePaymentIsUnconfirmed(String paymentStatus) throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");
        Payment payment = new Payment();
        payment.setStatus(paymentStatus);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());
        byte[] pdf = "QR payment PDF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(invoiceService.generateDocumentPdf(order, List.of(), true, qrBillService, payment)).thenReturn(pdf);

        mockMvc.perform(get("/api/orders/{id}/confirmation", orderId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(pdf))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAID", "IN_PRODUCTION", "SHIPPED", "COMPLETED", "CANCELLED", "REFUNDED", "UNKNOWN"})
    void confirmationDownload_isUnavailableOutsidePendingPayment(String orderStatus) throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(buildOrder(orderId, orderStatus)));

        assertConfirmationUnavailable(orderId);
        verifyNoInteractions(invoiceService, orderItemRepo);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEIVED", "COMPLETED", "REFUNDED", "UNKNOWN"})
    void confirmationDownload_isUnavailableForSettledOrUnexpectedPaymentStatus(String paymentStatus) throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setStatus(paymentStatus);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(buildOrder(orderId, "PENDING_PAYMENT")));
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        assertConfirmationUnavailable(orderId);
        verifyNoInteractions(invoiceService, orderItemRepo);
    }

    @Test
    void confirmationDownload_isUnavailableWhenOrderHasPaidTimestamp() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");
        order.setPaidAt(OffsetDateTime.now());
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));

        assertConfirmationUnavailable(orderId);
        verifyNoInteractions(invoiceService, orderItemRepo);
    }

    @Test
    void confirmationDownload_isUnavailableWhenPaymentHasReceivedTimestamp() throws Exception {
        UUID orderId = UUID.randomUUID();
        Payment payment = new Payment();
        payment.setStatus("PENDING");
        payment.setReceivedAt(OffsetDateTime.now());
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(buildOrder(orderId, "PENDING_PAYMENT")));
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        assertConfirmationUnavailable(orderId);
        verifyNoInteractions(invoiceService, orderItemRepo);
    }

    @Test
    void confirmationDownload_isUnavailableForMissingOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        assertConfirmationUnavailable(orderId);
        verifyNoInteractions(invoiceService, orderItemRepo);
    }

    private void assertConfirmationUnavailable(UUID orderId) throws Exception {
        mockMvc.perform(get("/api/orders/{id}/confirmation", orderId))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "PAID", "IN_PRODUCTION", "SHIPPED", "COMPLETED", "CANCELLED", "REFUNDED"})
    void getOrder_excludesCustomerDataInEveryStatus(String orderStatus) throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, orderStatus);
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID());
        item.setOriginalFilename("part.stl");
        item.setMaterialCode("PLA");
        item.setQuantity(2);
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of(item));

        MvcResult result = mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk()).andReturn();

        JsonNode response = assertPublicResponse(result, orderId, orderStatus);
        assertEquals("CHF", response.path("currency").asText());
        assertEquals(12.5, response.path("totalChf").asDouble());
        assertEquals(1, response.path("items").size());
        JsonNode publicItem = response.path("items").get(0);
        assertEquals(item.getId().toString(), publicItem.path("id").asText());
        assertEquals("part.stl", publicItem.path("displayName").asText());
        assertEquals("PLA", publicItem.path("materialCode").asText());
        assertEquals(2, publicItem.path("quantity").asInt());
        assertFalse(response.path("informationToken").isTextual());
    }

    @Test
    void checkout_excludesCustomerDataAndPreservesInformationCredential() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");
        when(orderService.createOrderFromQuote(eq(sessionId), any(CreateOrderRequest.class), any()))
                .thenReturn(order);
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());

        MvcResult result = mockMvc.perform(post("/api/orders/from-quote/{id}", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acceptTerms\":true,\"acceptPrivacy\":true}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode response = assertPublicResponse(result, orderId, "PENDING_PAYMENT");
        assertEquals(order.getInformationToken(), response.path("informationToken").asText());
    }

    @Test
    void reportPayment_excludesCustomerDataAndInformationCredential() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = buildOrder(orderId, "PENDING_PAYMENT");
        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of());

        MvcResult result = mockMvc.perform(post("/api/orders/{id}/payments/report", orderId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"TWINT\"}"))
                .andExpect(status().isOk()).andReturn();

        JsonNode response = assertPublicResponse(result, orderId, "PENDING_PAYMENT");
        assertFalse(response.path("informationToken").isTextual());
        org.mockito.Mockito.verify(paymentService).reportPayment(orderId, "TWINT");
    }

    private JsonNode assertPublicResponse(MvcResult result, UUID orderId, String orderStatus) throws Exception {
        JsonNode response = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertEquals(orderId.toString(), response.path("id").asText());
        assertEquals(orderStatus, response.path("status").asText());
        for (String field : List.of("customerEmail", "customerPhone", "billingCustomerType", "billingAddress",
                "shippingAddress", "shippingSameAsBilling", "sourceRequestId", "emailLogs")) {
            assertFalse(response.has(field), "Public response must not contain " + field);
        }
        return response;
    }

    private Order buildOrder(UUID orderId, String status) {
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(status);
        order.setCustomerEmail("customer@example.com");
        order.setCustomerPhone("+41790000000");
        order.setBillingCustomerType("PRIVATE");
        order.setBillingFirstName("Joe");
        order.setBillingLastName("Kung");
        order.setBillingAddressLine1("Via G. Pioda 1");
        order.setBillingZip("6900");
        order.setBillingCity("Lugano");
        order.setBillingCountryCode("CH");
        order.setShippingSameAsBilling(false);
        order.setShippingFirstName("Test");
        order.setShippingLastName("Recipient");
        order.setShippingAddressLine1("Via Test 2");
        order.setShippingZip("6500");
        order.setShippingCity("Bellinzona");
        order.setShippingCountryCode("CH");
        order.setSourceRequestId(UUID.randomUUID());
        order.setCurrency("CHF");
        order.setTotalChf(new java.math.BigDecimal("12.50"));
        return order;
    }
}
