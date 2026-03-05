package com.printcalculator.service.request;

import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.service.storage.ClamAVService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional(readOnly = true)
public class CustomQuoteRequestAttachmentService {

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

    private final CustomQuoteRequestAttachmentRepository attachmentRepo;
    private final ClamAVService clamAVService;

    public CustomQuoteRequestAttachmentService(CustomQuoteRequestAttachmentRepository attachmentRepo,
                                               ClamAVService clamAVService) {
        this.attachmentRepo = attachmentRepo;
        this.clamAVService = clamAVService;
    }

    @Transactional
    public int storeAttachments(CustomQuoteRequest request, List<MultipartFile> files) throws IOException {
        if (files == null || files.isEmpty()) {
            return 0;
        }
        if (files.size() > 15) {
            throw new IOException("Too many files. Max 15 allowed.");
        }

        int attachmentsCount = 0;
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            if (isCompressedFile(file)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Compressed files are not allowed.");
            }

            clamAVService.scan(file.getInputStream());

            CustomQuoteRequestAttachment attachment = new CustomQuoteRequestAttachment();
            attachment.setRequest(request);
            attachment.setOriginalFilename(file.getOriginalFilename());
            attachment.setMimeType(file.getContentType());
            attachment.setFileSizeBytes(file.getSize());
            attachment.setCreatedAt(OffsetDateTime.now());
            attachment.setStoredFilename(UUID.randomUUID() + ".upload");
            attachment.setStoredRelativePath("PENDING");

            attachment = attachmentRepo.save(attachment);

            Path relativePath = Path.of(
                    "quote-requests",
                    request.getId().toString(),
                    "attachments",
                    attachment.getId().toString(),
                    attachment.getStoredFilename()
            );
            attachment.setStoredRelativePath(relativePath.toString());
            attachmentRepo.save(attachment);

            Path absolutePath = resolveWithinStorageRoot(relativePath);
            Files.createDirectories(absolutePath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            }
            attachmentsCount++;
        }

        return attachmentsCount;
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "dat";
        }
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
        return mime != null && FORBIDDEN_COMPRESSED_MIME_TYPES.contains(mime.toLowerCase(Locale.ROOT));
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
}
