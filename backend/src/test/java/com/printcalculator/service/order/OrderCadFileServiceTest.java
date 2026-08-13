package com.printcalculator.service.order;

import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderDeliverableFile;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.entity.Payment;
import com.printcalculator.repository.OrderDeliverableFileRepository;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.repository.QuoteLineItemRepository;
import com.printcalculator.service.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCadFileServiceTest {
    @Mock
    private OrderRepository orderRepo;
    @Mock
    private OrderItemRepository orderItemRepo;
    @Mock
    private OrderDeliverableFileRepository deliverableFileRepo;
    @Mock
    private PaymentRepository paymentRepo;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepo;
    @Mock
    private StorageService storageService;

    @InjectMocks
    private OrderCadFileService service;

    @Test
    void downloadCustomerCadFiles_beforePaymentConfirmation_shouldReturnForbidden() {
        UUID orderId = UUID.randomUUID();
        Order order = buildCadOrder(orderId);
        Payment payment = new Payment();
        payment.setStatus("REPORTED");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.downloadCustomerCadFiles(orderId)
        );

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void downloadCustomerCadFiles_withSingleBaseFile_shouldReturnOriginalFile() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Order order = buildCadOrder(orderId);
        order.setStatus("PAID");

        OrderItem item = buildBaseItem(order, itemId, "part.stl");
        byte[] content = "solid mesh".getBytes();

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of(item));
        when(deliverableFileRepo.findByOrder_IdOrderByCreatedAtAsc(orderId)).thenReturn(List.of());
        when(storageService.loadAsResource(Path.of("orders", orderId.toString(), "3d-files", itemId.toString(), "part.stl")))
                .thenReturn(new ByteArrayResource(content));

        ResponseEntity<Resource> response = service.downloadCustomerCadFiles(orderId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.parseMediaType("model/stl"), response.getHeaders().getContentType());
        assertNotNull(response.getBody());
        assertArrayEquals(content, response.getBody().getInputStream().readAllBytes());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("part.stl"));
    }

    @Test
    void downloadCustomerCadFiles_withMultipleFiles_shouldReturnZip() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID deliverableId = UUID.randomUUID();
        Order order = buildCadOrder(orderId);
        order.setPaidAt(java.time.OffsetDateTime.now());

        OrderItem item = buildBaseItem(order, itemId, "part.stl");
        OrderDeliverableFile deliverable = new OrderDeliverableFile();
        deliverable.setId(deliverableId);
        deliverable.setOrder(order);
        deliverable.setOriginalFilename("modified.step");
        deliverable.setStoredRelativePath(Path.of(
                "orders",
                orderId.toString(),
                "cad-deliverables",
                deliverableId.toString(),
                "modified.step"
        ).toString());
        deliverable.setStoredFilename("modified.step");

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepo.findByOrder_Id(orderId)).thenReturn(List.of(item));
        when(deliverableFileRepo.findByOrder_IdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(deliverable));
        when(storageService.loadAsResource(Path.of("orders", orderId.toString(), "3d-files", itemId.toString(), "part.stl")))
                .thenReturn(new ByteArrayResource("solid".getBytes()));
        when(storageService.loadAsResource(Path.of("orders", orderId.toString(), "cad-deliverables", deliverableId.toString(), "modified.step")))
                .thenReturn(new ByteArrayResource("step".getBytes()));

        ResponseEntity<Resource> response = service.downloadCustomerCadFiles(orderId);

        assertEquals("application/zip", response.getHeaders().getContentType().toString());
        assertNotNull(response.getBody());
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getBody().getInputStream().readAllBytes()))) {
            ZipEntry first = zip.getNextEntry();
            assertNotNull(first);
            assertEquals("part.stl", first.getName());
            ZipEntry second = zip.getNextEntry();
            assertNotNull(second);
            assertEquals("modified.step", second.getName());
        }
    }

    @Test
    void uploadAdminCadFiles_shouldStoreFilesUnderOrderCadDeliverables() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID deliverableId = UUID.randomUUID();
        Order order = buildCadOrder(orderId);
        MockMultipartFile file = new MockMultipartFile("files", "model.step", "application/step", "step".getBytes());

        when(orderRepo.findById(orderId)).thenReturn(Optional.of(order));
        when(deliverableFileRepo.save(any(OrderDeliverableFile.class))).thenAnswer(invocation -> {
            OrderDeliverableFile deliverable = invocation.getArgument(0);
            if (deliverable.getId() == null) {
                deliverable.setId(deliverableId);
            }
            return deliverable;
        });

        service.uploadAdminCadFiles(orderId, List.of(file));

        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        verify(storageService).store(eq(file), pathCaptor.capture());
        assertTrue(pathCaptor.getValue().startsWith(Path.of(
                "orders",
                orderId.toString(),
                "cad-deliverables",
                deliverableId.toString()
        )));
    }

    private Order buildCadOrder(UUID orderId) {
        Order order = new Order();
        order.setId(orderId);
        order.setIsCadOrder(true);
        return order;
    }

    private OrderItem buildBaseItem(Order order, UUID itemId, String filename) {
        OrderItem item = new OrderItem();
        item.setId(itemId);
        item.setOrder(order);
        item.setItemType("PRINT_FILE");
        item.setOriginalFilename(filename);
        item.setStoredRelativePath(Path.of(
                "orders",
                order.getId().toString(),
                "3d-files",
                itemId.toString(),
                filename
        ).toString());
        item.setStoredFilename(filename);
        item.setMimeType("model/stl");
        return item;
    }
}
