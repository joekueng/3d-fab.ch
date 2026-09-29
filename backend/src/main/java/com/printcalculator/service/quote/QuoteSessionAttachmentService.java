package com.printcalculator.service.quote;

import com.printcalculator.dto.QuoteSessionAttachmentDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderDeliverableFile;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.entity.QuoteSessionAttachment;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.repository.OrderDeliverableFileRepository;
import com.printcalculator.repository.QuoteSessionAttachmentRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.storage.ClamAVService;
import com.printcalculator.service.storage.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(rollbackFor = Exception.class)
public class QuoteSessionAttachmentService {
    private static final Pattern SAFE_EXTENSION = Pattern.compile("^[a-z0-9]{1,10}$");
    private static final Pattern SAFE_FILENAME = Pattern.compile("[\\\\/\\p{Cntrl}]");

    private final QuoteSessionRepository sessionRepo;
    private final QuoteSessionAttachmentRepository attachmentRepo;
    private final OrderDeliverableFileRepository deliverableRepo;
    private final ClamAVService antivirus;
    private final StorageService storageService;
    private final Path quotesRoot;
    private final Path orderStorageRoot;
    private final long maxFileSizeBytes;
    private final int maxFilesPerSession;

    public QuoteSessionAttachmentService(QuoteSessionRepository sessionRepo,
                                         QuoteSessionAttachmentRepository attachmentRepo,
                                         OrderDeliverableFileRepository deliverableRepo,
                                         ClamAVService antivirus,
                                         StorageService storageService,
                                         @Value("${storage.quotes-root:storage_quotes}") String quotesRoot,
                                         @Value("${storage.location:storage_orders}") String orderStorageRoot,
                                         @Value("${app.invoice.attachment.max-file-size-bytes}") long maxFileSizeBytes,
                                         @Value("${app.invoice.attachment.max-files-per-session}") int maxFilesPerSession) {
        this.sessionRepo = sessionRepo;
        this.attachmentRepo = attachmentRepo;
        this.deliverableRepo = deliverableRepo;
        this.antivirus = antivirus;
        this.storageService = storageService;
        this.quotesRoot = Path.of(quotesRoot).toAbsolutePath().normalize();
        this.orderStorageRoot = Path.of(orderStorageRoot).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxFilesPerSession = maxFilesPerSession;
    }

    @Transactional(readOnly = true)
    public List<QuoteSessionAttachmentDto> list(UUID sessionId) {
        requireSession(sessionId);
        return attachmentRepo.findByQuoteSessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuoteSessionAttachmentDto> listForQuoteResponse(UUID sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        return attachmentRepo.findByQuoteSessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<QuoteSessionAttachmentDto> upload(UUID sessionId, List<MultipartFile> files) {
        QuoteSession session = requireLockedSession(sessionId);
        if ("CONVERTED".equals(session.getStatus()) || session.getConvertedOrderId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify a converted session");
        }
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one file is required");
        }

        long existingCount = attachmentRepo.countByQuoteSessionId(sessionId);
        long nonEmpty = files.stream().filter(file -> file != null && !file.isEmpty()).count();
        if (nonEmpty == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one non-empty file is required");
        }
        if (existingCount + nonEmpty > maxFilesPerSession) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Too many attachments. Maximum " + maxFilesPerSession + " files per invoice"
            );
        }

        List<QuoteSessionAttachmentDto> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (maxFileSizeBytes > 0 && file.getSize() > maxFileSizeBytes) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "File \"" + safeOriginalFilename(file.getOriginalFilename())
                                + "\" exceeds the maximum allowed size of " + maxFileSizeBytes + " bytes"
                );
            }
            saved.add(toDto(storeAttachment(session, file)));
        }
        return saved;
    }

    public void delete(UUID sessionId, UUID attachmentId) {
        QuoteSession session = requireLockedSession(sessionId);
        if ("CONVERTED".equals(session.getStatus()) || session.getConvertedOrderId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot modify a converted session");
        }
        QuoteSessionAttachment attachment = attachmentRepo.findByIdAndQuoteSession_Id(attachmentId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        Path path = resolveStoredPath(attachment.getStoredRelativePath(), sessionId);
        attachmentRepo.delete(attachment);
        if (path != null) {
            deleteAfterCommit(path);
        }
    }

    @Transactional(readOnly = true)
    public AttachmentPreview loadPreview(UUID sessionId, UUID attachmentId) {
        QuoteSessionAttachment attachment = attachmentRepo.findByIdAndQuoteSession_Id(attachmentId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        if (!isPreviewableImage(attachment.getMimeType())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Preview not available");
        }
        Path path = resolveStoredPath(attachment.getStoredRelativePath(), sessionId);
        if (path == null || !Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
        try {
            return new AttachmentPreview(
                    new UrlResource(path.toUri()),
                    attachment.getOriginalFilename(),
                    attachment.getMimeType(),
                    attachment.getFileSizeBytes() != null ? attachment.getFileSizeBytes() : Files.size(path)
            );
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        }
    }

    @EventListener
    public void snapshotToOrder(OrderCreatedEvent event) {
        Order order = event.getOrder();
        if (order == null || !Boolean.TRUE.equals(order.getIsCadOrder())) {
            return;
        }
        QuoteSession session = order.getSourceQuoteSession();
        if (session == null || session.getId() == null) {
            return;
        }
        List<QuoteSessionAttachment> attachments =
                attachmentRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId());
        for (QuoteSessionAttachment attachment : attachments) {
            copyToOrder(order, session.getId(), attachment);
        }
    }

    private void copyToOrder(Order order, UUID sessionId, QuoteSessionAttachment attachment) {
        Path source = resolveStoredPath(attachment.getStoredRelativePath(), sessionId);
        if (source == null || !Files.isRegularFile(source)) {
            return;
        }

        String storedFilename = UUID.randomUUID() + "." + safeExtension(attachment.getOriginalFilename());
        OrderDeliverableFile deliverable = new OrderDeliverableFile();
        deliverable.setOrder(order);
        deliverable.setOriginalFilename(safeOriginalFilename(attachment.getOriginalFilename()));
        deliverable.setStoredFilename(storedFilename);
        deliverable.setStoredRelativePath("PENDING");
        deliverable.setMimeType(attachment.getMimeType());
        deliverable.setFileSizeBytes(attachment.getFileSizeBytes());
        deliverable.setCreatedAt(OffsetDateTime.now());
        deliverable = deliverableRepo.save(deliverable);

        Path relativePath = Path.of(
                "orders",
                order.getId().toString(),
                "cad-deliverables",
                deliverable.getId().toString(),
                storedFilename
        );
        registerRollbackDelete(orderStorageRoot.resolve(relativePath).normalize());
        try {
            storageService.store(source, relativePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to copy attachment to order", e);
        }
        deliverable.setStoredRelativePath(relativePath.toString());
        deliverableRepo.save(deliverable);
    }

    private QuoteSessionAttachment storeAttachment(QuoteSession session, MultipartFile file) {
        String originalFilename = safeOriginalFilename(file.getOriginalFilename());
        String mimeType = detectMimeType(file);
        String storedFilename = UUID.randomUUID() + "." + safeExtension(originalFilename);

        QuoteSessionAttachment attachment = new QuoteSessionAttachment();
        attachment.setQuoteSession(session);
        attachment.setOriginalFilename(originalFilename);
        attachment.setStoredFilename(storedFilename);
        attachment.setStoredRelativePath("PENDING");
        attachment.setMimeType(mimeType);
        attachment.setFileSizeBytes(file.getSize());
        attachment.setCreatedAt(OffsetDateTime.now());
        attachment = attachmentRepo.saveAndFlush(attachment);

        Path relativePath = Path.of(
                session.getId().toString(),
                "attachments",
                attachment.getId().toString(),
                storedFilename
        );
        Path target = resolveStoredPath(relativePath.toString(), session.getId());
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid attachment storage path");
        }

        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store attachment", e);
        }
        registerRollbackDelete(target);

        try (InputStream input = file.getInputStream()) {
            if (!antivirus.scanRequired(input)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment rejected");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ANTIVIRUS_UNAVAILABLE");
        }

        try (InputStream input = file.getInputStream();
             DigestInputStream digestInput = new DigestInputStream(input, MessageDigest.getInstance("SHA-256"))) {
            Files.copy(digestInput, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            attachment.setSha256Hex(HexFormat.of().formatHex(digestInput.getMessageDigest().digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store attachment", e);
        }

        attachment.setStoredRelativePath(relativePath.toString());
        attachment.setFileSizeBytes(file.getSize());
        return attachmentRepo.save(attachment);
    }

    private QuoteSession requireSession(UUID sessionId) {
        return sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private QuoteSession requireLockedSession(UUID sessionId) {
        return sessionRepo.findLockedById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private Path resolveStoredPath(String storedRelativePath, UUID sessionId) {
        if (storedRelativePath == null || storedRelativePath.isBlank() || "PENDING".equals(storedRelativePath)) {
            return null;
        }
        try {
            Path raw = Path.of(storedRelativePath).normalize();
            Path resolved = raw.isAbsolute() ? raw : quotesRoot.resolve(raw).normalize();
            Path expectedSessionRoot = quotesRoot.resolve(sessionId.toString()).normalize();
            if (!resolved.startsWith(expectedSessionRoot)) {
                return null;
            }
            return resolved;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    private void registerRollbackDelete(Path absolutePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    Files.deleteIfExists(absolutePath);
                } catch (IOException ignored) {
                    // Best-effort cleanup for orphaned uploads.
                }
            }
        });
    }

    private void deleteAfterCommit(Path absolutePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteFileBestEffort(absolutePath);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFileBestEffort(absolutePath);
            }
        });
    }

    private void deleteFileBestEffort(Path absolutePath) {
        try {
            Files.deleteIfExists(absolutePath);
        } catch (IOException ignored) {
            // Metadata remains authoritative when a stale file cannot be removed.
        }
    }

    private QuoteSessionAttachmentDto toDto(QuoteSessionAttachment attachment) {
        QuoteSessionAttachmentDto dto = new QuoteSessionAttachmentDto();
        dto.setId(attachment.getId());
        dto.setOriginalFilename(attachment.getOriginalFilename());
        dto.setMimeType(attachment.getMimeType());
        dto.setFileSizeBytes(attachment.getFileSizeBytes());
        dto.setImage(isPreviewableImage(attachment.getMimeType()));
        dto.setCreatedAt(attachment.getCreatedAt());
        return dto;
    }

    static boolean isPreviewableImage(String mimeType) {
        return mimeType != null && switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png", "image/jpeg", "image/webp" -> true;
            default -> false;
        };
    }

    private String detectMimeType(MultipartFile file) {
        byte[] head;
        try (InputStream input = file.getInputStream()) {
            head = input.readNBytes(12);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read attachment");
        }

        String detected = detectSignatureMime(head);
        String declared = file.getContentType() != null
                ? file.getContentType().trim().toLowerCase(Locale.ROOT)
                : null;

        if (detected != null) {
            if (declared != null && declared.startsWith("image/") && !declared.equals(detected)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment type does not match its content");
            }
            return detected;
        }
        if (declared != null && (declared.startsWith("image/") || declared.equals("application/pdf"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment type does not match its content");
        }
        if (declared == null || declared.isBlank()) {
            return "application/octet-stream";
        }
        return declared;
    }

    private String detectSignatureMime(byte[] head) {
        if (head.length >= 8 && Arrays.equals(Arrays.copyOf(head, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
            return "image/png";
        }
        if (head.length >= 3 && head[0] == (byte) 255 && head[1] == (byte) 216 && head[2] == (byte) 255) {
            return "image/jpeg";
        }
        if (head.length >= 12
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        if (head.length >= 5
                && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-') {
            return "application/pdf";
        }
        return null;
    }

    private String safeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "allegato";
        }
        String cleaned = StringUtils.cleanPath(filename);
        cleaned = SAFE_FILENAME.matcher(cleaned).replaceAll("_").trim();
        if (cleaned.contains("..") || cleaned.isBlank()) {
            return "allegato";
        }
        return cleaned.length() > 255 ? cleaned.substring(cleaned.length() - 255) : cleaned;
    }

    private String safeExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index > 0 && index < filename.length() - 1) {
            String ext = filename.substring(index + 1).toLowerCase(Locale.ROOT);
            if (SAFE_EXTENSION.matcher(ext).matches()) {
                return ext;
            }
        }
        return "bin";
    }

    public record AttachmentPreview(Resource resource, String filename, String mimeType, long sizeBytes) {
    }
}
