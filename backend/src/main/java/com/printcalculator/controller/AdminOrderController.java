package com.printcalculator.controller;

import com.printcalculator.dto.AddressDto;
import com.printcalculator.dto.OrderDto;
import com.printcalculator.dto.OrderItemDto;
import com.printcalculator.entity.Order;
import com.printcalculator.entity.OrderItem;
import com.printcalculator.repository.OrderItemRepository;
import com.printcalculator.repository.OrderRepository;
import com.printcalculator.repository.PaymentRepository;
import com.printcalculator.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentService paymentService;

    public AdminOrderController(
            OrderRepository orderRepo,
            OrderItemRepository orderItemRepo,
            PaymentRepository paymentRepo,
            PaymentService paymentService
    ) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.paymentRepo = paymentRepo;
        this.paymentService = paymentService;
    }

    @GetMapping
    public ResponseEntity<List<OrderDto>> listOrders() {
        List<OrderDto> response = orderRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toOrderDto)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(toOrderDto(getOrderOrThrow(orderId)));
    }

    @PostMapping("/{orderId}/payments/confirm")
    @Transactional
    public ResponseEntity<OrderDto> confirmPayment(
            @PathVariable UUID orderId,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        getOrderOrThrow(orderId);
        String method = payload != null ? payload.get("method") : null;
        paymentService.confirmPayment(orderId, method);
        return ResponseEntity.ok(toOrderDto(getOrderOrThrow(orderId)));
    }

    private Order getOrderOrThrow(UUID orderId) {
        return orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Order not found"));
    }

    private OrderDto toOrderDto(Order order) {
        List<OrderItem> items = orderItemRepo.findByOrder_Id(order.getId());
        OrderDto dto = new OrderDto();
        dto.setId(order.getId());
        dto.setOrderNumber(getDisplayOrderNumber(order));
        dto.setStatus(order.getStatus());

        paymentRepo.findByOrder_Id(order.getId()).ifPresent(p -> {
            dto.setPaymentStatus(p.getStatus());
            dto.setPaymentMethod(p.getMethod());
        });

        dto.setCustomerEmail(order.getCustomerEmail());
        dto.setCustomerPhone(order.getCustomerPhone());
        dto.setPreferredLanguage(order.getPreferredLanguage());
        dto.setBillingCustomerType(order.getBillingCustomerType());
        dto.setCurrency(order.getCurrency());
        dto.setSetupCostChf(order.getSetupCostChf());
        dto.setShippingCostChf(order.getShippingCostChf());
        dto.setDiscountChf(order.getDiscountChf());
        dto.setSubtotalChf(order.getSubtotalChf());
        dto.setTotalChf(order.getTotalChf());
        dto.setCreatedAt(order.getCreatedAt());
        dto.setShippingSameAsBilling(order.getShippingSameAsBilling());

        AddressDto billing = new AddressDto();
        billing.setFirstName(order.getBillingFirstName());
        billing.setLastName(order.getBillingLastName());
        billing.setCompanyName(order.getBillingCompanyName());
        billing.setContactPerson(order.getBillingContactPerson());
        billing.setAddressLine1(order.getBillingAddressLine1());
        billing.setAddressLine2(order.getBillingAddressLine2());
        billing.setZip(order.getBillingZip());
        billing.setCity(order.getBillingCity());
        billing.setCountryCode(order.getBillingCountryCode());
        dto.setBillingAddress(billing);

        if (!Boolean.TRUE.equals(order.getShippingSameAsBilling())) {
            AddressDto shipping = new AddressDto();
            shipping.setFirstName(order.getShippingFirstName());
            shipping.setLastName(order.getShippingLastName());
            shipping.setCompanyName(order.getShippingCompanyName());
            shipping.setContactPerson(order.getShippingContactPerson());
            shipping.setAddressLine1(order.getShippingAddressLine1());
            shipping.setAddressLine2(order.getShippingAddressLine2());
            shipping.setZip(order.getShippingZip());
            shipping.setCity(order.getShippingCity());
            shipping.setCountryCode(order.getShippingCountryCode());
            dto.setShippingAddress(shipping);
        }

        List<OrderItemDto> itemDtos = items.stream().map(i -> {
            OrderItemDto idto = new OrderItemDto();
            idto.setId(i.getId());
            idto.setOriginalFilename(i.getOriginalFilename());
            idto.setMaterialCode(i.getMaterialCode());
            idto.setColorCode(i.getColorCode());
            idto.setQuantity(i.getQuantity());
            idto.setPrintTimeSeconds(i.getPrintTimeSeconds());
            idto.setMaterialGrams(i.getMaterialGrams());
            idto.setUnitPriceChf(i.getUnitPriceChf());
            idto.setLineTotalChf(i.getLineTotalChf());
            return idto;
        }).toList();
        dto.setItems(itemDtos);

        return dto;
    }

    private String getDisplayOrderNumber(Order order) {
        String orderNumber = order.getOrderNumber();
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber;
        }
        return order.getId() != null ? order.getId().toString() : "unknown";
    }
}
