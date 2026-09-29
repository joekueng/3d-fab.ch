package com.printcalculator.service.admin;

import com.printcalculator.dto.AdminQuoteItemStatsDto;
import com.printcalculator.dto.AdminQuoteItemStatsUpdateRequest;
import com.printcalculator.dto.AdminQuoteItemsResponse;
import com.printcalculator.entity.FilamentMaterialType;
import com.printcalculator.entity.FilamentVariant;
import com.printcalculator.entity.PricingPolicy;
import com.printcalculator.entity.PrinterMachine;
import com.printcalculator.entity.QuoteLineItem;
import com.printcalculator.entity.QuoteSession;
import com.printcalculator.repository.FilamentMaterialTypeRepository;
import com.printcalculator.repository.FilamentVariantRepository;
import com.printcalculator.repository.PricingPolicyRepository;
import com.printcalculator.repository.PrinterMachineRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.repository.QuoteSessionRepository;
import com.printcalculator.service.QuoteCalculator;
import com.printcalculator.service.QuoteSessionTotalsService;
import com.printcalculator.service.quote.QuoteSessionResponseAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminQuoteItemPricingServiceTest {

    @Mock
    private QuoteSessionRepository sessionRepo;
    @Mock
    private QuoteLineItemRepository lineItemRepo;
    @Mock
    private PricingPolicyRepository pricingRepo;
    @Mock
    private PrinterMachineRepository machineRepo;
    @Mock
    private FilamentMaterialTypeRepository materialRepo;
    @Mock
    private FilamentVariantRepository variantRepo;
    @Mock
    private QuoteCalculator quoteCalculator;
    @Mock
    private QuoteSessionTotalsService totalsService;
    @Mock
    private QuoteSessionResponseAssembler assembler;

    private AdminQuoteItemPricingService service;

    @BeforeEach
    void setUp() {
        service = new AdminQuoteItemPricingService(
                sessionRepo,
                lineItemRepo,
                pricingRepo,
                machineRepo,
                materialRepo,
                variantRepo,
                quoteCalculator,
                totalsService,
                assembler
        );
    }

    @Test
    void updateStats_withPreview_shouldReturnProposedPricesWithoutSaving() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        FilamentVariant variant = variant();
        QuoteLineItem item = printItem(variant);
        AdminQuoteItemStatsUpdateRequest request = request(false, item.getId(), 7200, "200");

        PricingPolicy policy = new PricingPolicy();
        PrinterMachine machine = machine(5L);

        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(machineRepo.findById(5L)).thenReturn(Optional.of(machine));
        when(quoteCalculator.calculatePrintItemUnitPrice(7200L, new BigDecimal("200"), machine, policy, variant))
                .thenReturn(new BigDecimal("20.00"));

        QuoteSessionTotalsService.QuoteSessionTotals currentTotals = totals("10.00");
        QuoteSessionTotalsService.QuoteSessionTotals proposedTotals = totals("40.00");
        when(totalsService.compute(session, List.of(item))).thenReturn(currentTotals);
        when(totalsService.compute(eq(session), anyList(), anyMap())).thenReturn(proposedTotals);
        when(assembler.resolveDistributedUnitPrice(any(BigDecimal.class), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

        AdminQuoteItemsResponse response = service.updateStats(session.getId(), request);

        assertEquals(new BigDecimal("10.00"), response.getItems().get(0).getUnitPriceChf());
        assertEquals(new BigDecimal("20.00"), response.getItems().get(0).getNewUnitPriceChf());
        assertEquals(new BigDecimal("40.00"), response.getGrandTotalChf());
        assertEquals(3600, item.getPrintTimeSeconds());
        assertEquals(0, new BigDecimal("100").compareTo(item.getMaterialGrams()));
        verify(lineItemRepo, never()).saveAll(any());
    }

    @Test
    void updateStats_withPersist_shouldWriteStatsAndKeepOriginalBreakdown() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        FilamentVariant variant = variant();
        QuoteLineItem item = printItem(variant);
        item.getPricingBreakdown().putAll(java.util.Map.of(
                "cadColorBaseUnitPrice", "9.00",
                "cadColorBaseCostPerKg", "20.00",
                "cadColorMarkup", "1.2"
        ));
        AdminQuoteItemStatsUpdateRequest request = request(true, item.getId(), 7200, "200");

        PricingPolicy policy = new PricingPolicy();
        PrinterMachine machine = machine(5L);

        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(machineRepo.findById(5L)).thenReturn(Optional.of(machine));
        when(quoteCalculator.calculatePrintItemUnitPrice(7200L, new BigDecimal("200"), machine, policy, variant))
                .thenReturn(new BigDecimal("20.00"));
        when(totalsService.compute(session, List.of(item))).thenReturn(totals("40.00"));
        when(assembler.resolveDistributedUnitPrice(any(BigDecimal.class), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

        AdminQuoteItemsResponse response = service.updateStats(session.getId(), request);

        assertEquals(7200, item.getPrintTimeSeconds());
        assertEquals(0, new BigDecimal("200").compareTo(item.getMaterialGrams()));
        assertEquals(0, new BigDecimal("20.00").compareTo(item.getUnitPriceChf()));
        assertNotNull(item.getPricingBreakdown().get("printStatsUpdatedAt"));
        assertEquals(3600, item.getPricingBreakdown().get("originalPrintTimeSeconds"));
        assertEquals(0, new BigDecimal("100").compareTo(
                new BigDecimal(item.getPricingBreakdown().get("originalMaterialGrams").toString())));
        assertEquals(0, new BigDecimal("10.00").compareTo(
                new BigDecimal(item.getPricingBreakdown().get("originalUnitPriceChf").toString())));
        assertEquals(0, new BigDecimal("20.00").compareTo(
                new BigDecimal(item.getPricingBreakdown().get("machine_cost").toString())));
        assertFalse(item.getPricingBreakdown().containsKey("cadColorBaseUnitPrice"));
        assertFalse(item.getPricingBreakdown().containsKey("cadColorBaseCostPerKg"));
        assertFalse(item.getPricingBreakdown().containsKey("cadColorMarkup"));
        assertNull(response.getItems().get(0).getNewUnitPriceChf());
        verify(lineItemRepo).saveAll(List.of(item));
    }

    @Test
    void updateStats_withConvertedSession_shouldReturnConflict() {
        QuoteSession session = session(UUID.randomUUID(), "CONVERTED");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStats(session.getId(), request(false, UUID.randomUUID(), 3600, "100"))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void updateStats_withItemOfAnotherSession_shouldReturnNotFound() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of());
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(new PricingPolicy());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStats(session.getId(), request(false, UUID.randomUUID(), 3600, "100"))
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void updateStats_withReviewRequiredItem_shouldReturnConflict() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        QuoteLineItem item = printItem(variant());
        item.setStatus("REVIEW_REQUIRED");
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(new PricingPolicy());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStats(session.getId(), request(false, item.getId(), 3600, "100"))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void updateStats_withDuplicateItemIds_shouldReturnBadRequest() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        QuoteLineItem item = printItem(variant());
        AdminQuoteItemStatsUpdateRequest request = new AdminQuoteItemStatsUpdateRequest();
        request.setPersist(false);
        AdminQuoteItemStatsUpdateRequest.Item first = requestItem(item.getId(), 3600, "100");
        AdminQuoteItemStatsUpdateRequest.Item second = requestItem(item.getId(), 7200, "200");
        request.setItems(List.of(first, second));

        PricingPolicy policy = new PricingPolicy();
        PrinterMachine machine = machine(5L);
        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(machineRepo.findById(5L)).thenReturn(Optional.of(machine));
        when(quoteCalculator.calculatePrintItemUnitPrice(anyLong(), any(BigDecimal.class), any(), any(), any()))
                .thenReturn(new BigDecimal("10.00"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.updateStats(session.getId(), request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void updateStats_withoutStoredMachine_shouldFallBackToFirstActiveMachine() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        FilamentVariant variant = variant();
        QuoteLineItem item = printItem(variant);
        item.setPricingBreakdown(null);
        AdminQuoteItemStatsUpdateRequest request = request(false, item.getId(), 7200, "200");

        PricingPolicy policy = new PricingPolicy();
        PrinterMachine fallbackMachine = machine(9L);

        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(machineRepo.findFirstByIsActiveTrueOrderByIdAsc()).thenReturn(Optional.of(fallbackMachine));
        when(quoteCalculator.calculatePrintItemUnitPrice(7200L, new BigDecimal("200"), fallbackMachine, policy, variant))
                .thenReturn(new BigDecimal("20.00"));
        when(totalsService.compute(session, List.of(item))).thenReturn(totals("10.00"));
        when(totalsService.compute(eq(session), anyList(), anyMap())).thenReturn(totals("40.00"));
        when(assembler.resolveDistributedUnitPrice(any(BigDecimal.class), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

        service.updateStats(session.getId(), request);

        verify(quoteCalculator).calculatePrintItemUnitPrice(
                7200L, new BigDecimal("200"), fallbackMachine, policy, variant);
    }

    @Test
    void updateStats_withoutItemVariant_shouldFallBackToActiveVariantOfMaterial() {
        QuoteSession session = session(UUID.randomUUID(), "CAD_ACTIVE");
        QuoteLineItem item = printItem(variant());
        item.setFilamentVariant(null);
        item.setMaterialCode("PLA");
        AdminQuoteItemStatsUpdateRequest request = request(false, item.getId(), 7200, "200");

        PricingPolicy policy = new PricingPolicy();
        PrinterMachine machine = machine(5L);
        FilamentMaterialType materialType = new FilamentMaterialType();
        materialType.setMaterialCode("PLA");
        FilamentVariant fallbackVariant = variant();

        when(sessionRepo.findById(session.getId())).thenReturn(Optional.of(session));
        when(lineItemRepo.findByQuoteSessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(List.of(item));
        when(pricingRepo.findFirstByIsActiveTrueOrderByValidFromDesc()).thenReturn(policy);
        when(machineRepo.findById(5L)).thenReturn(Optional.of(machine));
        when(materialRepo.findByMaterialCode("PLA")).thenReturn(Optional.of(materialType));
        when(variantRepo.findFirstByFilamentMaterialTypeAndIsActiveTrue(materialType))
                .thenReturn(Optional.of(fallbackVariant));
        when(quoteCalculator.calculatePrintItemUnitPrice(7200L, new BigDecimal("200"), machine, policy, fallbackVariant))
                .thenReturn(new BigDecimal("20.00"));
        when(totalsService.compute(session, List.of(item))).thenReturn(totals("10.00"));
        when(totalsService.compute(eq(session), anyList(), anyMap())).thenReturn(totals("40.00"));
        when(assembler.resolveDistributedUnitPrice(any(BigDecimal.class), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0, BigDecimal.class));

        AdminQuoteItemsResponse response = service.updateStats(session.getId(), request);

        assertEquals(new BigDecimal("20.00"), response.getItems().get(0).getNewUnitPriceChf());
    }

    private QuoteSession session(UUID id, String status) {
        QuoteSession session = new QuoteSession();
        session.setId(id);
        session.setStatus(status);
        return session;
    }

    private QuoteLineItem printItem(FilamentVariant variant) {
        QuoteLineItem item = new QuoteLineItem();
        item.setId(UUID.randomUUID());
        item.setStatus("READY");
        item.setLineItemType("PRINT_FILE");
        item.setDisplayName("fixture.stl");
        item.setQuantity(2);
        item.setPrintTimeSeconds(3600);
        item.setMaterialGrams(new BigDecimal("100"));
        item.setUnitPriceChf(new BigDecimal("10.00"));
        item.setPricingBreakdown(new java.util.HashMap<>(java.util.Map.of("printerMachineId", 5L)));
        item.setFilamentVariant(variant);
        return item;
    }

    private FilamentVariant variant() {
        FilamentVariant variant = new FilamentVariant();
        variant.setId(1L);
        variant.setCostChfPerKg(new BigDecimal("25.00"));
        return variant;
    }

    private PrinterMachine machine(Long id) {
        PrinterMachine machine = new PrinterMachine();
        machine.setId(id);
        machine.setPowerWatts(1000);
        return machine;
    }

    private AdminQuoteItemStatsUpdateRequest request(boolean persist, UUID itemId, int seconds, String grams) {
        AdminQuoteItemStatsUpdateRequest request = new AdminQuoteItemStatsUpdateRequest();
        request.setPersist(persist);
        request.setItems(List.of(requestItem(itemId, seconds, grams)));
        return request;
    }

    private AdminQuoteItemStatsUpdateRequest.Item requestItem(UUID itemId, int seconds, String grams) {
        AdminQuoteItemStatsUpdateRequest.Item item = new AdminQuoteItemStatsUpdateRequest.Item();
        item.setItemId(itemId);
        item.setPrintTimeSeconds(seconds);
        item.setMaterialGrams(new BigDecimal(grams));
        return item;
    }

    private QuoteSessionTotalsService.QuoteSessionTotals totals(String grandTotal) {
        BigDecimal total = new BigDecimal(grandTotal);
        return new QuoteSessionTotalsService.QuoteSessionTotals(
                total,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                total,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                total,
                BigDecimal.valueOf(7200)
        );
    }
}
