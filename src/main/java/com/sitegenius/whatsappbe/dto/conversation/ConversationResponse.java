package com.sitegenius.whatsappbe.dto.conversation;

import java.time.LocalDateTime;

public record ConversationResponse(

        Long id,

        Long organizationId,

        Long contactId,

        String contactName,

        String phoneNumber,

        Long assignedAgentId,

        String assignedAgentName,

        String status,

        LocalDateTime lastMessageAt,

        LocalDateTime createdAt,

        Integer unreadCount,

        String lastMessageText,

        String lastMessageDirection

) {
}