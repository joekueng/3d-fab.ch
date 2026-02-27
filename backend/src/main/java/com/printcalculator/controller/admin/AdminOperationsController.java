package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminContactRequestDto;
import com.printcalculator.dto.AdminContactRequestAttachmentDto;
import com.printcalculator.dto.AdminContactRequestDetailDto;
import com.printcalculator.dto.AdminFilamentStockDto;
import com.printcalculator.dto.AdminQuoteSessionDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.FilamentVariantStockKg;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.FilamentVariantStockKgRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/admin")
@Transactional(readOnly = true)
public class AdminOperationsController {
    private static final Logger logger = LoggerFactory.getLogger(AdminOperationsController.class);
    private static final Path CONTACT_ATTACHMENTS_ROOT = Paths.get("storage_requests").toAbsolutePath().normalize();

    private final FilamentVariantStockKgRepository filamentStockRepo;
    private final FilamentVariantRepository filamentVariantRepo;
    private final CustomQuoteRequestRepository customQuoteRequestRepo;
    private final CustomQuoteRequestAttachmentRepository customQuoteRequestAttachmentRepo;
    private final QuoteSessionRepository quoteSessionRepo;
    private final OrderRepository orderRepo;

    public AdminOperationsController(
            FilamentVariantStockKgRepository filamentStockRepo,
            FilamentVariantRepository filamentVariantRepo,
            CustomQuoteRequestRepository customQuoteRequestRepo,
            CustomQuoteRequestAttachmentRepository customQuoteRequestAttachmentRepo,
            QuoteSessionRepository quoteSessionRepo,
            OrderRepository orderRepo
    ) {
        this.filamentStockRepo = filamentStockRepo;
        this.filamentVariantRepo = filamentVariantRepo;
        this.customQuoteRequestRepo = customQuoteRequestRepo;
        this.customQuoteRequestAttachmentRepo = customQuoteRequestAttachmentRepo;
        this.quoteSessionRepo = quoteSessionRepo;
        this.orderRepo = orderRepo;
    }

    @GetMapping("/filament-stock")
    public ResponseEntity<List<AdminFilamentStockDto>> getFilamentStock() {
        List<FilamentVariantStockKg> stocks = filamentStockRepo.findAll(Sort.by(Sort.Direction.ASC, "stockKg"));
        Set<Long> variantIds = stocks.stream()
                .map(FilamentVariantStockKg::getFilamentVariantId)
                .collect(Collectors.toSet());

        Map<Long, FilamentVariant> variantsById;
        if (variantIds.isEmpty()) {
            variantsById = Collections.emptyMap();
        } else {
            variantsById = filamentVariantRepo.findAllById(variantIds).stream()
                    .collect(Collectors.toMap(FilamentVariant::getId, variant -> variant));
        }

        List<AdminFilamentStockDto> response = stocks.stream().map(stock -> {
            FilamentVariant variant = variantsById.get(stock.getFilamentVariantId());
            AdminFilamentStockDto dto = new AdminFilamentStockDto();
            dto.setFilamentVariantId(stock.getFilamentVariantId());
            dto.setStockSpools(stock.getStockSpools());
            dto.setSpoolNetKg(stock.getSpoolNetKg());
            dto.setStockKg(stock.getStockKg());

            if (variant != null) {
                dto.setMaterialCode(
                        variant.getFilamentMaterialType() != null
                                ? variant.getFilamentMaterialType().getMaterialCode()
                                : "UNKNOWN"
                );
                dto.setVariantDisplayName(variant.getVariantDisplayName());
                dto.setColorName(variant.getColorName());
                dto.setActive(variant.getIsActive());
            } else {
                dto.setMaterialCode("UNKNOWN");
                dto.setVariantDisplayName("Variant " + stock.getFilamentVariantId());
                dto.setColorName("-");
                dto.setActive(false);
            }

            return dto;
        }).toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/contact-requests")
    public ResponseEntity<List<AdminContactRequestDto>> getContactRequests() {
        List<AdminContactRequestDto> response = customQuoteRequestRepo.findAll(
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .map(this::toContactRequestDto)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/contact-requests/{requestId}")
    public ResponseEntity<AdminContactRequestDetailDto> getContactRequestDetail(@PathVariable UUID requestId) {
        CustomQuoteRequest request = customQuoteRequestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Contact request not found"));

        List<AdminContactRequestAttachmentDto> attachments = customQuoteRequestAttachmentRepo
                .findByRequest_IdOrderByCreatedAtAsc(requestId)
                .stream()
                .map(this::toContactRequestAttachmentDto)
                .toList();

        AdminContactRequestDetailDto dto = new AdminContactRequestDetailDto();
        dto.setId(request.getId());
        dto.setRequestType(request.getRequestType());
        dto.setCustomerType(request.getCustomerType());
        dto.setEmail(request.getEmail());
        dto.setPhone(request.getPhone());
        dto.setName(request.getName());
        dto.setCompanyName(request.getCompanyName());
        dto.setContactPerson(request.getContactPerson());
        dto.setMessage(request.getMessage());
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setUpdatedAt(request.getUpdatedAt());
        dto.setAttachments(attachments);

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/contact-requests/{requestId}/attachments/{attachmentId}/file")
    public ResponseEntity<Resource> downloadContactRequestAttachment(
            @PathVariable UUID requestId,
            @PathVariable UUID attachmentId
    ) {
        CustomQuoteRequestAttachment attachment = customQuoteRequestAttachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Attachment not found"));

        if (!attachment.getRequest().getId().equals(requestId)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment not found for request");
        }

        String relativePath = attachment.getStoredRelativePath();
        if (relativePath == null || relativePath.isBlank() || "PENDING".equals(relativePath)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        String expectedPrefix = "quote-requests/" + requestId + "/attachments/" + attachmentId + "/";
        if (!relativePath.startsWith(expectedPrefix)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        Path filePath = CONTACT_ATTACHMENTS_ROOT.resolve(relativePath).normalize();
        if (!filePath.startsWith(CONTACT_ATTACHMENTS_ROOT)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        if (!Files.exists(filePath)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
            }

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            String mimeType = attachment.getMimeType();
            if (mimeType != null && !mimeType.isBlank()) {
                try {
                    mediaType = MediaType.parseMediaType(mimeType);
                } catch (Exception ignored) {
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                }
            }

            String filename = attachment.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                filename = attachment.getStoredFilename() != null && !attachment.getStoredFilename().isBlank()
                        ? attachment.getStoredFilename()
                        : "attachment-" + attachmentId;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file not available");
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AdminQuoteSessionDto>> getQuoteSessions() {
        List<AdminQuoteSessionDto> response = quoteSessionRepo.findAll(
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
                .stream()
                .map(this::toQuoteSessionDto)
                .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Transactional
    public ResponseEntity<Void> deleteQuoteSession(@PathVariable UUID sessionId) {
        QuoteSession session = quoteSessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Session not found"));

        if (orderRepo.existsBySourceQuoteSession_Id(sessionId)) {
            throw new ResponseStatusException(CONFLICT, "Cannot delete session already linked to an order");
        }

        deleteSessionFiles(sessionId);
        quoteSessionRepo.delete(session);
        return ResponseEntity.noContent().build();
    }

    private AdminContactRequestDto toContactRequestDto(CustomQuoteRequest request) {
        AdminContactRequestDto dto = new AdminContactRequestDto();
        dto.setId(request.getId());
        dto.setRequestType(request.getRequestType());
        dto.setCustomerType(request.getCustomerType());
        dto.setEmail(request.getEmail());
        dto.setPhone(request.getPhone());
        dto.setName(request.getName());
        dto.setCompanyName(request.getCompanyName());
        dto.setStatus(request.getStatus());
        dto.setCreatedAt(request.getCreatedAt());
        return dto;
    }

    private AdminContactRequestAttachmentDto toContactRequestAttachmentDto(CustomQuoteRequestAttachment attachment) {
        AdminContactRequestAttachmentDto dto = new AdminContactRequestAttachmentDto();
        dto.setId(attachment.getId());
        dto.setOriginalFilename(attachment.getOriginalFilename());
        dto.setMimeType(attachment.getMimeType());
        dto.setFileSizeBytes(attachment.getFileSizeBytes());
        dto.setCreatedAt(attachment.getCreatedAt());
        return dto;
    }

    private AdminQuoteSessionDto toQuoteSessionDto(QuoteSession session) {
        AdminQuoteSessionDto dto = new AdminQuoteSessionDto();
        dto.setId(session.getId());
        dto.setStatus(session.getStatus());
        dto.setMaterialCode(session.getMaterialCode());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setExpiresAt(session.getExpiresAt());
        dto.setConvertedOrderId(session.getConvertedOrderId());
        return dto;
    }

    private void deleteSessionFiles(UUID sessionId) {
        Path sessionDir = Paths.get("storage_quotes", sessionId.toString());
        if (!Files.exists(sessionDir)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(sessionDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            logger.error("Failed to delete files for session {}", sessionId, e);
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Unable to delete session files");
        }
    }
}
