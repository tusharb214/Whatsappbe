package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.product.ProductRequest;
import com.sitegenius.whatsappbe.dto.product.ProductResponse;
import com.sitegenius.whatsappbe.service.ProductService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // =========================
    // GET ALL PRODUCTS
    // =========================

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<ProductResponse>> getAllProducts(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                productService.getAllProducts(authentication)
        );
    }


    // =========================
    // GET ACTIVE PRODUCTS
    // =========================

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<ProductResponse>> getActiveProducts(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                productService.getActiveProducts(authentication)
        );
    }


    // =========================
    // GET PRODUCT BY ID
    // =========================

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long productId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                productService.getProductById(
                        productId,
                        authentication
                )
        );
    }


    // =========================
    // CREATE PRODUCT
    // =========================

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request,
            Authentication authentication
    ) {

        ProductResponse response =
                productService.createProduct(
                        request,
                        authentication
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // =========================
    // UPDATE PRODUCT
    // =========================

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long productId,
            @Valid @RequestBody ProductRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                productService.updateProduct(
                        productId,
                        request,
                        authentication
                )
        );
    }


    // =========================
    // DEACTIVATE PRODUCT
    // =========================

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> deactivateProduct(
            @PathVariable Long productId,
            Authentication authentication
    ) {

        productService.deactivateProduct(
                productId,
                authentication
        );

        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{productId}/sync-meta")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ProductResponse> syncProductToMeta(
            @PathVariable Long productId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                productService.syncProductToMeta(
                        productId,
                        authentication
                )
        );
    }
}


