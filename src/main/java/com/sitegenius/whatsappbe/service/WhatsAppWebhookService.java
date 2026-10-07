package com.sitegenius.whatsappbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sitegenius.whatsappbe.entity.Contact;
import com.sitegenius.whatsappbe.entity.Conversation;
import com.sitegenius.whatsappbe.entity.Message;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.WhatsAppNumber;
import com.sitegenius.whatsappbe.repository.ContactRepository;
import com.sitegenius.whatsappbe.repository.ConversationRepository;
import com.sitegenius.whatsappbe.repository.MessageRepository;
import com.sitegenius.whatsappbe.repository.WhatsAppNumberRepository;
import com.sitegenius.whatsappbe.service.FlowExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.time.LocalDateTime;

@Service
public class WhatsAppWebhookService {

    private final ObjectMapper objectMapper;
    private final WhatsAppNumberRepository whatsAppNumberRepository;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final FlowExecutionService flowExecutionService;
    private final MessageRepository messageRepository;

    public WhatsAppWebhookService(
            ObjectMapper objectMapper,
            WhatsAppNumberRepository whatsAppNumberRepository,
            ContactRepository contactRepository,
            ConversationRepository conversationRepository,
            FlowExecutionService flowExecutionService,
            MessageRepository messageRepository) {

        this.objectMapper = objectMapper;
        this.whatsAppNumberRepository = whatsAppNumberRepository;
        this.contactRepository = contactRepository;
        this.conversationRepository = conversationRepository;
        this.flowExecutionService = flowExecutionService;
        this.messageRepository = messageRepository;
    }

    // =========================================================
    // PROCESS WHATSAPP WEBHOOK
    // =========================================================

    @Transactional
    public void processWebhook(String payload) {

        try {

            JsonNode root = objectMapper.readTree(payload);

            JsonNode entries = root.path("entry");

            if (!entries.isArray()) {
                return;
            }

            for (JsonNode entry : entries) {

                JsonNode changes = entry.path("changes");

                if (!changes.isArray()) {
                    continue;
                }

                for (JsonNode change : changes) {

                    JsonNode value = change.path("value");

                    JsonNode metadata = value.path("metadata");

                    String phoneNumberId =
                            metadata
                                    .path("phone_number_id")
                                    .asText(null);

                    if (phoneNumberId == null) {
                        continue;
                    }

                    WhatsAppNumber whatsAppNumber =
                            whatsAppNumberRepository
                                    .findByPhoneNumberId(phoneNumberId)
                                    .orElse(null);

                    if (whatsAppNumber == null) {

                        System.out.println(
                                "WhatsApp Number not found for phoneNumberId: "
                                        + phoneNumberId
                        );

                        continue;
                    }

                    Organization organization =
                            whatsAppNumber.getOrganization();
                    JsonNode statuses =
                            value.path("statuses");

                    if (statuses.isArray()) {

                        for (JsonNode status : statuses) {

                            processStatus(
                                    status,
                                    organization
                            );
                        }
                    }

                    JsonNode messages =
                            value.path("messages");

                    if (!messages.isArray()) {
                        continue;
                    }

                    for (JsonNode whatsappMessage : messages) {

                        processMessage(
                                whatsappMessage,
                                value,
                                organization
                        );
                    }
                }
            }

        } catch (Exception exception) {

            System.out.println(
                    "WhatsApp webhook processing failed: "
                            + exception.getMessage()
            );

            throw new RuntimeException(
                    "Failed to process WhatsApp webhook",
                    exception
            );
        }
    }


    // =========================================================
    // PROCESS SINGLE MESSAGE
    // =========================================================

    private void processMessage(
            JsonNode whatsappMessage,
            JsonNode value,
            Organization organization) {

        String from =
                whatsappMessage
                        .path("from")
                        .asText(null);

        String whatsappMessageId =
                whatsappMessage
                        .path("id")
                        .asText(null);

        String messageType =
                whatsappMessage
                        .path("type")
                        .asText("TEXT");

        if (from == null || whatsappMessageId == null) {
            return;
        }

        // =====================================================
        // PREVENT DUPLICATE MESSAGE
        // =====================================================

        if (messageRepository
                .findByWhatsappMessageId(whatsappMessageId)
                .isPresent()) {

            System.out.println(
                    "Duplicate WhatsApp message ignored: "
                            + whatsappMessageId
            );

            return;
        }

        // =====================================================
        // NORMALIZE CUSTOMER PHONE NUMBER
        // =====================================================

        String normalizedPhoneNumber =
                normalizePhoneNumber(from);

        if (normalizedPhoneNumber == null
                || normalizedPhoneNumber.isBlank()) {

            System.out.println(
                    "Invalid WhatsApp phone number: "
                            + from
            );

            return;
        }

        // =====================================================
        // EXTRACT MESSAGE TEXT
        // =====================================================

        String messageText =
                extractMessageText(
                        whatsappMessage,
                        messageType
                );

        // =====================================================
        // GET WHATSAPP PROFILE NAME
        // =====================================================

        JsonNode contacts =
                value.path("contacts");

        String profileName = null;

        if (contacts.isArray()
                && !contacts.isEmpty()) {

            JsonNode firstContact =
                    contacts.get(0);

            profileName =
                    firstContact
                            .path("profile")
                            .path("name")
                            .asText(null);
        }

        final String finalProfileName =
                profileName;

        // =====================================================
        // FIND OR CREATE CONTACT
        // =====================================================

        Contact contact =
                contactRepository
                        .findByOrganizationIdAndPhoneNumber(
                                organization.getId(),
                                normalizedPhoneNumber
                        )
                        .orElseGet(() ->
                                createContact(
                                        organization,
                                        normalizedPhoneNumber,
                                        finalProfileName
                                )
                        );

        // =====================================================
        // UPDATE WHATSAPP PROFILE NAME
        // =====================================================

        if (profileName != null
                && !profileName.isBlank()) {

            contact.setProfileName(profileName);

            if (contact.getName() == null
                    || contact.getName().isBlank()
                    || contact.getName()
                    .equals(contact.getPhoneNumber())) {

                contact.setName(profileName);
            }

            contactRepository.save(contact);
        }

        // =====================================================
        // FIND OR CREATE CONVERSATION
        // =====================================================

        Conversation conversation =
                conversationRepository
                        .findByOrganizationIdAndContactId(
                                organization.getId(),
                                contact.getId()
                        )
                        .orElseGet(() ->
                                createConversation(
                                        organization,
                                        contact
                                )
                        );

        // =====================================================
        // CREATE MESSAGE
        // =====================================================

        Message message = new Message();

        message.setOrganization(organization);

        message.setConversation(conversation);

        message.setDirection("INCOMING");

        message.setMessageType(
                messageType.toUpperCase()
        );

        message.setMessageText(messageText);

        message.setWhatsappMessageId(
                whatsappMessageId
        );

        messageRepository.save(message);

        // =====================================================
        // UPDATE CONVERSATION LAST MESSAGE TIME
        // =====================================================

        conversation.setLastMessageAt(
                LocalDateTime.now()
        );

        conversationRepository.save(conversation);


        // =========================================================
// FLOW AUTOMATION
// =========================================================

        try {

            flowExecutionService.processIncomingMessage(
                    organization.getId(),
                    from,
                    messageText,
                    messageType
            );

        } catch (Exception exception) {

            System.err.println(
                    "Flow execution failed: "
                            + exception.getMessage()
            );
        }

        // =====================================================
        // LOG
        // =====================================================

        System.out.println(
                "WhatsApp message saved successfully. "
                        + "Contact ID: "
                        + contact.getId()
                        + ", Conversation ID: "
                        + conversation.getId()
                        + ", Message ID: "
                        + message.getId()
        );
    }

    // =========================================================
// PROCESS WHATSAPP DELIVERY STATUS
// =========================================================

    private void processStatus(
            JsonNode status,
            Organization organization) {

        String whatsappMessageId =
                status
                        .path("id")
                        .asText(null);

        String deliveryStatus =
                status
                        .path("status")
                        .asText(null);

        if (whatsappMessageId == null ||
                whatsappMessageId.isBlank()) {

            return;
        }

        if (deliveryStatus == null ||
                deliveryStatus.isBlank()) {

            return;
        }

        String normalizedStatus =
                deliveryStatus.toUpperCase();

        switch (normalizedStatus) {

            case "SENT":
                break;

            case "DELIVERED":
                break;

            case "READ":
                break;

            case "FAILED":
                break;

            default:
                return;
        }

        Optional<Message> messageOptional =
                messageRepository
                        .findByWhatsappMessageIdAndOrganizationId(
                                whatsappMessageId,
                                organization.getId()
                        );

        if (messageOptional.isEmpty()) {

            System.out.println(
                    "Message not found for WhatsApp status: "
                            + whatsappMessageId
            );

            return;
        }

        Message message =
                messageOptional.get();

        message.setDeliveryStatus(
                normalizedStatus
        );

        messageRepository.save(message);

        System.out.println(
                "WhatsApp delivery status updated: "
                        + whatsappMessageId
                        + " -> "
                        + normalizedStatus
        );
    }

    // =========================================================
    // CREATE CONTACT
    // =========================================================

    private Contact createContact(
            Organization organization,
            String phoneNumber,
            String profileName) {

        Contact contact = new Contact();

        contact.setOrganization(organization);

        contact.setPhoneNumber(
                phoneNumber
        );

        if (profileName != null
                && !profileName.isBlank()) {

            contact.setName(profileName);

            contact.setProfileName(profileName);

        } else {

            contact.setName(phoneNumber);
        }

        contact.setStatus("ACTIVE");

        return contactRepository.save(contact);
    }

    // =========================================================
    // CREATE CONVERSATION
    // =========================================================

    private Conversation createConversation(
            Organization organization,
            Contact contact) {

        Conversation conversation =
                new Conversation();

        conversation.setOrganization(
                organization
        );

        conversation.setContact(
                contact
        );

        conversation.setStatus("OPEN");

        conversation.setLastMessageAt(
                LocalDateTime.now()
        );

        return conversationRepository.save(
                conversation
        );
    }

    // =========================================================
    // EXTRACT MESSAGE TEXT
    // =========================================================

    private String extractMessageText(
            JsonNode message,
            String messageType) {

        if ("text".equalsIgnoreCase(messageType)) {

            String text =
                    message
                            .path("text")
                            .path("body")
                            .asText(null);

            if (text != null) {
                return text;
            }
        }

        return "["
                + messageType.toUpperCase()
                + " MESSAGE]";
    }

    // =========================================================
    // NORMALIZE PHONE NUMBER
    // =========================================================

    private String normalizePhoneNumber(
            String phoneNumber) {

        if (phoneNumber == null
                || phoneNumber.isBlank()) {

            return null;
        }

        /*
         * Removes:
         *
         * +
         * spaces
         * -
         * brackets
         * other special characters
         *
         * Examples:
         *
         * +919876543210
         * 919876543210
         * +91 9876543210
         * +91-9876543210
         *
         * Result:
         *
         * 919876543210
         */

        return phoneNumber.replaceAll(
                "[^0-9]",
                ""
        );
    }
}