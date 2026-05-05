package com.printcalculator.controller;

import com.printcalculator.dto.HomeProjectDto;
import com.printcalculator.service.home.HomeProjectService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/home")
@Transactional(readOnly = true)
public class HomeProjectController {
    private final HomeProjectService homeProjectService;

    public HomeProjectController(HomeProjectService homeProjectService) {
        this.homeProjectService = homeProjectService;
    }

    @GetMapping("/projects")
    public ResponseEntity<List<HomeProjectDto>> getProjects(@RequestParam(required = false) String lang) {
        return ResponseEntity.ok(homeProjectService.getActiveProjects(lang));
    }
}
