package com.printcalculator.controller.admin;

import com.printcalculator.dto.AdminHomeProjectDto;
import com.printcalculator.dto.AdminUpsertHomeProjectRequest;
import com.printcalculator.service.admin.AdminHomeProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/home-projects")
@Transactional(readOnly = true)
public class AdminHomeProjectController {
    private final AdminHomeProjectService adminHomeProjectService;

    public AdminHomeProjectController(AdminHomeProjectService adminHomeProjectService) {
        this.adminHomeProjectService = adminHomeProjectService;
    }

    @GetMapping
    public ResponseEntity<List<AdminHomeProjectDto>> getProjects() {
        return ResponseEntity.ok(adminHomeProjectService.getProjects());
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<AdminHomeProjectDto> getProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(adminHomeProjectService.getProject(projectId));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AdminHomeProjectDto> createProject(@RequestBody AdminUpsertHomeProjectRequest payload) {
        return ResponseEntity.ok(adminHomeProjectService.createProject(payload));
    }

    @PutMapping("/{projectId}")
    @Transactional
    public ResponseEntity<AdminHomeProjectDto> updateProject(@PathVariable UUID projectId,
                                                             @RequestBody AdminUpsertHomeProjectRequest payload) {
        return ResponseEntity.ok(adminHomeProjectService.updateProject(projectId, payload));
    }

    @DeleteMapping("/{projectId}")
    @Transactional
    public ResponseEntity<Void> deleteProject(@PathVariable UUID projectId) {
        adminHomeProjectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }
}
