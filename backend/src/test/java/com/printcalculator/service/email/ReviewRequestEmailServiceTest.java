package com.printcalculator.service.email;

import com.printcalculator.dto.ReviewRequestPreviewDto;
import com.printcalculator.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewRequestEmailServiceTest {
    @Mock EmailNotificationService emailNotificationService;
    @Mock EmailAuditService emailAuditService;
    @InjectMocks ReviewRequestEmailService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "reviewUrl", "https://g.page/r/CXamfIi-St1wEAI/review");
    }

    @ParameterizedTest
    @ValueSource(strings = {"it", "en", "de", "fr"})
    void previewUsesOrderLanguageAndOptionalPhotos(String language) {
        Order order = order(language);
        ReviewRequestPreviewDto preview = service.preview(order);

        assertEquals("client@example.test", preview.recipient());
        assertEquals("https://g.page/r/CXamfIi-St1wEAI/review", preview.reviewUrl());
        assertTrue(preview.photoNote().length() > 20);
        assertTrue(preview.request().length() > 40);
    }

    @Test
    void sendUsesPreviewCopyAndRecordsAdminAttempt() {
        Order order = order("it");
        ReviewRequestPreviewDto preview = service.preview(order);
        EmailSendResult result = EmailSendResult.sent(OffsetDateTime.now(), OffsetDateTime.now());
        when(emailNotificationService.sendEmail(eq(preview.recipient()), eq(preview.subject()), eq("review-request"), anyMap()))
                .thenReturn(result);

        service.send(order);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.forClass(Map.class);
        verify(emailNotificationService).sendEmail(eq(preview.recipient()), eq(preview.subject()), eq("review-request"), context.capture());
        assertEquals(preview.request(), context.getValue().get("requestText"));
        assertEquals(preview.photoNote(), context.getValue().get("photoNoteText"));
        assertEquals(preview.reviewUrl(), context.getValue().get("reviewUrl"));
        verify(emailAuditService).recordOrderEmail(order, EmailAuditService.EVENT_GOOGLE_REVIEW_REQUEST_CUSTOMER,
                EmailAuditService.ORIGIN_ADMIN, preview.recipient(), preview.subject(), "review-request", null, result, null);
    }

    private Order order(String language) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerEmail("client@example.test");
        order.setBillingFirstName("Alex");
        order.setPreferredLanguage(language);
        return order;
    }
}
