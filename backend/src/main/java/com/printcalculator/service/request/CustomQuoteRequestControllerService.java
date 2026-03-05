package com.printcalculator.service.request;

import com.printcalculator.dto.QuoteRequestDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomQuoteRequestControllerService {

    private final CustomQuoteRequestRepository requestRepo;
    private final CustomQuoteRequestAttachmentService attachmentService;
    private final CustomQuoteRequestNotificationService notificationService;

    public CustomQuoteRequestControllerService(CustomQuoteRequestRepository requestRepo,
                                               CustomQuoteRequestAttachmentService attachmentService,
                                               CustomQuoteRequestNotificationService notificationService) {
        this.requestRepo = requestRepo;
        this.attachmentService = attachmentService;
        this.notificationService = notificationService;
    }

    @Transactional
    public CustomQuoteRequest createCustomQuoteRequest(QuoteRequestDto requestDto, List<MultipartFile> files) throws IOException {
        validateConsents(requestDto);

        CustomQuoteRequest request = new CustomQuoteRequest();
        request.setRequestType(requestDto.getRequestType());
        request.setCustomerType(requestDto.getCustomerType());
        request.setEmail(requestDto.getEmail());
        request.setPhone(requestDto.getPhone());
        request.setName(requestDto.getName());
        request.setCompanyName(requestDto.getCompanyName());
        request.setContactPerson(requestDto.getContactPerson());
        request.setMessage(requestDto.getMessage());
        request.setStatus("PENDING");
        request.setCreatedAt(OffsetDateTime.now());
        request.setUpdatedAt(OffsetDateTime.now());

        request = requestRepo.save(request);

        int attachmentsCount = attachmentService.storeAttachments(request, files);
        notificationService.sendNotifications(request, attachmentsCount, requestDto.getLanguage());

        return request;
    }

    public Optional<CustomQuoteRequest> getCustomQuoteRequest(UUID id) {
        return requestRepo.findById(id);
    }

    private void validateConsents(QuoteRequestDto requestDto) {
        if (!requestDto.isAcceptTerms() || !requestDto.isAcceptPrivacy()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Accettazione Termini e Privacy obbligatoria.");
        }
    }
}
