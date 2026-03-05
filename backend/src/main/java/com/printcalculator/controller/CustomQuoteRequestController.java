package com.printcalculator.controller;

import com.printcalculator.dto.QuoteRequestDto;
import com.printcalculator.entity.CustomQuoteRequest;
import com.printcalculator.entity.CustomQuoteRequestAttachment;
import com.printcalculator.repository.CustomQuoteRequestAttachmentRepository;
import com.printcalculator.repository.CustomQuoteRequestRepository;
import com.printcalculator.service.storage.ClamAVService;
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
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
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

    @Value("${app.mail.contact-request.customer.enabled:true}")
    private boolean contactRequestCustomerMailEnabled;

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
        String language = normalizeLanguage(requestDto.getLanguage());
        
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
        sendCustomerContactRequestConfirmation(request, attachmentsCount, language);

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

    private void sendCustomerContactRequestConfirmation(CustomQuoteRequest request, int attachmentsCount, String language) {
        if (!contactRequestCustomerMailEnabled) {
            return;
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            logger.warn("Contact request confirmation skipped: missing customer email for request {}", request.getId());
            return;
        }

        Map<String, Object> templateData = new HashMap<>();
        templateData.put("requestId", request.getId());
        templateData.put(
                "createdAt",
                request.getCreatedAt().format(
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(localeForLanguage(language))
                )
        );
        templateData.put("recipientName", resolveRecipientName(request, language));
        templateData.put("requestType", localizeRequestType(request.getRequestType(), language));
        templateData.put("customerType", localizeCustomerType(request.getCustomerType(), language));
        templateData.put("name", safeValue(request.getName()));
        templateData.put("companyName", safeValue(request.getCompanyName()));
        templateData.put("contactPerson", safeValue(request.getContactPerson()));
        templateData.put("email", safeValue(request.getEmail()));
        templateData.put("phone", safeValue(request.getPhone()));
        templateData.put("message", safeValue(request.getMessage()));
        templateData.put("attachmentsCount", attachmentsCount);
        templateData.put("currentYear", Year.now().getValue());
        String subject = applyCustomerContactRequestTexts(templateData, language, request.getId());

        emailNotificationService.sendEmail(
                request.getEmail(),
                subject,
                "contact-request-customer",
                templateData
        );
    }

    private String applyCustomerContactRequestTexts(
            Map<String, Object> templateData,
            String language,
            UUID requestId
    ) {
        return switch (language) {
            case "en" -> {
                templateData.put("emailTitle", "Contact request received");
                templateData.put("headlineText", "We received your contact request");
                templateData.put("greetingText", "Hi " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Thank you for contacting us. Our team will reply as soon as possible.");
                templateData.put("requestIdHintText", "Please keep this request ID for future order references:");
                templateData.put("detailsTitleText", "Request details");
                templateData.put("labelRequestId", "Request ID");
                templateData.put("labelDate", "Date");
                templateData.put("labelRequestType", "Request type");
                templateData.put("labelCustomerType", "Customer type");
                templateData.put("labelName", "Name");
                templateData.put("labelCompany", "Company");
                templateData.put("labelContactPerson", "Contact person");
                templateData.put("labelEmail", "Email");
                templateData.put("labelPhone", "Phone");
                templateData.put("labelMessage", "Message");
                templateData.put("labelAttachments", "Attachments");
                templateData.put("supportText", "If you need help, reply to this email.");
                templateData.put("footerText", "Automated request-receipt confirmation from 3D-Fab.");
                yield "We received your contact request #" + requestId + " - 3D-Fab";
            }
            case "de" -> {
                templateData.put("emailTitle", "Kontaktanfrage erhalten");
                templateData.put("headlineText", "Wir haben Ihre Kontaktanfrage erhalten");
                templateData.put("greetingText", "Hallo " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Vielen Dank fuer Ihre Anfrage. Unser Team antwortet Ihnen so schnell wie moeglich.");
                templateData.put("requestIdHintText", "Bitte speichern Sie diese Anfrage-ID fuer zukuenftige Bestellreferenzen:");
                templateData.put("detailsTitleText", "Anfragedetails");
                templateData.put("labelRequestId", "Anfrage-ID");
                templateData.put("labelDate", "Datum");
                templateData.put("labelRequestType", "Anfragetyp");
                templateData.put("labelCustomerType", "Kundentyp");
                templateData.put("labelName", "Name");
                templateData.put("labelCompany", "Firma");
                templateData.put("labelContactPerson", "Kontaktperson");
                templateData.put("labelEmail", "E-Mail");
                templateData.put("labelPhone", "Telefon");
                templateData.put("labelMessage", "Nachricht");
                templateData.put("labelAttachments", "Anhaenge");
                templateData.put("supportText", "Wenn Sie Hilfe brauchen, antworten Sie auf diese E-Mail.");
                templateData.put("footerText", "Automatische Bestaetigung des Anfrageeingangs von 3D-Fab.");
                yield "Wir haben Ihre Kontaktanfrage erhalten #" + requestId + " - 3D-Fab";
            }
            case "fr" -> {
                templateData.put("emailTitle", "Demande de contact recue");
                templateData.put("headlineText", "Nous avons recu votre demande de contact");
                templateData.put("greetingText", "Bonjour " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Merci pour votre message. Notre equipe vous repondra des que possible.");
                templateData.put("requestIdHintText", "Veuillez conserver cet ID de demande pour vos futures references de commande :");
                templateData.put("detailsTitleText", "Details de la demande");
                templateData.put("labelRequestId", "ID de demande");
                templateData.put("labelDate", "Date");
                templateData.put("labelRequestType", "Type de demande");
                templateData.put("labelCustomerType", "Type de client");
                templateData.put("labelName", "Nom");
                templateData.put("labelCompany", "Entreprise");
                templateData.put("labelContactPerson", "Contact");
                templateData.put("labelEmail", "Email");
                templateData.put("labelPhone", "Telephone");
                templateData.put("labelMessage", "Message");
                templateData.put("labelAttachments", "Pieces jointes");
                templateData.put("supportText", "Si vous avez besoin d'aide, repondez a cet email.");
                templateData.put("footerText", "Confirmation automatique de reception de demande par 3D-Fab.");
                yield "Nous avons recu votre demande de contact #" + requestId + " - 3D-Fab";
            }
            default -> {
                templateData.put("emailTitle", "Richiesta di contatto ricevuta");
                templateData.put("headlineText", "Abbiamo ricevuto la tua richiesta di contatto");
                templateData.put("greetingText", "Ciao " + templateData.get("recipientName") + ",");
                templateData.put("introText", "Grazie per averci contattato. Il nostro team ti rispondera' il prima possibile.");
                templateData.put("requestIdHintText", "Conserva questo ID richiesta per i futuri riferimenti d'ordine:");
                templateData.put("detailsTitleText", "Dettagli richiesta");
                templateData.put("labelRequestId", "ID richiesta");
                templateData.put("labelDate", "Data");
                templateData.put("labelRequestType", "Tipo richiesta");
                templateData.put("labelCustomerType", "Tipo cliente");
                templateData.put("labelName", "Nome");
                templateData.put("labelCompany", "Azienda");
                templateData.put("labelContactPerson", "Contatto");
                templateData.put("labelEmail", "Email");
                templateData.put("labelPhone", "Telefono");
                templateData.put("labelMessage", "Messaggio");
                templateData.put("labelAttachments", "Allegati");
                templateData.put("supportText", "Se hai bisogno, rispondi direttamente a questa email.");
                templateData.put("footerText", "Conferma automatica di ricezione richiesta da 3D-Fab.");
                yield "Abbiamo ricevuto la tua richiesta di contatto #" + requestId + " - 3D-Fab";
            }
        };
    }

    private String localizeRequestType(String requestType, String language) {
        if (requestType == null || requestType.isBlank()) {
            return "-";
        }

        String normalized = requestType.trim().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "en" -> switch (normalized) {
                case "custom", "print_service" -> "Custom part request";
                case "series" -> "Series production request";
                case "consult", "design_service" -> "Consultation request";
                case "question" -> "General question";
                default -> requestType;
            };
            case "de" -> switch (normalized) {
                case "custom", "print_service" -> "Anfrage fuer Einzelteil";
                case "series" -> "Anfrage fuer Serienproduktion";
                case "consult", "design_service" -> "Beratungsanfrage";
                case "question" -> "Allgemeine Frage";
                default -> requestType;
            };
            case "fr" -> switch (normalized) {
                case "custom", "print_service" -> "Demande de piece personnalisee";
                case "series" -> "Demande de production en serie";
                case "consult", "design_service" -> "Demande de conseil";
                case "question" -> "Question generale";
                default -> requestType;
            };
            default -> switch (normalized) {
                case "custom", "print_service" -> "Richiesta pezzo personalizzato";
                case "series" -> "Richiesta produzione in serie";
                case "consult", "design_service" -> "Richiesta consulenza";
                case "question" -> "Domanda generale";
                default -> requestType;
            };
        };
    }

    private String localizeCustomerType(String customerType, String language) {
        if (customerType == null || customerType.isBlank()) {
            return "-";
        }
        String normalized = customerType.trim().toLowerCase(Locale.ROOT);
        return switch (language) {
            case "en" -> switch (normalized) {
                case "private" -> "Private";
                case "business" -> "Business";
                default -> customerType;
            };
            case "de" -> switch (normalized) {
                case "private" -> "Privat";
                case "business" -> "Unternehmen";
                default -> customerType;
            };
            case "fr" -> switch (normalized) {
                case "private" -> "Prive";
                case "business" -> "Entreprise";
                default -> customerType;
            };
            default -> switch (normalized) {
                case "private" -> "Privato";
                case "business" -> "Azienda";
                default -> customerType;
            };
        };
    }

    private Locale localeForLanguage(String language) {
        return switch (language) {
            case "en" -> Locale.ENGLISH;
            case "de" -> Locale.GERMAN;
            case "fr" -> Locale.FRENCH;
            default -> Locale.ITALIAN;
        };
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "it";
        }
        String normalized = language.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("en")) {
            return "en";
        }
        if (normalized.startsWith("de")) {
            return "de";
        }
        if (normalized.startsWith("fr")) {
            return "fr";
        }
        return "it";
    }

    private String resolveRecipientName(CustomQuoteRequest request, String language) {
        if (request.getName() != null && !request.getName().isBlank()) {
            return request.getName().trim();
        }
        if (request.getContactPerson() != null && !request.getContactPerson().isBlank()) {
            return request.getContactPerson().trim();
        }
        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            return request.getCompanyName().trim();
        }
        return switch (language) {
            case "en" -> "customer";
            case "de" -> "Kunde";
            case "fr" -> "client";
            default -> "cliente";
        };
    }

    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }
}
