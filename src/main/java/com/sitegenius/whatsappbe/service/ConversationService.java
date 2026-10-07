package com.sitegenius.whatsappbe.service;

import com.sitegenius.whatsappbe.entity.Contact;
import com.sitegenius.whatsappbe.entity.Conversation;
import com.sitegenius.whatsappbe.entity.Message;
import com.sitegenius.whatsappbe.entity.Organization;
import com.sitegenius.whatsappbe.entity.User;
import com.sitegenius.whatsappbe.enums.Role;
import com.sitegenius.whatsappbe.repository.ContactRepository;
import com.sitegenius.whatsappbe.repository.ConversationRepository;
import com.sitegenius.whatsappbe.repository.MessageRepository;
import com.sitegenius.whatsappbe.repository.OrganizationRepository;
import com.sitegenius.whatsappbe.repository.UserRepository;
import com.sitegenius.whatsappbe.security.JwtAuthenticationDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ContactRepository contactRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            MessageRepository messageRepository) {

        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    // =========================================================
    // GET ALL CONVERSATIONS
    // =========================================================


    public List<Conversation> getAllConversations() {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return conversationRepository.findAll();
        }

        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException("Organization is required");
        }

        return conversationRepository.findByOrganizationIdOrderByLastMessageAtDesc(
                organizationId
        );
    }
    // =========================================================
    // FILTER CONVERSATIONS
    // status + assignedAgentId + unread
    // =========================================================

    public List<Conversation> filterConversations(
            String status,
            Long assignedAgentId,
            Boolean unread) {

        Role currentRole = getCurrentRole();

        // =====================================================
        // SUPER ADMIN
        // =====================================================

        if (currentRole == Role.SUPER_ADMIN) {

            List<Conversation> conversations =
                    conversationRepository.findAll();

            return conversations.stream()

                    // STATUS FILTER
                    .filter(conversation ->
                            status == null ||
                                    status.isBlank() ||
                                    conversation.getStatus()
                                            .equalsIgnoreCase(status))

                    // ASSIGNED AGENT FILTER
                    .filter(conversation ->
                            assignedAgentId == null ||
                                    (conversation.getAssignedAgent() != null &&
                                            conversation.getAssignedAgent()
                                                    .getId()
                                                    .equals(assignedAgentId)))

                    // UNREAD FILTER
                    .filter(conversation -> {

                        if (unread == null) {
                            return true;
                        }

                        long unreadCount =
                                messageRepository
                                        .countByConversationIdAndOrganizationIdAndDirectionAndReadFalse(
                                                conversation.getId(),
                                                conversation.getOrganization().getId(),
                                                "INCOMING"
                                        );

                        return unread
                                ? unreadCount > 0
                                : unreadCount == 0;
                    })

                    .toList();
        }

        // =====================================================
        // ORGANIZATION USERS
        // =====================================================

        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        List<Conversation> conversations;

        // =====================================================
        // AGENT
        // =====================================================

        if (currentRole == Role.AGENT) {

            Long currentUserId =
                    getCurrentUserId();

            conversations =
                    conversationRepository
                            .findByOrganizationIdAndAssignedAgentIdOrderByLastMessageAtDesc(
                                    organizationId,
                                    currentUserId
                            );



        } else {

            conversations =
                    conversationRepository
                            .findByOrganizationIdOrderByLastMessageAtDesc(
                                    organizationId
                            );
        }

        // =====================================================
        // APPLY FILTERS
        // =====================================================

        return conversations.stream()

                // STATUS FILTER
                .filter(conversation ->
                        status == null ||
                                status.isBlank() ||
                                conversation.getStatus()
                                        .equalsIgnoreCase(status))

                // ASSIGNED AGENT FILTER
                .filter(conversation ->
                        assignedAgentId == null ||
                                (conversation.getAssignedAgent() != null &&
                                        conversation.getAssignedAgent()
                                                .getId()
                                                .equals(assignedAgentId)))

                // UNREAD FILTER
                .filter(conversation -> {

                    if (unread == null) {
                        return true;
                    }

                    long unreadCount =
                            messageRepository
                                    .countByConversationIdAndOrganizationIdAndDirectionAndReadFalse(
                                            conversation.getId(),
                                            conversation.getOrganization().getId(),
                                            "INCOMING"
                                    );

                    return unread
                            ? unreadCount > 0
                            : unreadCount == 0;
                })

                .toList();
    }

    // =========================================================
    // GET CONVERSATION BY ID
    // =========================================================

    public Optional<Conversation> getConversationById(Long id) {

        Role currentRole = getCurrentRole();

        if (currentRole == Role.SUPER_ADMIN) {
            return conversationRepository.findById(id);
        }

        Long organizationId = getCurrentOrganizationId();

        if (organizationId == null) {
            return Optional.empty();
        }

        return conversationRepository.findByIdAndOrganizationId(
                id,
                organizationId
        );
    }

    // =========================================================
    // CREATE CONVERSATION
    // =========================================================

    public Conversation createConversation(Long contactId) {

        Role currentRole = getCurrentRole();

        if (currentRole != Role.ADMIN &&
                currentRole != Role.SUPER_ADMIN) {

            throw new RuntimeException(
                    "Only ADMIN can create conversations"
            );
        }

        Long organizationId = getCurrentOrganizationId();

        if (currentRole == Role.SUPER_ADMIN) {

            Contact contact = contactRepository.findById(contactId)
                    .orElseThrow(() ->
                            new RuntimeException("Contact not found"));

            organizationId = contact.getOrganization().getId();

        } else {

            if (organizationId == null) {
                throw new RuntimeException(
                        "Organization is required"
                );
            }
        }

        Optional<Conversation> existingConversation =
                conversationRepository
                        .findByOrganizationIdAndContactId(
                                organizationId,
                                contactId
                        );

        if (existingConversation.isPresent()) {
            return existingConversation.get();
        }

        Contact contact;

        if (currentRole == Role.SUPER_ADMIN) {

            contact = contactRepository.findById(contactId)
                    .orElseThrow(() ->
                            new RuntimeException("Contact not found"));

        } else {

            contact = contactRepository
                    .findByIdAndOrganizationId(
                            contactId,
                            organizationId
                    )
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Contact not found"
                            ));
        }

        Organization organization =
                organizationRepository.findById(organizationId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization not found"
                                ));

        Conversation conversation =
                new Conversation();

        conversation.setOrganization(organization);
        conversation.setContact(contact);
        conversation.setStatus("OPEN");
        conversation.setLastMessageAt(
                LocalDateTime.now()
        );

        return conversationRepository.save(conversation);
    }

    // =========================================================
    // ASSIGN CONVERSATION
    // =========================================================

    public Conversation assignConversation(
            Long conversationId,
            Long agentId) {

        Role currentRole = getCurrentRole();

        if (currentRole != Role.ADMIN &&
                currentRole != Role.SUPER_ADMIN) {

            throw new RuntimeException(
                    "Only ADMIN can assign conversations"
            );
        }

        Conversation conversation;

        if (currentRole == Role.SUPER_ADMIN) {

            conversation = conversationRepository
                    .findById(conversationId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Conversation not found"
                            ));

        } else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {
                throw new RuntimeException(
                        "Organization is required"
                );
            }

            conversation = conversationRepository
                    .findByIdAndOrganizationId(
                            conversationId,
                            organizationId
                    )
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Conversation not found"
                            ));
        }

        User agent = userRepository.findById(agentId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Agent not found"
                        ));

        if (agent.getRole() != Role.AGENT) {
            throw new RuntimeException(
                    "Selected user is not an AGENT"
            );
        }

        if (agent.getOrganization() == null) {
            throw new RuntimeException(
                    "Agent organization is required"
            );
        }

        if (conversation.getOrganization().getId()
                .longValue() !=
                agent.getOrganization().getId()
                        .longValue()) {

            throw new RuntimeException(
                    "Agent belongs to another organization"
            );
        }

        conversation.setAssignedAgent(agent);

        return conversationRepository.save(conversation);
    }

    // =========================================================
    // UPDATE STATUS
    // =========================================================

    public Conversation updateStatus(
            Long conversationId,
            String status) {

        Role currentRole = getCurrentRole();

        if (currentRole != Role.ADMIN &&
                currentRole != Role.SUPER_ADMIN) {

            throw new RuntimeException(
                    "Only ADMIN can update conversation status"
            );
        }

        Conversation conversation;

        if (currentRole == Role.SUPER_ADMIN) {

            conversation = conversationRepository
                    .findById(conversationId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Conversation not found"
                            ));

        } else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {
                throw new RuntimeException(
                        "Organization is required"
                );
            }

            conversation = conversationRepository
                    .findByIdAndOrganizationId(
                            conversationId,
                            organizationId
                    )
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Conversation not found"
                            ));
        }

        if (!status.equals("OPEN") &&
                !status.equals("CLOSED")) {

            throw new RuntimeException(
                    "Status must be OPEN or CLOSED"
            );
        }

        conversation.setStatus(status);

        return conversationRepository.save(conversation);
    }

    // =========================================================
    // MY CONVERSATIONS
    // =========================================================

    public List<Conversation> getMyConversations() {

        Long organizationId =
                getCurrentOrganizationId();

        if (organizationId == null) {
            throw new RuntimeException(
                    "Organization is required"
            );
        }

        Long agentId = getCurrentUserId();

        return conversationRepository
                .findByOrganizationIdAndAssignedAgentIdOrderByLastMessageAtDesc(
                        organizationId,
                        agentId
                );
    }

    // =========================================================
    // SEARCH CONVERSATIONS
    // =========================================================

    public List<Conversation> searchConversations(String keyword) {

        Long organizationId = getCurrentOrganizationId();
        Role role = getCurrentRole();
        Long userId = getCurrentUserId();

        String search =
                keyword == null
                        ? ""
                        : keyword.trim().toLowerCase();

        List<Conversation> conversations;

        if (role == Role.SUPER_ADMIN) {

            conversations =
                    conversationRepository.findAll();

        } else if (role == Role.AGENT) {

            conversations =
                    conversationRepository
                            .findByOrganizationIdAndAssignedAgentIdOrderByLastMessageAtDesc(
                                    organizationId,
                                    userId
                            );

        } else {

            conversations =
                    conversationRepository
                            .findByOrganizationIdOrderByLastMessageAtDesc(
                                    organizationId
                            );
        }

        if (search.isEmpty()) {
            return conversations;
        }

        return conversations.stream()
                .filter(conversation -> {

                    String contactName =
                            conversation.getContact().getName();

                    String phoneNumber =
                            conversation.getContact().getPhoneNumber();

                    String lastMessageText =
                            messageRepository
                                    .findTopByConversationIdAndOrganizationIdOrderByCreatedAtDesc(
                                            conversation.getId(),
                                            conversation.getOrganization().getId()
                                    )
                                    .map(Message::getMessageText)
                                    .orElse("");

                    return containsIgnoreCase(
                            contactName,
                            search
                    )
                            || containsIgnoreCase(
                            phoneNumber,
                            search
                    )
                            || containsIgnoreCase(
                            lastMessageText,
                            search
                    );
                })
                .toList();
    }

    private boolean containsIgnoreCase(
            String value,
            String search) {

        return value != null &&
                value.toLowerCase().contains(search);
    }

    // =========================================================
    // MARK CONVERSATION AS READ
    // =========================================================

    @Transactional
    public int markConversationAsRead(
            Long conversationId) {

        Role currentRole = getCurrentRole();

        Conversation conversation;

        if (currentRole == Role.SUPER_ADMIN) {

            conversation = conversationRepository
                    .findById(conversationId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Conversation not found"
                            ));

        } else {

            Long organizationId =
                    getCurrentOrganizationId();

            if (organizationId == null) {
                throw new RuntimeException(
                        "Organization is required"
                );
            }

            conversation = conversationRepository
                    .findByIdAndOrganizationId(
                            conversationId,
                            organizationId
                    )
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Conversation not found"
                            ));
        }

        // AGENT can mark only his assigned conversation as read
        if (currentRole == Role.AGENT) {

            Long currentUserId =
                    getCurrentUserId();

            if (conversation.getAssignedAgent() == null ||
                    !conversation.getAssignedAgent()
                            .getId()
                            .equals(currentUserId)) {

                throw new RuntimeException(
                        "You are not assigned to this conversation"
                );
            }
        }

        Long organizationId =
                conversation.getOrganization().getId();

        return messageRepository.markIncomingMessagesAsRead(
                conversationId,
                organizationId
        );
    }

    // =========================================================
    // GET CURRENT USER ID
    // =========================================================

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

    // =========================================================
    // GET CURRENT ORGANIZATION ID
    // =========================================================

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

    // =========================================================
    // GET CURRENT ROLE
    // =========================================================

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

    // =========================================================
    // GET AUTHENTICATION
    // =========================================================

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