package com.sitegenius.whatsappbe.dto.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponse(

        Long id,

        Long organizationId,

        String name,

        String description,

        BigDecimal price,

        String currency,

        String imageUrl,

        String sku,

        String status,

        Long categoryId,

        String categoryName,

        // =========================================================
        // META CATALOG
        // =========================================================

        String metaProductId,

        String metaRetailerId,

        LocalDateTime createdAt,

        LocalDateTime updatedAt

) {
}