package com.printcalculator.controller;

import com.printcalculator.dto.QuoteRequestDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.service.ClamAVService;
import com.printcalculator.service.email.EmailNotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/custom-quote-requests")
public class CustomQuoteRequestController {

    private static final Logger logger = LoggerFactory.getLogger(CustomQuoteRequestController.class);
    private final CustomQuoteRequestRepository requestRepo;
    private final CustomQuoteRequestAttachmentRepository attachmentRepo;
    private final ClamAVService clamAVService;
    private final EmailNotificationService emailNotificationService;

    @Value("${app.mail.contact-request.admin.enabled:true}")
    private boolean contactRequestAdminMailEnabled;

    @Value("${app.mail.contact-request.admin.address:infog@3d-fab.ch}")
    private String contactRequestAdminMailAddress;

    // TODO: Inject Storage Service
    private static final Path STORAGE_ROOT = Paths.get("storage_requests").toAbsolutePath().normalize();
    private static final Pattern SAFE_EXTENSION_PATTERN = Pattern.compile("^[a-z0-9]{1,10}$");
    private static final Set<String> FORBIDDEN_COMPRESSED_EXTENSIONS = Set.of(
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "zst"
    );
    private static final Set<String> FORBIDDEN_COMPRESSED_MIME_TYPES = Set.of(
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/vnd.rar",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-gzip",
            "application/x-tar",
            "application/x-bzip2",
            "application/x-xz",
            "application/zstd",
            "application/x-zstd"
    );

    public CustomQuoteRequestController(CustomQuoteRequestRepository requestRepo,
                                        CustomQuoteRequestAttachmentRepository attachmentRepo,
                                        ClamAVService clamAVService,
                                        EmailNotificationService emailNotificationService) {
        this.requestRepo = requestRepo;
        this.attachmentRepo = attachmentRepo;
        this.clamAVService = clamAVService;
        this.emailNotificationService = emailNotificationService;
    }

    // 1. Create Custom Quote Request
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<CustomQuoteRequest> createCustomQuoteRequest(
            @Valid @RequestPart("request") QuoteRequestDto requestDto,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) throws IOException {
        if (!requestDto.isAcceptTerms() || !requestDto.isAcceptPrivacy()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Accettazione Termini e Privacy obbligatoria."
            );
        }
        
        // 1. Create Request
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
        
        // 2. Handle Attachments
        int attachmentsCount = 0;
        if (files != null && !files.isEmpty()) {
            if (files.size() > 15) {
                throw new IOException("Too many files. Max 15 allowed.");
            }
            
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                if (isCompressedFile(file)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Compressed files are not allowed."
                    );
                }
                
                // Scan for virus
                clamAVService.scan(file.getInputStream());
                
                CustomQuoteRequestAttachment attachment = new CustomQuoteRequestAttachment();
                attachment.setRequest(request);
                attachment.setOriginalFilename(file.getOriginalFilename());
                attachment.setMimeType(file.getContentType());
                attachment.setFileSizeBytes(file.getSize());
                attachment.setCreatedAt(OffsetDateTime.now());
                
                // Generate path
                UUID fileUuid = UUID.randomUUID();
                String storedFilename = fileUuid + ".upload";
                
                // Note: We don't have attachment ID yet.
                // We'll save attachment first to get ID.
                attachment.setStoredFilename(storedFilename);
                attachment.setStoredRelativePath("PENDING");
                
                attachment = attachmentRepo.save(attachment);
                
                Path relativePath = Path.of(
                        "quote-requests",
                        request.getId().toString(),
                        "attachments",
                        attachment.getId().toString(),
                        storedFilename
                );
                attachment.setStoredRelativePath(relativePath.toString());
                attachmentRepo.save(attachment);
                
                // Save file to disk
                Path absolutePath = resolveWithinStorageRoot(relativePath);
                Files.createDirectories(absolutePath.getParent());
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, absolutePath, StandardCopyOption.REPLACE_EXISTING);
                }
                attachmentsCount++;
            }
        }

        sendAdminContactRequestNotification(request, attachmentsCount);

        return ResponseEntity.ok(request);
    }
    
    // 2. Get Request
    @GetMapping("/{id}")
    public ResponseEntity<CustomQuoteRequest> getCustomQuoteRequest(@PathVariable UUID id) {
        return requestRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    // Helper
    private String getExtension(String filename) {
        if (filename == null) return "dat";
        String cleaned = StringUtils.cleanPath(filename);
        if (cleaned.contains("..")) {
            return "dat";
        }
        int i = cleaned.lastIndexOf('.');
        if (i > 0 && i < cleaned.length() - 1) {
            String ext = cleaned.substring(i + 1).toLowerCase(Locale.ROOT);
            if (SAFE_EXTENSION_PATTERN.matcher(ext).matches()) {
                return ext;
            }
        }
        return "dat";
    }

    private boolean isCompressedFile(MultipartFile file) {
        String ext = getExtension(file.getOriginalFilename());
        if (FORBIDDEN_COMPRESSED_EXTENSIONS.contains(ext)) {
            return true;
        }
        String mime = file.getContentType();
        return mime != null && FORBIDDEN_COMPRESSED_MIME_TYPES.contains(mime.toLowerCase());
    }

    private Path resolveWithinStorageRoot(Path relativePath) {
        try {
            Path normalizedRelative = relativePath.normalize();
            if (normalizedRelative.isAbsolute()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment path");
            }
            Path absolutePath = STORAGE_ROOT.resolve(normalizedRelative).normalize();
            if (!absolutePath.startsWith(STORAGE_ROOT)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment path");
            }
            return absolutePath;
        } catch (InvalidPathException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment path");
        }
    }

    private void sendAdminContactRequestNotification(CustomQuoteRequest request, int attachmentsCount) {
        if (!contactRequestAdminMailEnabled) {
            return;
        }
        if (contactRequestAdminMailAddress == null || contactRequestAdminMailAddress.isBlank()) {
            logger.warn("Contact request admin notification enabled but no admin address configured.");
            return;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("requestId", request.getId());
        templateData.put("createdAt", request.getCreatedAt());
        templateData.put("requestType", safeValue(request.getRequestType()));
        templateData.put("customerType", safeValue(request.getCustomerType()));
        templateData.put("name", safeValue(request.getName()));
        templateData.put("companyName", safeValue(request.getCompanyName()));
        templateData.put("contactPerson", safeValue(request.getContactPerson()));
        templateData.put("email", safeValue(request.getEmail()));
        templateData.put("phone", safeValue(request.getPhone()));
        templateData.put("message", safeValue(request.getMessage()));
        templateData.put("attachmentsCount", attachmentsCount);
        templateData.put("currentYear", Year.now().getValue());

        emailNotificationService.sendEmail(
                contactRequestAdminMailAddress,
                "Nuova richiesta di contatto #" + request.getId(),
                "contact-request-admin",
                templateData
        );
    }

    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
