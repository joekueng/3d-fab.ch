package com.printcalculator.service.information;

import com.printcalculator.dto.InformationDto;
import com.printcalculator.entity.OrderInformation;
import com.printcalculator.entity.OrderInformation.*;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.OrderInformationRepository;
import com.printcalculator.service.storage.ClamAVService;
import com.printcalculator.event.OrderCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(rollbackFor = Exception.class)
public class OrderInformationService {
    public static final long MAX_FILE = 10 * 1024 * 1024;
    public static final long MAX_TOTAL = 30 * 1024 * 1024;
    private final OrderInformationRepository repo;
    private final ClamAVService antivirus;
    private final com.printcalculator.repository.OrderItemRepository orderItems;
    private final Path root;
    private final com.printcalculator.repository.OrderRepository orders;
    public OrderInformationService(OrderInformationRepository repo, ClamAVService antivirus,
            @Value("${storage.information-root:storage_orders/information}") String root,
            com.printcalculator.repository.OrderItemRepository orderItems, com.printcalculator.repository.OrderRepository orders) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.repo = repo; this.antivirus = antivirus; this.root = Path.of(root).toAbsolutePath().normalize();
    }
    public static String newToken() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public InformationDto.Credential create() {
        OrderInformation draft = new OrderInformation();
        draft.setAccessToken(newToken()); draft.setExpiresAt(OffsetDateTime.now().plusDays(30));
        repo.save(draft);
        return new InformationDto.Credential(draft.getId(), draft.getAccessToken());
    }
    private OrderInformation draft(UUID id, String token) {
        OrderInformation value = repo.lockById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        authorize(value, token);
        if (value.getOrderId() != null || value.getExpiresAt().isBefore(OffsetDateTime.now()))
            throw new ResponseStatusException(GONE);
        return value;
    }
    private void authorize(OrderInformation value, String token) {
        if (token == null || !MessageDigest.isEqual(value.getAccessToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(FORBIDDEN);
    }
    private OrderInformation order(UUID id, String token, boolean admin) {
        OrderInformation value = repo.findByOrderId(id).orElseGet(() -> {
            if (!admin) throw new ResponseStatusException(NOT_FOUND);
            // Older orders acquire a private information area when opened by an administrator.
            Order legacy = orders.findLockedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            Optional<OrderInformation> concurrent = repo.findByOrderId(id);
            if (concurrent.isPresent()) return concurrent.get();
            if (legacy.getInformationToken() == null) legacy.setInformationToken(newToken());
            OrderInformation created = new OrderInformation();
            created.setOrderId(id); created.setAccessToken(legacy.getInformationToken());
            QuoteSession source = legacy.getSourceQuoteSession();
            if (source != null && source.getNotes() != null && !source.getNotes().isBlank()) {
                created.setEntries(List.of(new Entry(UUID.randomUUID(), source.getNotes(), "", "", legacy.getCreatedAt(), null, List.of())));
            }
            return repo.save(created);
        });
        if (!admin) authorize(value, token);
        return value;
    }
    /**
     * Quote links intentionally grant access to the complete active session.
     * The quote service validates expiry/status before resolving its persisted draft.
     * Order credentials remain separate and cannot be resolved through a quote link.
     */
    public InformationDto.Credential sessionCredential(QuoteSession session) {
        if (session.getInformationDraftId() == null) return null;
        OrderInformation value = repo.lockById(session.getInformationDraftId())
                .orElseThrow(() -> new ResponseStatusException(GONE));
        if (value.getOrderId() != null || value.getExpiresAt().isBefore(OffsetDateTime.now()))
            throw new ResponseStatusException(GONE);
        return new InformationDto.Credential(value.getId(), value.getAccessToken());
    }

    public void validateCheckout(QuoteSession session, String token) {
        if (session.getInformationDraftId() != null) draft(session.getInformationDraftId(), token);
    }
    public void validateModelsForCheckout(QuoteSession session, List<com.printcalculator.entity.QuoteLineItem> items) {
        if (session.getInformationDraftId() == null) return;
        OrderInformation value = repo.lockById(session.getInformationDraftId()).orElseThrow(() -> new ResponseStatusException(GONE));
        for (Entry entry : value.getEntries()) {
            String key = entry.modelKey();
            if (key != null && !key.isBlank() && items.stream().noneMatch(item -> key.equals(item.getClientModelKey()) || key.equals(String.valueOf(item.getId()))))
                throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_MODEL_REMOVED");
        }
    }
    public void link(QuoteSession session, InformationDto.DraftLink link) {
        if (link == null) return;
        OrderInformation value = draft(link.id(), link.token());
        value.setExpiresAt(session.getExpiresAt().isAfter(value.getExpiresAt()) ? session.getExpiresAt() : value.getExpiresAt());
        session.setInformationDraftId(value.getId());
    }
    public void scanDraft(UUID id, String token) {
        OrderInformation value = draft(id, token);
        for (Entry entry : value.getEntries()) for (Attachment attachment : entry.attachments()) {
            try (InputStream stream = Files.newInputStream(path(value, attachment))) {
                if (!antivirus.scanRequired(stream)) throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_FILE_REJECTED");
            } catch (IOException e) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "INFORMATION_FILE_UNAVAILABLE");
            }
        }
    }

    public InformationDto getDraft(UUID id, String token) { return dto(draft(id, token)); }
    public InformationDto getOrder(UUID id, String token, boolean admin) {
        OrderInformation value = order(id, token, admin);
        return new InformationDto(value.getId(), List.copyOf(value.getEntries()), admin ? value.getAccessToken() : null);
    }
    private InformationDto dto(OrderInformation value) { return new InformationDto(value.getId(), List.copyOf(value.getEntries())); }
    public InformationDto saveDraft(UUID id, String token, InformationDto.EntryRequest request, List<MultipartFile> files) throws IOException {
        OrderInformation value = draft(id, token);
        List<Attachment> existing = value.getEntries().stream().flatMap(e -> e.attachments().stream()).toList();
        List<Attachment> kept = retain(existing, request.attachments());
        List<Attachment> added = store(value, files, kept);
        List<Attachment> all = new ArrayList<>(kept); all.addAll(added);
        String text = normalize(request.text());
        value.setEntries(text.isBlank() && all.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(
                new Entry(UUID.randomUUID(), text, normalize(request.model()), normalize(request.modelKey()), OffsetDateTime.now(), null, all))));
        if (value.getExpiresAt().isBefore(OffsetDateTime.now().plusDays(30))) value.setExpiresAt(OffsetDateTime.now().plusDays(30));
        for (Attachment removed : existing) if (!kept.contains(removed)) deleteAfterCommit(path(value, removed));
        return dto(value);
    }
    public InformationDto append(UUID id, String token, InformationDto.EntryRequest request, List<MultipartFile> files) throws IOException {
        OrderInformation value = order(id, token, false);
        if (value.getEntries().size() >= 100) throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_LIMIT");
        if (request.attachments() != null && !request.attachments().isEmpty()) throw new ResponseStatusException(BAD_REQUEST);
        validateModel(value.getOrderId(), request.modelKey());
        List<Attachment> existing = value.getEntries().stream().flatMap(e -> e.attachments().stream()).toList();
        List<Attachment> added = store(value, files, existing);
        String text = normalize(request.text());
        if (text.isBlank() && added.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_EMPTY");
        List<Entry> entries = new ArrayList<>(value.getEntries());
        entries.add(new Entry(UUID.randomUUID(), text, normalize(request.model()), normalize(request.modelKey()), OffsetDateTime.now(), null, added));
        value.setEntries(entries);
        return dto(value);
    }
    public InformationDto markRead(UUID id, InformationDto.ReadRequest request) {
        OrderInformation value = order(id, null, true);
        value.setEntries(value.getEntries().stream().map(e -> request.entries().contains(e.id()) && e.readAt() == null
                ? new Entry(e.id(), e.text(), e.model(), e.modelKey(), e.createdAt(), OffsetDateTime.now(), e.attachments()) : e).toList());
        return dto(value);
    }
    public List<UUID> unreadOrders() {
        return repo.orderIds();
    }
    public ResponseEntity<Resource> download(UUID owner, String token, UUID file, boolean isOrder, boolean admin) throws IOException {
        OrderInformation value = isOrder ? order(owner, token, admin) : draft(owner, token);
        Attachment attachment = value.getEntries().stream().flatMap(e -> e.attachments().stream())
                .filter(a -> a.id().equals(file)).findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        Path path = path(value, attachment);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(NOT_FOUND);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.name(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(attachment.mime())).contentLength(attachment.size())
                .body(new UrlResource(path.toUri()));
    }
    @EventListener
    public void snapshot(OrderCreatedEvent event) throws IOException {
        Order order = event.getOrder();
        OrderInformation value = new OrderInformation();
        value.setOrderId(order.getId()); value.setAccessToken(order.getInformationToken());
        QuoteSession session = order.getSourceQuoteSession();
        if (session != null && session.getInformationDraftId() != null) {
            OrderInformation source = repo.lockById(session.getInformationDraftId()).orElseThrow(() -> new ResponseStatusException(GONE));
            for (Entry entry : source.getEntries()) {
                validateModel(order.getId(), entry.modelKey());
                List<Attachment> copies = new ArrayList<>();
                for (Attachment a : entry.attachments()) {
                    Attachment copy = new Attachment(UUID.randomUUID(), a.name(), a.mime(), a.size());
                    Path target = path(value, copy); Files.createDirectories(target.getParent());
                    rollbackDelete(target); Files.copy(path(source, a), target); copies.add(copy);
                }
                value.getEntries().add(new Entry(UUID.randomUUID(), entry.text(), entry.model(), entry.modelKey(), OffsetDateTime.now(), null, copies));
            }
        } else if (session != null && session.getNotes() != null && !session.getNotes().isBlank()) {
            value.getEntries().add(new Entry(UUID.randomUUID(), session.getNotes(), "", "", OffsetDateTime.now(), null, List.of()));
        }
        value.setEntries(value.getEntries());
        repo.save(value);
    }
    private void validateModel(UUID orderId, String key) {
        if (key == null || key.isBlank()) return;
        if (orderItems.findByOrder_Id(orderId).stream().noneMatch(item -> key.equals(item.getClientModelKey()) || key.equals(item.getId().toString())))
            throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_MODEL_REMOVED");
    }
    private List<Attachment> retain(List<Attachment> existing, List<UUID> ids) {
        if (ids == null) return List.of();
        if (new HashSet<>(ids).size() != ids.size() || ids.stream().anyMatch(id -> existing.stream().noneMatch(a -> a.id().equals(id))))
            throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_ATTACHMENT_INVALID");
        return existing.stream().filter(a -> ids.contains(a.id())).toList();
    }
    private List<Attachment> store(OrderInformation value, List<MultipartFile> files, List<Attachment> existing) throws IOException {
        if (files == null) files = List.of();
        if (existing.size() + files.size() > 10 || existing.stream().mapToLong(Attachment::size).sum()
                + files.stream().mapToLong(MultipartFile::getSize).sum() > MAX_TOTAL)
            throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_LIMIT");
        List<Attachment> added = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty() || file.getSize() > MAX_FILE) throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_FILE_SIZE");
            String mime = validateType(file);
            try (InputStream stream = file.getInputStream()) {
                if (!antivirus.scanRequired(stream)) throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_FILE_REJECTED");
            }
            String name = normalize(file.getOriginalFilename()).replaceAll("[\\/\\\\\\p{Cntrl}]", "_");
            if (name.isBlank() || name.length() > 255) throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_FILE_NAME");
            Attachment a = new Attachment(UUID.randomUUID(), name, mime, file.getSize());
            Path target = path(value, a); Files.createDirectories(target.getParent()); rollbackDelete(target);
            try (InputStream input = file.getInputStream()) { Files.copy(input, target); }
            added.add(a);
        }
        return added;
    }
    static String validateType(MultipartFile file) throws IOException {
        byte[] head;
        try (InputStream input = file.getInputStream()) { head = input.readNBytes(12); }
        String name = normalize(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        String mime = null;
        if (head.length >= 5 && new String(head, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-") && name.endsWith(".pdf")) mime = "application/pdf";
        if (head.length >= 8 && Arrays.equals(Arrays.copyOf(head, 8), new byte[]{(byte)137,80,78,71,13,10,26,10}) && name.endsWith(".png")) mime = "image/png";
        if (head.length >= 3 && head[0] == (byte)255 && head[1] == (byte)216 && head[2] == (byte)255 && (name.endsWith(".jpg") || name.endsWith(".jpeg"))) mime = "image/jpeg";
        if (mime == null || (file.getContentType() != null && !mime.equalsIgnoreCase(file.getContentType())))
            throw new ResponseStatusException(BAD_REQUEST, "INFORMATION_FILE_TYPE");
        return mime;
    }
    private static String normalize(String value) { return value == null ? "" : value.trim(); }
    private Path path(OrderInformation value, Attachment file) { return root.resolve(value.getId().toString()).resolve(file.id().toString()); }
    private void rollbackDelete(Path path) { afterCompletion(path, false); }
    private void deleteAfterCommit(Path path) { afterCompletion(path, true); }
    private void afterCompletion(Path path, boolean committed) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if ((status == STATUS_COMMITTED) == committed) try { Files.deleteIfExists(path); }
                catch (IOException e) { org.slf4j.LoggerFactory.getLogger(OrderInformationService.class).warn("Information file cleanup failed: {}", path, e); }
            }
        });
    }
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanup() {
        for (OrderInformation candidate : repo.findByOrderIdIsNullAndExpiresAtBefore(OffsetDateTime.now())) {
            OrderInformation value = repo.lockById(candidate.getId()).orElse(null);
            if (value == null || value.getOrderId() != null || !value.getExpiresAt().isBefore(OffsetDateTime.now())) continue;
            for (Entry entry : value.getEntries()) for (Attachment a : entry.attachments()) deleteAfterCommit(path(value, a));
            repo.delete(value);
        }
    }
}
