package com.sitegenius.whatsappbe.dto.message;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long organizationId,
        String direction,
        String messageType,
        String messageText,

        Long senderAgentId,
        String senderAgentName,

        String whatsappMessageId,

        String mediaId,
        String mediaUrl,
        String mediaMimeType,
        String mediaFileName,
        Long mediaSize,
        String mediaCaption,

        String deliveryStatus,

        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}