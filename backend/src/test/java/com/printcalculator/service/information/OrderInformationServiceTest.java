package com.printcalculator.service.information;

import com.printcalculator.dto.InformationDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderInformation;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.event.OrderCreatedEvent;
import com.printcalculator.repository.OrderInformationRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.service.storage.ClamAVService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(excludeAutoConfiguration = org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration.class, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.datasource.url=jdbc:h2:mem:information;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON", "logging.level.org.hibernate.SQL=OFF"}, showSql = false)
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@Import({OrderInformationService.class, OrderInformationServiceTest.Repositories.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderInformationServiceTest {
    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(
        basePackageClasses = OrderInformationRepository.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
            type = org.springframework.context.annotation.FilterType.REGEX,
            pattern = "com\\.printcalculator\\.repository\\.(?!OrderInformationRepository).*"))
    static class Repositories {}

    static final Path ROOT;
    static { try { ROOT = Files.createTempDirectory("order-information-test-"); } catch (IOException e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("storage.information-root", ROOT::toString); }
    @Autowired OrderInformationService service;
    @Autowired OrderInformationRepository repo;
    @MockitoBean ClamAVService antivirus;
    @MockitoBean OrderItemRepository orderItems;
    @MockitoBean com.printcalculator.repository.OrderRepository orders;
    @BeforeEach void allowCleanFiles() { lenient().when(antivirus.scanRequired(any())).thenReturn(true); }
    @AfterEach void cleanup() throws IOException {
        repo.deleteAll();
        try (var paths = Files.walk(ROOT)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) if (!p.equals(ROOT)) Files.deleteIfExists(p); }
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("files", "orientation.pdf", "application/pdf", "%PDF-1.7\n%%EOF".getBytes()); }
    private InformationDto.EntryRequest entry(String text) { return new InformationDto.EntryRequest(text, "", "", List.of()); }
    private Order order(InformationDto.Credential credential) throws IOException {
        QuoteSession session = new QuoteSession(); session.setInformationDraftId(credential.id());
        Order order = new Order(); order.setId(UUID.randomUUID()); order.setSourceQuoteSession(session);
        service.snapshot(new OrderCreatedEvent(this, order)); return order;
    }
    @Test void recoversStoredNotesAndDiskFilesFromTheSessionAssociation() throws Exception {
        var credential = service.create();
        service.saveDraft(credential.id(), credential.token(), entry("Saved instructions"), List.of(pdf()));
        QuoteSession session = new QuoteSession();
        session.setInformationDraftId(credential.id());
        var recovered = service.sessionCredential(session);
        var value = service.getDraft(recovered.id(), recovered.token());
        assertEquals("Saved instructions", value.entries().getFirst().text());
        var response = service.download(recovered.id(), recovered.token(),
                value.entries().getFirst().attachments().getFirst().id(), false, false);
        assertArrayEquals(pdf().getBytes(), response.getBody().getInputStream().readAllBytes());
    }

    @Test void rejectsAttachmentWhenAntivirusUnavailableWithoutSavingIt() throws Exception {
        var c = service.create();
        when(antivirus.scanRequired(any())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(ResponseStatusException.class, () -> service.saveDraft(c.id(), c.token(), entry("Keep"), List.of(pdf())));
        assertTrue(service.getDraft(c.id(), c.token()).entries().isEmpty());
        try (var paths = Files.walk(ROOT)) { assertEquals(0, paths.filter(Files::isRegularFile).count()); }
    }

    @Test void persistsDraftAndRequiresItsSeparateCredentialForReadAndWrite() throws Exception {
        var c = service.create();
        service.saveDraft(c.id(), c.token(), entry("Vertical orientation"), List.of(pdf()));
        var restored = service.getDraft(c.id(), c.token());
        assertEquals("Vertical orientation", restored.entries().getFirst().text());
        assertEquals(1, restored.entries().getFirst().attachments().size());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.getDraft(c.id(), "wrong")).getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> service.saveDraft(c.id(), "wrong", entry("overwrite"), List.of()));
        verify(antivirus).scanRequired(any());
    }
    @Test void snapshotsFilesAtPurchaseAndPreservesThemAfterDraftRemovalAndCleanup() throws Exception {
        var c = service.create(); var before = service.saveDraft(c.id(), c.token(), entry("Original"), List.of(pdf()));
        var order = order(c); var purchased = service.getOrder(order.getId(), order.getInformationToken(), false);
        UUID originalFile = before.entries().getFirst().attachments().getFirst().id();
        UUID purchasedFile = purchased.entries().getFirst().attachments().getFirst().id();
        assertNotEquals(originalFile, purchasedFile);
        service.saveDraft(c.id(), c.token(), entry("Modified later"), List.of());
        assertFalse(Files.exists(ROOT.resolve(c.id().toString()).resolve(originalFile.toString())));
        var draft = repo.findById(c.id()).orElseThrow(); draft.setExpiresAt(OffsetDateTime.now().minusDays(1)); repo.save(draft);
        service.cleanup();
        assertFalse(repo.existsById(c.id()));
        assertEquals("Original", service.getOrder(order.getId(), order.getInformationToken(), false).entries().getFirst().text());
        assertArrayEquals(pdf().getBytes(), service.download(order.getId(), order.getInformationToken(), purchasedFile, true, false).getBody().getContentAsByteArray());
    }
    @Test void postPurchaseAddsTimestampedEntriesAndReadAcknowledgementDoesNotHideLaterAdditions() throws Exception {
        var c = service.create(); var order = order(c);
        var first = service.append(order.getId(), order.getInformationToken(), entry("First"), List.of());
        assertNotNull(first.entries().getFirst().createdAt());
        service.append(order.getId(), order.getInformationToken(), entry("Second"), List.of());
        var read = service.markRead(order.getId(), new InformationDto.ReadRequest(List.of(first.entries().getFirst().id())));
        assertEquals(List.of("First", "Second"), read.entries().stream().map(OrderInformation.Entry::text).toList());
        assertNotNull(read.entries().getFirst().readAt()); assertNull(read.entries().getLast().readAt());
        assertTrue(service.unreadOrders().contains(order.getId()));
        service.markRead(order.getId(), new InformationDto.ReadRequest(read.entries().stream().map(OrderInformation.Entry::id).toList()));
        assertFalse(service.unreadOrders().contains(order.getId()));
    }
    @Test void rejectsOtherOrdersCredentialsAndCrossOrderFileIdentifiers() throws Exception {
        var c = service.create(); service.saveDraft(c.id(), c.token(), entry(""), List.of(pdf()));
        var first = order(c); var second = order(service.create());
        var file = service.getOrder(first.getId(), first.getInformationToken(), false).entries().getFirst().attachments().getFirst().id();
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.download(first.getId(), second.getInformationToken(), file, true, false)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.download(second.getId(), second.getInformationToken(), file, true, false)).getStatusCode().value());
        assertEquals("no-store", service.download(first.getId(), first.getInformationToken(), file, true, false).getHeaders().getCacheControl());
        assertTrue(service.download(first.getId(), null, file, true, true).getHeaders().getContentDisposition().isAttachment());
    }
    @Test void rejectsSpoofedTypesAndRollsBackAlreadyStoredFilesOnBatchFailure() throws Exception {
        var c = service.create();
        var spoofed = new MockMultipartFile("files", "photo.png", "image/png", "<script>bad</script>".getBytes());
        assertThrows(ResponseStatusException.class, () -> service.saveDraft(c.id(), c.token(), entry(""), List.of(pdf(), spoofed)));
        assertTrue(service.getDraft(c.id(), c.token()).entries().isEmpty());
        try (var paths = Files.walk(ROOT)) { assertEquals(0, paths.filter(Files::isRegularFile).count()); }
    }
    @Test void rejectsOversizedFilesTotalWeightAndCount() throws Exception {
        var c = service.create();
        var large = new MockMultipartFile("files", "large.pdf", "application/pdf", new byte[(int)OrderInformationService.MAX_FILE + 1]);
        assertThrows(ResponseStatusException.class, () -> service.saveDraft(c.id(), c.token(), entry(""), List.of(large)));
        assertThrows(ResponseStatusException.class, () -> service.saveDraft(c.id(), c.token(), entry(""), Collections.nCopies(11, pdf())));
        byte[] bytes = new byte[(int)OrderInformationService.MAX_FILE]; System.arraycopy("%PDF-".getBytes(), 0, bytes, 0, 5);
        var tenMb = new MockMultipartFile("files", "large.pdf", "application/pdf", bytes);
        assertThrows(ResponseStatusException.class, () -> service.saveDraft(c.id(), c.token(), entry(""), Collections.nCopies(4, tenMb)));
    }
    @Test void preservesUnambiguousModelKeyAndRejectsRemovedModels() throws Exception {
        var c = service.create();
        service.saveDraft(c.id(), c.token(), new InformationDto.EntryRequest("Rotate", "same-name.stl", "part-two", List.of()), List.of());
        OrderItem item = new OrderItem(); item.setId(UUID.randomUUID()); item.setClientModelKey("part-two");
        when(orderItems.findByOrder_Id(any())).thenReturn(List.of(item));
        Order order = order(c);
        assertEquals("part-two", service.getOrder(order.getId(), order.getInformationToken(), false).entries().getFirst().modelKey());
        assertThrows(ResponseStatusException.class, () -> service.append(order.getId(), order.getInformationToken(), new InformationDto.EntryRequest("Wrong", "same-name.stl", "missing", List.of()), List.of()));
    }
    @Test void concurrentAdditionsAreSerializedWithoutLostEntries() throws Exception {
        var order = order(service.create());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Void> first = () -> { service.append(order.getId(), order.getInformationToken(), entry("A"), List.of()); return null; };
            Callable<Void> second = () -> { service.append(order.getId(), order.getInformationToken(), entry("B"), List.of()); return null; };
            for (Future<Void> future : executor.invokeAll(List.of(first, second))) future.get(10, TimeUnit.SECONDS);
        }
        assertEquals(2, service.getOrder(order.getId(), order.getInformationToken(), false).entries().size());
    }
    @Test void legacyOrdersKeepTheirNotesAndOnlyAdminReceivesTheCustomerCredential() {
        Order legacy = new Order(); legacy.setId(UUID.randomUUID()); legacy.setInformationToken(null); legacy.setCreatedAt(OffsetDateTime.now().minusDays(10));
        QuoteSession quote = new QuoteSession(); quote.setNotes("Old instructions"); legacy.setSourceQuoteSession(quote);
        when(orders.findLockedById(legacy.getId())).thenReturn(Optional.of(legacy));
        var admin = service.getOrder(legacy.getId(), null, true);
        assertNotNull(admin.customerToken()); assertEquals("Old instructions", admin.entries().getFirst().text());
        var customer = service.getOrder(legacy.getId(), admin.customerToken(), false);
        assertNull(customer.customerToken()); assertEquals(admin.entries(), customer.entries());
    }
    @Test void publicHttpEndpointsValidateRequestsAndEnforceFileCredentials() throws Exception {
        var limits = mock(com.printcalculator.service.QuoteRateLimitService.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.printcalculator.controller.OrderInformationController(service, limits)).build();
        var c = service.create();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/information-drafts/{id}", c.id())
                .header("X-Information-Token", "incorrect"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        var metadata = new MockMultipartFile("entry", "", "application/json", "{\"text\":\"HTTP instructions\",\"model\":\"\",\"modelKey\":\"\",\"attachments\":[]}".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/information-drafts/{id}", c.id())
                .file(metadata).file(pdf()).header("X-Information-Token", c.token()).with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.entries[0].text").value("HTTP instructions"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        var excessive = new MockMultipartFile("entry", "", "application/json", ("{\"text\":\"" + "a".repeat(5001) + "\"}").getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/information-drafts/{id}", c.id())
                .file(excessive).header("X-Information-Token", c.token()).with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
    @Test void expiredDraftCannotBeReadOrConverted() {
        var c = service.create(); var draft = repo.findById(c.id()).orElseThrow();
        draft.setExpiresAt(OffsetDateTime.now().minusMinutes(1)); repo.save(draft);
        QuoteSession session = new QuoteSession(); session.setInformationDraftId(c.id());
        assertThrows(ResponseStatusException.class, () -> service.getDraft(c.id(), c.token()));
        assertThrows(ResponseStatusException.class, () -> service.validateCheckout(session, c.token()));
    }
}
