package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.catalogue.CatalogueRequest;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.service.CatalogueService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalogue")
public class CatalogueController {

    private final CatalogueService catalogueService;

    public CatalogueController(
            CatalogueService catalogueService
    ) {
        this.catalogueService = catalogueService;
    }

    // =========================================================
    // SEND CATALOGUE
    // =========================================================

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<String> sendCatalogue(
            @Valid @RequestBody CatalogueRequest request,
            Authentication authentication
    ) {

        List<Product> products =
                catalogueService.validateCatalogue(
                        request.conversationId(),
                        request.productIds(),
                        authentication
                );

        return ResponseEntity.ok(
                "Catalogue validated successfully. "
                        + products.size()
                        + " product(s) ready to send."
        );
    }

    // =========================================================
    // SEND SINGLE PRODUCT
    // =========================================================

    @PostMapping("/send-product")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<String> sendProduct(
            @RequestParam Long conversationId,
            @RequestParam Long productId,
            Authentication authentication
    ) {

        String messageId =
                catalogueService.sendProduct(
                        conversationId,
                        productId,
                        authentication
                );

        return ResponseEntity.ok(
                "Product sent successfully. "
                        + "WhatsApp Message ID: "
                        + messageId
        );
    }
}