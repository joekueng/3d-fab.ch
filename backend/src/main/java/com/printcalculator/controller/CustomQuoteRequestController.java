package com.printcalculator.controller;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/custom-quote-requests")
@CrossOrigin(origins = "*")
public class CustomQuoteRequestController {

    private final CustomQuoteRequestRepository requestRepo;
    private final CustomQuoteRequestAttachmentRepository attachmentRepo;
    
    // TODO: Inject Storage Service
    private static final String STORAGE_ROOT = "storage_requests";

    public CustomQuoteRequestController(CustomQuoteRequestRepository requestRepo,
                                        CustomQuoteRequestAttachmentRepository attachmentRepo) {
        this.requestRepo = requestRepo;
        this.attachmentRepo = attachmentRepo;
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
            return filename.substring(i + 1);
        }
        return "dat";
    }
}
