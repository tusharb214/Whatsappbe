package com.sitegenius.whatsappbe.controller;

import com.sitegenius.whatsappbe.dto.message.MessageResponse;
import com.sitegenius.whatsappbe.entity.Message;
import com.sitegenius.whatsappbe.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/conversation/{conversationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<List<MessageResponse>> getMessagesByConversation(
            @PathVariable Long conversationId) {

        return ResponseEntity.ok(
                messageService
                        .getMessagesByConversation(conversationId)
                        .stream()
                        .map(this::toResponse)
                        .toList()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'AGENT')")
    public ResponseEntity<MessageResponse> getMessageById(
            @PathVariable Long id) {

        return messageService
                .getMessageById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/conversation/{conversationId}/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<MessageResponse> sendAgentMessage(
            @PathVariable Long conversationId,
            @RequestParam String messageText,
            @RequestParam(required = false) String messageType) {

        Message message =
                messageService.sendAgentMessage(
                        conversationId,
                        messageText,
                        messageType
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(message));
    }

    @PostMapping("/conversation/{conversationId}/incoming")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> createIncomingMessage(
            @PathVariable Long conversationId,
            @RequestParam String messageText,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String whatsappMessageId) {

        Message message =
                messageService.createIncomingMessage(
                        conversationId,
                        messageText,
                        messageType,
                        whatsappMessageId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(message));
    }

    @PostMapping("/conversation/{conversationId}/send-media")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<MessageResponse> sendMediaMessage(
            @PathVariable Long conversationId,
            @RequestParam String mediaId,
            @RequestParam String messageType,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String caption) {

        Message message =
                messageService.sendAgentMediaMessage(
                        conversationId,
                        mediaId,
                        messageType,
                        fileName,
                        caption
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toResponse(message));
    }


    private MessageResponse toResponse(Message message) {

        Long conversationId = null;
        Long organizationId = null;
        Long senderAgentId = null;
        String senderAgentName = null;

        if (message.getConversation() != null) {
            conversationId =
                    message.getConversation().getId();
        }

        if (message.getOrganization() != null) {
            organizationId =
                    message.getOrganization().getId();
        }

        if (message.getSenderAgent() != null) {
            senderAgentId =
                    message.getSenderAgent().getId();

            senderAgentName =
                    message.getSenderAgent().getName();
        }

        return new MessageResponse(
                message.getId(),
                conversationId,
                organizationId,
                message.getDirection(),
                message.getMessageType(),
                message.getMessageText(),

                senderAgentId,
                senderAgentName,

                message.getWhatsappMessageId(),

                message.getMediaId(),
                message.getMediaUrl(),
                message.getMediaMimeType(),
                message.getMediaFileName(),
                message.getMediaSize(),
                message.getMediaCaption(),

                message.getDeliveryStatus(),

                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }


    @PostMapping("/media/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<?> uploadMedia(
            @RequestParam("file") MultipartFile file) {

        try {

            if (file == null || file.isEmpty()) {
                return ResponseEntity
                        .badRequest()
                        .body("File is required");
            }

            String mediaId =
                    messageService.uploadWhatsAppMedia(
                            file.getBytes(),
                            file.getOriginalFilename(),
                            file.getContentType()
                    );

            return ResponseEntity.ok(
                    java.util.Map.of(
                            "success", true,
                            "mediaId", mediaId,
                            "fileName", file.getOriginalFilename(),
                            "mimeType", file.getContentType(),
                            "size", file.getSize()
                    )
            );

        } catch (IOException e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to read uploaded file");

        } catch (RuntimeException e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}