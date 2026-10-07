package com.sitegenius.whatsappbe.dto.catalogue;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CatalogueRequest(

        @NotNull(message = "Conversation ID is required")
        Long conversationId,

        @NotEmpty(message = "At least one product is required")
        List<Long> productIds
) {
}