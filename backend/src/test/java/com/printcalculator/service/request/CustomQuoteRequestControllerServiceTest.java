package com.printcalculator.service.request;

import com.printcalculator.dto.QuoteRequestDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomQuoteRequestControllerServiceTest {

    @Mock
    private CustomQuoteRequestRepository requestRepo;
    @Mock
    private CustomQuoteRequestAttachmentService attachmentService;
    @Mock
    private CustomQuoteRequestNotificationService notificationService;

    @InjectMocks
    private CustomQuoteRequestControllerService service;

    @Test
    void createCustomQuoteRequest_withMissingConsents_shouldThrowBadRequest() throws Exception {
        QuoteRequestDto dto = buildRequest(false, true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.createCustomQuoteRequest(dto, List.of())
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(requestRepo, attachmentService, notificationService);
    }

    @Test
    void createCustomQuoteRequest_withValidPayload_shouldPersistAndDelegate() throws Exception {
        UUID requestId = UUID.randomUUID();
        QuoteRequestDto dto = buildRequest(true, true);
        List<MultipartFile> files = List.of();

        when(requestRepo.save(any(CustomQuoteRequest.class))).thenAnswer(invocation -> {
            CustomQuoteRequest request = invocation.getArgument(0);
            request.setId(requestId);
            return request;
        });
        when(attachmentService.storeAttachments(any(CustomQuoteRequest.class), eq(files))).thenReturn(2);

        CustomQuoteRequest saved = service.createCustomQuoteRequest(dto, files);

        assertNotNull(saved);
        assertEquals(requestId, saved.getId());
        assertEquals("PENDING", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        verify(requestRepo).save(any(CustomQuoteRequest.class));
        verify(attachmentService).storeAttachments(saved, files);
        verify(notificationService).sendNotifications(saved, 2, "de-CH");
    }

    @Test
    void getCustomQuoteRequest_shouldDelegateToRepository() {
        UUID requestId = UUID.randomUUID();
        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setId(requestId);
        when(requestRepo.findById(requestId)).thenReturn(Optional.of(request));

        Optional<CustomQuoteRequest> result = service.getCustomQuoteRequest(requestId);

        assertEquals(Optional.of(request), result);
        verify(requestRepo).findById(requestId);
    }

    private QuoteRequestDto buildRequest(boolean acceptTerms, boolean acceptPrivacy) {
        QuoteRequestDto dto = new QuoteRequestDto();
        dto.setRequestType("PRINT_SERVICE");
        dto.setCustomerType("PRIVATE");
        dto.setLanguage("de-CH");
        dto.setEmail("customer@example.com");
        dto.setPhone("+41910000000");
        dto.setName("Mario Rossi");
        dto.setCompanyName("3D Fab SA");
        dto.setContactPerson("Mario Rossi");
        dto.setMessage("Vorrei una quotazione.");
        dto.setAcceptTerms(acceptTerms);
        dto.setAcceptPrivacy(acceptPrivacy);
        return dto;
    }
}
