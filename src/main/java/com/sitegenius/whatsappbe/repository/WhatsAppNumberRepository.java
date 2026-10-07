package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.WhatsAppNumber;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WhatsAppNumberRepository
        extends JpaRepository<WhatsAppNumber, Long> {

    List<WhatsAppNumber> findByOrganizationId(Long organizationId);

    Optional<WhatsAppNumber> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<WhatsAppNumber> findByPhoneNumberId(String phoneNumberId);

    Optional<WhatsAppNumber> findFirstByOrganizationIdAndStatus(
            Long organizationId,
            String status
    );
}