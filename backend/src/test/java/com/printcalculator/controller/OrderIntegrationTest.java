package com.printcalculator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.CreateOrderRequest;
import com.printcalculator.dto.CustomerDto;
import com.printcalculator.dto.AddressDto;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.boot.test.mock.mockito.MockBean;
import com.printcalculator.service.ClamAVService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderIntegrationTest {

    @MockBean
    private ClamAVService clamAVService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuoteSessionRepository sessionRepository;

    @Autowired
    private QuoteLineItemRepository lineItemRepository;

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private ObjectMapper objectMapper;

    private UUID sessionId;
    private UUID lineItemId;
    private final String TEST_FILENAME = "test_model.stl";

    @BeforeEach
    void setup() throws Exception {
        // Mock ClamAV to always return true (safe)
        when(clamAVService.scan(any())).thenReturn(true);

        // 1. Create Quote Session
        QuoteSession session = new QuoteSession();
        session.setStatus("ACTIVE");
        session.setMaterialCode("PLA");
        session.setPricingVersion("v1");
        session.setCreatedAt(OffsetDateTime.now());
        session.setExpiresAt(OffsetDateTime.now().plusDays(7));
        session.setSetupCostChf(BigDecimal.valueOf(5.00));
        session.setSupportsEnabled(false);
        session = sessionRepository.save(session);
        this.sessionId = session.getId();

        // 2. Create Dummy File on Disk (storage_quotes)
        Path sessionDir = Paths.get("storage_quotes", sessionId.toString());
        Files.createDirectories(sessionDir);
        Path filePath = sessionDir.resolve(UUID.randomUUID() + ".stl");
        Files.writeString(filePath, "dummy content");

        // 3. Create Quote Line Item
        QuoteLineItem item = new QuoteLineItem();
        item.setQuoteSession(session);
        item.setStatus("READY");
        item.setOriginalFilename(TEST_FILENAME);
        item.setStoredPath(filePath.toString());
        item.setQuantity(2);
        item.setPrintTimeSeconds(120);
        item.setMaterialGrams(BigDecimal.valueOf(10.5));
        item.setUnitPriceChf(BigDecimal.valueOf(10.00));
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());
        item = lineItemRepository.save(item);
        this.lineItemId = item.getId();
    }

    @AfterEach
    void cleanup() throws Exception {
        // Cleanup generated files
        FileSystemUtils.deleteRecursively(Paths.get("storage_quotes"));
        FileSystemUtils.deleteRecursively(Paths.get("storage_orders"));
        
        // Clean DB
        orderRepository.deleteAll();
        lineItemRepository.deleteAll();
        sessionRepository.deleteAll();
    }

    @Test
    void testCreateOrderFromQuote_ShouldCopyFilesAndUpdateStatus() throws Exception {
        // Prepare Request
        CreateOrderRequest request = new CreateOrderRequest();
        
        CustomerDto customer = new CustomerDto();
        customer.setEmail("integration@test.com");
        customer.setCustomerType("PRIVATE");
        request.setCustomer(customer);
        
        AddressDto billing = new AddressDto();
        billing.setFirstName("John");
        billing.setLastName("Doe");
        billing.setAddressLine1("Street 1");
        billing.setCity("City");
        billing.setZip("1000");
        billing.setCountryCode("CH");
        request.setBillingAddress(billing);
        
        request.setShippingSameAsBilling(true);

        // Execute Request
        mockMvc.perform(post("/api/orders/from-quote/" + sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Verify Session Status
        QuoteSession updatedSession = sessionRepository.findById(sessionId).orElseThrow();
        assertEquals("CONVERTED", updatedSession.getStatus(), "Session status should be CONVERTED");
        assertNotNull(updatedSession.getConvertedOrderId(), "Converted Order ID should be set");

        UUID orderId = updatedSession.getConvertedOrderId();

        // Verify File Copy
        Path orderStorageDir = Paths.get("storage_orders");
        // We need to find the specific file. Structure: storage_orders/orderId/3d-files/orderItemId/filename
        // Since we don't know OrderItemId easily without querying DB, let's walk the dir.
        
        try (var stream = Files.walk(orderStorageDir)) {
            boolean fileFound = stream
                    .filter(Files::isRegularFile)
                    .anyMatch(path -> {
                        try {
                            return Files.readString(path).equals("dummy content");
                        } catch (Exception e) {
                            return false;
                        }
                    });
            assertTrue(fileFound, "The file should have been copied to storage_orders with correct content");
        }
    }
}
