package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository
        extends JpaRepository<Conversation, Long> {

    // =========================================================
    // ORGANIZATION CONVERSATIONS
    // Latest message first
    // =========================================================

    List<Conversation> findByOrganizationIdOrderByLastMessageAtDesc(
            Long organizationId
    );

    // =========================================================
    // GET CONVERSATION BY ID + ORGANIZATION
    // =========================================================

    Optional<Conversation> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    // =========================================================
    // GET CONVERSATION BY CONTACT
    // =========================================================

    Optional<Conversation> findByOrganizationIdAndContactId(
            Long organizationId,
            Long contactId
    );

    // =========================================================
    // FILTER BY STATUS
    // =========================================================

    List<Conversation> findByOrganizationIdAndStatus(
            Long organizationId,
            String status
    );

    // =========================================================
    // AGENT CONVERSATIONS
    // =========================================================

    List<Conversation> findByAssignedAgentId(
            Long agentId
    );

    // =========================================================
    // AGENT + ORGANIZATION
    // Latest message first
    // =========================================================

    List<Conversation> findByOrganizationIdAndAssignedAgentIdOrderByLastMessageAtDesc(
            Long organizationId,
            Long agentId
    );
}