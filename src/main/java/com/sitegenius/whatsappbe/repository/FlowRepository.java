package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.Flow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowRepository extends JpaRepository<Flow, Long> {

    List<Flow> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    Optional<Flow> findByIdAndOrganizationId(
            Long id,
            Long organizationId
    );

    boolean existsByNameAndOrganizationId(
            String name,
            Long organizationId
    );
}