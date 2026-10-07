package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.conversation.ConversationResponse;
import com.sitegenius.whatsappbe.entity.Conversation;
import com.sitegenius.whatsappbe.entity.Message;
import com.sitegenius.whatsappbe.repository.MessageRepository;
import com.sitegenius.whatsappbe.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageRepository messageRepository;

    public ConversationController(
            ConversationService conversationService,
            MessageRepository messageRepository) {

        this.conversationService = conversationService;
        this.messageRepository = messageRepository;
    }

    // =========================================================
    // GET ALL CONVERSATIONS + FILTERS
    // status + assignedAgentId + unread
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<ConversationResponse>> getAllConversations(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(required = false) Boolean unread) {

        List<Conversation> conversations;

        // No filters
        if ((status == null || status.isBlank())
                && assignedAgentId == null
                && unread == null) {

            conversations =
                    conversationService.getAllConversations();

        } else {

            // Apply filters
            conversations =
                    conversationService.filterConversations(
                            status,
                            assignedAgentId,
                            unread
                    );
        }

        return ResponseEntity.ok(
                conversations.stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    // =========================================================
    // GET CONVERSATION BY ID
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<ConversationResponse> getConversationById(
            @PathVariable Long id) {

        return conversationService
                .getConversationById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================================================
    // CREATE CONVERSATION
    // =========================================================

    @PostMapping("/contact/{contactId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ConversationResponse> createConversation(
            @PathVariable Long contactId) {

        Conversation conversation =
                conversationService.createConversation(contactId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(conversation));
    }

    // =========================================================
    // ASSIGN CONVERSATION
    // =========================================================

    @PutMapping("/{conversationId}/assign/{agentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ConversationResponse> assignConversation(
            @PathVariable Long conversationId,
            @PathVariable Long agentId) {

        return ResponseEntity.ok(
                toResponse(
                        conversationService.assignConversation(
                                conversationId,
                                agentId
                        )
                )
        );
    }

    // =========================================================
    // UPDATE STATUS
    // =========================================================

    @PutMapping("/{conversationId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ConversationResponse> updateStatus(
            @PathVariable Long conversationId,
            @RequestParam String status) {

        return ResponseEntity.ok(
                toResponse(
                        conversationService.updateStatus(
                                conversationId,
                                status
                        )
                )
        );
    }

    // =========================================================
    // MY CONVERSATIONS
    // =========================================================

    @GetMapping("/my")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<List<ConversationResponse>> getMyConversations() {

        return ResponseEntity.ok(
                conversationService.getMyConversations()
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    // =========================================================
    // MARK CONVERSATION AS READ
    // =========================================================

    @PutMapping("/{conversationId}/read")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<String> markConversationAsRead(
            @PathVariable Long conversationId) {

        int updatedCount =
                conversationService.markConversationAsRead(
                        conversationId
                );

        return ResponseEntity.ok(
                updatedCount + " message(s) marked as read"
        );
    }

    // =========================================================
    // CONVERT CONVERSATION TO RESPONSE
    // =========================================================

    private ConversationResponse toResponse(
            Conversation conversation) {

        Long organizationId = null;
        Long contactId = null;
        String contactName = null;
        String phoneNumber = null;
        Long assignedAgentId = null;
        String assignedAgentName = null;

        // =====================================================
        // ORGANIZATION
        // =====================================================

        if (conversation.getOrganization() != null) {

            organizationId =
                    conversation.getOrganization().getId();
        }

        // =====================================================
        // CONTACT
        // =====================================================

        if (conversation.getContact() != null) {

            contactId =
                    conversation.getContact().getId();

            contactName =
                    conversation.getContact().getName();

            phoneNumber =
                    conversation.getContact().getPhoneNumber();
        }

        // =====================================================
        // ASSIGNED AGENT
        // =====================================================

        if (conversation.getAssignedAgent() != null) {

            assignedAgentId =
                    conversation.getAssignedAgent().getId();

            assignedAgentName =
                    conversation.getAssignedAgent().getName();
        }

        // =====================================================
        // UNREAD MESSAGE COUNT
        // =====================================================

        int unreadCount = 0;

        if (organizationId != null) {

            unreadCount = (int)
                    messageRepository
                            .countByConversationIdAndOrganizationIdAndDirectionAndReadFalse(
                                    conversation.getId(),
                                    organizationId,
                                    "INCOMING"
                            );
        }

        // =====================================================
        // LAST MESSAGE
        // =====================================================

        String lastMessageText = null;
        String lastMessageDirection = null;

        if (organizationId != null) {

            Optional<Message> lastMessage =
                    messageRepository
                            .findTopByConversationIdAndOrganizationIdOrderByCreatedAtDesc(
                                    conversation.getId(),
                                    organizationId
                            );

            if (lastMessage.isPresent()) {

                Message message =
                        lastMessage.get();

                lastMessageText =
                        message.getMessageText();

                lastMessageDirection =
                        message.getDirection();
            }
        }

        // =====================================================
        // FINAL RESPONSE
        // =====================================================

        return new ConversationResponse(
                conversation.getId(),
                organizationId,
                contactId,
                contactName,
                phoneNumber,
                assignedAgentId,
                assignedAgentName,
                conversation.getStatus(),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt(),
                unreadCount,
                lastMessageText,
                lastMessageDirection
        );
    }

    // =========================================================
    // SEARCH CONVERSATIONS
    // =========================================================

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<ConversationResponse>> searchConversations(
            @RequestParam String keyword) {

        return ResponseEntity.ok(
                conversationService.searchConversations(keyword)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }
}