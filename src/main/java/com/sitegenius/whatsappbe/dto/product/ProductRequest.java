package com.sitegenius.whatsappbe.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank(message = "Product name is required")
        String name,

        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(
                value = "0.0",
                inclusive = true,
                message = "Price cannot be negative"
        )
        BigDecimal price,

        String currency,

        String imageUrl,

        @NotBlank(message = "SKU is required")
        String sku,

        String status,

        Long categoryId

) {
}