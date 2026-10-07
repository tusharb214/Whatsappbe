package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.category.CategoryRequest;
import com.sitegenius.whatsappbe.dto.category.CategoryResponse;
import com.sitegenius.whatsappbe.service.CategoryService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // =========================
    // GET ALL CATEGORIES
    // =========================

    @GetMapping

    public ResponseEntity<List<CategoryResponse>> getAllCategories() {

        return ResponseEntity.ok(
                categoryService.getAllCategories()
        );
    }


    // =========================
    // GET ACTIVE CATEGORIES
    // =========================

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {

        return ResponseEntity.ok(
                categoryService.getActiveCategories()
        );
    }


    // =========================
    // GET CATEGORY BY ID
    // =========================

    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<CategoryResponse> getCategoryById(
            @PathVariable Long categoryId
    ) {

        return ResponseEntity.ok(
                categoryService.getCategoryById(
                        categoryId
                )
        );
    }


    // =========================
    // CREATE CATEGORY
    // =========================

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request
    ) {

        CategoryResponse response =
                categoryService.createCategory(
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // =========================
    // UPDATE CATEGORY
    // =========================

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long categoryId,
            @Valid @RequestBody CategoryRequest request
    ) {

        return ResponseEntity.ok(
                categoryService.updateCategory(
                        categoryId,
                        request
                )
        );
    }


    // =========================
    // DEACTIVATE CATEGORY
    // =========================

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deactivateCategory(
            @PathVariable Long categoryId
    ) {

        categoryService.deactivateCategory(
                categoryId
        );

        return ResponseEntity
                .noContent()
                .build();
    }
}