package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    List<Contact> findByOrganizationId(Long organizationId);

    Optional<Contact> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    boolean existsByOrganizationIdAndPhoneNumber(
            Long organizationId,
            String phoneNumber
    );
    Optional<Contact> findByOrganizationIdAndPhoneNumber(
            Long organizationId,
            String phoneNumber
    );
}