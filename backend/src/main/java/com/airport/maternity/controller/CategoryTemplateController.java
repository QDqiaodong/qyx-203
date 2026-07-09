package com.airport.maternity.controller;

import com.airport.maternity.dto.ResponseDTO;
import com.airport.maternity.service.CategoryTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@CrossOrigin(origins = "*")
public class CategoryTemplateController {

    @Autowired
    private CategoryTemplateService categoryTemplateService;

    @GetMapping("/device-types")
    public ResponseDTO<List<String>> getDeviceTypes() {
        return ResponseDTO.success(categoryTemplateService.getDeviceTypes());
    }

    @GetMapping("/terminal-areas")
    public ResponseDTO<List<String>> getTerminalAreas() {
        return ResponseDTO.success(categoryTemplateService.getTerminalAreas());
    }

    @PostMapping("/refresh")
    public ResponseDTO<Void> refresh() {
        categoryTemplateService.refreshCategoryTemplate();
        return ResponseDTO.success("模板已刷新", null);
    }
}