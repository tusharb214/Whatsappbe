package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository
        extends JpaRepository<Message, Long> {

    // =========================================================
    // ORGANIZATION / CONVERSATION QUERIES
    // =========================================================

    List<Message> findByOrganizationIdOrderByCreatedAtAsc(
            Long organizationId
    );

    List<Message> findByConversationIdOrderByCreatedAtAsc(
            Long conversationId
    );

    Optional<Message> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    List<Message> findByConversationIdAndOrganizationIdOrderByCreatedAtAsc(
            Long conversationId,
            Long organizationId
    );


    // =========================================================
    // WHATSAPP MESSAGE ID
    // =========================================================

    Optional<Message> findByWhatsappMessageId(
            String whatsappMessageId
    );

    /**
     * Organization-safe lookup.
     *
     * Important for multi-tenant architecture so one organization
     * cannot accidentally access another organization's message.
     */
    Optional<Message> findByWhatsappMessageIdAndOrganizationId(
            String whatsappMessageId,
            Long organizationId
    );


    // =========================================================
    // UNREAD MESSAGE COUNTS
    // =========================================================

    long countByConversationIdAndOrganizationIdAndDirectionAndReadFalse(
            Long conversationId,
            Long organizationId,
            String direction
    );

    long countByConversationIdAndDirectionAndReadFalse(
            Long conversationId,
            String direction
    );


    // =========================================================
    // MARK INCOMING MESSAGES AS READ
    // =========================================================

    @Modifying
    @Query("""
        UPDATE Message m
        SET m.read = true
        WHERE m.conversation.id = :conversationId
          AND m.organization.id = :organizationId
          AND m.direction = 'INCOMING'
          AND m.read = false
    """)
    int markIncomingMessagesAsRead(
            @Param("conversationId") Long conversationId,
            @Param("organizationId") Long organizationId
    );


    // =========================================================
    // LAST MESSAGE
    // =========================================================

    Optional<Message> findTopByConversationIdAndOrganizationIdOrderByCreatedAtDesc(
            Long conversationId,
            Long organizationId
    );


    // =========================================================
    // WHATSAPP DELIVERY STATUS
    // =========================================================

    /**
     * Updates message delivery status from WhatsApp webhook.
     *
     * Example:
     * SENT
     * DELIVERED
     * READ
     * FAILED
     */
    @Modifying
    @Query("""
        UPDATE Message m
        SET m.deliveryStatus = :deliveryStatus
        WHERE m.whatsappMessageId = :whatsappMessageId
          AND m.organization.id = :organizationId
    """)
    int updateDeliveryStatus(
            @Param("whatsappMessageId") String whatsappMessageId,
            @Param("organizationId") Long organizationId,
            @Param("deliveryStatus") String deliveryStatus
    );
}