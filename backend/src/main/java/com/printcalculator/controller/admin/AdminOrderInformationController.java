package com.printcalculator.controller.admin;

import com.printcalculator.dto.InformationDto;
import com.printcalculator.service.information.OrderInformationService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderInformationController {
    @ModelAttribute
    public void privateResponse(jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
    private final OrderInformationService service;
    public AdminOrderInformationController(OrderInformationService service) { this.service = service; }
    @GetMapping("/information-unread") public List<UUID> unread() { return service.unreadOrders(); }
    @GetMapping("/{id}/information") public InformationDto get(@PathVariable UUID id) { return service.getOrder(id, null, true); }
    @PostMapping("/{id}/information/read") public InformationDto read(@PathVariable UUID id, @Valid @RequestBody InformationDto.ReadRequest request) { return service.markRead(id, request); }
    @GetMapping("/{id}/information/files/{file}") public ResponseEntity<Resource> file(@PathVariable UUID id, @PathVariable UUID file) throws IOException { return service.download(id, null, file, true, true); }
}
