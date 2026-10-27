package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.OrganizationPaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationPaymentSettingsRepository
        extends JpaRepository<OrganizationPaymentSettings, Long> {

    Optional<OrganizationPaymentSettings> findByOrganizationId(Long organizationId);
}
