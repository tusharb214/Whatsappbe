package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Conversation;
import com.sitegenius.whatsappbe.entity.Message;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.Role;
import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.repository.ConversationRepository;
import com.sitegenius.whatsappbe.repository.MessageRepository;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import com.sitegenius.whatsappbe.repository.UserRepository;
import com.sitegenius.whatsappbe.security.JwtAuthenticationDetails;
import com.sitegenius.whatsappbe.service.FlowExecutionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final OrganizationRepository organizationRepository;
    private final FlowExecutionService flowExecutionService;
    private final UserRepository userRepository;
    private final WhatsAppCloudApiService whatsAppCloudApiService;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            FlowExecutionService flowExecutionService,
            WhatsAppCloudApiService whatsAppCloudApiService) {

        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.flowExecutionService = flowExecutionService;
        this.whatsAppCloudApiService = whatsAppCloudApiService;
    }

    public List<Message> getMessagesByConversation(
            Long conversationId) {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return messageRepository
                    .findByConversationIdOrderByCreatedAtAsc(
                            conversationId
                    );
        }

        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        conversationRepository
                .findByIdAndOrganizationId(
                        conversationId,
                        organizationId
                )
                .orElseThrow(() ->
                        new RuntimeException(
                                "Conversation not found"
                        ));

        return messageRepository
                .findByConversationIdAndOrganizationIdOrderByCreatedAtAsc(
                        conversationId,
                        organizationId
                );
    }

    public Optional<Message> getMessageById(Long id) {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return messageRepository.findById(id);
        }

        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {
            return Optional.empty();
        }

        return messageRepository
                .findByIdAndOrganizationId(
                        id,
                        organizationId
                );
    }

    public Message createIncomingMessage(
            Long conversationId,
            String messageText,
            String messageType,
            String whatsappMessageId) {

        Conversation conversation =
                conversationRepository.findById(
                        conversationId
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Conversation not found"
                        ));

        Organization organization =
                conversation.getOrganization();

        Message message =
                new Message();

        message.setOrganization(organization);
        message.setConversation(conversation);
        message.setDirection("INCOMING");

        message.setMessageType(
                messageType == null ||
                        messageType.isBlank()
                        ? "TEXT"
                        : messageType
        );

        message.setMessageText(messageText);

        message.setWhatsappMessageId(
                whatsappMessageId
        );

        Message savedMessage =
                messageRepository.save(message);

        conversation.setLastMessageAt(
                LocalDateTime.now()
        );


        try {

            String customerPhoneNumber =
                    conversation
                            .getContact()
                            .getPhoneNumber();

            flowExecutionService.processIncomingMessage(
                    organization.getId(),
                    customerPhoneNumber,
                    messageText,
                    messageType == null || messageType.isBlank()
                            ? "TEXT"
                            : messageType
            );

        } catch (Exception exception) {

            System.err.println(
                    "Flow execution failed: "
                            + exception.getMessage()
            );
        }

        conversationRepository.save(conversation);

        return savedMessage;
    }

    public Message sendAgentMessage(
            Long conversationId,
            String messageText,
            String messageType) {

        Role currentRole = getCurrentRole();

        /*
         * Only ADMIN and AGENT can send messages.
         */
        if (currentRole != Role.ADMIN &&
                currentRole != Role.AGENT) {

            throw new RuntimeException(
                    "Only ADMIN or AGENT can send messages"
            );
        }

        /*
         * Get current user's organization.
         */
        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        /*
         * Get conversation only from
         * current user's organization.
         */
        Conversation conversation =
                conversationRepository
                        .findByIdAndOrganizationId(
                                conversationId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Conversation not found"
                                ));

        /*
         * Closed conversations cannot receive
         * new messages.
         */
        if ("CLOSED".equals(conversation.getStatus())) {

            throw new RuntimeException(
                    "Conversation is closed"
            );
        }

        /*
         * Validate message text.
         */
        if (messageText == null ||
                messageText.isBlank()) {

            throw new RuntimeException(
                    "Message text is required"
            );
        }

        /*
         * Get currently logged-in user.
         */
        Long currentUserId =
                getCurrentUserId();

        User senderAgent =
                userRepository.findById(
                        currentUserId
                ).orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        ));

        /*
         * AGENT can only reply to conversations
         * assigned to that agent.
         */
        if (currentRole == Role.AGENT) {

            if (conversation.getAssignedAgent() == null ||
                    !conversation.getAssignedAgent()
                            .getId()
                            .equals(currentUserId)) {

                throw new RuntimeException(
                        "Conversation is not assigned to you"
                );
            }
        }







        /*
         * Get customer's WhatsApp phone number
         * from Conversation -> Contact.
         */
        if (conversation.getContact() == null) {

            throw new RuntimeException(
                    "Conversation contact not found"
            );
        }

        String customerPhoneNumber =
                conversation
                        .getContact()
                        .getPhoneNumber();

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number not found"
            );
        }

        /*
         * -------------------------------------------------
         * REAL WHATSAPP SEND
         * -------------------------------------------------
         *
         * Organization ID is passed to the
         * WhatsAppCloudApiService.
         *
         * The service will find the ACTIVE
         * WhatsApp number belonging to this organization
         * and use its:
         *
         * - phoneNumberId
         * - accessToken
         *
         * to send the message through Meta.
         */
        String whatsappMessageId =
                whatsAppCloudApiService.sendTextMessage(
                        organizationId,
                        customerPhoneNumber,
                        messageText
                );

        /*
         * Meta send succeeded.
         *
         * Now save the outgoing message in DB.
         */
        Message message =
                new Message();

        message.setOrganization(
                conversation.getOrganization()
        );

        message.setConversation(
                conversation
        );

        message.setDirection(
                "OUTGOING"
        );

        message.setMessageType(
                "TEXT"
        );

        message.setMessageText(
                messageText
        );

        message.setSenderAgent(
                senderAgent
        );

        /*
         * Save Meta's WhatsApp message ID.
         */
        message.setWhatsappMessageId(
                whatsappMessageId
        );

        Message savedMessage =
                messageRepository.save(message);

        /*
         * Update conversation last message time.
         */
        conversation.setLastMessageAt(
                LocalDateTime.now()
        );

        conversationRepository.save(
                conversation
        );

        return savedMessage;
    }

    public String uploadWhatsAppMedia(
            byte[] fileBytes,
            String fileName,
            String mimeType) {

        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        return whatsAppCloudApiService.uploadMedia(
                organizationId,
                fileBytes,
                fileName,
                mimeType
        );
    }

    public Message  sendAgentMediaMessage(
            Long conversationId,
            String mediaId,
            String messageType,
            String fileName,
            String caption
    ){

        Role currentRole = getCurrentRole();

        if (currentRole != Role.ADMIN &&
                currentRole != Role.AGENT) {

            throw new RuntimeException(
                    "Only ADMIN or AGENT can send messages"
            );
        }

        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        Conversation conversation =
                conversationRepository
                        .findByIdAndOrganizationId(
                                conversationId,
                                organizationId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Conversation not found"
                                ));

        if ("CLOSED".equals(conversation.getStatus())) {
            throw new RuntimeException(
                    "Conversation is closed"
            );
        }

        if (mediaId == null || mediaId.isBlank()) {
            throw new RuntimeException(
                    "Media ID is required"
            );
        }

        if (messageType == null || messageType.isBlank()) {
            throw new RuntimeException(
                    "Message type is required"
            );
        }

        Long currentUserId =
                getCurrentUserId();

        User senderAgent =
                userRepository.findById(
                        currentUserId
                ).orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        ));

        if (currentRole == Role.AGENT) {

            if (conversation.getAssignedAgent() == null ||
                    !conversation.getAssignedAgent()
                            .getId()
                            .equals(currentUserId)) {

                throw new RuntimeException(
                        "Conversation is not assigned to you"
                );
            }
        }

        if (conversation.getContact() == null) {
            throw new RuntimeException(
                    "Conversation contact not found"
            );
        }

        String customerPhoneNumber =
                conversation
                        .getContact()
                        .getPhoneNumber();

        if (customerPhoneNumber == null ||
                customerPhoneNumber.isBlank()) {

            throw new RuntimeException(
                    "Customer phone number not found"
            );
        }

        String whatsappMessageId;

        switch (messageType.toUpperCase()) {

            case "IMAGE":

                whatsappMessageId =
                        whatsAppCloudApiService.sendImageMessage(
                                organizationId,
                                customerPhoneNumber,
                                mediaId,
                                caption
                        );
                break;

            case "DOCUMENT":

                whatsappMessageId =
                        whatsAppCloudApiService.sendDocumentMessage(
                                organizationId,
                                customerPhoneNumber,
                                mediaId,
                                fileName,
                                caption
                        );
                break;

            case "VIDEO":

                whatsappMessageId =
                        whatsAppCloudApiService.sendVideoMessage(
                                organizationId,
                                customerPhoneNumber,
                                mediaId,
                                caption
                        );
                break;

            case "AUDIO":

                whatsappMessageId =
                        whatsAppCloudApiService.sendAudioMessage(
                                organizationId,
                                customerPhoneNumber,
                                mediaId
                        );
                break;

            default:

                throw new RuntimeException(
                        "Unsupported media type: " + messageType
                );
        }

        Message message =
                new Message();

        message.setOrganization(
                conversation.getOrganization()
        );

        message.setConversation(
                conversation
        );

        message.setDirection(
                "OUTGOING"
        );

        message.setMessageType(
                messageType.toUpperCase()
        );

        message.setMessageText(
                caption
        );

        message.setSenderAgent(
                senderAgent
        );

        message.setWhatsappMessageId(
                whatsappMessageId
        );

        message.setMediaId(
                mediaId
        );

        message.setMediaFileName(
                fileName
        );

        message.setMediaCaption(
                caption
        );

        Message savedMessage =
                messageRepository.save(message);

        conversation.setLastMessageAt(
                LocalDateTime.now()
        );

        conversationRepository.save(
                conversation
        );

        return savedMessage;
    }

    private Long getCurrentUserId() {

        Authentication authentication =
                getAuthentication();

        Object details =
                authentication.getDetails();

        if (!(details instanceof JwtAuthenticationDetails)) {

            throw new RuntimeException(
                    "JWT authentication details not found"
            );
        }

        JwtAuthenticationDetails jwtDetails =
                (JwtAuthenticationDetails) details;

        return jwtDetails.getUserId();
    }

    private Long getCurrentOrganizationId() {

        Authentication authentication =
                getAuthentication();

        Object details =
                authentication.getDetails();

        if (!(details instanceof JwtAuthenticationDetails)) {

            throw new RuntimeException(
                    "JWT authentication details not found"
            );
        }

        JwtAuthenticationDetails jwtDetails =
                (JwtAuthenticationDetails) details;

        return jwtDetails.getOrganizationId();
    }

    private Role getCurrentRole() {

        Authentication authentication =
                getAuthentication();

        String authority =
                authentication.getAuthorities()
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User role not found"
                                ))
                        .getAuthority();

        return Role.valueOf(
                authority.replace("ROLE_", "")
        );
    }

    private Authentication getAuthentication() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        return authentication;
    }
}