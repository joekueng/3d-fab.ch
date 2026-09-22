package com.printcalculator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.QuoteRequestDto;
import com.printcalculator.event.listener.CustomQuoteRequestEmailListener;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.repository.EmailLogRepository;
import com.printcalculator.service.email.EmailAuditService;
import com.printcalculator.service.email.EmailNotificationService;
import com.printcalculator.service.email.EmailSendResult;
import com.printcalculator.service.request.ContactRequestLocalizationService;
import com.printcalculator.service.request.CustomQuoteRequestAttachmentService;
import com.printcalculator.service.request.CustomQuoteRequestControllerService;
import com.printcalculator.service.request.CustomQuoteRequestNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(excludeAutoConfiguration = JpaRepositoriesAutoConfiguration.class, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:contact-requests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "logging.level.org.hibernate.SQL=OFF",
        "app.mail.contact-request.admin.enabled=true",
        "app.mail.contact-request.admin.address=admin@example.test",
        "app.mail.contact-request.customer.enabled=true"
}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CustomQuoteRequestController.class, CustomQuoteRequestControllerService.class,
        CustomQuoteRequestEmailListener.class,
        CustomQuoteRequestNotificationService.class, ContactRequestLocalizationService.class,
        EmailAuditService.class, CustomQuoteRequestControllerIntegrationTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomQuoteRequestControllerIntegrationTest {
    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = CustomQuoteRequestRepository.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com\\.printcalculator\\.repository\\.(?!(CustomQuoteRequestRepository|EmailLogRepository)$).*"))
    static class Configuration {
        @Bean
        TaskExecutor taskExecutor() {
            // Keep after-commit async listeners deterministic while retaining real transactions.
            return new SyncTaskExecutor();
        }
    }

    @Autowired private CustomQuoteRequestController controller;
    @Autowired private CustomQuoteRequestControllerService service;
    @Autowired private CustomQuoteRequestRepository requests;
    @Autowired private EmailLogRepository emailLogs;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private CustomQuoteRequestAttachmentService attachments;
    @MockitoBean private EmailNotificationService emailSender;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(emailSender.sendEmail(anyString(), anyString(), anyString(), anyMap()))
                .thenAnswer(invocation -> EmailSendResult.sent(OffsetDateTime.now(), OffsetDateTime.now()));
    }

    @AfterEach
    void cleanUp() {
        emailLogs.deleteAll();
        requests.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({"PRIVATE,SENT", "BUSINESS,SENT", "PRIVATE,FAILED", "PRIVATE,SKIPPED"})
    void multipartSubmissionPersistsRequestAndBothEmailAuditRecords(String customerType, String emailStatus) throws Exception {
        EmailSendResult result = switch (emailStatus) {
            case "FAILED" -> EmailSendResult.failed(OffsetDateTime.now(), "Test SMTP failure");
            case "SKIPPED" -> EmailSendResult.skipped(OffsetDateTime.now(), "Email disabled for test");
            default -> EmailSendResult.sent(OffsetDateTime.now(), OffsetDateTime.now());
        };
        when(emailSender.sendEmail(anyString(), anyString(), anyString(), anyMap())).thenReturn(result);

        var response = mvc.perform(multipart("/api/custom-quote-requests")
                        .file(requestPart(customerType)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse();

        UUID requestId = UUID.fromString(mapper.readTree(response.getContentAsString()).get("id").asText());
        assertTrue(requests.existsById(requestId));
        var logs = emailLogs.findByContactRequest_IdOrderByAttemptedAtDesc(requestId);
        assertEquals(2, logs.size());
        assertTrue(logs.stream().allMatch(log -> emailStatus.equals(log.getStatus())));
        assertEquals(1, logs.stream().filter(log -> EmailAuditService.EVENT_CONTACT_REQUEST_ADMIN.equals(log.getEventType())).count());
        assertEquals(1, logs.stream().filter(log -> EmailAuditService.EVENT_CONTACT_REQUEST_CUSTOMER.equals(log.getEventType())).count());
    }

    @Test
    void notificationsWaitUntilTheEnclosingTransactionCommits() {
        UUID requestId = new TransactionTemplate(transactionManager).execute(status -> {
            UUID id = createRequest();
            verifyNoInteractions(emailSender);
            assertEquals(0, emailLogs.count());
            return id;
        });

        assertTrue(requests.existsById(requestId));
        assertEquals(2, emailLogs.findByContactRequest_IdOrderByAttemptedAtDesc(requestId).size());
    }

    @Test
    void rolledBackRequestDoesNotSendOrAuditNotifications() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createRequest();
            status.setRollbackOnly();
        });

        assertEquals(0, requests.count());
        assertEquals(0, emailLogs.count());
        verifyNoInteractions(emailSender);
    }

    @Test
    void attachmentIoFailureRollsBackRequestAndDoesNotSendNotifications() throws Exception {
        when(attachments.storeAttachments(any(), any())).thenThrow(new IOException("Test storage failure"));

        assertThrows(IOException.class, () -> service.createCustomQuoteRequest(requestDto(), List.of()));

        assertEquals(0, requests.count());
        assertEquals(0, emailLogs.count());
        verifyNoInteractions(emailSender);
    }

    @Test
    void unexpectedNotificationFailureDoesNotFailTheAcceptedHttpRequest() throws Exception {
        when(emailSender.sendEmail(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("Test notification failure"));

        mvc.perform(multipart("/api/custom-quote-requests").file(requestPart("PRIVATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertEquals(1, requests.count());
    }

    private UUID createRequest() {
        try {
            return service.createCustomQuoteRequest(requestDto(), List.of()).getId();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private QuoteRequestDto requestDto() {
        QuoteRequestDto dto = new QuoteRequestDto();
        dto.setRequestType("custom");
        dto.setCustomerType("PRIVATE");
        dto.setEmail("customer@example.test");
        dto.setMessage("Vorrei un prodotto personalizzato.");
        dto.setLanguage("it");
        dto.setAcceptTerms(true);
        dto.setAcceptPrivacy(true);
        return dto;
    }

    private MockMultipartFile requestPart(String customerType) throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "requestType", "custom",
                "customerType", customerType,
                "email", "customer@example.test",
                "message", "Vorrei un prodotto personalizzato.",
                "language", "it",
                "acceptTerms", true,
                "acceptPrivacy", true
        ));
        return new MockMultipartFile("request", "blob", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }
}
