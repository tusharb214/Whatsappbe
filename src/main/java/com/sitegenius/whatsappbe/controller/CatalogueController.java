
package com.sitegenius.whatsappbe.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.sitegenius.whatsappbe.dto.catalogue.CatalogueRequest;
import com.sitegenius.whatsappbe.entity.Product;
import com.sitegenius.whatsappbe.service.CatalogueService;
import com.sitegenius.whatsappbe.service.WhatsAppCloudApiService;

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
    private final WhatsAppCloudApiService whatsAppCloudApiService;

    public CatalogueController(
            CatalogueService catalogueService,
            WhatsAppCloudApiService whatsAppCloudApiService
    ) {
        this.catalogueService = catalogueService;
        this.whatsAppCloudApiService = whatsAppCloudApiService;
    }

    // SEND CATALOGUE
    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<String> sendCatalogue(
            @Valid @RequestBody CatalogueRequest request,
            Authentication authentication
    ) {
        List<Product> products = catalogueService.validateCatalogue(
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

    // SEND SINGLE PRODUCT
    @PostMapping("/send-product")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<String> sendProduct(
            @RequestParam Long conversationId,
            @RequestParam Long productId,
            Authentication authentication
    ) {
        String messageId = catalogueService.sendProduct(
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

    // GET CONFIGURED META CATALOG
    @GetMapping("/meta")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<JsonNode> getMetaCatalog() {
        return ResponseEntity.ok(
                whatsAppCloudApiService.getConfiguredMetaCatalog()
        );
    }

    // GET PRODUCTS FROM CONFIGURED META CATALOG
    @GetMapping("/meta/products")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<JsonNode> getMetaCatalogProducts() {
        return ResponseEntity.ok(
                whatsAppCloudApiService.getConfiguredMetaCatalogProducts()
        );
    }
}
