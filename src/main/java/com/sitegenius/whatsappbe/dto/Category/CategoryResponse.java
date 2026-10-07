package com.sitegenius.whatsappbe.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        Long organizationId,
        String name,
        String description,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}