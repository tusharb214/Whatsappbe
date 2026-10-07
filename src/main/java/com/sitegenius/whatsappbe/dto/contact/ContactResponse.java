package com.sitegenius.whatsappbe.dto.contact;

import java.time.LocalDateTime;

public record ContactResponse(
        Long id,
        Long organizationId,
        String name,
        String phoneNumber,
        String email,
        String profileName,
        String status,
        LocalDateTime createdAt
) {
}