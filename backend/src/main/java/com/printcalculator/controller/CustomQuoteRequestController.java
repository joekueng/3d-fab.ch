package com.printcalculator.controller;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/custom-quote-requests")
public class CustomQuoteRequestController {

    private final CustomQuoteRequestRepository requestRepo;
    private final CustomQuoteRequestAttachmentRepository attachmentRepo;
    private final com.printcalculator.service.ClamAVService clamAVService;
    
    // TODO: Inject Storage Service
    private static final String STORAGE_ROOT = "storage_requests";
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
                                        com.printcalculator.service.ClamAVService clamAVService) {
        this.requestRepo = requestRepo;
        this.attachmentRepo = attachmentRepo;
        this.clamAVService = clamAVService;
    }

    // 1. Create Custom Quote Request
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<CustomQuoteRequest> createCustomQuoteRequest(
            @RequestPart("request") com.printcalculator.dto.QuoteRequestDto requestDto,
            @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) throws IOException {
        
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
                String ext = getExtension(file.getOriginalFilename());
                String storedFilename = fileUuid.toString() + "." + ext;
                
                // Note: We don't have attachment ID yet.
                // We'll save attachment first to get ID.
                attachment.setStoredFilename(storedFilename);
                attachment.setStoredRelativePath("PENDING");
                
                attachment = attachmentRepo.save(attachment);
                
                String relativePath = "quote-requests/" + request.getId() + "/attachments/" + attachment.getId() + "/" + storedFilename;
                attachment.setStoredRelativePath(relativePath);
                attachmentRepo.save(attachment);
                
                // Save file to disk
                Path absolutePath = Paths.get(STORAGE_ROOT, relativePath);
                Files.createDirectories(absolutePath.getParent());
                Files.copy(file.getInputStream(), absolutePath);
            }
        }
        
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
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            return filename.substring(i + 1).toLowerCase();
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
}
