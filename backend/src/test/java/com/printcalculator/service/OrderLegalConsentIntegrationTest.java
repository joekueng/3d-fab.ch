package com.printcalculator.service;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.dto.CustomerDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.CustomerRepository;
import com.printcalculator.repository.LegalConsentRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.information.OrderInformationService;
import com.printcalculator.service.payment.InvoicePdfRenderingService;
import com.printcalculator.service.payment.PaymentService;
import com.printcalculator.service.payment.QrBillService;
import com.printcalculator.service.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
        "spring.datasource.url=jdbc:h2:mem:order-consent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "logging.level.org.hibernate.SQL=OFF"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderService.class, LegalConsentService.class, OrderLegalConsentIntegrationTest.H2LockSyntax.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderLegalConsentIntegrationTest {
    // Reuse PaymentWorkflowIntegrationTest's PostgreSQL text mapping/H2 lock convention.
    @org.springframework.boot.test.context.TestConfiguration
    static class H2LockSyntax {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer h2Locks() {
            return properties -> properties.put("hibernate.session_factory.statement_inspector",
                    (org.hibernate.resource.jdbc.spi.StatementInspector) sql -> sql.replace("for no key update", "for update"));
        }
    }

    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired QuoteSessionRepository sessions;
    @Autowired CustomerRepository customers;
    @Autowired LegalConsentRepository consents;
    @MockitoBean StorageService storage;
    @MockitoBean InvoicePdfRenderingService invoices;
    @MockitoBean QrBillService qrBills;
    @MockitoBean PaymentService payments;
    @MockitoBean QuoteSessionTotalsService totals;
    @MockitoBean MaterialPrintCompatibilityService materials;
    @MockitoBean OrderInformationService information;

    UUID sessionId;

    @BeforeEach
    void prepareCadOrder() {
        QuoteSession session = new QuoteSession();
        session.setStatus("CAD_ACTIVE");
        session.setMaterialCode("PLA");
        session.setPricingVersion("v1");
        session.setSetupCostChf(BigDecimal.ZERO);
        session.setExpiresAt(OffsetDateTime.now().plusMonths(3));
        session.setCadHours(BigDecimal.ONE);
        session.setCadHourlyRateChf(new BigDecimal("50"));
        sessionId = sessions.saveAndFlush(session).getId();
        when(totals.calculateCadTotal(any())).thenReturn(new BigDecimal("50"));
        when(totals.compute(any(), anyList())).thenReturn(new QuoteSessionTotalsService.QuoteSessionTotals(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50"), new BigDecimal("50"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("50"), BigDecimal.ZERO));
        when(qrBills.generateQrBillSvg(any())).thenReturn(new byte[0]);
        when(invoices.generateDocumentPdf(any(), anyList(), eq(true), eq(qrBills), isNull())).thenReturn(new byte[0]);
    }

    @AfterEach
    void cleanUp() {
        consents.deleteAll();
        orders.deleteAllInBatch();
        sessions.deleteAll();
        customers.deleteAll();
    }

    @Test
    void orderAndAcceptanceCommitTogetherAndCannotBeDuplicated() {
        Order order = service.createOrderFromQuote(sessionId, request(), "203.0.113.42");

        var consent = consents.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals("de", consent.getLanguage());
        assertEquals("buyer@example.test", consent.getEmail());
        assertEquals("203.0.113.42", consent.getClientIp());
        assertEquals(LegalConsentService.TERMS_VERSION, consent.getTermsVersion());
        assertEquals(LegalConsentService.PRIVACY_VERSION, consent.getPrivacyVersion());
        assertTrue(consent.isTermsAccepted());
        assertTrue(consent.isPrivacyAccepted());
        assertNotNull(consent.getAcceptedAt());
        assertNull(consent.getContactRequest());
        assertEquals(order.getId(), sessions.findById(sessionId).orElseThrow().getConvertedOrderId());

        assertThrows(IllegalStateException.class, () -> service.createOrderFromQuote(sessionId, request(), "203.0.113.42"));
        assertEquals(1, consents.count());
        assertThrows(DataIntegrityViolationException.class, () -> consents.saveAndFlush(
                new com.printcalculator.entity.LegalConsent(order, null, true, true, "v2", "v2", "it", "203.0.113.42")));
        assertEquals(1, consents.count());
    }

    @Test
    void failureAfterRecordingAcceptanceRollsBackOrderCustomerAndConsent() {
        when(payments.getOrCreatePaymentForOrder(any(), anyString())).thenThrow(new IllegalStateException("Payment failure"));

        assertThrows(IllegalStateException.class, () -> service.createOrderFromQuote(sessionId, request(), "203.0.113.42"));

        assertEquals(0, consents.count());
        assertEquals(0, orders.count());
        assertEquals(0, customers.count());
        assertNull(sessions.findById(sessionId).orElseThrow().getConvertedOrderId());
    }

    @ParameterizedTest
    @CsvSource({"false,true", "true,false", "false,false"})
    void missingAcceptanceDoesNotCreateOrder(boolean terms, boolean privacy) {
        CreateOrderRequest request = request();
        request.setAcceptTerms(terms);
        request.setAcceptPrivacy(privacy);
        assertThrows(IllegalArgumentException.class, () -> service.createOrderFromQuote(sessionId, request, "203.0.113.42"));
        assertEquals(0, consents.count());
        assertEquals(0, orders.count());
        verifyNoInteractions(payments);
    }

    private CreateOrderRequest request() {
        CustomerDto customer = new CustomerDto();
        customer.setEmail("buyer@example.test");
        customer.setCustomerType("PRIVATE");
        AddressDto address = new AddressDto();
        address.setAddressLine1("Teststrasse 1");
        address.setZip("8000");
        address.setCity("Zurich");
        address.setCountryCode("CH");
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomer(customer);
        request.setBillingAddress(address);
        request.setShippingSameAsBilling(true);
        request.setLanguage("de-CH");
        request.setAcceptTerms(true);
        request.setAcceptPrivacy(true);
        return request;
    }
}
