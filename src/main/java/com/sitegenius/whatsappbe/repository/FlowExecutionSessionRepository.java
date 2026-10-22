package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.FlowExecutionSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FlowExecutionSessionRepository
        extends JpaRepository<FlowExecutionSession, Long> {

    Optional<FlowExecutionSession>
    findByOrganizationIdAndCustomerPhoneNumber(
        Long organizationId,
        String customerPhoneNumber
    );
}
