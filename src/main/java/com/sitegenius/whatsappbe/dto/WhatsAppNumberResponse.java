package com.sitegenius.whatsappbe.dto;

import java.time.LocalDateTime;

public record WhatsAppNumberResponse(
        Long id,
        Long organizationId,
        String phoneNumber,
        String displayName,
        String phoneNumberId,
        String wabaId,
        String status,
        LocalDateTime createdAt
) {
}